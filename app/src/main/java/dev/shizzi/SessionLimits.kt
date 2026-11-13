package dev.shizzi

/** Why a session was stopped automatically. */
enum class LimitReason { TIME, DATA, BATTERY }

/**
 * Optional, user-chosen limits that end a sharing session on their own.
 *
 * Both limits are off (0) by default, so nothing changes unless the user turns one on.
 */
object SessionLimits {

    /** Choices offered for the time limit, in minutes. 0 means off. */
    val TIME_OPTIONS_MINUTES = listOf(0, 15, 30, 60, 120, 240, 480)

    /** Choices offered for the data limit, in megabytes (1 MB = 1,000,000 bytes). 0 means off. */
    val DATA_OPTIONS_MB = listOf(0, 250, 500, 1_000, 2_000, 5_000, 10_000, 20_000)

    /** Choices offered for the battery saver, in percent. 0 means off. */
    val BATTERY_OPTIONS_PERCENT = listOf(0, 10, 15, 20, 30, 40, 50)

    private const val BYTES_PER_MB = 1_000_000L
    private const val MS_PER_MINUTE = 60_000L

    /**
     * Returns which limit [state] has reached, or null if none.
     *
     * Only a connected session can reach a limit. A session whose start time is unknown
     * ([SessionUiState.connectedSince] == 0) never trips the time limit.
     */
    fun reached(
        timeLimitMinutes: Int,
        dataLimitMb: Int,
        state: SessionUiState,
        now: Long,
        batteryLimitPercent: Int = 0,
        batteryPercent: Int = -1,
        isCharging: Boolean = false,
    ): LimitReason? {
        if (state.status != UiStatus.CONNECTED) return null

        val hasBatteryReading = batteryPercent in 0..100
        if (batteryLimitPercent > 0 && hasBatteryReading && !isCharging &&
            batteryPercent <= batteryLimitPercent
        ) {
            return LimitReason.BATTERY
        }

        val hasTimeLimit = timeLimitMinutes > 0 && state.connectedSince > 0
        if (hasTimeLimit && now - state.connectedSince >= timeLimitMinutes * MS_PER_MINUTE) {
            return LimitReason.TIME
        }

        val used = state.traffic.up + state.traffic.down
        if (dataLimitMb > 0 && used >= dataLimitMb * BYTES_PER_MB) return LimitReason.DATA

        return null
    }

    fun timeLabel(minutes: Int): String = when {
        minutes <= 0 -> tr("Off")
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }

    fun dataLabel(megabytes: Int): String = when {
        megabytes <= 0 -> tr("Off")
        else -> Traffic.format(megabytes * BYTES_PER_MB)
    }

    fun batteryLabel(percent: Int): String = when {
        percent <= 0 -> tr("Off")
        else -> tr("Below $percent%", "Por debajo del $percent%")
    }

    fun describe(reason: LimitReason): String = when (reason) {
        LimitReason.TIME -> tr("Time limit reached")
        LimitReason.DATA -> tr("Data limit reached")
        LimitReason.BATTERY -> tr("Battery is low")
    }

    /**
     * How far the session is toward its nearest time or data limit, from 0 to 1,
     * or null when neither limit is on.
     */
    fun progress(timeLimitMinutes: Int, dataLimitMb: Int, state: SessionUiState, now: Long): Float? {
        val parts = buildList {
            if (timeLimitMinutes > 0 && state.connectedSince > 0) {
                add((now - state.connectedSince).toFloat() / (timeLimitMinutes * MS_PER_MINUTE))
            }
            if (dataLimitMb > 0) {
                add((state.traffic.up + state.traffic.down).toFloat() / (dataLimitMb * BYTES_PER_MB))
            }
        }
        return parts.maxOrNull()?.coerceIn(0f, 1f)
    }

    /** Formats an elapsed duration as H:MM:SS (or M:SS under an hour). */
    fun formatElapsed(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            hours > 0 -> "%d:%02d:%02d".format(hours, minutes, seconds)
            else -> "%d:%02d".format(minutes, seconds)
        }
    }
}
