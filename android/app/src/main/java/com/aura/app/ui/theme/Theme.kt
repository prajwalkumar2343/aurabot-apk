package com.aura.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = Color.White,
    onSecondary = Color.Black,
    tertiary = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF121212),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF8E8E93),
    error = Color.White
)

private val LightScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    secondary = Color.Black,
    onSecondary = Color.White,
    tertiary = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color(0xFFF2F2F7),
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF8E8E93),
    error = Color.Black
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
