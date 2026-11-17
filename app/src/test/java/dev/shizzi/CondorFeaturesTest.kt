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
        assertEquals("WIFI:T:WPA;S:a\;b\\:c;P:p\\,w\\\\d;;", wifiQrPayload("a;b:c", "p,w\\d"))
    }

    @Test
    fun `converts bytes and time to megabits per second`() {
        assertEquals(8.0, mbps(1_000_000, 1_000), 0.0001)
        assertEquals(100.0, mbps(25_000_000, 2_000), 0.0001)
        assertEquals(0.0, mbps(1_000, 0), 0.0)
    }
}
