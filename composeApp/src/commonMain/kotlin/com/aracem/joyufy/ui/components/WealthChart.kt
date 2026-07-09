package com.aracem.joyufy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aracem.joyufy.domain.model.Account
import com.aracem.joyufy.ui.dashboard.AccountPoint
import com.aracem.joyufy.ui.dashboard.ChartMode
import com.aracem.joyufy.ui.dashboard.WealthPoint
import com.aracem.joyufy.ui.theme.Accent
import com.aracem.joyufy.ui.theme.Negative
import com.aracem.joyufy.ui.theme.Positive
import com.aracem.joyufy.ui.theme.joyufyColors

// ── Y-axis range ─────────────────────────────────────────────────────────────
//
// Choose a Y range that makes movement visible. Two cases lift the baseline
// above zero:
//   - The data sits clearly above zero (min > 3% of max), OR
//   - The data is concentrated: relative spread is tiny vs absolute value
//     ((max-min)/max < 10%) — these are the cases where a 0-anchored chart
//     looks dead-flat even though something is changing.
// When we lift, headroom is expressed as a fraction of the visible span
// rather than of the absolute value, so the data hugs the chart edges
// regardless of how large the numbers are.
//
// Returns Pair(yMin, yMax) ready to use as chart bounds.
private fun smartYRange(rawMin: Double, rawMax: Double): Pair<Double, Double> {
    val safeMax = rawMax.coerceAtLeast(rawMin + 1.0)
    val rawRange = safeMax - rawMin
    val relativeSpread = if (safeMax > 0) rawRange / safeMax else 1.0
    val liftBaseline = rawMin > 0 && (rawMin > safeMax * 0.03 || relativeSpread < 0.10)
    return if (liftBaseline) {
        // 12% headroom below the min and 12% above the max, both measured as
        // a fraction of the visible span. Guarantees ~76% of the chart height
        // is occupied by data movement, no matter how concentrated.
        val pad = rawRange * 0.12
        Pair((rawMin - pad).coerceAtLeast(0.0), safeMax + pad)
    } else {
        Pair(rawMin.coerceAtMost(0.0), safeMax * 1.05)
    }
}

// ── Layout constants ──────────────────────────────────────────────────────────

private val leftPadding = 56f
private val rightPadding = 8f
private val topPadding = 8f
private val bottomPadding = 24f

// ── Formatters ────────────────────────────────────────────────────────────────

private fun Long.toShortDate(): String = formatShortDate()
private fun Long.toLongDate(): String = formatDate()

private fun Double.toShortAmount(): String = formatAmountCompact()

// ── Main composable ───────────────────────────────────────────────────────────

@Composable
fun WealthChart(
    points: List<WealthPoint>,
    mode: ChartMode,
    hiddenAccountIds: Set<Long> = emptySet(),
    showTotal: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val filteredPoints = remember(points, hiddenAccountIds, showTotal) {
        points.map { wp ->
            val visibleByAccount = wp.byAccount.filter { it.account.id !in hiddenAccountIds }
            val visibleTotal = visibleByAccount.sumOf { it.balance }
            wp.copy(totalWealth = visibleTotal, byAccount = visibleByAccount)
        }
    }

    if (filteredPoints.isEmpty()) {
        Box(modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.joyufyColors.contentSecondary.copy(alpha = 0.3f),
                    modifier = Modifier.size(36.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Sin datos aún",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.joyufyColors.contentSecondary,
                )
            }
        }
        return
    }

    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.joyufyColors.contentSecondary
    val gridColor = MaterialTheme.joyufyColors.border
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val secondaryColor = MaterialTheme.joyufyColors.contentSecondary

    // hoverX drives only Canvas redraws — no Box-level recomposition
    var hoverX by remember { mutableStateOf<Float?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Move -> hoverX = event.changes.firstOrNull()?.position?.x
                            PointerEventType.Exit -> hoverX = null
                            else -> {}
                        }
                    }
                }
            }
    ) {
        val w = size.width
        val chartLeft = leftPadding
        val chartRight = w - rightPadding
        val chartW = chartRight - chartLeft

        val hoveredIndex: Int? = run {
            val x = hoverX ?: return@run null
            if (filteredPoints.size <= 1) return@run if (filteredPoints.isNotEmpty()) 0 else null
            val relX = (x - chartLeft).coerceIn(0f, chartW)
            ((relX / chartW) * (filteredPoints.size - 1)).toInt().coerceIn(0, filteredPoints.size - 1)
        }

        when (mode) {
            ChartMode.AREA -> drawAreaChart(
                points = filteredPoints,
                measurer = textMeasurer,
                labelColor = labelColor,
                gridColor = gridColor,
                hoveredIndex = hoveredIndex,
                showTotalLine = showTotal,
                surfaceColor = surfaceColor,
                onSurfaceColor = onSurfaceColor,
                secondaryColor = secondaryColor,
            )
            ChartMode.BARS -> drawBarChart(
                points = filteredPoints,
                measurer = textMeasurer,
                labelColor = labelColor,
                gridColor = gridColor,
                hoveredIndex = hoveredIndex,
                surfaceColor = surfaceColor,
                onSurfaceColor = onSurfaceColor,
                secondaryColor = secondaryColor,
            )
        }
    }
}

// ── Single-account chart (for AccountDetailScreen) ────────────────────────────

@Composable
fun SingleAccountChart(
    points: List<com.aracem.joyufy.ui.account.SingleAccountPoint>,
    account: Account,
    mode: ChartMode,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) {
        Box(modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                tint = MaterialTheme.joyufyColors.contentSecondary.copy(alpha = 0.3f),
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Sin datos aún",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.joyufyColors.contentSecondary,
            )
            } // Column
        }
        return
    }

    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.joyufyColors.contentSecondary
    val gridColor = MaterialTheme.joyufyColors.border
    val lineColor = account.color
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val secondaryColor = MaterialTheme.joyufyColors.contentSecondary

    var hoverX by remember { mutableStateOf<Float?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Move -> hoverX = event.changes.firstOrNull()?.position?.x
                            PointerEventType.Exit -> hoverX = null
                            else -> {}
                        }
                    }
                }
            }
    ) {
        val w = size.width
        val chartLeft = leftPadding
        val chartRight = w - rightPadding
        val chartW = chartRight - chartLeft

        val hoveredIndex: Int? = run {
            val x = hoverX ?: return@run null
            if (points.size <= 1) return@run if (points.isNotEmpty()) 0 else null
            val relX = (x - chartLeft).coerceIn(0f, chartW)
            ((relX / chartW) * (points.size - 1)).toInt().coerceIn(0, points.size - 1)
        }

        when (mode) {
            ChartMode.AREA -> drawSingleAreaChart(
                points, textMeasurer, labelColor, gridColor, lineColor, hoveredIndex,
                surfaceColor, onSurfaceColor, secondaryColor,
            )
            ChartMode.BARS -> drawSingleBarChart(
                points, textMeasurer, labelColor, gridColor, lineColor, hoveredIndex,
                surfaceColor, onSurfaceColor, secondaryColor, account.name,
            )
        }
    }
}

// ── Canvas tooltip helpers ────────────────────────────────────────────────────

private fun DrawScope.drawTooltipBackground(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    surfaceColor: Color,
    borderColor: Color,
) {
    drawRoundRect(
        color = surfaceColor.copy(alpha = 0.96f),
        topLeft = Offset(x, y),
        size = Size(width, height),
        cornerRadius = CornerRadius(6.dp.toPx()),
    )
    drawRoundRect(
        color = borderColor.copy(alpha = 0.15f),
        topLeft = Offset(x, y),
        size = Size(width, height),
        cornerRadius = CornerRadius(6.dp.toPx()),
        style = Stroke(width = 0.5f.dp.toPx()),
    )
}

/**
 * Computes the absolute and percentage change from baseline to current,
 * formatted with sign and a colored visual.
 * Returns null when baseline is null or zero (no meaningful change to show).
 */
private data class ValueChange(val label: String, val color: Color)

private fun valueChange(baseline: Double?, current: Double, secondaryColor: Color): ValueChange? {
    if (baseline == null || baseline == 0.0) return null
    val delta = current - baseline
    val pct = (current - baseline) / baseline * 100.0
    if (kotlin.math.abs(delta) < 0.005) return ValueChange("0,00 € (0.00%)", secondaryColor)
    val sign = if (delta > 0) "+" else ""
    val color = if (delta >= 0) Positive else Negative
    return ValueChange("$sign${delta.formatCurrency()} ($sign${pct.formatPercent()})", color)
}

/**
 * Draws a multi-account tooltip entirely on canvas.
 * Returns nothing — pure side effect on DrawScope.
 *
 * Baseline values are used to render the % change vs the first visible point.
 */
private fun DrawScope.drawWealthTooltip(
    measurer: TextMeasurer,
    point: WealthPoint,
    hoverXPx: Float,
    showTotal: Boolean,
    surfaceColor: Color,
    onSurfaceColor: Color,
    secondaryColor: Color,
    totalBaseline: Double? = null,
    accountBaselines: Map<Long, Double> = emptyMap(),
) {
    val paddingH = 10.dp.toPx()
    val paddingV = 7.dp.toPx()
    val rowHeight = 16.dp.toPx()
    val dotSize = 7.dp.toPx()
    val dotTextGap = 5.dp.toPx()
    val colGap = 6.dp.toPx()
    val pctGap = 6.dp.toPx()

    // Pre-measure everything using an explicit large constraint so the measurer
    // never sees the canvas height and never crashes with negative maxHeight.
    val constraints = androidx.compose.ui.unit.Constraints(maxWidth = 600, maxHeight = 400)
    val dateStyle = TextStyle(color = secondaryColor, fontSize = 10.sp)
    val totalStyle = TextStyle(color = onSurfaceColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    val rowLabelStyle = TextStyle(color = secondaryColor, fontSize = 10.sp)
    val rowValueStyle = TextStyle(color = onSurfaceColor, fontSize = 10.sp)

    val dateLr = measurer.measure(point.weekDate.toLongDate(), dateStyle, constraints = constraints)
    val totalLr = if (showTotal) measurer.measure(point.totalWealth.formatCurrency(), totalStyle, constraints = constraints) else null
    val totalChange = if (showTotal) valueChange(totalBaseline, point.totalWealth, secondaryColor) else null
    val totalChangeLr = totalChange?.let {
        measurer.measure(it.label, TextStyle(color = it.color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold), constraints = constraints)
    }

    data class RowLr(
        val color: Color,
        val nameLr: androidx.compose.ui.text.TextLayoutResult,
        val valueLr: androidx.compose.ui.text.TextLayoutResult,
        val changeLr: androidx.compose.ui.text.TextLayoutResult?,
    )
    val rows = point.byAccount.map { ap ->
        val change = valueChange(accountBaselines[ap.account.id], ap.balance, secondaryColor)
        RowLr(
            ap.account.color,
            measurer.measure(ap.account.name, rowLabelStyle, constraints = constraints),
            measurer.measure(ap.balance.formatCurrency(), rowValueStyle, constraints = constraints),
            change?.let { measurer.measure(it.label, TextStyle(color = it.color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold), constraints = constraints) },
        )
    }

    val totalLineW = (totalLr?.size?.width?.toFloat() ?: 0f) +
        (totalChangeLr?.let { pctGap + it.size.width } ?: 0f)
    val maxRowContentW = rows.maxOfOrNull {
        dotSize + dotTextGap + it.nameLr.size.width + colGap + it.valueLr.size.width +
            (it.changeLr?.let { p -> pctGap + p.size.width } ?: 0f)
    } ?: 0f
    val contentW = maxOf(dateLr.size.width.toFloat(), totalLineW, maxRowContentW)
    val tooltipW = contentW + paddingH * 2

    var tooltipH = paddingV + dateLr.size.height
    if (totalLr != null) tooltipH += 4.dp.toPx() + totalLr.size.height
    if (rows.isNotEmpty()) {
        if (totalLr != null) tooltipH += 5.dp.toPx()
        tooltipH += rows.size * rowHeight
    }
    tooltipH += paddingV

    val gap = 10.dp.toPx()
    val tooltipX = if (hoverXPx + gap + tooltipW > size.width) {
        (hoverXPx - gap - tooltipW).coerceAtLeast(0f)
    } else {
        (hoverXPx + gap).coerceAtMost(size.width - tooltipW)
    }
    val tooltipY = (topPadding + 2.dp.toPx()).coerceAtMost(size.height - tooltipH).coerceAtLeast(0f)

    drawTooltipBackground(tooltipX, tooltipY, tooltipW, tooltipH, surfaceColor, onSurfaceColor)

    var curY = tooltipY + paddingV

    // Use drawText(TextLayoutResult) — does NOT recompute constraints, no crash possible
    drawText(dateLr, topLeft = Offset(tooltipX + paddingH, curY))
    curY += dateLr.size.height

    if (totalLr != null) {
        curY += 4.dp.toPx()
        drawText(totalLr, topLeft = Offset(tooltipX + paddingH, curY))
        if (totalChangeLr != null) {
            // Right-align the % badge on the same row as the total value, sharing baseline.
            val pctX = tooltipX + tooltipW - paddingH - totalChangeLr.size.width
            val pctY = curY + (totalLr.size.height - totalChangeLr.size.height) / 2f
            drawText(totalChangeLr, topLeft = Offset(pctX, pctY))
        }
        curY += totalLr.size.height
    }

    if (rows.isNotEmpty()) {
        if (totalLr != null) curY += 5.dp.toPx()
        rows.forEach { row ->
            val rowMidY = curY + rowHeight / 2f
            drawCircle(color = row.color, radius = dotSize / 2f, center = Offset(tooltipX + paddingH + dotSize / 2f, rowMidY))
            drawText(row.nameLr, topLeft = Offset(tooltipX + paddingH + dotSize + dotTextGap, rowMidY - row.nameLr.size.height / 2f))
            // Right edge: % badge first, then value with a gap, so layout reads "name … value  +1.2%"
            val pctW = row.changeLr?.size?.width ?: 0
            val pctX = tooltipX + tooltipW - paddingH - pctW
            val valX = pctX - (if (row.changeLr != null) pctGap else 0f) - row.valueLr.size.width
            drawText(row.valueLr, topLeft = Offset(valX, rowMidY - row.valueLr.size.height / 2f))
            row.changeLr?.let { drawText(it, topLeft = Offset(pctX, rowMidY - it.size.height / 2f)) }
            curY += rowHeight
        }
    }
}

/**
 * Draws a single-account tooltip entirely on canvas.
 */
private fun DrawScope.drawSingleTooltip(
    measurer: TextMeasurer,
    weekDate: Long,
    balance: Double,
    accountName: String,
    accountColor: Color,
    hoverXPx: Float,
    surfaceColor: Color,
    onSurfaceColor: Color,
    secondaryColor: Color,
    baseline: Double? = null,
) {
    val paddingH = 10.dp.toPx()
    val paddingV = 7.dp.toPx()
    val dotSize = 7.dp.toPx()
    val dotTextGap = 5.dp.toPx()
    val pctGap = 6.dp.toPx()

    val constraints = androidx.compose.ui.unit.Constraints(maxWidth = 400, maxHeight = 200)
    val dateStyle = TextStyle(color = secondaryColor, fontSize = 10.sp)
    val valueStyle = TextStyle(color = onSurfaceColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)

    val dateLr = measurer.measure(weekDate.toLongDate(), dateStyle, constraints = constraints)
    val valueLr = measurer.measure(balance.formatCurrency(), valueStyle, constraints = constraints)
    val change = valueChange(baseline, balance, secondaryColor)
    val changeLr = change?.let {
        measurer.measure(it.label, TextStyle(color = it.color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold), constraints = constraints)
    }

    val valueRowW = dotSize + dotTextGap + valueLr.size.width + (changeLr?.let { pctGap + it.size.width } ?: 0f)
    val contentW = maxOf(dateLr.size.width.toFloat(), valueRowW)
    val tooltipW = contentW + paddingH * 2
    val tooltipH = paddingV + dateLr.size.height + 4.dp.toPx() + dotSize + paddingV

    val gap = 10.dp.toPx()
    val tooltipX = if (hoverXPx + gap + tooltipW > size.width) {
        (hoverXPx - gap - tooltipW).coerceAtLeast(0f)
    } else {
        (hoverXPx + gap).coerceAtMost(size.width - tooltipW)
    }
    val tooltipY = (topPadding + 2.dp.toPx()).coerceAtMost(size.height - tooltipH).coerceAtLeast(0f)

    drawTooltipBackground(tooltipX, tooltipY, tooltipW, tooltipH, surfaceColor, onSurfaceColor)

    var curY = tooltipY + paddingV
    drawText(dateLr, topLeft = Offset(tooltipX + paddingH, curY))
    curY += dateLr.size.height + 4.dp.toPx()

    val rowMidY = curY + dotSize / 2f
    drawCircle(color = accountColor, radius = dotSize / 2f, center = Offset(tooltipX + paddingH + dotSize / 2f, rowMidY))
    drawText(valueLr, topLeft = Offset(tooltipX + paddingH + dotSize + dotTextGap, rowMidY - valueLr.size.height / 2f))
    changeLr?.let {
        val pctX = tooltipX + tooltipW - paddingH - it.size.width
        drawText(it, topLeft = Offset(pctX, rowMidY - it.size.height / 2f))
    }
}

// ── Area chart (multi-account) ────────────────────────────────────────────────

private fun DrawScope.drawAreaChart(
    points: List<WealthPoint>,
    measurer: TextMeasurer,
    labelColor: Color,
    gridColor: Color,
    hoveredIndex: Int?,
    showTotalLine: Boolean = true,
    surfaceColor: Color,
    onSurfaceColor: Color,
    secondaryColor: Color,
) {
    val w = size.width
    val h = size.height
    val chartLeft = leftPadding
    val chartRight = w - rightPadding
    val chartTop = topPadding
    val chartBottom = h - bottomPadding
    val chartW = chartRight - chartLeft
    val chartH = chartBottom - chartTop

    val allValues = points.flatMap { wp ->
        val accountValues = wp.byAccount.map { it.balance }
        if (showTotalLine) listOf(wp.totalWealth) + accountValues else accountValues
    }
    val (minVal, maxVal) = smartYRange(allValues.minOrNull()!!, allValues.maxOrNull()!!)
    val range = maxVal - minVal

    fun xOf(i: Int): Float = if (points.size == 1) chartLeft + chartW / 2f
        else chartLeft + (i.toFloat() / (points.size - 1)) * chartW
    fun yOf(v: Double): Float = chartBottom - ((v - minVal) / range * chartH).toFloat()

    drawGridAndLabels(measurer, labelColor, gridColor, chartLeft, chartRight, chartTop, chartBottom,
        chartW, chartH, minVal, maxVal, range, points.size) { i -> xOf(i) }
    drawXLabels(measurer, labelColor, chartBottom, chartLeft, points.size) { i ->
        Pair(xOf(i), points[i].weekDate.toShortDate())
    }

    if (points.size < 2) {
        drawCircle(color = Accent, radius = 5.dp.toPx(), center = Offset(xOf(0), yOf(points[0].totalWealth)))
        return
    }

    // ── Total area fill ───────────────────────────────────────────────────────
    if (showTotalLine) {
        val fillPath = Path().apply {
            moveTo(xOf(0), chartBottom)
            lineTo(xOf(0), yOf(points[0].totalWealth))
            for (i in 1 until points.size) {
                lineTo(xOf(i), yOf(points[i].totalWealth))
            }
            lineTo(xOf(points.size - 1), chartBottom)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(Accent.copy(alpha = 0.20f), Color.Transparent),
                startY = chartTop,
                endY = chartBottom,
            ),
        )
    }

    // ── Per-account colored lines ─────────────────────────────────────────────
    val accounts = points.flatMap { it.byAccount.map { ap -> ap.account } }
        .distinctBy { it.id }
    accounts.forEach { account ->
        val accountColor = account.color
        val linePath = Path().apply {
            var started = false
            points.forEachIndexed { i, wp ->
                val ap = wp.byAccount.find { it.account.id == account.id } ?: return@forEachIndexed
                val y = yOf(ap.balance)
                if (!started) {
                    moveTo(xOf(i), y)
                    started = true
                } else {
                    lineTo(xOf(i), y)
                }
            }
        }
        drawPath(
            path = linePath,
            color = accountColor.copy(alpha = 0.7f),
            style = Stroke(width = 1.5f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }

    // ── Total line ────────────────────────────────────────────────────────────
    if (showTotalLine) {
        val totalLinePath = Path().apply {
            moveTo(xOf(0), yOf(points[0].totalWealth))
            for (i in 1 until points.size) {
                lineTo(xOf(i), yOf(points[i].totalWealth))
            }
        }
        drawPath(
            path = totalLinePath,
            color = Accent,
            style = Stroke(width = 2.5f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawCircle(
            color = Accent,
            radius = 6.dp.toPx(),
            center = Offset(xOf(points.size - 1), yOf(points.last().totalWealth)),
        )
    }

    // ── Hover: vertical line + dots + tooltip ─────────────────────────────────
    if (hoveredIndex != null) {
        val hx = xOf(hoveredIndex)
        drawLine(
            color = Accent.copy(alpha = 0.5f),
            start = Offset(hx, chartTop),
            end = Offset(hx, chartBottom),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
        )
        if (showTotalLine) {
            drawCircle(
                color = Accent,
                radius = 5.dp.toPx(),
                center = Offset(hx, yOf(points[hoveredIndex].totalWealth)),
            )
        }
        points[hoveredIndex].byAccount.forEach { ap ->
            drawCircle(
                color = ap.account.color,
                radius = 3.dp.toPx(),
                center = Offset(hx, yOf(ap.balance)),
            )
        }
        drawWealthTooltip(
            measurer = measurer,
            point = points[hoveredIndex],
            hoverXPx = hx,
            showTotal = showTotalLine,
            surfaceColor = surfaceColor,
            onSurfaceColor = onSurfaceColor,
            secondaryColor = secondaryColor,
            totalBaseline = points.first().totalWealth,
            accountBaselines = firstSeenBalances(points),
        )
    }
}

/**
 * For each account id, returns its balance at the first point where the
 * account appears. Used as the baseline for % change badges in tooltips.
 */
private fun firstSeenBalances(points: List<WealthPoint>): Map<Long, Double> {
    val out = HashMap<Long, Double>()
    points.forEach { wp ->
        wp.byAccount.forEach { ap ->
            if (ap.account.id !in out) out[ap.account.id] = ap.balance
        }
    }
    return out
}

// ── Bar chart (multi-account) ─────────────────────────────────────────────────

private fun DrawScope.drawBarChart(
    points: List<WealthPoint>,
    measurer: TextMeasurer,
    labelColor: Color,
    gridColor: Color,
    hoveredIndex: Int?,
    surfaceColor: Color,
    onSurfaceColor: Color,
    secondaryColor: Color,
) {
    val w = size.width
    val h = size.height
    val chartLeft = leftPadding
    val chartRight = w - rightPadding
    val chartTop = topPadding
    val chartBottom = h - bottomPadding
    val chartW = chartRight - chartLeft
    val chartH = chartBottom - chartTop

    val allBarValues = points.flatMap { wp ->
        listOf(wp.totalWealth) + wp.byAccount.map { it.balance }
    }
    val (minVal, maxVal) = smartYRange(allBarValues.minOrNull()!!, allBarValues.maxOrNull()!!)
    val range = maxVal - minVal

    val barCount = points.size
    val slotW = chartW / barCount
    val barW = (slotW * 0.55f).coerceAtLeast(3f)

    drawGridAndLabels(measurer, labelColor, gridColor, chartLeft, chartRight, chartTop, chartBottom,
        chartW, chartH, minVal, maxVal, range, points.size) { i ->
        chartLeft + i * slotW + slotW / 2f
    }

    val accounts = if (points.isNotEmpty()) points.first().byAccount.map { it.account } else emptyList()

    points.forEachIndexed { i, point ->
        val x = chartLeft + i * slotW + (slotW - barW) / 2f
        val isHovered = i == hoveredIndex

        if (accounts.isEmpty() || point.byAccount.isEmpty()) {
            val barH = ((point.totalWealth - minVal) / range * chartH).toFloat().coerceAtLeast(2f)
            val y = chartBottom - barH
            drawRoundRect(
                color = if (isHovered) Accent else Accent.copy(alpha = 0.85f),
                topLeft = Offset(x, y),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
        } else {
            var stackBottom = chartBottom
            point.byAccount.forEach { ap ->
                if (ap.balance > minVal) {
                    val segH = (((ap.balance - minVal) / range) * chartH).toFloat().coerceAtLeast(1f)
                    val segY = stackBottom - segH
                    drawRoundRect(
                        color = ap.account.color.copy(alpha = if (isHovered) 1f else 0.85f),
                        topLeft = Offset(x, segY),
                        size = Size(barW, segH),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                    stackBottom -= segH
                }
            }
        }

        if (isHovered) {
            val totalBarH = ((point.totalWealth - minVal) / range * chartH).toFloat().coerceAtLeast(2f)
            drawRoundRect(
                color = Accent.copy(alpha = 0.4f),
                topLeft = Offset(x - 1f, chartBottom - totalBarH - 1f),
                size = Size(barW + 2f, totalBarH + 2f),
                cornerRadius = CornerRadius(3.dp.toPx()),
                style = Stroke(width = 1.5f),
            )
        }
    }

    val labelStyle = TextStyle(color = labelColor, fontSize = 10.sp)
    val safeConstraints = androidx.compose.ui.unit.Constraints(maxWidth = 200, maxHeight = 60)
    val sampleWidth = measurer.measure("00/00", labelStyle, constraints = safeConstraints).size.width + 8f
    val maxLabels = (chartW / sampleWidth).toInt().coerceAtLeast(2)
    val step = (points.size.toFloat() / maxLabels).coerceAtLeast(1f)
    val xLabelIndices = (0 until points.size).filter { i ->
        i == 0 || i == points.size - 1 || (i % step.toInt() == 0)
    }
    xLabelIndices.forEach { i ->
        val lr = measurer.measure(points[i].weekDate.toShortDate(), labelStyle, constraints = safeConstraints)
        val x = chartLeft + i * slotW + slotW / 2f - lr.size.width / 2f
        drawText(lr, topLeft = Offset(x, chartBottom + 4f))
    }

    // Tooltip
    if (hoveredIndex != null) {
        val barCenterX = chartLeft + hoveredIndex * slotW + slotW / 2f
        drawWealthTooltip(
            measurer = measurer,
            point = points[hoveredIndex],
            hoverXPx = barCenterX,
            showTotal = true,
            surfaceColor = surfaceColor,
            onSurfaceColor = onSurfaceColor,
            secondaryColor = secondaryColor,
            totalBaseline = points.first().totalWealth,
            accountBaselines = firstSeenBalances(points),
        )
    }
}

// ── Single-account area chart ─────────────────────────────────────────────────

private fun DrawScope.drawSingleAreaChart(
    points: List<com.aracem.joyufy.ui.account.SingleAccountPoint>,
    measurer: TextMeasurer,
    labelColor: Color,
    gridColor: Color,
    lineColor: Color,
    hoveredIndex: Int? = null,
    surfaceColor: Color,
    onSurfaceColor: Color,
    secondaryColor: Color,
) {
    val w = size.width
    val h = size.height
    val chartLeft = leftPadding
    val chartRight = w - rightPadding
    val chartTop = topPadding
    val chartBottom = h - bottomPadding
    val chartW = chartRight - chartLeft
    val chartH = chartBottom - chartTop

    val (minVal, maxVal) = smartYRange(points.minOf { it.balance }, points.maxOf { it.balance })
    val range = maxVal - minVal

    fun xOf(i: Int): Float = if (points.size == 1) chartLeft + chartW / 2f
        else chartLeft + (i.toFloat() / (points.size - 1)) * chartW
    fun yOf(v: Double): Float = chartBottom - ((v - minVal) / range * chartH).toFloat()

    drawGridAndLabels(measurer, labelColor, gridColor, chartLeft, chartRight, chartTop, chartBottom,
        chartW, chartH, minVal, maxVal, range, points.size) { i -> xOf(i) }
    drawXLabels(measurer, labelColor, chartBottom, chartLeft, points.size) { i ->
        Pair(xOf(i), points[i].weekDate.toShortDate())
    }

    if (points.size < 2) {
        drawCircle(color = lineColor, radius = 5.dp.toPx(), center = Offset(xOf(0), yOf(points[0].balance)))
        return
    }

    val fillPath = Path().apply {
        moveTo(xOf(0), chartBottom)
        lineTo(xOf(0), yOf(points[0].balance))
        for (i in 1 until points.size) {
            lineTo(xOf(i), yOf(points[i].balance))
        }
        lineTo(xOf(points.size - 1), chartBottom)
        close()
    }
    drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
            colors = listOf(lineColor.copy(alpha = 0.30f), Color.Transparent),
            startY = chartTop,
            endY = chartBottom,
        ),
    )

    val linePath = Path().apply {
        moveTo(xOf(0), yOf(points[0].balance))
        for (i in 1 until points.size) {
            lineTo(xOf(i), yOf(points[i].balance))
        }
    }
    drawPath(
        path = linePath,
        color = lineColor,
        style = Stroke(width = 2.5f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    drawCircle(
        color = lineColor,
        radius = 6.dp.toPx(),
        center = Offset(xOf(points.size - 1), yOf(points.last().balance)),
    )

    if (hoveredIndex != null) {
        val hx = xOf(hoveredIndex)
        drawLine(
            color = lineColor.copy(alpha = 0.5f),
            start = Offset(hx, chartTop),
            end = Offset(hx, chartBottom),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
        )
        drawCircle(
            color = lineColor,
            radius = 5.dp.toPx(),
            center = Offset(hx, yOf(points[hoveredIndex].balance)),
        )
        drawSingleTooltip(
            measurer = measurer,
            weekDate = points[hoveredIndex].weekDate,
            balance = points[hoveredIndex].balance,
            accountName = "",
            accountColor = lineColor,
            hoverXPx = hx,
            surfaceColor = surfaceColor,
            onSurfaceColor = onSurfaceColor,
            secondaryColor = secondaryColor,
            baseline = points.first().balance,
        )
    }
}

// ── Single-account bar chart ──────────────────────────────────────────────────

private fun DrawScope.drawSingleBarChart(
    points: List<com.aracem.joyufy.ui.account.SingleAccountPoint>,
    measurer: TextMeasurer,
    labelColor: Color,
    gridColor: Color,
    barColor: Color,
    hoveredIndex: Int? = null,
    surfaceColor: Color,
    onSurfaceColor: Color,
    secondaryColor: Color,
    accountName: String,
) {
    val w = size.width
    val h = size.height
    val chartLeft = leftPadding
    val chartRight = w - rightPadding
    val chartTop = topPadding
    val chartBottom = h - bottomPadding
    val chartW = chartRight - chartLeft
    val chartH = chartBottom - chartTop

    val (minVal, maxVal) = smartYRange(points.minOf { it.balance }, points.maxOf { it.balance })
    val range = maxVal - minVal

    val barCount = points.size
    val slotW = chartW / barCount
    val barW = (slotW * 0.55f).coerceAtLeast(3f)

    drawGridAndLabels(measurer, labelColor, gridColor, chartLeft, chartRight, chartTop, chartBottom,
        chartW, chartH, minVal, maxVal, range, points.size) { i -> chartLeft + i * slotW + slotW / 2f }

    points.forEachIndexed { i, point ->
        val barH = ((point.balance - minVal) / range * chartH).toFloat().coerceAtLeast(2f)
        val x = chartLeft + i * slotW + (slotW - barW) / 2f
        val y = chartBottom - barH
        val isHovered = i == hoveredIndex
        drawRoundRect(
            color = if (isHovered) barColor else barColor.copy(alpha = 0.85f),
            topLeft = Offset(x, y),
            size = Size(barW, barH),
            cornerRadius = CornerRadius(3.dp.toPx()),
        )
        if (isHovered) {
            drawRoundRect(
                color = barColor.copy(alpha = 0.4f),
                topLeft = Offset(x - 1f, y - 1f),
                size = Size(barW + 2f, barH + 2f),
                cornerRadius = CornerRadius(3.dp.toPx()),
                style = Stroke(width = 1.5f),
            )
        }
    }

    val labelStyle = TextStyle(color = labelColor, fontSize = 10.sp)
    val safeConstraints = androidx.compose.ui.unit.Constraints(maxWidth = 200, maxHeight = 60)
    val sampleWidth = measurer.measure("00/00", labelStyle, constraints = safeConstraints).size.width + 8f
    val maxLabels = (chartW / sampleWidth).toInt().coerceAtLeast(2)
    val step = (points.size.toFloat() / maxLabels).coerceAtLeast(1f)
    val xLabelIndices = (0 until points.size).filter { i ->
        i == 0 || i == points.size - 1 || (i % step.toInt() == 0)
    }
    xLabelIndices.forEach { i ->
        val lr = measurer.measure(points[i].weekDate.toShortDate(), labelStyle, constraints = safeConstraints)
        val x = chartLeft + i * slotW + slotW / 2f - lr.size.width / 2f
        drawText(lr, topLeft = Offset(x, chartBottom + 4f))
    }

    if (hoveredIndex != null) {
        val barCenterX = chartLeft + hoveredIndex * slotW + slotW / 2f
        drawSingleTooltip(
            measurer = measurer,
            weekDate = points[hoveredIndex].weekDate,
            balance = points[hoveredIndex].balance,
            accountName = accountName,
            accountColor = barColor,
            hoverXPx = barCenterX,
            surfaceColor = surfaceColor,
            onSurfaceColor = onSurfaceColor,
            secondaryColor = secondaryColor,
            baseline = points.first().balance,
        )
    }
}

// ── Shared drawing helpers ────────────────────────────────────────────────────

private fun DrawScope.drawGridAndLabels(
    measurer: TextMeasurer,
    labelColor: Color,
    gridColor: Color,
    chartLeft: Float,
    chartRight: Float,
    chartTop: Float,
    chartBottom: Float,
    chartW: Float,
    chartH: Float,
    minVal: Double,
    maxVal: Double,
    range: Double,
    pointCount: Int,
    xOf: (Int) -> Float,
) {
    val labelStyle = TextStyle(color = labelColor, fontSize = 10.sp)
    val safeConstraints = androidx.compose.ui.unit.Constraints(maxWidth = 200, maxHeight = 60)
    val ySteps = 4
    repeat(ySteps + 1) { step ->
        val fraction = step.toFloat() / ySteps
        val value = minVal + fraction * range
        val y = chartBottom - fraction * chartH
        drawLine(
            color = gridColor,
            start = Offset(chartLeft, y),
            end = Offset(chartRight, y),
            strokeWidth = 0.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
        )
        val lr = measurer.measure(value.toShortAmount(), labelStyle, constraints = safeConstraints)
        drawText(lr, topLeft = Offset(chartLeft - lr.size.width - 6f, y - lr.size.height / 2f))
    }
}

private fun DrawScope.drawXLabels(
    measurer: TextMeasurer,
    labelColor: Color,
    chartBottom: Float,
    chartLeft: Float,
    pointCount: Int,
    labelAt: (Int) -> Pair<Float, String>,
) {
    val labelStyle = TextStyle(color = labelColor, fontSize = 10.sp)
    val safeConstraints = androidx.compose.ui.unit.Constraints(maxWidth = 200, maxHeight = 60)
    val sampleLr = measurer.measure("00/00", labelStyle, constraints = safeConstraints)
    val sampleWidth = sampleLr.size.width + 8f
    val chartWidth = size.width - leftPadding - rightPadding
    val maxLabels = (chartWidth / sampleWidth).toInt().coerceAtLeast(2)
    val step = (pointCount.toFloat() / maxLabels).coerceAtLeast(1f)
    val xLabelIndices = (0 until pointCount).filter { i ->
        i == 0 || i == pointCount - 1 || (i % step.toInt() == 0)
    }
    xLabelIndices.forEach { i ->
        val (x, label) = labelAt(i)
        val lr = measurer.measure(label, labelStyle, constraints = safeConstraints)
        val lx = x - lr.size.width / 2f
        drawText(lr, topLeft = Offset(lx, chartBottom + 4f))
    }
}
