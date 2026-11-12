package dev.shizzi

import androidx.datastore.preferences.core.preferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class SessionLimitsTest {

    private val start = 1_000_000L
    private val connected = SessionUiState(status = UiStatus.CONNECTED, connectedSince = start)

    @Test
    fun `no limits never stop a session`() {
        val heavy = connected.copy(traffic = Traffic(up = 50_000_000_000, down = 50_000_000_000))

        assertNull(SessionLimits.reached(0, 0, heavy, now = start + 24 * 3_600_000L))
    }

    @Test
    fun `time limit trips once the time has passed`() {
        assertNull(SessionLimits.reached(30, 0, connected, now = start + 29 * 60_000L))
        assertEquals(LimitReason.TIME, SessionLimits.reached(30, 0, connected, now = start + 30 * 60_000L))
    }

    @Test
    fun `data limit counts both directions`() {
        val under = connected.copy(traffic = Traffic(up = 400_000_000, down = 599_999_999))
        val over = connected.copy(traffic = Traffic(up = 400_000_000, down = 600_000_000))

        assertNull(SessionLimits.reached(0, 1_000, under, now = start))
        assertEquals(LimitReason.DATA, SessionLimits.reached(0, 1_000, over, now = start))
    }

    @Test
    fun `only a connected session can reach a limit`() {
        val stopped = connected.copy(status = UiStatus.READY, traffic = Traffic(down = 9_000_000_000))

        assertNull(SessionLimits.reached(1, 1, stopped, now = start + 3_600_000L))
    }

    @Test
    fun `unknown start time never trips the time limit`() {
        val unknown = connected.copy(connectedSince = 0L)

        assertNull(SessionLimits.reached(1, 0, unknown, now = start + 3_600_000L))
    }

    @Test
    fun `start time is stamped once and cleared when not connected`() {
        val first = SessionUiState(status = UiStatus.CONNECTED).stampConnectedSince(5L)
        assertEquals(5L, first.connectedSince)

        assertEquals(5L, first.stampConnectedSince(99L).connectedSince)
        assertEquals(0L, first.copy(status = UiStatus.READY).stampConnectedSince(99L).connectedSince)
        assertEquals(0L, first.asStopped().connectedSince)
    }

    @Test
    fun `formats elapsed time`() {
        assertEquals("0:05", SessionLimits.formatElapsed(5_000))
        assertEquals("12:00", SessionLimits.formatElapsed(720_000))
        assertEquals("1:02:03", SessionLimits.formatElapsed(3_723_000))
    }

    @Test
    fun `limits default to off and read back`() {
        assertEquals(0, toSettings(preferencesOf()).timeLimitMinutes)
        assertEquals(0, toSettings(preferencesOf()).dataLimitMb)

        val stored = preferencesOf(TIME_LIMIT_MINUTES to 60, DATA_LIMIT_MB to 2_000)
        assertEquals(60, toSettings(stored).timeLimitMinutes)
        assertEquals(2_000, toSettings(stored).dataLimitMb)
    }

    @Test
    fun `translation falls back to english and switches to spanish`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            assertEquals("Start", tr("Start"))
            assertEquals("Not in the table", tr("Not in the table"))

            Locale.setDefault(Locale.forLanguageTag("es-MX"))
            assertEquals("Iniciar", tr("Start"))
            assertEquals("Not in the table", tr("Not in the table"))
            assertEquals("3 dispositivos", tr("3 devices", "3 dispositivos"))
        } finally {
            Locale.setDefault(original)
        }
    }
}
