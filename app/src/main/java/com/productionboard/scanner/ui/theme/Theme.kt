package com.productionboard.scanner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFF646CFF)
val Success = Color(0xFF2ECC71)
val Warning = Color(0xFFF5A623)
val Danger = Color(0xFFEF4444)
val Background = Color(0xFF1A1A1E)
val Surface = Color(0xFF242428)
val SurfaceVariant = Color(0xFF2C2C32)

private val ColorScheme = darkColorScheme(
    primary = Accent,
    secondary = Accent,
    background = Background,
    surface = Surface,
    surfaceVariant = SurfaceVariant,
    error = Danger,
)

@Composable
fun BoardScannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColorScheme, content = content)
}
