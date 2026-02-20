package com.aracem.nexlify.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aracem.nexlify.ui.dashboard.ChartMode
import com.aracem.nexlify.ui.dashboard.WealthPoint
import com.aracem.nexlify.ui.theme.Accent
import com.aracem.nexlify.ui.theme.nexlifyColors
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun WealthChart(
    points: List<WealthPoint>,
    mode: ChartMode,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) {
        Box(modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            Text(
                "Sin datos aún",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.nexlifyColors.contentSecondary,
            )
        }
        return
    }

    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.nexlifyColors.contentSecondary
    val gridColor = MaterialTheme.nexlifyColors.border

    Canvas(modifier = modifier.fillMaxWidth().height(200.dp)) {
        when (mode) {
            ChartMode.AREA -> drawAreaChart(points, textMeasurer, labelColor, gridColor)
            ChartMode.BARS -> drawBarChart(points, textMeasurer, labelColor, gridColor)
        }
    }
}

// ── Layout constants ──────────────────────────────────────────────────────────

private val leftPadding = 56f   // space for Y-axis labels
private val rightPadding = 8f
private val topPadding = 8f
private val bottomPadding = 24f // space for X-axis labels

// ── Formatters ────────────────────────────────────────────────────────────────

private fun Long.toShortDate(): String {
    val local = Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return "%d/%02d".format(local.dayOfMonth, local.monthNumber)
}

private fun Double.toShortAmount(): String = when {
    this >= 1_000_000 -> "${"%.1f".format(this / 1_000_000)}M"
    this >= 1_000     -> "${"%.0f".format(this / 1_000)}k"
    else              -> "%.0f".format(this)
}

// ── Area chart ────────────────────────────────────────────────────────────────

private fun DrawScope.drawAreaChart(
    points: List<WealthPoint>,
    measurer: TextMeasurer,
    labelColor: Color,
    gridColor: Color,
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

    fun xOf(i: Int): Float = if (points.size == 1) chartLeft + chartW / 2f
        else chartLeft + (i.toFloat() / (points.size - 1)) * chartW
    fun yOf(v: Double): Float = chartBottom - ((v - minVal) / range * chartH).toFloat()

    // ── Y grid lines + labels (4 lines) ──────────────────────────────────────
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

    // ── X-axis date labels (show ~5 evenly spaced) ────────────────────────────
    val xLabelCount = minOf(5, points.size)
    val xLabelIndices = if (points.size <= xLabelCount) {
        points.indices.toList()
    } else {
        (0 until xLabelCount).map { it * (points.size - 1) / (xLabelCount - 1) }
    }
    xLabelIndices.forEach { i ->
        val label = points[i].weekDate.toShortDate()
        val measured = measurer.measure(label, labelStyle)
        val x = xOf(i) - measured.size.width / 2f
        drawText(measurer, label, topLeft = Offset(x, chartBottom + 4f), style = labelStyle)
    }

    if (points.size < 2) {
        // Single point — just draw a dot
        drawCircle(color = Accent, radius = 5.dp.toPx(), center = Offset(xOf(0), yOf(points[0].totalWealth)))
        return
    }

    // ── Fill ──────────────────────────────────────────────────────────────────
    val fillPath = Path().apply {
        moveTo(xOf(0), chartBottom)
        lineTo(xOf(0), yOf(points[0].totalWealth))
        for (i in 1 until points.size) {
            val x0 = xOf(i - 1); val y0 = yOf(points[i - 1].totalWealth)
            val x1 = xOf(i);     val y1 = yOf(points[i].totalWealth)
            val cx = (x0 + x1) / 2f
            cubicTo(cx, y0, cx, y1, x1, y1)
        }
        lineTo(xOf(points.size - 1), chartBottom)
        close()
    }
    drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
            colors = listOf(Accent.copy(alpha = 0.30f), Color.Transparent),
            startY = chartTop,
            endY = chartBottom,
        ),
    )

    // ── Line ──────────────────────────────────────────────────────────────────
    val linePath = Path().apply {
        moveTo(xOf(0), yOf(points[0].totalWealth))
        for (i in 1 until points.size) {
            val x0 = xOf(i - 1); val y0 = yOf(points[i - 1].totalWealth)
            val x1 = xOf(i);     val y1 = yOf(points[i].totalWealth)
            val cx = (x0 + x1) / 2f
            cubicTo(cx, y0, cx, y1, x1, y1)
        }
    }
    drawPath(
        path = linePath,
        color = Accent,
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )

    // ── Last point dot ────────────────────────────────────────────────────────
    drawCircle(
        color = Accent,
        radius = 4.dp.toPx(),
        center = Offset(xOf(points.size - 1), yOf(points.last().totalWealth)),
    )
}

// ── Bar chart ─────────────────────────────────────────────────────────────────

private fun DrawScope.drawBarChart(
    points: List<WealthPoint>,
    measurer: TextMeasurer,
    labelColor: Color,
    gridColor: Color,
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

    val labelStyle = TextStyle(color = labelColor, fontSize = 10.sp)

    // ── Y grid lines + labels ─────────────────────────────────────────────────
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

    // ── Bars ──────────────────────────────────────────────────────────────────
    val barCount = points.size
    val slotW = chartW / barCount
    val barW = (slotW * 0.55f).coerceAtLeast(3f)

    points.forEachIndexed { i, point ->
        val barH = ((point.totalWealth - minVal) / range * chartH).toFloat().coerceAtLeast(2f)
        val x = chartLeft + i * slotW + (slotW - barW) / 2f
        val y = chartBottom - barH
        drawRoundRect(
            color = Accent.copy(alpha = 0.85f),
            topLeft = Offset(x, y),
            size = Size(barW, barH),
            cornerRadius = CornerRadius(3.dp.toPx()),
        )
    }

    // ── X-axis date labels (show ~5 evenly spaced) ────────────────────────────
    val xLabelCount = minOf(5, points.size)
    val xLabelIndices = if (points.size <= xLabelCount) {
        points.indices.toList()
    } else {
        (0 until xLabelCount).map { it * (points.size - 1) / (xLabelCount - 1) }
    }
    xLabelIndices.forEach { i ->
        val label = points[i].weekDate.toShortDate()
        val measured = measurer.measure(label, labelStyle)
        val x = chartLeft + i * slotW + slotW / 2f - measured.size.width / 2f
        drawText(measurer, label, topLeft = Offset(x, chartBottom + 4f), style = labelStyle)
    }
}
