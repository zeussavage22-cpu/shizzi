package dev.shizzi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.shizzi.SessionLimits
import dev.shizzi.SessionRecord
import dev.shizzi.Traffic
import dev.shizzi.UsageHistory
import dev.shizzi.UsageLedger
import dev.shizzi.tr
import dev.shizzi.ui.theme.ScreenPadding
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.SurfaceElevation
import dev.shizzi.ui.theme.themedSurface
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val ChartDays = 14
private val ChartHeight = 140.dp
private const val ConfirmWindowMillis = 4_000L

/** Data shared per day and per session, kept on the phone only. */
@Composable
fun HistoryPage(onClear: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    remember { UsageHistory.load(context) }
    val ledger: UsageLedger by UsageHistory.ledger.collectAsState()
    val today = LocalDate.now()
    val gap = ShizziTheme.spacing.sm

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        ScreenHeader(title = tr("Usage history"), onBack = onBack)

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            Spacer(Modifier.height(ShizziTheme.spacing.sm))

            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                StatTile(Icons.Filled.Today, tr("Today"), Traffic.format(ledger.bytesOn(today)), Modifier.weight(1f))
                StatTile(Icons.Filled.DateRange, tr("7 days"), Traffic.format(ledger.lastDays(today, 7)), Modifier.weight(1f))
                StatTile(Icons.Filled.CalendarMonth, tr("This month"), Traffic.format(ledger.month(today)), Modifier.weight(1f))
            }

            DailyChart(ledger.series(today, ChartDays))

            SectionLabel(tr("Recent sessions"))

            if (ledger.sessions.isEmpty()) {
                Text(
                    text = tr("No sessions yet. Start sharing and they will show up here."),
                    style = ShizziTheme.typography.body,
                    color = ShizziTheme.colors.onSurfaceMuted,
                )
            } else {
                ledger.sessions.forEach { SessionRow(it) }
            }

            Spacer(Modifier.height(ShizziTheme.spacing.md))
            ClearHistoryButton(onClear = onClear)
            Spacer(Modifier.height(ShizziTheme.spacing.xxl))
        }
    }
}

@Composable
private fun DailyChart(series: List<Pair<LocalDate, Long>>) {
    val bar = ShizziTheme.colors.primary
    val empty = ShizziTheme.colors.onSurfaceMuted.copy(alpha = 0.15f)
    val peak = series.maxOfOrNull { it.second } ?: 0L
    val dayFormat = remember { DateTimeFormatter.ofPattern("d/M") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .themedSurface(fill = ShizziTheme.colors.surface, elevation = SurfaceElevation.FLAT)
            .padding(ShizziTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = tr("Last 14 days").uppercase(),
                style = ShizziTheme.typography.caption,
                color = ShizziTheme.colors.onSurfaceMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = tr("Max", "Máx") + " ${Traffic.format(peak)}",
                style = ShizziTheme.typography.caption,
                color = ShizziTheme.colors.onSurfaceMuted,
            )
        }

        Canvas(modifier = Modifier.fillMaxWidth().height(ChartHeight)) {
            if (series.isEmpty()) return@Canvas
            val slot = size.width / series.size
            val barWidth = slot * 0.62f
            val radius = CornerRadius(barWidth / 3, barWidth / 3)
            val top = peak.coerceAtLeast(1L).toFloat()

            series.forEachIndexed { i, (_, bytes) ->
                val x = slot * i + (slot - barWidth) / 2
                val h = if (bytes <= 0) 3.dp.toPx() else (bytes / top) * size.height
                drawRoundRect(
                    color = if (bytes <= 0) empty else bar,
                    topLeft = Offset(x, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = radius,
                )
            }
        }

        Row {
            Text(
                text = series.firstOrNull()?.first?.format(dayFormat).orEmpty(),
                style = ShizziTheme.typography.caption,
                color = ShizziTheme.colors.onSurfaceMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = tr("Today"),
                style = ShizziTheme.typography.caption,
                color = ShizziTheme.colors.onSurfaceMuted,
            )
        }
    }
}

@Composable
private fun SessionRow(record: SessionRecord) {
    val format = remember { DateTimeFormatter.ofPattern("d MMM · HH:mm") }
    val start = remember(record.start) {
        java.time.Instant.ofEpochMilli(record.start)
            .atZone(java.time.ZoneId.systemDefault())
            .format(format)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .themedSurface(fill = ShizziTheme.colors.surface, elevation = SurfaceElevation.FLAT)
            .padding(ShizziTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.xs)) {
            Text(
                text = start,
                style = ShizziTheme.typography.subheading,
                color = ShizziTheme.colors.onSurface,
            )
            Text(
                text = "${SessionLimits.formatElapsed(record.durationMs)} · " +
                    tr("${record.peakDevices} devices max", "${record.peakDevices} dispositivos máx."),
                style = ShizziTheme.typography.body,
                color = ShizziTheme.colors.onSurfaceMuted,
            )
        }

        Text(
            text = Traffic.format(record.bytes),
            style = ShizziTheme.typography.title,
            color = ShizziTheme.colors.primary,
        )
    }
}

/** Two taps to clear, so the history is never wiped by accident. */
@Composable
private fun ClearHistoryButton(onClear: () -> Unit) {
    var isArmed by remember { mutableStateOf(false) }

    LaunchedEffect(isArmed) {
        if (!isArmed) return@LaunchedEffect
        delay(ConfirmWindowMillis)
        isArmed = false
    }

    GhostButton(
        label = if (isArmed) tr("Tap again to clear") else tr("Clear history"),
        isActive = isArmed,
        onClick = {
            if (isArmed) {
                isArmed = false
                onClear()
            } else {
                isArmed = true
            }
        },
    )
}
