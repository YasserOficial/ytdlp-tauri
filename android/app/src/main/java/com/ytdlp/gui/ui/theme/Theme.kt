package com.ytdlp.gui.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Cores principais - Glassmorphism style
val Purple500 = Color(0xFF6366F1)
val Purple700 = Color(0xFF4F46E5)
val Pink500 = Color(0xFFEC4899)
val Pink700 = Color(0xFFDB2777)

// Cores de fundo com transparência
val DarkBackground = Color(0xFF0F0F23)
val DarkSurface = Color(0xFF1A1A2E)
val DarkCard = Color(0x40FFFFFF)

val LightBackground = Color(0xFFF5F5F5)
val LightSurface = Color(0xFFFFFFFF)
val LightCard = Color(0x80FFFFFF)

// Gradientes
val GradientStart = Color(0xFF6366F1)
val GradientEnd = Color(0xFFEC4899)

private val DarkColorScheme = darkColorScheme(
    primary = Purple500,
    onPrimary = Color.White,
    secondary = Pink500,
    onSecondary = Color.White,
    tertiary = GradientEnd,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkCard,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFE0E0E0),
    outline = Color(0x40FFFFFF),
    error = Color(0xFFEF4444)
)

private val LightColorScheme = lightColorScheme(
    primary = Purple700,
    onPrimary = Color.White,
    secondary = Pink700,
    onSecondary = Color.White,
    tertiary = GradientEnd,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightCard,
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFF4A4A4A),
    outline = Color(0x20000000),
    error = Color(0xFFDC2626)
)

@Composable
fun YtDlpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
