package com.funny.submaker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
    outlineVariant = Color(0xFF293543),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2F5FD2),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE3FF),
    onPrimaryContainer = Color(0xFF00164A),
    secondary = Color(0xFF3D5F90),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E3FF),
    onSecondaryContainer = Color(0xFF001C3A),
    tertiary = Color(0xFF785573),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F8FC),
    onBackground = Color(0xFF151B25),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF151B25),
    surfaceVariant = Color(0xFFE0E6F2),
    onSurfaceVariant = Color(0xFF424A59),
    outline = Color(0xFF727B8B),
    outlineVariant = Color(0xFFC5CEDD),
)

@Composable
fun SubMakerTheme(content: @Composable () -> Unit) {
    val colorScheme =
        DarkColorScheme // 出于 debug 原因，现在强制使用暗色主题，你不要改 if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
