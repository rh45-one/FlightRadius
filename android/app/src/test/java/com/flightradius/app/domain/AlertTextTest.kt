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
    fun `16 point cardinal bearing`() {
        assertEquals("N", AlertText.cardinal(0.0))
        assertEquals("N", AlertText.cardinal(359.0))
        assertEquals("NE", AlertText.cardinal(45.0))
        assertEquals("ENE", AlertText.cardinal(67.5))
        assertEquals("E", AlertText.cardinal(90.0))
        assertEquals("S", AlertText.cardinal(180.0))
        assertEquals("WSW", AlertText.cardinal(247.5))
        assertEquals("NW", AlertText.cardinal(315.0))
        // Negative wraps.
        assertEquals("NW", AlertText.cardinal(-45.0))
    }

    @Test
    fun `alert title and body`() {
        assertEquals(
            "✈ IBE3174 within 25.0 km",
            AlertText.alertTitle(obs(), DistanceUnit.KM)
        )
        val body = AlertText.alertBody(obs(), DistanceUnit.KM)
        assertTrue(body.contains("12.4 km"))
        assertTrue(body.contains("045° NE"))
        assertTrue(body.contains("closing 380 km/h"))
        assertTrue(body.contains("FL350"))
    }

    @Test
    fun `body omits absent fields`() {
        val body = AlertText.alertBody(
            obs(closingSpeedKmh = null, altitudeM = null), DistanceUnit.KM)
        assertEquals("12.4 km · bearing 045° NE", body)
    }

    @Test
    fun `closing below steady threshold shows steady`() {
        val body = AlertText.alertBody(
            obs(closingSpeedKmh = 2.0), DistanceUnit.KM)
        assertTrue(body.contains("steady"))
        assertTrue(!body.contains("closing"))
        val line = AlertText.statusLine(
            obs(closingSpeedKmh = -3.0), DistanceUnit.KM)
        assertTrue(line.contains("steady"))
        assertTrue(!line.contains("approaching") && !line.contains("receding"))
        assertTrue(!line.contains("\u25b2") && !line.contains("\u25bc"))
    }

    @Test
    fun `status line shows trend arrow`() {
        assertTrue(AlertText.statusLine(obs(), DistanceUnit.KM)
            .contains("▲ approaching"))
        assertTrue(AlertText.statusLine(obs(closingSpeedKmh = -10.0), DistanceUnit.KM)
            .contains("▼ receding"))
    }
}
