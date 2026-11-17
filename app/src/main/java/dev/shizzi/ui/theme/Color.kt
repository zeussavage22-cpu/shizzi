package dev.shizzi.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class ShizziColors(
    val primary: Color,
    val onPrimary: Color,

    val primaryBright: Color,
    val background: Color,
    val surface: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val border: Color,
    val shadow: Color,
    val isDark: Boolean,
    val primaryContainer: Color = primary,
    val onPrimaryContainer: Color = onPrimary,
    val secondary: Color = primary,
    val tertiary: Color = primaryBright,
    val surfaceVariant: Color = surface,
    val surfaceContainer: Color = surface,
)

// Cóndor palette: deep night-sky violet with a cyan highlight.

val LightColors = ShizziColors(
    primary = Color(0xFF6D5EFC),
    onPrimary = Color(0xFFFFFFFF),
    primaryBright = Color(0xFFC4B5FD),
    background = Color(0xFFF6F5FF),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14122B),
    onSurfaceMuted = Color(0xFF5E5A80),
    border = Color(0xFF14122B),
    shadow = Color(0xFF14122B),
    isDark = false,
    primaryContainer = Color(0xFFE6E1FF),
    onPrimaryContainer = Color(0xFF231A75),
    secondary = Color(0xFF0891B2),
    tertiary = Color(0xFF0891B2),
    surfaceVariant = Color(0xFFEDEBFA),
    surfaceContainer = Color(0xFFEFEDFC),
)

val DarkColors = ShizziColors(
    primary = Color(0xFF8B7CFF),
    onPrimary = Color(0xFF0B0A1C),
    primaryBright = Color(0xFFC4B5FD),
    background = Color(0xFF0A0918),
    surface = Color(0xFF16152E),
    onSurface = Color(0xFFF4F2FF),
    onSurfaceMuted = Color(0xFFA6A3CC),
    border = Color(0xFF3B3870),
    shadow = Color(0xFF000000),
    isDark = true,
    primaryContainer = Color(0xFF2E2770),
    onPrimaryContainer = Color(0xFFE6E1FF),
    secondary = Color(0xFF22D3EE),
    tertiary = Color(0xFF22D3EE),
    surfaceVariant = Color(0xFF1E1C3D),
    surfaceContainer = Color(0xFF1E1C3D),
)
