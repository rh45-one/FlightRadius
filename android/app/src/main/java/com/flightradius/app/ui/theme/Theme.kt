package com.flightradius.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.flightradius.app.data.prefs.ThemeMode

// -- Palette: Apple-style neutral base + FlightRadius cyan accent ------------
// Dark: pure-black canvas with elevated grouped surfaces. Light: grouped
// grey canvas with white cards. Status hues use a lighter variant on dark and
// a deeper variant on light so text and icons stay above 4.5:1 in both.

val Neutral950 = Color(0xFF000000)
val Neutral900 = Color(0xFF1C1C1E)
val Neutral850 = Color(0xFF2C2C2E)
val Neutral800 = Color(0xFF3A3A3C)
val Neutral500 = Color(0xFF8E8E93)
val Neutral400 = Color(0xFFAEAEB2)
val Neutral100 = Color(0xFFF2F2F7)
val Neutral200 = Color(0xFFE5E5EA)
val Neutral300 = Color(0xFFD1D1D6)
val Neutral600 = Color(0xFF6C6C70)
val Cyan400 = Color(0xFF22D3EE)
val Cyan700 = Color(0xFF0E7490)
val Emerald400 = Color(0xFF34D399)
val Emerald700 = Color(0xFF047857)
val Amber400 = Color(0xFFFBBF24)
val Amber800 = Color(0xFF92400E)
val Rose400 = Color(0xFFFB7185)
val Rose700 = Color(0xFFBE123C)

/** Semantic colors beyond the Material scheme (zone/status colors). */
data class ExtendedColors(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color
)

val DarkExtended = ExtendedColors(
    success = Emerald400, warning = Amber400, danger = Rose400, info = Cyan400
)
val LightExtended = ExtendedColors(
    success = Emerald700, warning = Amber800, danger = Rose700, info = Cyan700
)

val LocalExtendedColors = staticCompositionLocalOf { DarkExtended }

private val DarkScheme = darkColorScheme(
    primary = Cyan400,
    onPrimary = Neutral950,
    primaryContainer = Color(0xFF0B3B48),
    onPrimaryContainer = Color(0xFFA5F3FC),
    secondary = Neutral400,
    onSecondary = Neutral950,
    secondaryContainer = Neutral850,
    onSecondaryContainer = Color(0xFFF2F2F7),
    tertiary = Emerald400,
    onTertiary = Neutral950,
    background = Neutral950,
    onBackground = Color.White,
    surface = Neutral950,
    onSurface = Color.White,
    surfaceVariant = Neutral850,
    onSurfaceVariant = Color(0xFFAEAEB2),
    surfaceContainerLowest = Neutral950,
    surfaceContainerLow = Neutral900,
    surfaceContainer = Neutral900,
    surfaceContainerHigh = Neutral850,
    surfaceContainerHighest = Neutral800,
    outline = Neutral500,
    outlineVariant = Neutral800,
    error = Rose400,
    onError = Neutral950,
    errorContainer = Color(0xFF4C0519),
    onErrorContainer = Color(0xFFFECDD3)
)

private val LightScheme = lightColorScheme(
    primary = Cyan700,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFFAFE),
    onPrimaryContainer = Color(0xFF164E63),
    secondary = Neutral600,
    onSecondary = Color.White,
    secondaryContainer = Neutral200,
    onSecondaryContainer = Color(0xFF1C1C1E),
    tertiary = Emerald700,
    onTertiary = Color.White,
    background = Neutral100,
    onBackground = Color.Black,
    surface = Neutral100,
    onSurface = Color.Black,
    surfaceVariant = Neutral200,
    onSurfaceVariant = Neutral600,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Neutral200,
    surfaceContainerHighest = Neutral300,
    outline = Neutral500,
    outlineVariant = Neutral300,
    error = Rose700,
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF4C0519)
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FlightRadiusTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }
    val extended = if (dark) DarkExtended else LightExtended

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}

val ColorScheme.extended: ExtendedColors
    @Composable get() = LocalExtendedColors.current
