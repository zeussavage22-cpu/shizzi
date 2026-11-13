package dev.shizzi.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.filled.WifiTetheringError
import androidx.compose.material.icons.filled.WifiTetheringOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shizzi.UiStatus
import dev.shizzi.tr
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.standardSpring
import dev.shizzi.ui.theme.standardTween

private val RingSize = 236.dp
private val TrackWidth = 10.dp
private val LimitWidth = 4.dp
private val GlyphSize = 64.dp

private val ErrorTint = Color(0xFFE5484D)

/**
 * The home screen's centerpiece: an animated ring that shows the session at a glance.
 *
 * - Ready: a quiet, dim ring.
 * - Starting: an arc sweeping around the ring.
 * - Connected: a glowing ring with pulses radiating out, and the live speed in the middle.
 * - Error: a red ring.
 *
 * When a time or data limit is on, a thin outer arc shows how much of it has been used.
 */
@Composable
fun ConnectionRing(
    status: UiStatus,
    isWaitingForUsb: Boolean,
    headline: String,
    caption: String,
    limitProgress: Float?,
    modifier: Modifier = Modifier,
) {
    val colors = ShizziTheme.colors
    val isSharing = status == UiStatus.CONNECTED && !isWaitingForUsb
    val isLoading = status == UiStatus.LOADING

    val accent by animateColorAsState(
        targetValue = when {
            status == UiStatus.ERROR -> ErrorTint
            isSharing -> colors.primary
            isWaitingForUsb -> colors.primary.copy(alpha = 0.6f)
            else -> colors.onSurfaceMuted.copy(alpha = 0.35f)
        },
        animationSpec = standardSpring(),
        label = "ringAccent",
    )
    val track = colors.onSurfaceMuted.copy(alpha = 0.12f)

    val fill by animateFloatAsState(
        targetValue = when {
            isSharing || status == UiStatus.ERROR -> 1f
            isWaitingForUsb -> 0.5f
            else -> 0f
        },
        animationSpec = standardTween(),
        label = "ringFill",
    )
    val limit by animateFloatAsState(
        targetValue = limitProgress ?: 0f,
        animationSpec = standardTween(),
        label = "ringLimit",
    )

    val motion = rememberInfiniteTransition(label = "ringMotion")
    val spin by motion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing)),
        label = "ringSpin",
    )
    val pulse by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_400, easing = LinearEasing), RepeatMode.Restart),
        label = "ringPulse",
    )

    Box(modifier = modifier.size(RingSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = TrackWidth.toPx()
            val limitStroke = LimitWidth.toPx()
            val inset = stroke / 2 + limitStroke * 3
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            val radius = arcSize.width / 2

            // Pulses radiating outward while sharing.
            if (isSharing) {
                for (wave in 0..1) {
                    val phase = (pulse + wave * 0.5f) % 1f
                    drawCircle(
                        color = accent.copy(alpha = (1f - phase) * 0.18f),
                        radius = radius * (0.55f + phase * 0.5f),
                        center = center,
                    )
                }
            }

            // Soft inner glow.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.16f * fill + 0.03f), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )

            // Track.
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )

            // Progress / state arc.
            if (isLoading) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color.Transparent, accent, accent)),
                    startAngle = spin,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            } else if (fill > 0f) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * fill,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }

            // Limit usage, as a thin outer arc.
            if (limitProgress != null && limit > 0f) {
                val outer = limitStroke * 1.5f
                drawArc(
                    color = if (limit >= 0.9f) ErrorTint else accent.copy(alpha = 0.7f),
                    startAngle = -90f,
                    sweepAngle = 360f * limit,
                    useCenter = false,
                    topLeft = Offset(outer, outer),
                    size = Size(size.width - outer * 2, size.height - outer * 2),
                    style = Stroke(width = limitStroke, cap = StrokeCap.Round),
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.xs),
        ) {
            Icon(
                imageVector = glyphFor(status, isWaitingForUsb),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(GlyphSize),
            )

            Text(
                text = headline,
                style = ShizziTheme.typography.display.copy(fontSize = 26.sp, fontWeight = FontWeight.W700),
                color = ShizziTheme.colors.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )

            Text(
                text = caption.uppercase(),
                style = ShizziTheme.typography.caption,
                color = ShizziTheme.colors.onSurfaceMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

private fun glyphFor(status: UiStatus, isWaitingForUsb: Boolean) = when {
    status == UiStatus.ERROR -> Icons.Filled.WifiTetheringError
    status == UiStatus.READY -> Icons.Filled.WifiTetheringOff
    isWaitingForUsb -> Icons.Filled.WifiTetheringOff
    else -> Icons.Filled.WifiTethering
}

/** Short status word shown under the headline. */
fun ringCaption(status: UiStatus, isWaitingForUsb: Boolean): String = when (status) {
    UiStatus.CONNECTED -> if (isWaitingForUsb) tr("Waiting for USB") else tr("Sharing")
    UiStatus.READY -> tr("Ready to share")
    UiStatus.LOADING -> tr("Starting")
    UiStatus.ERROR -> tr("Failed")
}
