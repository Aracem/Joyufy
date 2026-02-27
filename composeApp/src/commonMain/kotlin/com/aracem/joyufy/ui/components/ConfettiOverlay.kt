package com.aracem.joyufy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.sin
import kotlin.random.Random

private data class ConfettiParticle(
    val x: Float,          // 0..1 horizontal start position
    val color: Color,
    val size: Float,
    val speed: Float,      // relative fall speed
    val wobble: Float,     // horizontal wobble amplitude
    val phase: Float,      // sin phase offset
    val rotation: Float,   // initial rotation degrees
    val rotationSpeed: Float,
    val startDelay: Float, // 0..1 fraction of total duration before this particle appears
)

private val confettiColors = listOf(
    Color(0xFF34C77B), // Positive green
    Color(0xFF7B6EF6), // Accent purple
    Color(0xFFFFD700), // Gold
    Color(0xFFFF6B6B), // Coral
    Color(0xFF4ECDC4), // Teal
    Color(0xFFFFE66D), // Yellow
    Color(0xFFA8E063), // Light green
    Color(0xFFF7B731), // Orange
)

@Composable
fun ConfettiOverlay(
    count: Int = 4000,
    durationMs: Long = 4000L,
    modifier: Modifier = Modifier,
) {
    val particles = remember {
        List(count) {
            ConfettiParticle(
                x = Random.nextFloat(),
                color = confettiColors.random(),
                size = Random.nextFloat() * 6f + 6f,
                speed = Random.nextFloat() * 0.5f + 0.5f,
                wobble = Random.nextFloat() * 60f + 20f,
                phase = Random.nextFloat() * 6.28f,
                rotation = Random.nextFloat() * 360f,
                rotationSpeed = (Random.nextFloat() - 0.5f) * 720f,
                startDelay = Random.nextFloat() * 0.6f,
            )
        }
    }

    var elapsed by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (true) {
            withFrameNanos { }
            elapsed = System.currentTimeMillis() - start
        }
    }

    Canvas(modifier = modifier) {
        val t = (elapsed / durationMs.toFloat()).coerceIn(0f, 1f)
        particles.forEach { p ->
            val localT = ((t - p.startDelay) / (1f - p.startDelay)).coerceIn(0f, 1f)
            if (localT <= 0f) return@forEach
            val adjustedT = (localT / p.speed).coerceIn(0f, 1f)
            val y = adjustedT * (size.height + p.size * 4)
            val x = p.x * size.width + sin(adjustedT * 6.28f * 2 + p.phase) * p.wobble
            val alpha = if (t > 0.75f) 1f - ((t - 0.75f) / 0.25f) else 1f
            val currentRotation = p.rotation + p.rotationSpeed * adjustedT

            rotate(degrees = currentRotation, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color.copy(alpha = alpha),
                    topLeft = Offset(x - p.size / 2, y - p.size / 2),
                    size = Size(p.size, p.size * 0.5f),
                )
            }
        }
    }
}
