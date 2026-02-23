package com.aracem.joyufy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.aracem.joyufy.ui.theme.joyufyColors
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// ── Layout constants ──────────────────────────────────────────────────────────

private val leftPadding = 56f
private val rightPadding = 8f
private val topPadding = 8f
private val bottomPadding = 24f

// ── Formatters ────────────────────────────────────────────────────────────────

private fun Long.toShortDate(): String {
    val local = Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return "%d/%02d".format(local.dayOfMonth, local.monthNumber)
}

private fun Long.toLongDate(): String {
    val local = Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return "%02d/%02d/%04d".format(local.dayOfMonth, local.monthNumber, local.year)
}

private fun Double.toShortAmount(): String = when {
    this >= 1_000_000 -> "${"%.1f".format(this / 1_000_000)}M"
    this >= 1_000     -> "${"%.0f".format(this / 1_000)}k"
    else              -> "%.0f".format(this)
}

// ── Main composable ───────────────────────────────────────────────────────────

@Composable
fun WealthChart(
    points: List<WealthPoint>,
    mode: ChartMode,
    hiddenAccountIds: Set<Long> = emptySet(),
    showTotal: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // Filter points according to visibility
    val filteredPoints = remember(points, hiddenAccountIds, showTotal) {
        points.map { wp ->
            val visibleByAccount = wp.byAccount.filter { it.account.id !in hiddenAccountIds }
            val visibleTotal = if (showTotal) wp.totalWealth
                else visibleByAccount.sumOf { it.balance }
            wp.copy(totalWealth = visibleTotal, byAccount = visibleByAccount)
        }
    }

    if (filteredPoints.isEmpty()) {
        Box(modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            Text(
                "Sin datos aún",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.joyufyColors.contentSecondary,
            )
        }
        return
    }

    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.joyufyColors.contentSecondary
    val gridColor = MaterialTheme.joyufyColors.border
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val secondaryColor = MaterialTheme.joyufyColors.contentSecondary

    // Hover state
    var hoverX by remember { mutableStateOf<Float?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Compute the hovered index based on hoverX
    val hoveredIndex: Int? = remember(hoverX, filteredPoints, canvasSize) {
        val x = hoverX ?: return@remember null
        val w = canvasSize.width.toFloat()
        val chartLeft = leftPadding
        val chartRight = w - rightPadding
        val chartW = chartRight - chartLeft
        if (filteredPoints.size <= 1) return@remember if (filteredPoints.isNotEmpty()) 0 else null
        val relX = (x - chartLeft).coerceIn(0f, chartW)
        val idx = ((relX / chartW) * (filteredPoints.size - 1)).toInt().coerceIn(0, filteredPoints.size - 1)
        idx
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            when (event.type) {
                                PointerEventType.Move -> {
                                    val pos = event.changes.firstOrNull()?.position
                                    hoverX = pos?.x
                                }
                                PointerEventType.Exit -> {
                                    hoverX = null
                                }
                                else -> {}
                            }
                        }
                    }
                }
        ) {
            when (mode) {
                ChartMode.AREA -> drawAreaChart(
                    points = filteredPoints,
                    measurer = textMeasurer,
                    labelColor = labelColor,
                    gridColor = gridColor,
                    hoveredIndex = hoveredIndex,
                    showTotalLine = showTotal,
                )
                ChartMode.BARS -> drawBarChart(
                    points = filteredPoints,
                    measurer = textMeasurer,
                    labelColor = labelColor,
                    gridColor = gridColor,
                    hoveredIndex = hoveredIndex,
                )
            }
        }

        // Tooltip overlay
        if (hoveredIndex != null) {
            val point = filteredPoints[hoveredIndex]
            WealthTooltip(
                point = point,
                showTotal = showTotal,
                surfaceColor = surfaceColor,
                onSurfaceColor = onSurfaceColor,
                secondaryColor = secondaryColor,
            )
        }
    }
}

// ── Tooltip ───────────────────────────────────────────────────────────────────

@Composable
private fun BoxScope.WealthTooltip(
    point: WealthPoint,
    showTotal: Boolean,
    surfaceColor: Color,
    onSurfaceColor: Color,
    secondaryColor: Color,
) {
    Surface(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = 60.dp, top = 4.dp)
            .widthIn(min = 140.dp, max = 220.dp),
        color = surfaceColor.copy(alpha = 0.96f),
        shape = MaterialTheme.shapes.small,
        shadowElevation = 4.dp,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = point.weekDate.toLongDate(),
                style = MaterialTheme.typography.labelSmall,
                color = secondaryColor,
            )
            Spacer(Modifier.height(4.dp))
            if (showTotal) {
                Text(
                    text = point.totalWealth.formatCurrency(),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = onSurfaceColor,
                )
            }
            if (point.byAccount.isNotEmpty()) {
                if (showTotal) Spacer(Modifier.height(6.dp))
                point.byAccount.forEach { ap ->
                    AccountTooltipRow(ap)
                }
            }
        }
    }
}

@Composable
private fun AccountTooltipRow(ap: AccountPoint) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(ap.account.color)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = ap.account.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.joyufyColors.contentSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = ap.balance.formatCurrency(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
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
            Text(
                "Sin datos aún",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.joyufyColors.contentSecondary,
            )
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

    val hoveredIndex: Int? = remember(hoverX, points, canvasSize) {
        val x = hoverX ?: return@remember null
        val w = canvasSize.width.toFloat()
        val chartLeft = leftPadding
        val chartRight = w - rightPadding
        val chartW = chartRight - chartLeft
        if (points.size <= 1) return@remember if (points.isNotEmpty()) 0 else null
        val relX = (x - chartLeft).coerceIn(0f, chartW)
        ((relX / chartW) * (points.size - 1)).toInt().coerceIn(0, points.size - 1)
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
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
            when (mode) {
                ChartMode.AREA -> drawSingleAreaChart(points, textMeasurer, labelColor, gridColor, lineColor, hoveredIndex)
                ChartMode.BARS -> drawSingleBarChart(points, textMeasurer, labelColor, gridColor, lineColor, hoveredIndex)
            }
        }

        if (hoveredIndex != null) {
            val point = points[hoveredIndex]
            SingleAccountTooltip(
                point = point,
                accountName = account.name,
                accountColor = lineColor,
                surfaceColor = surfaceColor,
                onSurfaceColor = onSurfaceColor,
                secondaryColor = secondaryColor,
            )
        }
    }
}

@Composable
private fun BoxScope.SingleAccountTooltip(
    point: com.aracem.joyufy.ui.account.SingleAccountPoint,
    accountName: String,
    accountColor: Color,
    surfaceColor: Color,
    onSurfaceColor: Color,
    secondaryColor: Color,
) {
    Surface(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = 60.dp, top = 4.dp)
            .widthIn(min = 130.dp, max = 200.dp),
        color = surfaceColor.copy(alpha = 0.96f),
        shape = MaterialTheme.shapes.small,
        shadowElevation = 4.dp,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = point.weekDate.toLongDate(),
                style = MaterialTheme.typography.labelSmall,
                color = secondaryColor,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accountColor)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = point.balance.formatCurrency(),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = onSurfaceColor,
                )
            }
        }
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
) {
    val w = size.width
    val h = size.height
    val chartLeft = leftPadding
    val chartRight = w - rightPadding
    val chartTop = topPadding
    val chartBottom = h - bottomPadding
    val chartW = chartRight - chartLeft
    val chartH = chartBottom - chartTop

    val allValues = points.map { it.totalWealth }
    val minVal = allValues.minOrNull()!!.coerceAtMost(0.0)
    val maxVal = allValues.maxOrNull()!!.coerceAtLeast(minVal + 1.0)
    val range = maxVal - minVal

    fun xOf(i: Int): Float = if (points.size == 1) chartLeft + chartW / 2f
        else chartLeft + (i.toFloat() / (points.size - 1)) * chartW
    fun yOf(v: Double): Float = chartBottom - ((v - minVal) / range * chartH).toFloat()

    drawGridAndLabels(measurer, labelColor, gridColor, chartLeft, chartRight, chartTop, chartBottom,
        chartW, chartH, minVal, maxVal, range, points.size) { i ->
        xOf(i)
    }
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
                val cx = (xOf(i - 1) + xOf(i)) / 2f
                cubicTo(cx, yOf(points[i - 1].totalWealth), cx, yOf(points[i].totalWealth), xOf(i), yOf(points[i].totalWealth))
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
    val accounts = points.first().byAccount.map { it.account }
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
                    val cx = (xOf(i - 1) + xOf(i)) / 2f
                    val prevAp = points[i - 1].byAccount.find { it.account.id == account.id }
                    val prevY = if (prevAp != null) yOf(prevAp.balance) else y
                    cubicTo(cx, prevY, cx, y, xOf(i), y)
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
                val cx = (xOf(i - 1) + xOf(i)) / 2f
                cubicTo(cx, yOf(points[i - 1].totalWealth), cx, yOf(points[i].totalWealth), xOf(i), yOf(points[i].totalWealth))
            }
        }
        drawPath(
            path = totalLinePath,
            color = Accent,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawCircle(
            color = Accent,
            radius = 4.dp.toPx(),
            center = Offset(xOf(points.size - 1), yOf(points.last().totalWealth)),
        )
    }

    // ── Hover vertical line ───────────────────────────────────────────────────
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
    }
}

// ── Bar chart (multi-account) ─────────────────────────────────────────────────

private fun DrawScope.drawBarChart(
    points: List<WealthPoint>,
    measurer: TextMeasurer,
    labelColor: Color,
    gridColor: Color,
    hoveredIndex: Int?,
) {
    val w = size.width
    val h = size.height
    val chartLeft = leftPadding
    val chartRight = w - rightPadding
    val chartTop = topPadding
    val chartBottom = h - bottomPadding
    val chartW = chartRight - chartLeft
    val chartH = chartBottom - chartTop

    val minVal = points.minOf { it.totalWealth }.coerceAtMost(0.0)
    val maxVal = points.maxOf { it.totalWealth }.coerceAtLeast(minVal + 1.0)
    val range = maxVal - minVal

    val barCount = points.size
    val slotW = chartW / barCount
    val barW = (slotW * 0.55f).coerceAtLeast(3f)

    drawGridAndLabels(measurer, labelColor, gridColor, chartLeft, chartRight, chartTop, chartBottom,
        chartW, chartH, minVal, maxVal, range, points.size) { i ->
        chartLeft + i * slotW + slotW / 2f
    }

    // ── Bars (stacked by account) ─────────────────────────────────────────────
    val accounts = if (points.isNotEmpty()) points.first().byAccount.map { it.account } else emptyList()

    points.forEachIndexed { i, point ->
        val x = chartLeft + i * slotW + (slotW - barW) / 2f
        val isHovered = i == hoveredIndex

        if (accounts.isEmpty() || point.byAccount.isEmpty()) {
            // No account breakdown — draw single bar
            val barH = ((point.totalWealth - minVal) / range * chartH).toFloat().coerceAtLeast(2f)
            val y = chartBottom - barH
            drawRoundRect(
                color = if (isHovered) Accent else Accent.copy(alpha = 0.85f),
                topLeft = Offset(x, y),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
        } else {
            // Stacked bars per account
            var stackBottom = chartBottom
            point.byAccount.forEach { ap ->
                if (ap.balance > 0) {
                    val segH = ((ap.balance / (maxVal - minVal)) * chartH).toFloat().coerceAtLeast(1f)
                    val segY = stackBottom - segH
                    val barAlpha = if (isHovered) 1f else 0.85f
                    drawRoundRect(
                        color = ap.account.color.copy(alpha = barAlpha),
                        topLeft = Offset(x, segY),
                        size = Size(barW, segH),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                    stackBottom -= segH
                }
            }
        }

        if (isHovered) {
            // Highlight outline
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

    // ── X-axis date labels ────────────────────────────────────────────────────
    val labelStyle = TextStyle(color = labelColor, fontSize = 10.sp)
    val sampleWidth = measurer.measure("00/00", labelStyle).size.width + 8f
    val maxLabels = (chartW / sampleWidth).toInt().coerceAtLeast(2)
    val step = (points.size.toFloat() / maxLabels).coerceAtLeast(1f)
    val xLabelIndices = (0 until points.size).filter { i ->
        i == 0 || i == points.size - 1 || (i % step.toInt() == 0)
    }
    xLabelIndices.forEach { i ->
        val label = points[i].weekDate.toShortDate()
        val measured = measurer.measure(label, labelStyle)
        val x = chartLeft + i * slotW + slotW / 2f - measured.size.width / 2f
        drawText(measurer, label, topLeft = Offset(x, chartBottom + 4f), style = labelStyle)
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
) {
    val w = size.width
    val h = size.height
    val chartLeft = leftPadding
    val chartRight = w - rightPadding
    val chartTop = topPadding
    val chartBottom = h - bottomPadding
    val chartW = chartRight - chartLeft
    val chartH = chartBottom - chartTop

    val minVal = points.minOf { it.balance }.coerceAtMost(0.0)
    val maxVal = points.maxOf { it.balance }.coerceAtLeast(minVal + 1.0)
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
            val cx = (xOf(i - 1) + xOf(i)) / 2f
            cubicTo(cx, yOf(points[i - 1].balance), cx, yOf(points[i].balance), xOf(i), yOf(points[i].balance))
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
            val cx = (xOf(i - 1) + xOf(i)) / 2f
            cubicTo(cx, yOf(points[i - 1].balance), cx, yOf(points[i].balance), xOf(i), yOf(points[i].balance))
        }
    }
    drawPath(
        path = linePath,
        color = lineColor,
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    drawCircle(
        color = lineColor,
        radius = 4.dp.toPx(),
        center = Offset(xOf(points.size - 1), yOf(points.last().balance)),
    )

    // ── Hover vertical line + dot ─────────────────────────────────────────────
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
) {
    val w = size.width
    val h = size.height
    val chartLeft = leftPadding
    val chartRight = w - rightPadding
    val chartTop = topPadding
    val chartBottom = h - bottomPadding
    val chartW = chartRight - chartLeft
    val chartH = chartBottom - chartTop

    val minVal = points.minOf { it.balance }.coerceAtMost(0.0)
    val maxVal = points.maxOf { it.balance }.coerceAtLeast(minVal + 1.0)
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
    val sampleWidth = measurer.measure("00/00", labelStyle).size.width + 8f
    val maxLabels = (chartW / sampleWidth).toInt().coerceAtLeast(2)
    val step = (points.size.toFloat() / maxLabels).coerceAtLeast(1f)
    val xLabelIndices = (0 until points.size).filter { i ->
        i == 0 || i == points.size - 1 || (i % step.toInt() == 0)
    }
    xLabelIndices.forEach { i ->
        val label = points[i].weekDate.toShortDate()
        val measured = measurer.measure(label, labelStyle)
        val x = chartLeft + i * slotW + slotW / 2f - measured.size.width / 2f
        drawText(measurer, label, topLeft = Offset(x, chartBottom + 4f), style = labelStyle)
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
        val label = value.toShortAmount()
        val measured = measurer.measure(label, labelStyle)
        drawText(
            measurer,
            label,
            topLeft = Offset(chartLeft - measured.size.width - 6f, y - measured.size.height / 2f),
            style = labelStyle,
        )
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
    // Measure a sample label to know how much space each needs
    val sampleWidth = measurer.measure("00/00", labelStyle).size.width + 8f
    val chartWidth = size.width - leftPadding - rightPadding
    // How many labels fit without overlapping
    val maxLabels = (chartWidth / sampleWidth).toInt().coerceAtLeast(2)
    val step = (pointCount.toFloat() / maxLabels).coerceAtLeast(1f)
    val xLabelIndices = (0 until pointCount).filter { i ->
        i == 0 || i == pointCount - 1 || (i % step.toInt() == 0)
    }
    xLabelIndices.forEach { i ->
        val (x, label) = labelAt(i)
        val measured = measurer.measure(label, labelStyle)
        val lx = x - measured.size.width / 2f
        drawText(measurer, label, topLeft = Offset(lx, chartBottom + 4f), style = labelStyle)
    }
}
