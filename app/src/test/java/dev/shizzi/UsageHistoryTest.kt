package dev.shizzi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class UsageHistoryTest {

    private val today = LocalDate.of(2026, 11, 13)

    @Test
    fun `adds bytes per day and totals ranges`() {
        val ledger = UsageLedger()
            .addBytes(today, 100)
            .addBytes(today, 50)
            .addBytes(today.minusDays(1), 20)
            .addBytes(today.minusDays(10), 7)

        assertEquals(150, ledger.bytesOn(today))
        assertEquals(170, ledger.lastDays(today, 7))
        assertEquals(177, ledger.month(today))
        assertEquals(14, ledger.series(today, 14).size)
        assertEquals(today, ledger.series(today, 14).last().first)
    }

    @Test
    fun `ignores non positive amounts`() {
        assertEquals(UsageLedger(), UsageLedger().addBytes(today, 0).addBytes(today, -5))
    }

    @Test
    fun `keeps only the newest sessions`() {
        var ledger = UsageLedger()
        repeat(UsageLedger.MAX_SESSIONS + 5) { i -> ledger = ledger.addSession(SessionRecord(i.toLong(), i + 1L, 1, 1)) }

        assertEquals(UsageLedger.MAX_SESSIONS, ledger.sessions.size)
        assertEquals((UsageLedger.MAX_SESSIONS + 4).toLong(), ledger.sessions.first().start)
    }

    @Test
    fun `prunes old days`() {
        val ledger = UsageLedger()
            .addBytes(today.minusDays(UsageLedger.KEEP_DAYS + 1L), 1)
            .addBytes(today, 2)
            .pruned(today)

        assertEquals(mapOf(today.toString() to 2L), ledger.days)
    }

    @Test
    fun `round trips through json and survives garbage`() {
        val ledger = UsageLedger()
            .addBytes(today, 1234)
            .addSession(SessionRecord(start = 10, end = 70_010, bytes = 1234, peakDevices = 3))

        assertEquals(ledger, UsageLedger.fromJson(ledger.toJson()))
        assertEquals(UsageLedger(), UsageLedger.fromJson("not json"))
        assertEquals(UsageLedger(), UsageLedger.fromJson(null))
    }

    @Test
    fun `battery saver stops only below the limit and when not charging`() {
        val connected = SessionUiState(status = UiStatus.CONNECTED, connectedSince = 1)

        assertEquals(LimitReason.BATTERY, SessionLimits.reached(0, 0, connected, 2, 20, 15, false))
        assertNull(SessionLimits.reached(0, 0, connected, 2, 20, 15, true))
        assertNull(SessionLimits.reached(0, 0, connected, 2, 20, 50, false))
        assertNull(SessionLimits.reached(0, 0, connected, 2, 0, 5, false))
        assertNull(SessionLimits.reached(0, 0, connected, 2, 20, -1, false))
    }

    @Test
    fun `limit progress uses the nearest limit`() {
        val state = SessionUiState(
            status = UiStatus.CONNECTED,
            connectedSince = 1_000,
            traffic = Traffic(down = 750_000_000),
        )

        assertNull(SessionLimits.progress(0, 0, state, 1_000))
        assertEquals(0.75f, SessionLimits.progress(0, 1_000, state, 1_000)!!, 0.001f)
        assertEquals(1f, SessionLimits.progress(1, 1_000, state, 1_000 + 120_000)!!, 0.001f)
    }
}
