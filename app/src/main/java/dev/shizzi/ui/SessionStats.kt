package dev.shizzi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.shizzi.SessionLimits
import dev.shizzi.SessionUiState
import dev.shizzi.Traffic
import dev.shizzi.tr
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.SurfaceElevation
import dev.shizzi.ui.theme.themedSurface
import kotlinx.coroutines.delay

private const val TickMillis = 1_000L
private const val SpeedSamples = 60
private val TileIconSize = 18.dp
private val SparklineHeight = 56.dp

/** Current speed (bytes/s, both directions) plus the last minute of samples, oldest first. */
@Immutable
data class SpeedHistory(val current: Long, val samples: List<Long>)

/**
 * Live numbers for a connected session in a grid of cards, plus a one-minute speed chart.
 *
 * Purely informational: it only reads [state] and never changes the session.
 */
@Composable
fun SessionStats(state: SessionUiState, speed: SpeedHistory, modifier: Modifier = Modifier) {
    val now by rememberTicker()
    val gap = ShizziTheme.spacing.sm

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            StatTile(Icons.Filled.Devices, tr("Devices"), state.clientCount.toString(), Modifier.weight(1f))
            StatTile(Icons.Filled.Schedule, tr("Time"), elapsedText(state, now), Modifier.weight(1f))
            StatTile(
                Icons.Filled.DataUsage,
                tr("Total data"),
                Traffic.format(state.traffic.up + state.traffic.down),
                Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            StatTile(Icons.Filled.ArrowDownward, tr("Downloaded"), Traffic.format(state.traffic.down), Modifier.weight(1f))
            StatTile(Icons.Filled.ArrowUpward, tr("Uploaded"), Traffic.format(state.traffic.up), Modifier.weight(1f))
            StatTile(Icons.Filled.Speed, tr("Speed"), "${Traffic.format(speed.current)}/s", Modifier.weight(1f))
        }

        SpeedChart(speed)
    }
}

private fun elapsedText(state: SessionUiState, now: Long): String = when {
    state.connectedSince <= 0L -> "–"
    else -> SessionLimits.formatElapsed(now - state.connectedSince)
}

@Composable
fun StatTile(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .themedSurface(fill = ShizziTheme.colors.surface, elevation = SurfaceElevation.FLAT)
            .padding(horizontal = ShizziTheme.spacing.sm, vertical = ShizziTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.xs),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ShizziTheme.colors.primary,
            modifier = Modifier.size(TileIconSize),
        )
        Text(
            text = value,
            style = ShizziTheme.typography.label,
            color = ShizziTheme.colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label.uppercase(),
            style = ShizziTheme.typography.caption,
            color = ShizziTheme.colors.onSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SpeedChart(speed: SpeedHistory) {
    val line = ShizziTheme.colors.primary
    val peak = speed.samples.maxOrNull() ?: 0L

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .themedSurface(fill = ShizziTheme.colors.surface, elevation = SurfaceElevation.FLAT)
            .padding(ShizziTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = tr("Speed · last minute").uppercase(),
                style = ShizziTheme.typography.caption,
                color = ShizziTheme.colors.onSurfaceMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = tr("Peak", "Pico") + " ${Traffic.format(peak)}/s",
                style = ShizziTheme.typography.caption,
                color = ShizziTheme.colors.onSurfaceMuted,
            )
        }

        Canvas(modifier = Modifier.fillMaxWidth().height(SparklineHeight)) {
            val samples = speed.samples
            if (samples.size < 2) return@Canvas

            val top = (peak.coerceAtLeast(1L)).toFloat()
            val stepX = size.width / (SpeedSamples - 1)
            val startX = size.width - stepX * (samples.size - 1)
            fun pointAt(i: Int) = Offset(
                x = startX + stepX * i,
                y = size.height - (samples[i] / top) * size.height * 0.92f,
            )

            val stroke = Path().apply {
                moveTo(pointAt(0).x, pointAt(0).y)
                for (i in 1 until samples.size) lineTo(pointAt(i).x, pointAt(i).y)
            }
            val area = Path().apply {
                addPath(stroke)
                lineTo(pointAt(samples.lastIndex).x, size.height)
                lineTo(pointAt(0).x, size.height)
                close()
            }

            drawPath(
                path = area,
                brush = Brush.verticalGradient(listOf(line.copy(alpha = 0.28f), Color.Transparent)),
            )
            drawPath(
                path = stroke,
                color = line,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/** Current wall-clock time, refreshed once a second while on screen. */
@Composable
private fun rememberTicker(): State<Long> {
    val clock = remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            clock.longValue = System.currentTimeMillis()
            delay(TickMillis)
        }
    }

    return clock
}

/**
 * Speed in bytes per second across both directions, from the change between status updates,
 * sampled once a second into a one-minute history.
 */
@Composable
fun rememberSpeedHistory(state: SessionUiState): SpeedHistory {
    var current by remember { mutableLongStateOf(0L) }
    var last by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var samples by remember { mutableStateOf(emptyList<Long>()) }

    val total = state.traffic.up + state.traffic.down
    val isLive = state.connectedSince > 0

    LaunchedEffect(total) {
        val now = System.currentTimeMillis()
        val previous = last
        last = total to now

        if (previous == null) return@LaunchedEffect

        val (previousTotal, previousAt) = previous
        val elapsed = now - previousAt
        val delta = total - previousTotal
        current = when {
            delta < 0 -> 0L
            elapsed <= 0 -> current
            else -> delta * 1000 / elapsed
        }
    }

    LaunchedEffect(isLive) {
        if (!isLive) {
            samples = emptyList()
            current = 0L
            last = null
            return@LaunchedEffect
        }
        while (true) {
            delay(TickMillis)
            val at = last?.second
            if (at != null && System.currentTimeMillis() - at >= TickMillis * 3) current = 0L
            samples = (samples + current).takeLast(SpeedSamples)
        }
    }

    return SpeedHistory(current, samples)
}
