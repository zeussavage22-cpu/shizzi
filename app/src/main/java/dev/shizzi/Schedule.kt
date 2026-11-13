package dev.shizzi

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Daily automatic start and stop. Off by default.
 *
 * Alarms are re-armed after each firing, after a reboot and whenever the settings change.
 */
object Schedule {

    const val ACTION_START = "dev.shizzi.action.SCHEDULE_START"
    const val ACTION_STOP = "dev.shizzi.action.SCHEDULE_STOP"

    private const val REQUEST_START = 41
    private const val REQUEST_STOP = 42

    /** Next time (epoch ms) strictly after [now] that the clock reads [minuteOfDay]. */
    fun nextAt(now: Long, minuteOfDay: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        val minute = minuteOfDay.coerceIn(0, 24 * 60 - 1)
        val time = LocalTime.of(minute / 60, minute % 60)
        val current = Instant.ofEpochMilli(now).atZone(zone)
        var candidate = current.toLocalDate().atTime(time).atZone(zone)
        if (!candidate.toInstant().isAfter(current.toInstant())) {
            candidate = LocalDate.from(current).plusDays(1).atTime(time).atZone(zone)
        }
        return candidate.toInstant().toEpochMilli()
    }

    /** True when [minuteOfDay] falls inside the start..stop window (which may cross midnight). */
    fun isInsideWindow(minuteOfDay: Int, start: Int, stop: Int): Boolean = when {
        start == stop -> false
        start < stop -> minuteOfDay in start until stop
        else -> minuteOfDay >= start || minuteOfDay < stop
    }

    fun format(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

    fun sync(context: Context, settings: Settings) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val start = pending(context, ACTION_START, REQUEST_START)
        val stop = pending(context, ACTION_STOP, REQUEST_STOP)
        alarms.cancel(start)
        alarms.cancel(stop)

        if (!settings.scheduleEnabled) return

        val now = System.currentTimeMillis()
        arm(alarms, nextAt(now, settings.scheduleStartMinute), start)
        arm(alarms, nextAt(now, settings.scheduleStopMinute), stop)
        SessionLog.info(
            "schedule armed: on ${format(settings.scheduleStartMinute)}, " +
                "off ${format(settings.scheduleStopMinute)}",
        )
    }

    private fun arm(alarms: AlarmManager, at: Long, intent: PendingIntent) {
        val canBeExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        runCatching {
            if (canBeExact) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            }
        }.onFailure {
            runCatching { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent) }
        }
    }

    private fun pending(context: Context, action: String, request: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            request,
            Intent(context, ScheduleReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

/** Fires the scheduled start/stop and re-arms the schedule (also after boot or an update). */
class ScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val application = context.applicationContext
        val pending = goAsync()

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = (application as App).settingsStore.settings.first()
                when (intent.action) {
                    Schedule.ACTION_START -> if (settings.scheduleEnabled) startIfIdle(application)
                    Schedule.ACTION_STOP -> if (settings.scheduleEnabled) stopIfRunning(application)
                }
                Schedule.sync(application, settings)
            } catch (failure: Throwable) {
                SessionLog.error("schedule: ${failure.javaClass.simpleName}: ${failure.message}")
            } finally {
                pending.finish()
            }
        }
    }

    private fun startIfIdle(context: Context) {
        if (SessionService.isSessionUp || SessionService.isSessionBusy) return
        SessionLog.info("schedule: starting")
        runCatching { SessionService.start(context) }
            .onFailure { SessionLog.error("schedule could not start: ${it.message}") }
    }

    private fun stopIfRunning(context: Context) {
        if (!SessionService.isRunning) return
        SessionLog.info("schedule: stopping")
        runCatching { SessionService.stop(context) }
            .onFailure { SessionLog.error("schedule could not stop: ${it.message}") }
    }
}

/** Alarms do not survive a reboot, an app update or a clock change, so re-arm them then. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val application = context.applicationContext
        val pending = goAsync()

        CoroutineScope(Dispatchers.Default).launch {
            try {
                Schedule.sync(application, (application as App).settingsStore.settings.first())
            } catch (failure: Throwable) {
                SessionLog.error("schedule re-arm failed: ${failure.message}")
            } finally {
                pending.finish()
            }
        }
    }
}
