package com.flightradius.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {

    @Test
    fun `distance London to Paris matches backend expectation`() {
        val d = Geo.distanceKm(51.5074, -0.1278, 48.8566, 2.3522)
        assertTrue(d in 343.0..344.0)
    }

    @Test
    fun `distance to self is zero`() {
        assertEquals(0.0, Geo.distanceKm(10.0, 20.0, 10.0, 20.0), 1e-9)
    }

    @Test
    fun `bearing due north is 0`() {
        val b = Geo.initialBearingDeg(50.0, 8.0, 51.0, 8.0)
        assertTrue(b < 5.0 || b > 355.0)
    }

    @Test
    fun `bearing due east is ~90`() {
        val b = Geo.initialBearingDeg(50.0, 8.0, 50.0, 10.0)
        assertEquals(90.0, b, 8.0)
    }

    @Test
    fun `bearing due south is ~180`() {
        val b = Geo.initialBearingDeg(50.0, 8.0, 49.0, 8.0)
        assertEquals(180.0, b, 5.0)
    }

    @Test
    fun `bearing due west is ~270`() {
        val b = Geo.initialBearingDeg(50.0, 8.0, 50.0, 6.0)
        assertEquals(270.0, b, 8.0)
    }

    @Test
    fun `deriveSpeedHeading computes plausible speed`() {
        // ~111.19 km per degree latitude at equator; 0.1 deg south in 60s.
        val prev = TimedPosition(50.0, 8.0, 1000.0)
        val cur = TimedPosition(49.9, 8.0, 1060.0)
        val m = Geo.deriveSpeedHeading(prev, cur)!!
        assertEquals(11119.0 / 60.0, m.speedMps, 60.0)
        assertEquals(180.0, m.headingDeg, 5.0)
    }

    @Test
    fun `deriveSpeedHeading returns null when dt under 1s`() {
        val prev = TimedPosition(50.0, 8.0, 1000.0)
        val cur = TimedPosition(50.0, 8.0, 1000.5)
        assertNull(Geo.deriveSpeedHeading(prev, cur))
    }
}
