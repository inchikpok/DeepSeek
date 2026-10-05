package com.custom.treadmill.ui

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

private val LightColors = lightColorScheme(
    primary = Color(0xFF0D47A1),
    onPrimary = Color.White,
    secondary = Color(0xFF1976D2),
    tertiary = Color(0xFFFFB300),
    surfaceVariant = Color(0xFFEBEBEB)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF64B5F6),
    onPrimary = Color(0xFF00325A),
    secondary = Color(0xFF90CAF9),
    tertiary = Color(0xFFFFCA28),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurface = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFFBDBDBD)
)

@Composable
fun TreadmillTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(colorScheme = colors, content = content)
}
