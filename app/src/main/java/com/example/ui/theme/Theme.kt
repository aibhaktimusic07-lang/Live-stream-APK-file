package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StudioColorScheme = darkColorScheme(
    primary = NeonBlue,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF0F3B60),
    onPrimaryContainer = NeonBlueLight,

    secondary = NeonPurple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF381A66),
    onSecondaryContainer = NeonPurpleLight,

    tertiary = NeonPurpleGlow,
    onTertiary = Color.White,

    background = StudioDarkBg,
    onBackground = TextPrimary,

    surface = StudioDarkSurface,
    onSurface = TextPrimary,

    surfaceVariant = StudioDarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,

    outline = StudioCardBorder,
    outlineVariant = Color(0xFF1E2438),

    error = LiveActiveRed,
    onError = Color.White
)

@Composable
fun LiveStudioTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioColorScheme,
        typography = Typography,
        content = content
    )
}
