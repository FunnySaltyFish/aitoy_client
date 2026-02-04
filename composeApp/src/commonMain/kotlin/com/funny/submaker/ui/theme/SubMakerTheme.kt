package com.funny.submaker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7DD3FC),
    onPrimary = Color(0xFF003545),
    primaryContainer = Color(0xFF0A3443),
    onPrimaryContainer = Color(0xFFCBEAFF),
    secondary = Color(0xFFA9C7FF),
    onSecondary = Color(0xFF001E30),
    secondaryContainer = Color(0xFF0A2A4A),
    onSecondaryContainer = Color(0xFFD4E4FF),
    tertiary = Color(0xFFF5C2E7),
    onTertiary = Color(0xFF3C0032),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFE6E6E6),
    surface = Color(0xFF0E141B),
    onSurface = Color(0xFFE6E6E6),
    surfaceVariant = Color(0xFF17212B),
    onSurfaceVariant = Color(0xFFB9C4D0),
    outline = Color(0xFF445361),
)

@Composable
fun SubMakerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content,
    )
}

