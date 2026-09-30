package com.flightradius.app.notifications

import com.flightradius.app.domain.AircraftAlertState
import com.flightradius.app.domain.AircraftObservation
import com.flightradius.app.domain.AlertZone
import com.flightradius.app.domain.DistanceUnit
import com.flightradius.app.domain.LocationSource
import com.flightradius.app.domain.MonitoringSnapshot
import com.flightradius.app.domain.UserFix
import com.flightradius.app.ui.format.Format
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class LiveUpdateContentTest {

    private val old = Locale.getDefault()

    @Before fun setLocale() = Locale.setDefault(Locale.US)
    @After fun restoreLocale() = Locale.setDefault(old)

    private fun obs(
        id: Long, km: Double, radius: Double = 10.0, callsign: String? = "AC$id",
        icao: String? = null, closing: Double? = null, bearing: Double = 45.0
    ) = AircraftObservation(
        aircraftId = id, callsign = callsign, icao24 = icao, distanceKm = km,
        lat = 0.0, lon = 0.0, bearingDeg = bearing, closingSpeedKmh = closing,
        effectiveRadiusKm = radius
    )

    private fun snap(vararg o: AircraftObservation) = MonitoringSnapshot(
        timeMs = 0L, fix = UserFix(0.0, 0.0, null, 0L, LocationSource.MANUAL),
        ranked = o.sortedBy { it.distanceKm }, noData = emptyList(), fleets = emptyList(),
        closest = o.minByOrNull { it.distanceKm }
    )

    private fun inside(vararg ids: Long) =
        ids.associateWith { AircraftAlertState(zone = AlertZone.INSIDE) }

    @Test
    fun `nobody inside means no live update`() {
        assertNull(LiveUpdateContent.from(null, emptyMap(), DistanceUnit.KM))
        assertNull(LiveUpdateContent.from(snap(obs(1, 3.0)), emptyMap(), DistanceUnit.KM))
        val outside = mapOf(1L to AircraftAlertState(zone = AlertZone.OUTSIDE))
        assertNull(LiveUpdateContent.from(snap(obs(1, 3.0)), outside, DistanceUnit.KM))
    }

    @Test
    fun `nearest inside aircraft wins and outside ones are ignored`() {
        val s = snap(obs(1, 2.0), obs(2, 6.0), obs(3, 4.0))
        val c = LiveUpdateContent.from(s, inside(2, 3), DistanceUnit.KM)!!
        assertEquals("AC3", c.name)
        assertEquals(1, c.moreInside)
    }

    @Test
    fun `title uses callsign then icao24 and includes distance and cardinal`() {
        val c = LiveUpdateContent.from(snap(obs(1, 3.0, callsign = null, icao = "abc123")), inside(1), DistanceUnit.KM)!!
        assertEquals("abc123 · 3.0 km NE", c.title)
        val d = LiveUpdateContent.from(snap(obs(1, 3.0, callsign = null, icao = null)), inside(1), DistanceUnit.KM)!!
        assertEquals("?", d.name)
    }

    @Test
    fun `progress math and clamps`() {
        assertEquals(70, LiveUpdateContent.progressFor(3.0, 10.0))
        assertEquals(0, LiveUpdateContent.progressFor(10.0, 10.0))
        assertEquals(0, LiveUpdateContent.progressFor(12.0, 10.0))
        assertEquals(100, LiveUpdateContent.progressFor(0.0, 10.0))
        assertEquals(100, LiveUpdateContent.progressFor(-1.0, 10.0))
        assertEquals(0, LiveUpdateContent.progressFor(1.0, 0.0))
        assertEquals(67, LiveUpdateContent.progressFor(3.3, 10.0))
    }

    @Test
    fun `short text is rounded like the dial and follows the unit`() {
        assertEquals("10 km", LiveUpdateContent.shortText(9.96, DistanceUnit.KM))
        assertEquals("0.4 km", LiveUpdateContent.shortText(0.4, DistanceUnit.KM))
        assertEquals("3.4 km", LiveUpdateContent.shortText(3.4, DistanceUnit.KM))
        assertEquals("9.3 mi", LiveUpdateContent.shortText(15.0, DistanceUnit.MI))
        assertEquals("148 km", LiveUpdateContent.shortText(148.4, DistanceUnit.KM))
    }

    @Test
    fun `trend and radius text`() {
        val approaching = LiveUpdateContent.from(snap(obs(1, 3.0, closing = 200.0)), inside(1), DistanceUnit.KM)!!
        assertEquals(Format.ClosingTrend.APPROACHING, approaching.trend)
        assertEquals("10 km", approaching.radiusText)
        val receding = LiveUpdateContent.from(snap(obs(1, 3.0, closing = -200.0)), inside(1), DistanceUnit.KM)!!
        assertEquals(Format.ClosingTrend.RECEDING, receding.trend)
        val unknown = LiveUpdateContent.from(snap(obs(1, 3.0)), inside(1), DistanceUnit.KM)!!
        assertNull(unknown.trend)
        assertEquals(0, unknown.moreInside)
        assertEquals(70, unknown.progress)
        assertEquals("3.0 km", unknown.shortText)
    }
}
