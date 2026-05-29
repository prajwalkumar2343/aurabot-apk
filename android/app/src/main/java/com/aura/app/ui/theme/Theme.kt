package com.aura.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF95D5B2),
    onPrimary = Color(0xFF062218),
    secondary = Color(0xFFFFD166),
    onSecondary = Color(0xFF261900),
    tertiary = Color(0xFF74C0FC),
    background = Color(0xFF07110E),
    onBackground = Color(0xFFE7F4EC),
    surface = Color(0xFF10201A),
    onSurface = Color(0xFFE7F4EC),
    surfaceVariant = Color(0xFF21352D),
    onSurfaceVariant = Color(0xFFC2D5CB),
    error = Color(0xFFFFB4AB)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1B6B4A),
    onPrimary = Color.White,
    secondary = Color(0xFF8A5A00),
    onSecondary = Color.White,
    tertiary = Color(0xFF0E5E91),
    background = Color(0xFFF4FBF6),
    onBackground = Color(0xFF102018),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF102018),
    surfaceVariant = Color(0xFFDDEBE2),
    onSurfaceVariant = Color(0xFF41564B),
    error = Color(0xFFBA1A1A)
)

@Composable
fun AuraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scheme: ColorScheme = if (darkTheme) DarkScheme else LightScheme
    MaterialTheme(
        colorScheme = scheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
