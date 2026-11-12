package dev.shizzi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.shizzi.SessionLimits
import dev.shizzi.SessionUiState
import dev.shizzi.Traffic
import dev.shizzi.tr
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.SurfaceElevation
import dev.shizzi.ui.theme.themedSurface
import kotlinx.coroutines.delay

private const val TickMillis = 1_000L

/**
 * Live numbers for a connected session: devices, data used, current speed and elapsed time.
 *
 * Purely informational: it only reads [state] and never changes the session.
 */
@Composable
fun SessionStats(state: SessionUiState, modifier: Modifier = Modifier) {
    val now by rememberTicker()
    val speed = rememberSpeed(state)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .themedSurface(fill = ShizziTheme.colors.surface, elevation = SurfaceElevation.FLAT)
            .padding(ShizziTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.md),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Stat(tr("Devices"), state.clientCount.toString(), Modifier.weight(1f))
            Stat(tr("Downloaded"), "↓ ${Traffic.format(state.traffic.down)}", Modifier.weight(1f))
            Stat(tr("Uploaded"), "↑ ${Traffic.format(state.traffic.up)}", Modifier.weight(1f))
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Stat(tr("Time"), elapsedText(state, now), Modifier.weight(1f))
            Stat(tr("Speed"), "${Traffic.format(speed)}/s", Modifier.weight(1f))
            Stat(
                tr("Total data"),
                Traffic.format(state.traffic.up + state.traffic.down),
                Modifier.weight(1f),
            )
        }
    }
}

private fun elapsedText(state: SessionUiState, now: Long): String = when {
    state.connectedSince <= 0L -> "–"
    else -> SessionLimits.formatElapsed(now - state.connectedSince)
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.xs),
    ) {
        Text(
            text = value,
            style = ShizziTheme.typography.label,
            color = ShizziTheme.colors.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label.uppercase(),
            style = ShizziTheme.typography.caption,
            color = ShizziTheme.colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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

/** Bytes per second across both directions, from the change between status updates. */
@Composable
private fun rememberSpeed(state: SessionUiState): Long {
    var speed by remember { mutableLongStateOf(0L) }
    var last by remember { mutableStateOf<Pair<Long, Long>?>(null) }

    val total = state.traffic.up + state.traffic.down

    LaunchedEffect(total) {
        val now = System.currentTimeMillis()
        val previous = last
        last = total to now

        if (previous == null) return@LaunchedEffect

        val (previousTotal, previousAt) = previous
        val elapsed = now - previousAt
        val delta = total - previousTotal
        speed = when {
            delta < 0 -> 0L
            elapsed <= 0 -> speed
            else -> delta * 1000 / elapsed
        }
    }

    LaunchedEffect(Unit) {
        // Settle to zero when no new bytes arrive for a while.
        while (true) {
            delay(TickMillis * 3)
            val at = last?.second ?: continue
            if (System.currentTimeMillis() - at >= TickMillis * 3) speed = 0L
        }
    }

    return speed
}
