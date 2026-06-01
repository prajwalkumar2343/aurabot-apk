package com.aura.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF), // Pure White accents
    onPrimary = Color(0xFF0F0F10), // Pitch Black on white buttons
    secondary = Color(0xFF8E8E93), // Silver-grey accents
    onSecondary = Color.White,
    tertiary = Color(0xFF8B5CF6), // Subtle Violet fallback
    background = Color(0xFF0F0F10), // Deep matte pitch-black
    onBackground = Color(0xFFF2F2F7), // Crisp off-white text
    surface = Color(0xFF1C1C1E), // Matte Carbon Dark Gray
    onSurface = Color.White,
    surfaceVariant = Color(0x1AFFFFFF), // Soft white translucent overlay
    onSurfaceVariant = Color(0xFF8E8E93),
    error = Color(0xFFEF4444)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0F0F10), // Matte Black accents
    onPrimary = Color.White,
    secondary = Color(0xFF8E8E93), // Slate-grey accents
    onSecondary = Color.White,
    tertiary = Color(0xFF8B5CF6),
    background = Color(0xFFF2F2F7), // Crisp, clean light gray
    onBackground = Color(0xFF0F0F10), // Matte Black primary text
    surface = Color(0xFFFFFFFF), // Pure White cards
    onSurface = Color(0xFF0F0F10),
    surfaceVariant = Color(0x0F000000), // Translucent black overlay
    onSurfaceVariant = Color(0xFF55555C), // Highly readable dark slate subtext
    error = Color(0xFFDC2626)
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

