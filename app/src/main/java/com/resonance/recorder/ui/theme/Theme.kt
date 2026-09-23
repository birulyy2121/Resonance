package com.resonance.recorder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB2B8),
    onPrimary = Color(0xFF650012),
    secondary = Color(0xFFC3C8FF),
    background = Color(0xFF0D0D0E),
    surface = Color(0xFF171719),
    surfaceVariant = Color(0xFF29282D),
    onBackground = Color(0xFFE8E7EA),
    onSurface = Color(0xFFE8E7EA)
)

@Composable
fun ResonanceTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
