package com.aracem.nexlify.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = ContentPrimary,
    primaryContainer = AccentDim,
    onPrimaryContainer = Accent,

    background = Background,
    onBackground = ContentPrimary,

    surface = SurfaceDefault,
    onSurface = ContentPrimary,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = ContentSecondary,

    outline = Border,
    outlineVariant = Border,

    error = Negative,
    onError = ContentPrimary,
)

@Composable
fun NexlifyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = NexlifyTypography,
        shapes = NexlifyShapes,
        content = content,
    )
}
