package com.aura.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF00F0FF), // Electric Neon Cyan
    onPrimary = Color(0xFF030408), // Space Obsidian
    secondary = Color(0xFF8B5CF6), // Cosmic Violet
    onSecondary = Color.White,
    tertiary = Color(0xFFEC4899), // Electric Pink
    background = Color(0xFF04050D), // Space Deep Black
    onBackground = Color(0xFFF3F4F6),
    surface = Color(0x1F1A1B2F), // Frosted Deep Space Glass
    onSurface = Color.White,
    surfaceVariant = Color(0x14FFFFFF), // Frosted Thin White Glass
    onSurfaceVariant = Color(0xFF9CA3AF),
    error = Color(0xFFEF4444)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF6366F1), // Royal Indigo
    onPrimary = Color.White,
    secondary = Color(0xFF06B6D4), // Soft Cyan
    onSecondary = Color.White,
    tertiary = Color(0xFF8B5CF6), // Soft Violet
    background = Color(0xFFF6F8FC), // Soft Slate
    onBackground = Color(0xFF0F172A),
    surface = Color(0xCCFFFFFF), // Frosted Translucent White Glass
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0x75E2E8F0), // Frosted Thin Grey Glass
    onSurfaceVariant = Color(0xFF64748B),
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

