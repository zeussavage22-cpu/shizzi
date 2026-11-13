package dev.shizzi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleTest {

    private val zone = ZoneId.of("America/Mexico_City")

    private fun at(hour: Int, minute: Int) =
        ZonedDateTime.of(2026, 11, 13, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `next time is later today when still ahead`() {
        assertEquals(at(22, 0), Schedule.nextAt(at(9, 30), 22 * 60, zone))
    }

    @Test
    fun `next time rolls to tomorrow once passed or exactly now`() {
        val tomorrow8 = ZonedDateTime.of(2026, 11, 14, 8, 0, 0, 0, zone).toInstant().toEpochMilli()

        assertEquals(tomorrow8, Schedule.nextAt(at(9, 30), 8 * 60, zone))
        assertEquals(tomorrow8, Schedule.nextAt(at(8, 0), 8 * 60, zone))
    }

    @Test
    fun `window handles crossing midnight`() {
        assertTrue(Schedule.isInsideWindow(9 * 60, 8 * 60, 22 * 60))
        assertFalse(Schedule.isInsideWindow(23 * 60, 8 * 60, 22 * 60))
        assertTrue(Schedule.isInsideWindow(23 * 60, 22 * 60, 6 * 60))
        assertTrue(Schedule.isInsideWindow(1 * 60, 22 * 60, 6 * 60))
        assertFalse(Schedule.isInsideWindow(12 * 60, 22 * 60, 6 * 60))
    }

    @Test
    fun `formats times`() {
        assertEquals("08:05", Schedule.format(8 * 60 + 5))
        assertEquals("22:00", Schedule.format(22 * 60))
    }

    @Test
    fun `idle stop needs no devices for the whole period`() {
        val empty = SessionUiState(status = UiStatus.CONNECTED, clientCount = 0)
        val busy = empty.copy(clientCount = 2)

        assertTrue(SessionLimits.idleReached(5, empty, lastActiveAt = 1_000, now = 1_000 + 5 * 60_000))
        assertFalse(SessionLimits.idleReached(5, empty, lastActiveAt = 1_000, now = 1_000 + 4 * 60_000))
        assertFalse(SessionLimits.idleReached(5, busy, lastActiveAt = 1_000, now = 1_000 + 60 * 60_000))
        assertFalse(SessionLimits.idleReached(0, empty, lastActiveAt = 1_000, now = 1_000 + 60 * 60_000))
    }

    @Test
    fun `new settings default to off`() {
        val settings = toSettings(androidx.datastore.preferences.core.preferencesOf())

        assertFalse(settings.scheduleEnabled)
        assertFalse(settings.notifyDevices)
        assertEquals(0, settings.idleStopMinutes)
        assertEquals(0, settings.batteryLimitPercent)
    }
}
