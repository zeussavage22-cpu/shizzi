package dev.shizzi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.shizzi.SessionLimits
import dev.shizzi.tr

data class PlanState(
    val quotaMb: Int = 0,
    val resetDay: Int = 1,
    val autoStop: Boolean = false,
    val appLock: Boolean = false,
    val fastEngine: Boolean = false,
)

data class PlanActions(
    val onSetQuota: (megabytes: Int, resetDay: Int, autoStop: Boolean) -> Unit = { _, _, _ -> },
    val onSetAppLock: (Boolean) -> Unit = {},
    val onSetFastEngine: (Boolean) -> Unit = {},
)

private enum class PlanPicker { NONE, QUOTA, DAY }

/** Monthly data plan tracking and the app lock. Both are off by default. */
@Composable
fun PlanSection(state: PlanState, actions: PlanActions) {
    var open by remember { mutableStateOf(PlanPicker.NONE) }

    SettingsChoice(
        label = SettingsText(
            title = tr("Monthly data plan", "Plan de datos mensual"),
            subtitle = tr(
                "Track how much you share each month and get warned at 80%",
                "Controla cuánto compartes al mes y recibe un aviso al 80 %",
            ),
        ),
        value = SessionLimits.quotaLabel(state.quotaMb),
        onClick = { open = PlanPicker.QUOTA },
    )

    if (state.quotaMb > 0) {
        SettingsChoice(
            label = SettingsText(title = tr("Renews on day", "Se renueva el día")),
            value = state.resetDay.toString(),
            onClick = { open = PlanPicker.DAY },
        )

        SettingsToggle(
            label = SettingsText(
                title = tr("Stop at the limit", "Detener al llegar al límite"),
                subtitle = tr("Stop sharing once the plan is used up", "Deja de compartir cuando se acabe el plan"),
            ),
            isChecked = state.autoStop,
            onCheckedChange = { actions.onSetQuota(state.quotaMb, state.resetDay, it) },
        )
    }

    SettingsToggle(
        label = SettingsText(
            title = tr("Fast engine (experimental)", "Motor rápido (experimental)"),
            subtitle = tr(
                "Lets Android's own TCP carry connections: faster and steadier on weak Wi-Fi. " +
                    "Applies on the next start; if your phone blocks it, the app falls back by itself.",
                "Deja que el TCP de Android lleve las conexiones: más rápido y estable con Wi-Fi débil. " +
                    "Se aplica al siguiente inicio; si tu teléfono no lo permite, la app vuelve sola al motor normal.",
            ),
        ),
        isChecked = state.fastEngine,
        onCheckedChange = actions.onSetFastEngine,
    )

    SettingsToggle(
        label = SettingsText(
            title = tr("App lock", "Bloqueo de la app"),
            subtitle = tr(
                "Ask for your fingerprint or screen lock to open the app",
                "Pide tu huella o el bloqueo de pantalla para abrir la app",
            ),
        ),
        isChecked = state.appLock,
        onCheckedChange = actions.onSetAppLock,
    )

    when (open) {
        PlanPicker.NONE -> Unit

        PlanPicker.QUOTA -> OptionPicker(
            title = tr("Monthly data plan", "Plan de datos mensual"),
            options = SessionLimits.QUOTA_OPTIONS_MB,
            selected = state.quotaMb,
            labelOf = SessionLimits::quotaLabel,
            onSelect = { actions.onSetQuota(it, state.resetDay, state.autoStop) },
            onDismiss = { open = PlanPicker.NONE },
        )

        PlanPicker.DAY -> OptionPicker(
            title = tr("Renews on day", "Se renueva el día"),
            options = (1..28).toList(),
            selected = state.resetDay,
            labelOf = { it.toString() },
            onSelect = { actions.onSetQuota(state.quotaMb, it, state.autoStop) },
            onDismiss = { open = PlanPicker.NONE },
        )
    }
}
