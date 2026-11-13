package dev.shizzi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.shizzi.SessionLimits
import dev.shizzi.tr
import dev.shizzi.ui.theme.ShizziTheme

private val CheckSize = 20.dp

data class LimitsState(
    val timeLimitMinutes: Int,
    val dataLimitMb: Int,
    val batteryLimitPercent: Int = 0,
    val idleStopMinutes: Int = 0,
)

data class LimitsActions(
    val onSetTimeLimit: (Int) -> Unit,
    val onSetDataLimit: (Int) -> Unit,
    val onSetBatteryLimit: (Int) -> Unit = {},
    val onSetIdleStop: (Int) -> Unit = {},
)

private enum class OpenPicker { NONE, TIME, DATA, BATTERY, IDLE }

private fun idleLabel(minutes: Int): String = when {
    minutes <= 0 -> tr("Off")
    else -> "$minutes min"
}

/** Optional automatic stop after a time or data amount. Both are off unless the user picks one. */
@Composable
fun LimitsSection(state: LimitsState, actions: LimitsActions) {
    var open by remember { mutableStateOf(OpenPicker.NONE) }

    SettingsChoice(
        label = SettingsText(
            title = tr("Stop after"),
            subtitle = tr("Stop sharing automatically after this much time"),
        ),
        value = SessionLimits.timeLabel(state.timeLimitMinutes),
        onClick = { open = OpenPicker.TIME },
    )

    SettingsChoice(
        label = SettingsText(
            title = tr("Data limit"),
            subtitle = tr("Stop sharing automatically once devices use this much data"),
        ),
        value = SessionLimits.dataLabel(state.dataLimitMb),
        onClick = { open = OpenPicker.DATA },
    )

    SettingsChoice(
        label = SettingsText(
            title = tr("Battery saver"),
            subtitle = tr("Stop sharing when the battery runs low and the phone is not charging"),
        ),
        value = SessionLimits.batteryLabel(state.batteryLimitPercent),
        onClick = { open = OpenPicker.BATTERY },
    )

    SettingsChoice(
        label = SettingsText(
            title = tr("Stop when nobody is connected"),
            subtitle = tr("Stop sharing after this long with no devices connected"),
        ),
        value = idleLabel(state.idleStopMinutes),
        onClick = { open = OpenPicker.IDLE },
    )

    when (open) {
        OpenPicker.NONE -> Unit

        OpenPicker.IDLE -> OptionPicker(
            title = tr("Stop when nobody is connected"),
            options = SessionLimits.IDLE_OPTIONS_MINUTES,
            selected = state.idleStopMinutes,
            labelOf = ::idleLabel,
            onSelect = actions.onSetIdleStop,
            onDismiss = { open = OpenPicker.NONE },
        )

        OpenPicker.BATTERY -> OptionPicker(
            title = tr("Battery saver"),
            options = SessionLimits.BATTERY_OPTIONS_PERCENT,
            selected = state.batteryLimitPercent,
            labelOf = SessionLimits::batteryLabel,
            onSelect = actions.onSetBatteryLimit,
            onDismiss = { open = OpenPicker.NONE },
        )

        OpenPicker.TIME -> OptionPicker(
            title = tr("Stop after"),
            options = SessionLimits.TIME_OPTIONS_MINUTES,
            selected = state.timeLimitMinutes,
            labelOf = SessionLimits::timeLabel,
            onSelect = actions.onSetTimeLimit,
            onDismiss = { open = OpenPicker.NONE },
        )

        OpenPicker.DATA -> OptionPicker(
            title = tr("Data limit"),
            options = SessionLimits.DATA_OPTIONS_MB,
            selected = state.dataLimitMb,
            labelOf = SessionLimits::dataLabel,
            onSelect = actions.onSetDataLimit,
            onDismiss = { open = OpenPicker.NONE },
        )
    }
}

@Composable
private fun OptionPicker(
    title: String,
    options: List<Int>,
    selected: Int,
    labelOf: (Int) -> String,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ThemedBottomSheet(onDismiss = onDismiss) {
        Text(
            text = title,
            style = ShizziTheme.typography.heading,
            color = ShizziTheme.colors.onSurface,
        )

        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSelect(option)
                        onDismiss()
                    }
                    .padding(vertical = ShizziTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = labelOf(option),
                    style = ShizziTheme.typography.subheading,
                    color = ShizziTheme.colors.onSurface,
                    modifier = Modifier.weight(1f),
                )

                if (option == selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = ShizziTheme.colors.primary,
                        modifier = Modifier.size(CheckSize),
                    )
                }
            }
        }

        Spacer(Modifier.height(ShizziTheme.spacing.lg))
    }
}
