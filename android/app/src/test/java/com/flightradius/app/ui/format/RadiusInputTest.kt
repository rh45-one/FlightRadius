package com.flightradius.app.ui.format

import com.flightradius.app.domain.DistanceUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadiusInputTest {
    private val km = DistanceUnit.KM
    private val mi = DistanceUnit.MI

    @Test fun `km steps are 1 to 50 by 1 then 55 to 200 by 5`() {
        val s = RadiusInput.steps(km)
        assertEquals(1, s.first())
        assertEquals(50, s[49])
        assertEquals(55, s[50])
        assertEquals(200, s.last())
        assertEquals(50 + 30, s.size)
        assertTrue(s.zipWithNext().all { (a, b) -> b > a })
    }

    @Test fun `mile steps are 1 to 30 by 1 then 35 to 125 by 5`() {
        val s = RadiusInput.steps(mi)
        assertEquals(1, s.first())
        assertEquals(30, s[29])
        assertEquals(35, s[30])
        assertEquals(125, s.last())
        assertEquals(30 + 19, s.size)
    }

    @Test fun `stored values snap to the nearest step`() {
        assertEquals(10.0, RadiusInput.snapKm(10.3, km), 1e-9)
        assertEquals(11.0, RadiusInput.snapKm(10.6, km), 1e-9)
        assertEquals(1.0, RadiusInput.snapKm(0.5, km), 1e-9)
        assertEquals(200.0, RadiusInput.snapKm(500.0, km), 1e-9)
    }

    @Test fun `snapping in the middle of the coarse range`() {
        assertEquals(50.0, RadiusInput.snapKm(52.0, km), 1e-9)
        assertEquals(55.0, RadiusInput.snapKm(53.0, km), 1e-9)
        assertEquals(60.0, RadiusInput.snapKm(61.0, km), 1e-9)
    }

    @Test fun `miles convert to km and snap in miles`() {
        // 16.09 km is 10 mi.
        assertEquals(9, RadiusInput.nearestIndex(16.0, mi))
        assertEquals(10.0, Format.kmToUnit(RadiusInput.snapKm(16.0, mi), mi), 1e-6)
        assertEquals(RadiusInput.toKm(10.0, mi), RadiusInput.kmAt(9, mi), 1e-9)
    }

    @Test fun `kmAt clamps the index`() {
        assertEquals(1.0, RadiusInput.kmAt(-5, km), 1e-9)
        assertEquals(200.0, RadiusInput.kmAt(10_000, km), 1e-9)
    }

    @Test fun `dialog parsing accepts comma and point`() {
        assertEquals(10.0, RadiusInput.parseKm("10", km)!!, 1e-9)
        assertEquals(10.5, RadiusInput.parseKm("10,5", km)!!, 1e-9)
        assertEquals(10.5, RadiusInput.parseKm("10.5", km)!!, 1e-9)
        assertEquals(10.5, RadiusInput.parseKm("  10,5 ", km)!!, 1e-9)
    }

    @Test fun `dialog parsing rejects junk and out of range values`() {
        for (bad in listOf("", " ", "abc", "0", "0.4", "500.1", "-3", "1,2,3", "NaN", "Infinity")) {
            assertNull(bad, RadiusInput.parseKm(bad, km))
        }
        assertEquals(0.5, RadiusInput.parseKm("0.5", km)!!, 1e-9)
        assertEquals(500.0, RadiusInput.parseKm("500", km)!!, 1e-9)
    }

    @Test fun `range is applied in the users unit`() {
        assertNull(RadiusInput.parseKm("311", mi)) // 500.5 km
        assertEquals(RadiusInput.toKm(310.0, mi), RadiusInput.parseKm("310", mi)!!, 1e-9)
        assertNull(RadiusInput.parseKm("0.3", mi)) // 0.48 km
    }

    @Test fun `field text drops a trailing zero and uses the locale separator`() {
        val old = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.US)
            assertEquals("10", RadiusInput.toFieldText(10.0, km))
            assertEquals("10.5", RadiusInput.toFieldText(10.5, km))
            assertEquals("25", RadiusInput.toFieldText(25.0, km))
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("es-ES"))
            assertEquals("10,5", RadiusInput.toFieldText(10.5, km))
            assertEquals(10.5, RadiusInput.parseKm(RadiusInput.toFieldText(10.5, km), km)!!, 1e-9)
        } finally {
            java.util.Locale.setDefault(old)
        }
    }

    @Test fun `radius formatting`() {
        java.util.Locale.setDefault(java.util.Locale.US)
        assertEquals("10 km", Format.radius(10.0, km))
        assertEquals("0.5 km", Format.radius(0.5, km))
        assertEquals("10 mi", Format.radius(RadiusInput.toKm(10.0, mi), mi))
    }
}
