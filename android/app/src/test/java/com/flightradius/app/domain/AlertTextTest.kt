package com.flightradius.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertTextTest {

    private fun obs(
        distanceKm: Double = 12.4,
        bearingDeg: Double = 45.0,
        closingSpeedKmh: Double? = 380.0,
        altitudeM: Double? = 10_668.0, // ~FL350
        callsign: String? = "IBE3174",
        radiusKm: Double = 25.0
    ) = AircraftObservation(
        aircraftId = 1, callsign = callsign, icao24 = "abc123",
        distanceKm = distanceKm, lat = 52.0, lon = 13.0,
        altitudeM = altitudeM, bearingDeg = bearingDeg,
        closingSpeedKmh = closingSpeedKmh, effectiveRadiusKm = radiusKm
    )

    @Test
    fun `metric units`() {
        assertEquals("12.4 km", AlertText.formatDistance(12.44, DistanceUnit.KM))
        assertEquals("380 km/h", AlertText.formatSpeed(380.4, DistanceUnit.KM))
        assertEquals("500 m", AlertText.formatAltitude(500.0, DistanceUnit.KM))
    }

    @Test
    fun `imperial units`() {
        assertEquals("12.4 mi", AlertText.formatDistance(20.0, DistanceUnit.MI))
        assertEquals("62 mph", AlertText.formatSpeed(100.0, DistanceUnit.MI))
        assertEquals("1640 ft", AlertText.formatAltitude(500.0, DistanceUnit.MI))
    }

    @Test
    fun `flight level above 5000 ft`() {
        // 10668m ~ 35000ft -> FL350
        assertEquals("FL350", AlertText.formatAltitude(10_668.0, DistanceUnit.KM))
        assertEquals("FL051", AlertText.formatAltitude(1_550.0, DistanceUnit.MI))
        // Below threshold: plain altitude.
        assertEquals("300 m", AlertText.formatAltitude(300.0, DistanceUnit.KM))
        assertNull(AlertText.formatAltitude(null, DistanceUnit.KM))
    }

    @Test
    fun `16 point compass index`() {
        assertEquals(0, AlertText.compassIndex(0.0))
        assertEquals(0, AlertText.compassIndex(359.0))
        assertEquals(2, AlertText.compassIndex(45.0))
        assertEquals(3, AlertText.compassIndex(67.5))
        assertEquals(4, AlertText.compassIndex(90.0))
        assertEquals(8, AlertText.compassIndex(180.0))
        assertEquals(11, AlertText.compassIndex(247.5))
        assertEquals(14, AlertText.compassIndex(315.0))
        // Negative wraps.
        assertEquals(14, AlertText.compassIndex(-45.0))
    }
}
