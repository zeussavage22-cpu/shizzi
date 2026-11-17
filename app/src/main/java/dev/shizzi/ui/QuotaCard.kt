package dev.shizzi.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.shizzi.Traffic
import dev.shizzi.UsageHistory
import dev.shizzi.UsageLedger
import dev.shizzi.tr
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.SurfaceElevation
import dev.shizzi.ui.theme.themedSurface
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val WarnColor = Color(0xFFF59E0B)
private val FullColor = Color(0xFFE5484D)

/** Progress through the monthly data plan, with the renewal date. */
@Composable
fun QuotaCard(quotaMb: Int, resetDay: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    remember { UsageHistory.load(context) }
    val ledger: UsageLedger by UsageHistory.ledger.collectAsState()

    val today = LocalDate.now()
    val limit = quotaMb * 1_000_000L
    val used = ledger.cycleTotal(today, resetDay)
    val fraction = (used.toFloat() / limit.coerceAtLeast(1)).coerceIn(0f, 1f)
    val shown by animateFloatAsState(fraction, label = "quota")
    val renews = UsageLedger.cycleStart(today, resetDay).plusMonths(1)
    val dateFormat = remember { DateTimeFormatter.ofPattern("d MMM") }

    val colors = ShizziTheme.colors
    val fill = when {
        fraction >= 1f -> Brush.horizontalGradient(listOf(FullColor, FullColor))
        fraction >= 0.8f -> Brush.horizontalGradient(listOf(WarnColor, FullColor))
        else -> Brush.horizontalGradient(listOf(colors.primary, colors.secondary))
    }
    val track = colors.onSurfaceMuted.copy(alpha = 0.15f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .themedSurface(fill = colors.surface, elevation = SurfaceElevation.FLAT)
            .padding(ShizziTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.sm),
    ) {
        Row {
            Text(
                text = tr("Monthly plan", "Plan mensual").uppercase(),
                style = ShizziTheme.typography.caption,
                color = colors.onSurfaceMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(fraction * 100).toInt()}%",
                style = ShizziTheme.typography.caption,
                color = colors.onSurface,
            )
        }

        Text(
            text = tr(
                "${Traffic.format(used)} of ${Traffic.format(limit)}",
                "${Traffic.format(used)} de ${Traffic.format(limit)}",
            ),
            style = ShizziTheme.typography.title,
            color = colors.onSurface,
        )

        Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
            val radius = CornerRadius(size.height / 2, size.height / 2)
            drawRoundRect(color = track, cornerRadius = radius)
            if (shown > 0f) {
                drawRoundRect(brush = fill, size = Size(size.width * shown, size.height), cornerRadius = radius)
            }
        }

        Text(
            text = tr(
                "${Traffic.format((limit - used).coerceAtLeast(0))} left · renews ${renews.format(dateFormat)}",
                "Quedan ${Traffic.format((limit - used).coerceAtLeast(0))} · se renueva el ${renews.format(dateFormat)}",
            ),
            style = ShizziTheme.typography.body,
            color = colors.onSurfaceMuted,
        )
    }
}
