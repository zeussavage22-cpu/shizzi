package dev.shizzi.ui

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.shizzi.Schedule
import dev.shizzi.tr

data class ScheduleState(
    val isEnabled: Boolean = false,
    val startMinute: Int = dev.shizzi.DEFAULT_SCHEDULE_START,
    val stopMinute: Int = dev.shizzi.DEFAULT_SCHEDULE_STOP,
    val notifyDevices: Boolean = false,
)

data class ScheduleActions(
    val onSetSchedule: (isEnabled: Boolean, startMinute: Int, stopMinute: Int) -> Unit = { _, _, _ -> },
    val onSetNotifyDevices: (Boolean) -> Unit = {},
)

/** Daily automatic start/stop and device alerts. Everything here is off by default. */
@Composable
fun ScheduleSection(state: ScheduleState, actions: ScheduleActions) {
    val context = LocalContext.current

    fun pickTime(initial: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            context,
            { _, hour, minute -> onPicked(hour * 60 + minute) },
            initial / 60,
            initial % 60,
            DateFormat.is24HourFormat(context),
        ).show()
    }

    SettingsToggle(
        label = SettingsText(
            title = tr("Automatic schedule"),
            subtitle = tr("Start and stop sharing every day at the times below"),
        ),
        isChecked = state.isEnabled,
        onCheckedChange = { actions.onSetSchedule(it, state.startMinute, state.stopMinute) },
    )

    if (state.isEnabled) {
        SettingsChoice(
            label = SettingsText(title = tr("Turn on at")),
            value = Schedule.format(state.startMinute),
            onClick = {
                pickTime(state.startMinute) { actions.onSetSchedule(true, it, state.stopMinute) }
            },
        )

        SettingsChoice(
            label = SettingsText(title = tr("Turn off at")),
            value = Schedule.format(state.stopMinute),
            onClick = {
                pickTime(state.stopMinute) { actions.onSetSchedule(true, state.startMinute, it) }
            },
        )
    }

    SettingsToggle(
        label = SettingsText(
            title = tr("Device alerts"),
            subtitle = tr("Get a notification when a device connects or disconnects"),
        ),
        isChecked = state.notifyDevices,
        onCheckedChange = actions.onSetNotifyDevices,
    )
}
