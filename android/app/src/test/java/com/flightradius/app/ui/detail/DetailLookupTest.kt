package com.flightradius.app.ui.detail

import com.flightradius.app.domain.AircraftClass
import com.flightradius.app.domain.AircraftMeta
import com.flightradius.app.domain.AircraftObservation
import com.flightradius.app.domain.LocationSource
import com.flightradius.app.domain.MonitoringSnapshot
import com.flightradius.app.domain.NearbyAircraft
import com.flightradius.app.domain.UserFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailLookupTest {

    private val tracked = AircraftObservation(
        aircraftId = 7, callsign = "IBE3174", icao24 = "34644e", distanceKm = 20.0,
        lat = 40.0, lon = -3.0, altitudeM = 10_000.0, velocityMps = 220.0, headingDeg = 40.0,
        lastContactSec = 100.0, bearingDeg = 310.0, closingSpeedKmh = 300.0, effectiveRadiusKm = 25.0
    )
    private val nearby = NearbyAircraft(
        "abc123", null, AircraftClass.HELICOPTER, 40.1, -3.1, 3.0, 40.0, altitudeM = 400.0,
        velocityMps = 45.0, trackDeg = 220.0, verticalRateMps = -1.5, registration = "EC-KCM",
        typecode = "EC35", matchesRule = true
    )

    private fun snap(
        ranked: List<AircraftObservation> = listOf(tracked),
        nearby: List<NearbyAircraft> = listOf(this.nearby),
        timeMs: Long = 5_000L
    ) = MonitoringSnapshot(
        timeMs = timeMs, fix = UserFix(0.0, 0.0, null, 0L, LocationSource.MANUAL),
        ranked = ranked, noData = emptyList(), fleets = emptyList(), closest = ranked.firstOrNull(),
        nearby = nearby
    )

    @Test
    fun `tracked lookup uses the aircraft id`() {
        val d = DetailLookup.find(DetailKind.TRACKED, "7", snap())!!
        assertEquals(DetailKind.TRACKED, d.kind)
        assertEquals("t:7", d.key)
        assertEquals("IBE3174", d.name)
        assertEquals(25.0, d.radiusKm!!, 0.0)
        assertEquals(300.0, d.closingSpeedKmh!!, 0.0)
        assertNull(DetailLookup.find(DetailKind.TRACKED, "8", snap()))
    }

    @Test
    fun `nearby lookup uses the icao24 case-insensitively and carries the extras`() {
        val d = DetailLookup.find(DetailKind.NEARBY, "ABC123", snap())!!
        assertEquals(DetailKind.NEARBY, d.kind)
        assertEquals("n:abc123", d.key)
        assertEquals("EC-KCM", d.name)
        assertEquals("EC-KCM", d.registration)
        assertEquals("EC35", d.model)
        assertEquals(-1.5, d.verticalRateMps!!, 0.0)
        assertTrue(d.matchesRule)
        assertNull(d.radiusKm)
    }

    @Test
    fun `missing aircraft or snapshot gives null so callers keep the last values`() {
        assertNull(DetailLookup.find(DetailKind.NEARBY, "zzz999", snap()))
        assertNull(DetailLookup.find(DetailKind.NEARBY, "abc123", null))
        assertNull(DetailLookup.find(DetailKind.NEARBY, "abc123", snap(nearby = emptyList())))
    }

    @Test
    fun `a nearby aircraft that became tracked resolves as tracked`() {
        val ranked = tracked.copy(aircraftId = 9, icao24 = "abc123", callsign = "PEGASO1")
        val d = DetailLookup.find(DetailKind.NEARBY, "abc123", snap(ranked = listOf(ranked)))!!
        assertEquals(DetailKind.TRACKED, d.kind)
        assertEquals("t:9", d.key)
        assertEquals(9L, d.aircraftId)
    }

    @Test
    fun `database metadata fills registration model operator`() {
        val meta = AircraftMeta(AircraftClass.HELICOPTER, "EC35", "EC-KCM", "Eurocopter EC135", "Babcock")
        val d = DetailLookup.find(DetailKind.TRACKED, "7", snap(), meta)!!
        assertEquals(AircraftClass.HELICOPTER, d.cls)
        assertEquals("EC-KCM", d.registration)
        assertEquals("Eurocopter EC135", d.model)
        assertEquals("Babcock", d.operator)
        assertFalse(DetailLookup.find(DetailKind.TRACKED, "7", snap(), AircraftMeta(AircraftClass.UNKNOWN))!!.cls != null)
    }

    @Test
    fun `route kind parsing and keys`() {
        assertEquals(DetailKind.TRACKED, DetailKind.fromRoute("tracked"))
        assertEquals(DetailKind.NEARBY, DetailKind.fromRoute("nearby"))
        assertEquals(DetailKind.NEARBY, DetailKind.fromRoute(null))
        assertEquals("t:3", DetailLookup.key(DetailKind.TRACKED, "3"))
        assertEquals("n:abc", DetailLookup.key(DetailKind.NEARBY, "abc"))
    }
}
