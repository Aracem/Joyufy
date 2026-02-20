package com.aracem.nexlify.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.aracem.nexlify.ui.dashboard.ChartMode
import com.aracem.nexlify.ui.dashboard.WealthPoint
import com.aracem.nexlify.ui.theme.Accent
import com.aracem.nexlify.ui.theme.ContentSecondary

@Composable
fun WealthChart(
    points: List<WealthPoint>,
    mode: ChartMode,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) {
        Box(modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            Text(
                "Sin datos aún",
                style = MaterialTheme.typography.bodySmall,
                color = ContentSecondary,
            )
        }
        return
    }

    Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
        when (mode) {
            ChartMode.AREA -> drawAreaChart(points)
            ChartMode.BARS -> drawBarChart(points)
        }
    }
}

private fun DrawScope.drawAreaChart(points: List<WealthPoint>) {
    if (points.size < 2) return

    val minVal = points.minOf { it.totalWealth }
    val maxVal = points.maxOf { it.totalWealth }
    val range = (maxVal - minVal).coerceAtLeast(1.0)

    val w = size.width
    val h = size.height
    val padding = 8f

    fun xOf(i: Int) = padding + (i.toFloat() / (points.size - 1)) * (w - 2 * padding)
    fun yOf(v: Double) = h - padding - ((v - minVal) / range * (h - 2 * padding)).toFloat()

    // Fill path
    val fillPath = Path().apply {
        moveTo(xOf(0), h)
        lineTo(xOf(0), yOf(points[0].totalWealth))
        for (i in 1 until points.size) {
            val x0 = xOf(i - 1); val y0 = yOf(points[i - 1].totalWealth)
            val x1 = xOf(i);     val y1 = yOf(points[i].totalWealth)
            val cx = (x0 + x1) / 2
            cubicTo(cx, y0, cx, y1, x1, y1)
        }
        lineTo(xOf(points.size - 1), h)
        close()
    }
    drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
            colors = listOf(Accent.copy(alpha = 0.35f), Color.Transparent),
            startY = 0f,
            endY = h,
        ),
    )

    // Line path
    val linePath = Path().apply {
        moveTo(xOf(0), yOf(points[0].totalWealth))
        for (i in 1 until points.size) {
            val x0 = xOf(i - 1); val y0 = yOf(points[i - 1].totalWealth)
            val x1 = xOf(i);     val y1 = yOf(points[i].totalWealth)
            val cx = (x0 + x1) / 2
            cubicTo(cx, y0, cx, y1, x1, y1)
        }
    }
    drawPath(
        path = linePath,
        color = Accent,
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 2.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )

    // Last point dot
    drawCircle(
        color = Accent,
        radius = 4.dp.toPx(),
        center = Offset(xOf(points.size - 1), yOf(points.last().totalWealth)),
    )
}

private fun DrawScope.drawBarChart(points: List<WealthPoint>) {
    val minVal = points.minOf { it.totalWealth }.coerceAtMost(0.0)
    val maxVal = points.maxOf { it.totalWealth }
    val range = (maxVal - minVal).coerceAtLeast(1.0)

    val w = size.width
    val h = size.height
    val padding = 8f
    val barCount = points.size
    val totalWidth = w - 2 * padding
    val barWidth = (totalWidth / barCount * 0.6f).coerceAtLeast(4f)
    val gap = totalWidth / barCount

    points.forEachIndexed { i, point ->
        val barH = ((point.totalWealth - minVal) / range * (h - 2 * padding)).toFloat()
        val x = padding + i * gap + (gap - barWidth) / 2
        val y = h - padding - barH
        drawRoundRect(
            color = Accent.copy(alpha = 0.85f),
            topLeft = Offset(x, y),
            size = Size(barWidth, barH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
        )
    }
}
