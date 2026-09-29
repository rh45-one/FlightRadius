package com.flightradius.app.ui.format

import com.flightradius.app.domain.AircraftObservation
import com.flightradius.app.domain.DistanceUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class FormatTest {

    @Test
    fun `distance formatting per unit`() {
        assertEquals("12.3 km", Format.distance(12.34, DistanceUnit.KM))
        assertEquals("7.7 mi", Format.distance(12.34, DistanceUnit.MI))
        assertEquals("12.3", Format.distanceNumber(12.34, DistanceUnit.KM))
        assertEquals("mi", Format.distanceUnitLabel(DistanceUnit.MI))
    }

    @Test
    fun `speed converts mps to unit speed`() {
        assertEquals("360 km/h", Format.speed(100.0, DistanceUnit.KM))
        assertEquals("224 mph", Format.speed(100.0, DistanceUnit.MI))
        assertNull(Format.speed(null, DistanceUnit.KM))
    }

    @Test
    fun `altitude uses flight level above 5000 ft`() {
        assertEquals("FL350", Format.altitude(10_668.0, DistanceUnit.KM))
        assertEquals("500 m", Format.altitude(500.0, DistanceUnit.KM))
        assertNull(Format.altitude(null, DistanceUnit.KM))
    }

    @Test
    fun `bearing formats degrees and cardinal`() {
        assertEquals("045° NE", Format.bearing(45.0))
        assertEquals("000° N", Format.bearing(360.0))
    }

    @Test
    fun `age and countdown`() {
        assertEquals("now", Format.age(10_000, 10_000))
        assertEquals("12 s ago", Format.age(22_000, 10_000))
        assertEquals("3 min ago", Format.age(200_000, 10_000))
        assertEquals("12s", Format.countdown(0, 12_000))
        assertEquals("1m 5s", Format.countdown(0, 65_000))
    }

    @Test
    fun `closing indicator`() {
        val o = AircraftObservation(
            aircraftId = 1, callsign = "X", icao24 = null,
            distanceKm = 5.0, lat = 0.0, lon = 0.0,
            bearingDeg = 90.0, effectiveRadiusKm = 25.0,
            closingSpeedKmh = 420.0
        )
        assertEquals("▲ approaching · 420 km/h", Format.closing(o, DistanceUnit.KM))
        assertEquals(
            "▼ receding · 100 km/h",
            Format.closing(o.copy(closingSpeedKmh = -100.0), DistanceUnit.KM)
        )
        assertNull(Format.closing(o.copy(closingSpeedKmh = null), DistanceUnit.KM))
        // Below 5 km/h: neutral "steady", no arrow and no numeric speed.
        assertEquals("steady",
            Format.closing(o.copy(closingSpeedKmh = 3.0), DistanceUnit.KM))
        assertEquals("steady",
            Format.closing(o.copy(closingSpeedKmh = -4.9), DistanceUnit.KM))
        assertTrue(Format.closingIsSteady(o.copy(closingSpeedKmh = 0.0)))
        assertTrue(!Format.closingIsSteady(o.copy(closingSpeedKmh = 12.0)))
        assertTrue(!Format.closingIsSteady(o.copy(closingSpeedKmh = null)))
    }

    @Test
    fun `closingParts splits direction and speed`() {
        val o = AircraftObservation(
            aircraftId = 1, callsign = "X", icao24 = null,
            distanceKm = 5.0, lat = 0.0, lon = 0.0,
            bearingDeg = 90.0, effectiveRadiusKm = 25.0,
            closingSpeedKmh = 420.0
        )
        assertEquals(
            Format.ClosingParts(
                "approaching", "420 km/h", Format.ClosingTrend.APPROACHING),
            Format.closingParts(o, DistanceUnit.KM))
        assertEquals(
            Format.ClosingParts(
                "receding", "100 km/h", Format.ClosingTrend.RECEDING),
            Format.closingParts(
                o.copy(closingSpeedKmh = -100.0), DistanceUnit.KM))
        assertEquals(
            Format.ClosingParts("steady", "3 km/h", Format.ClosingTrend.STEADY),
            Format.closingParts(
                o.copy(closingSpeedKmh = 3.0), DistanceUnit.KM))
        assertNull(Format.closingParts(
            o.copy(closingSpeedKmh = null), DistanceUnit.KM))
    }

    @Test
    fun `durations and coverage hours`() {
        assertEquals("45 s", Format.duration(45))
        assertEquals("2 min", Format.duration(120))
        assertEquals("2 min 30 s", Format.duration(150))
        assertEquals("3 h", Format.duration(3 * 3600 + 30))
        assertEquals("3 h 5 min", Format.duration(3 * 3600 + 300))
        assertEquals("< 1 h", Format.hours(0.4))
        assertEquals("7.5 h", Format.hours(7.5))
        assertEquals("7 h", Format.hours(7.0))
        assertEquals("1 day+", Format.hours(30.0))
    }
}
