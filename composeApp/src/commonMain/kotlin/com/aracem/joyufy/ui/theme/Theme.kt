package com.aracem.joyufy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

// ── Material3 color schemes ───────────────────────────────────────────────

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB8CDFF),
    onPrimary = Color(0xFF132B59),
    primaryContainer = Color(0xFF414F70),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFFC5C6DC),
    onSecondary = Color(0xFF2E3042),
    secondaryContainer = Color(0xFF44465B),
    onSecondaryContainer = Color(0xFFE1E2F9),
    tertiary = Color(0xFFE4C8FF),
    onTertiary = Color(0xFF402452),
    tertiaryContainer = Color(0xFF583A69),
    onTertiaryContainer = Color(0xFFF4DBFF),
    background = Dark_Background,
    onBackground = Dark_ContentPrimary,
    surface = Dark_SurfaceDefault,
    onSurface = Dark_ContentPrimary,
    surfaceVariant = Dark_SurfaceRaised,
    onSurfaceVariant = Dark_ContentSecondary,
    outline = Dark_Border,
    outlineVariant = Dark_Border,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF345D9C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFDDFF),
    onPrimaryContainer = Color(0xFF173B70),
    secondary = Color(0xFF5C5F72),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1E2F9),
    onSecondaryContainer = Color(0xFF191B2C),
    tertiary = Color(0xFF725385),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF1DAFF),
    onTertiaryContainer = Color(0xFF2A0E3D),
    background = Light_Background,
    onBackground = Light_ContentPrimary,
    surface = Light_SurfaceDefault,
    onSurface = Light_ContentPrimary,
    surfaceVariant = Light_SurfaceRaised,
    onSurfaceVariant = Light_ContentSecondary,
    outline = Light_Border,
    outlineVariant = Light_Border,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

// ── Custom tokens via CompositionLocal ───────────────────────────────────
// These expose tokens that don't map 1:1 into Material3 color roles.

data class JoyufyColors(
    val contentSecondary: Color,
    val contentDisabled: Color,
    val surfaceRaised: Color,
    val sidebar: Color,
    val hero: Color,
    val monthly: Color,
    val composition: Color,
    val positive: Color,
    val positiveContainer: Color,
    val negative: Color,
    val negativeContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val border: Color,
    val isDark: Boolean,
)

val LocalJoyufyColors = compositionLocalOf {
    JoyufyColors(
        contentSecondary = Dark_ContentSecondary,
        contentDisabled = Dark_ContentDisabled,
        surfaceRaised = Dark_SurfaceRaised,
        sidebar = Color(0xFF202337),
        hero = Color(0xFF28324B),
        monthly = Color(0xFF303A52),
        composition = Color(0xFF3A304D),
        positive = Color(0xFF88D9B0),
        positiveContainer = Color(0xFF254B41),
        negative = Color(0xFFFFB4AB),
        negativeContainer = Color(0xFF573039),
        warning = Color(0xFFFFDFAC),
        warningContainer = Color(0xFF50452E),
        border = Dark_Border,
        isDark = true,
    )
}

// ── Theme entry point ─────────────────────────────────────────────────────

@Composable
fun JoyufyTheme(
    darkMode: Boolean = true,
    content: @Composable () -> Unit,
) {
    val joyufyColors = if (darkMode) {
        JoyufyColors(
            contentSecondary = Dark_ContentSecondary,
            contentDisabled = Dark_ContentDisabled,
            surfaceRaised = Dark_SurfaceRaised,
            sidebar = Color(0xFF202337),
            hero = Color(0xFF28324B),
            monthly = Color(0xFF303A52),
            composition = Color(0xFF3A304D),
            positive = Color(0xFF88D9B0),
            positiveContainer = Color(0xFF254B41),
            negative = Color(0xFFFFB4AB),
            negativeContainer = Color(0xFF573039),
            warning = Color(0xFFFFDFAC),
            warningContainer = Color(0xFF50452E),
            border = Dark_Border,
            isDark = true,
        )
    } else {
        JoyufyColors(
            contentSecondary = Light_ContentSecondary,
            contentDisabled = Light_ContentDisabled,
            surfaceRaised = Light_SurfaceRaised,
            sidebar = Color(0xFFEDF0FB),
            hero = Color(0xFFE2EAFE),
            monthly = Color(0xFFD9E6FB),
            composition = Color(0xFFEADFF7),
            positive = Color(0xFF176D51),
            positiveContainer = Color(0xFFD4EFE2),
            negative = Color(0xFFA83C49),
            negativeContainer = Color(0xFFFFDAD6),
            warning = Color(0xFF604920),
            warningContainer = Color(0xFFF5E6C4),
            border = Light_Border,
            isDark = false,
        )
    }

    CompositionLocalProvider(LocalJoyufyColors provides joyufyColors) {
        MaterialTheme(
            colorScheme = if (darkMode) DarkColorScheme else LightColorScheme,
            typography = JoyufyTypography,
            shapes = JoyufyShapes,
            content = content,
        )
    }
}

// ── Convenience accessor ──────────────────────────────────────────────────

val MaterialTheme.joyufyColors: JoyufyColors
    @Composable get() = LocalJoyufyColors.current
