package com.custom.treadmill.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0D47A1),
    onPrimary = Color.White,
    secondary = Color(0xFF1976D2),
    tertiary = Color(0xFFFFB300)
)

@Composable
fun TreadmillTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
