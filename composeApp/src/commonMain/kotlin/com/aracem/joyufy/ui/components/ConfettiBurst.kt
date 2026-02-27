package com.aracem.joyufy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val burstColors = listOf(
    Color(0xFF34C77B), // Positive green
    Color(0xFF7B6EF6), // Accent purple
    Color(0xFFFFD700), // Gold
    Color(0xFFFF6B6B), // Coral
    Color(0xFF4ECDC4), // Teal
    Color(0xFFFFE66D), // Yellow
    Color(0xFFA8E063), // Light green
    Color(0xFFF7B731), // Orange
)

private data class BurstParticle(
    val color: Color,
    val size: Float,
    val angle: Float,        // launch angle in radians
    val speed: Float,        // initial speed in px/s
    val rotationSpeed: Float,// degrees/s
    val rotation: Float,     // initial rotation
)

/**
 * Confetti that explodes outward from [origin] (in px, window coordinates).
 * Duration: [durationMs] ms. Gravity pulls particles down after launch.
 */
@Composable
fun ConfettiBurst(
    origin: Offset,
    count: Int = 320,
    durationMs: Long = 1800L,
    modifier: Modifier = Modifier,
) {
    val particles = remember(origin) {
        List(count) {
            BurstParticle(
                color = burstColors.random(),
                size = Random.nextFloat() * 8f + 5f,
                // Launch in all directions but biased upward (upper hemisphere more dense)
                angle = Random.nextFloat() * 2f * Math.PI.toFloat(),
                speed = Random.nextFloat() * 900f + 400f,
                rotationSpeed = (Random.nextFloat() - 0.5f) * 800f,
                rotation = Random.nextFloat() * 360f,
            )
        }
    }

    var elapsed by remember { mutableStateOf(0L) }
    LaunchedEffect(origin) {
        val start = System.currentTimeMillis()
        while (elapsed < durationMs) {
            withFrameNanos { }
            elapsed = System.currentTimeMillis() - start
        }
    }

    Canvas(modifier = modifier) {
        val t = (elapsed / durationMs.toFloat()).coerceIn(0f, 1f)
        val gravity = size.height * 1.8f  // px/s² — pulls down relative to screen height

        particles.forEach { p ->
            val seconds = t * (durationMs / 1000f)

            // Projectile motion: x = v·cos(a)·t,  y = v·sin(a)·t + ½g·t²
            val dx = cos(p.angle) * p.speed * seconds
            val dy = sin(p.angle) * p.speed * seconds + 0.5f * gravity * seconds * seconds

            val x = origin.x + dx
            val y = origin.y + dy

            // Fade out in last 30%
            val alpha = if (t > 0.7f) 1f - ((t - 0.7f) / 0.3f) else 1f
            val currentRotation = p.rotation + p.rotationSpeed * seconds

            rotate(degrees = currentRotation, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color.copy(alpha = alpha.coerceIn(0f, 1f)),
                    topLeft = Offset(x - p.size / 2f, y - p.size / 4f),
                    size = Size(p.size, p.size * 0.5f),
                )
            }
        }
    }
}
