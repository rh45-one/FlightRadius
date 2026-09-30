package com.flightradius.app.ui.format

import com.flightradius.app.domain.AircraftObservation
import com.flightradius.app.domain.DistanceUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertFormatTest {

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
    fun `alert title and body`() {
        assertEquals(
            "✈ IBE3174 within 25.0 km",
            AlertFormat.alertTitle(obs(), DistanceUnit.KM)
        )
        val body = AlertFormat.alertBody(obs(), DistanceUnit.KM)
        assertTrue(body.contains("12.4 km"))
        assertTrue(body.contains("045° NE"))
        assertTrue(body.contains("closing 380 km/h"))
        assertTrue(body.contains("FL350"))
    }

    @Test
    fun `body omits absent fields`() {
        val body = AlertFormat.alertBody(
            obs(closingSpeedKmh = null, altitudeM = null), DistanceUnit.KM)
        assertEquals("12.4 km · bearing 045° NE", body)
    }

    @Test
    fun `closing below steady threshold shows steady`() {
        val body = AlertFormat.alertBody(
            obs(closingSpeedKmh = 2.0), DistanceUnit.KM)
        assertTrue(body.contains("steady"))
        assertTrue(!body.contains("closing"))
        val line = AlertFormat.statusLine(
            obs(closingSpeedKmh = -3.0), DistanceUnit.KM)
        assertTrue(line.contains("steady"))
        assertTrue(!line.contains("approaching") && !line.contains("receding"))
        assertTrue(!line.contains("\u25b2") && !line.contains("\u25bc"))
    }

    @Test
    fun `status line shows trend arrow`() {
        assertTrue(AlertFormat.statusLine(obs(), DistanceUnit.KM)
            .contains("▲ approaching"))
        assertTrue(AlertFormat.statusLine(obs(closingSpeedKmh = -10.0), DistanceUnit.KM)
            .contains("▼ receding"))
    }
}
