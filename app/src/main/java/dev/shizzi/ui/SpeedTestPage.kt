package dev.shizzi.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shizzi.SpeedPhase
import dev.shizzi.SpeedTest
import dev.shizzi.SpeedTestState
import dev.shizzi.tr
import dev.shizzi.ui.theme.ScreenPadding
import dev.shizzi.ui.theme.ShizziTheme
import kotlin.math.ln

private val GaugeSize = 260.dp
private const val GaugeSweep = 260f
private const val GaugeStart = 140f

/** Maps a speed onto the gauge on a log scale, so 5 Mbps and 500 Mbps both read well. */
private fun gaugeFraction(mbps: Double): Float {
    if (mbps <= 0) return 0f
    return (ln(1 + mbps) / ln(1 + 1000.0)).toFloat().coerceIn(0f, 1f)
}

private fun formatMbps(value: Double?): String = when {
    value == null -> "–"
    value >= 100 -> "%.0f".format(value)
    else -> "%.1f".format(value)
}

@Composable
fun SpeedTestPage(onStart: () -> Unit) {
    val test: SpeedTestState by SpeedTest.state.collectAsState()
    val gap = ShizziTheme.spacing.sm

    val shown = when (test.phase) {
        SpeedPhase.DOWNLOAD, SpeedPhase.UPLOAD -> test.liveMbps
        SpeedPhase.DONE -> test.downloadMbps ?: 0.0
        else -> 0.0
    }
    val needle by animateFloatAsState(gaugeFraction(shown), label = "gauge")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        Text(
            text = tr("Speed test", "Prueba de velocidad"),
            style = ShizziTheme.typography.display,
            color = ShizziTheme.colors.onSurface,
            modifier = Modifier.fillMaxWidth().padding(top = ShizziTheme.spacing.lg),
        )
        Text(
            text = tr(
                "Measures this phone's own connection, which is what devices on your hotspot share.",
                "Mide la conexión de este teléfono, que es la que comparten los dispositivos de tu hotspot.",
            ),
            style = ShizziTheme.typography.body,
            color = ShizziTheme.colors.onSurfaceMuted,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(ShizziTheme.spacing.md))

        Box(modifier = Modifier.size(GaugeSize), contentAlignment = Alignment.Center) {
            Gauge(fraction = needle)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (test.phase == SpeedPhase.IDLE) "0" else formatMbps(shown),
                    style = ShizziTheme.typography.display.copy(fontSize = 52.sp, fontWeight = FontWeight.W700),
                    color = ShizziTheme.colors.onSurface,
                )
                Text(
                    text = "Mbps",
                    style = ShizziTheme.typography.label,
                    color = ShizziTheme.colors.onSurfaceMuted,
                )
                Text(
                    text = phaseLabel(test).uppercase(),
                    style = ShizziTheme.typography.caption,
                    color = ShizziTheme.colors.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = ShizziTheme.spacing.xs),
                )
            }
        }

        ConnectButton(
            label = when {
                test.isRunning -> tr("Testing…", "Midiendo…")
                test.phase == SpeedPhase.IDLE -> tr("Start test", "Iniciar")
                else -> tr("Test again", "Repetir")
            },
            state = if (test.isRunning) ConnectButtonState.LOADING else ConnectButtonState.START,
            onClick = onStart,
        )

        Spacer(Modifier.height(ShizziTheme.spacing.sm))

        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            StatTile(Icons.Filled.NetworkPing, "Ping", test.pingMs?.let { "$it ms" } ?: "–", Modifier.weight(1f))
            StatTile(Icons.Filled.ArrowDownward, tr("Download", "Bajada"), formatMbps(test.downloadMbps) + " Mbps", Modifier.weight(1f))
            StatTile(Icons.Filled.ArrowUpward, tr("Upload", "Subida"), formatMbps(test.uploadMbps) + " Mbps", Modifier.weight(1f))
        }

        if (test.phase == SpeedPhase.FAILED) {
            Text(
                text = tr("The test failed: ", "La prueba falló: ") + test.error,
                style = ShizziTheme.typography.body,
                color = ShizziTheme.colors.onSurfaceMuted,
            )
        }

        Spacer(Modifier.height(ShizziTheme.spacing.xxxl))
    }
}

private fun phaseLabel(test: SpeedTestState): String = when (test.phase) {
    SpeedPhase.IDLE -> tr("Ready", "Listo")
    SpeedPhase.PING -> tr("Measuring ping", "Midiendo ping")
    SpeedPhase.DOWNLOAD -> tr("Download", "Bajada")
    SpeedPhase.UPLOAD -> tr("Upload", "Subida")
    SpeedPhase.DONE -> tr("Download result", "Resultado de bajada")
    SpeedPhase.FAILED -> tr("Failed")
}

@Composable
private fun Gauge(fraction: Float) {
    val colors = ShizziTheme.colors
    val track = colors.onSurfaceMuted.copy(alpha = 0.14f)
    val gradient = Brush.sweepGradient(listOf(colors.secondary, colors.primary, colors.secondary))

    Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = 16.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)

        drawArc(track, GaugeStart, GaugeSweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        if (fraction > 0f) {
            drawArc(
                brush = gradient,
                startAngle = GaugeStart,
                sweepAngle = GaugeSweep * fraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}
