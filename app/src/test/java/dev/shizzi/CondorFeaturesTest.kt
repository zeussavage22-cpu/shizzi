package dev.shizzi

import dev.shizzi.ui.wifiQrPayload
import org.junit.Assert.assertEquals
import org.junit.Test

class CondorFeaturesTest {

    @Test
    fun `wifi qr payload uses the standard format`() {
        assertEquals("WIFI:T:WPA;S:Casa;P:secreta123;;", wifiQrPayload("Casa", "secreta123"))
        assertEquals("WIFI:T:nopass;S:Libre;;", wifiQrPayload("Libre", ""))
    }

    @Test
    fun `wifi qr payload escapes reserved characters`() {
        assertEquals("WIFI:T:WPA;S:a\\;b\\:c;P:p\\,w\\\\d;;", wifiQrPayload("a;b:c", "p,w\\d"))
    }

    @Test
    fun `converts bytes and time to megabits per second`() {
        assertEquals(8.0, mbps(1_000_000, 1_000), 0.0001)
        assertEquals(100.0, mbps(25_000_000, 2_000), 0.0001)
        assertEquals(0.0, mbps(1_000, 0), 0.0)
    }
}

class DataPlanTest {

    private val today = java.time.LocalDate.of(2026, 11, 17)

    @org.junit.Test
    fun `cycle starts on the reset day of this or the previous month`() {
        org.junit.Assert.assertEquals(java.time.LocalDate.of(2026, 11, 1), UsageLedger.cycleStart(today, 1))
        org.junit.Assert.assertEquals(java.time.LocalDate.of(2026, 11, 17), UsageLedger.cycleStart(today, 17))
        org.junit.Assert.assertEquals(java.time.LocalDate.of(2026, 10, 20), UsageLedger.cycleStart(today, 20))
    }

    @org.junit.Test
    fun `cycle total counts only days in the current cycle`() {
        val ledger = UsageLedger()
            .addBytes(java.time.LocalDate.of(2026, 10, 19), 1_000)
            .addBytes(java.time.LocalDate.of(2026, 10, 20), 200)
            .addBytes(today, 30)

        org.junit.Assert.assertEquals(230, ledger.cycleTotal(today, 20))
        org.junit.Assert.assertEquals(30, ledger.cycleTotal(today, 1))
    }

    @org.junit.Test
    fun `records pick the longest, biggest and busiest sessions`() {
        val ledger = UsageLedger()
            .addSession(SessionRecord(start = 0, end = 10_000, bytes = 5, peakDevices = 1))
            .addSession(SessionRecord(start = 0, end = 1_000, bytes = 99, peakDevices = 4))

        val records = ledger.records()
        org.junit.Assert.assertEquals(10_000, records.longest!!.durationMs)
        org.junit.Assert.assertEquals(99, records.biggest!!.bytes)
        org.junit.Assert.assertEquals(4, records.mostDevices)
    }
}
