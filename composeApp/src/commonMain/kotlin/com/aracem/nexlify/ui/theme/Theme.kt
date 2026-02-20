package com.aracem.nexlify.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

// ── Material3 color schemes ───────────────────────────────────────────────

private val DarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Dark_ContentPrimary,
    primaryContainer = AccentDim,
    onPrimaryContainer = Accent,
    background = Dark_Background,
    onBackground = Dark_ContentPrimary,
    surface = Dark_SurfaceDefault,
    onSurface = Dark_ContentPrimary,
    surfaceVariant = Dark_SurfaceRaised,
    onSurfaceVariant = Dark_ContentSecondary,
    outline = Dark_Border,
    outlineVariant = Dark_Border,
    error = Negative,
    onError = Dark_ContentPrimary,
)

private val LightColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Light_ContentPrimary,
    primaryContainer = AccentDim,
    onPrimaryContainer = Accent,
    background = Light_Background,
    onBackground = Light_ContentPrimary,
    surface = Light_SurfaceDefault,
    onSurface = Light_ContentPrimary,
    surfaceVariant = Light_SurfaceRaised,
    onSurfaceVariant = Light_ContentSecondary,
    outline = Light_Border,
    outlineVariant = Light_Border,
    error = Negative,
    onError = Color.White,
)

// ── Custom tokens via CompositionLocal ───────────────────────────────────
// These expose tokens that don't map 1:1 into Material3 color roles.

data class NexlifyColors(
    val contentSecondary: Color,
    val contentDisabled: Color,
    val surfaceRaised: Color,
    val border: Color,
    val isDark: Boolean,
)

val LocalNexlifyColors = compositionLocalOf {
    NexlifyColors(
        contentSecondary = Dark_ContentSecondary,
        contentDisabled = Dark_ContentDisabled,
        surfaceRaised = Dark_SurfaceRaised,
        border = Dark_Border,
        isDark = true,
    )
}

// ── Theme entry point ─────────────────────────────────────────────────────

@Composable
fun NexlifyTheme(
    darkMode: Boolean = true,
    content: @Composable () -> Unit,
) {
    val nexlifyColors = if (darkMode) {
        NexlifyColors(
            contentSecondary = Dark_ContentSecondary,
            contentDisabled = Dark_ContentDisabled,
            surfaceRaised = Dark_SurfaceRaised,
            border = Dark_Border,
            isDark = true,
        )
    } else {
        NexlifyColors(
            contentSecondary = Light_ContentSecondary,
            contentDisabled = Light_ContentDisabled,
            surfaceRaised = Light_SurfaceRaised,
            border = Light_Border,
            isDark = false,
        )
    }

    CompositionLocalProvider(LocalNexlifyColors provides nexlifyColors) {
        MaterialTheme(
            colorScheme = if (darkMode) DarkColorScheme else LightColorScheme,
            typography = NexlifyTypography,
            shapes = NexlifyShapes,
            content = content,
        )
    }
}

// ── Convenience accessor ──────────────────────────────────────────────────

val MaterialTheme.nexlifyColors: NexlifyColors
    @Composable get() = LocalNexlifyColors.current
