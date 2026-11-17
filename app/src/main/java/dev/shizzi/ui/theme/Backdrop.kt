package dev.shizzi.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Cóndor's background: the base color with soft violet and cyan glows, like a night sky. */
fun Modifier.condorBackdrop(colors: ShizziColors): Modifier = drawBehind {
    drawRect(colors.background)

    val violet = colors.primary.copy(alpha = if (colors.isDark) 0.24f else 0.12f)
    val cyan = colors.secondary.copy(alpha = if (colors.isDark) 0.14f else 0.08f)

    val top = Offset(size.width * 0.1f, size.height * 0.02f)
    val topRadius = size.width * 1.0f
    drawCircle(
        brush = Brush.radialGradient(listOf(violet, Color.Transparent), center = top, radius = topRadius),
        radius = topRadius,
        center = top,
    )

    val bottom = Offset(size.width * 0.95f, size.height * 0.85f)
    val bottomRadius = size.width * 0.9f
    drawCircle(
        brush = Brush.radialGradient(listOf(cyan, Color.Transparent), center = bottom, radius = bottomRadius),
        radius = bottomRadius,
        center = bottom,
    )
}
