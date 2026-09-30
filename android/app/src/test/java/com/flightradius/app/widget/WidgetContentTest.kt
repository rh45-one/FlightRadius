package com.flightradius.app.widget

import com.flightradius.app.domain.AircraftClass
import com.flightradius.app.domain.AircraftObservation
import com.flightradius.app.domain.DistanceUnit
import com.flightradius.app.domain.LocationSource
import com.flightradius.app.domain.MonitoringSnapshot
import com.flightradius.app.domain.NearbyAircraft
import com.flightradius.app.domain.TrackedAircraft
import com.flightradius.app.domain.UserFix
import com.flightradius.app.service.MonitoringState
import com.flightradius.app.service.MonitoringStatus
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WidgetContentTest {

    private val old = Locale.getDefault()
    @Before fun locale() = Locale.setDefault(Locale.US)
    @After fun restore() = Locale.setDefault(old)

    private fun obs(km: Double, radius: Double = 25.0, callsign: String = "IBE3174") = AircraftObservation(
        aircraftId = 1, callsign = callsign, icao24 = null, distanceKm = km,
        lat = 0.0, lon = 0.0, altitudeM = 400.0, bearingDeg = 40.0, effectiveRadiusKm = radius
    )

    private fun snap(
        ranked: List<AircraftObservation> = emptyList(),
        nearby: List<NearbyAircraft> = emptyList(),
        noData: List<TrackedAircraft> = emptyList(),
        radius: Double? = null,
        timeMs: Long = 1_000L
    ) = MonitoringSnapshot(
        timeMs = timeMs, fix = UserFix(0.0, 0.0, null, 0L, LocationSource.MANUAL),
        ranked = ranked, noData = noData, fleets = emptyList(), closest = ranked.firstOrNull(),
        nearby = nearby, airspaceRadiusKm = radius
    )

    private fun content(
        status: MonitoringStatus = MonitoringStatus.RUNNING,
        snapshot: MonitoringSnapshot? = null,
        tracked: Int = 1,
        watch: Boolean = false,
        unit: DistanceUnit = DistanceUnit.KM,
        now: Long = 2_000L
    ) = WidgetContent.from(
        MonitoringState(status = status, lastSnapshot = snapshot),
        tracked, watch, unit, 15, now
    ) { "12:34" }

    @Test
    fun `nothing tracked and no watch means no aircraft`() {
        val c = content(tracked = 0)
        assertNull(c.hero)
        assertEquals(WidgetEmpty.NO_AIRCRAFT, c.empty)
    }

    @Test
    fun `tracked without a snapshot is loading`() {
        assertEquals(WidgetEmpty.LOADING, content(snapshot = null).empty)
    }

    @Test
    fun `inside near and clear map to hero states with dial rounding`() {
        assertEquals(HeroState.INSIDE, content(snapshot = snap(listOf(obs(3.0)))).hero!!.state)
        assertEquals(HeroState.NEAR, content(snapshot = snap(listOf(obs(30.0)))).hero!!.state)
        assertEquals(HeroState.CLEAR, content(snapshot = snap(listOf(obs(90.0)))).hero!!.state)
        val h = content(snapshot = snap(listOf(obs(3.0)))).hero!!
        assertEquals("3.0 km", h.distanceText)
        assertEquals("IBE3174", h.name)
        assertEquals("NE 040°", h.directionText)
        assertEquals("400 m", h.altitudeText)
        assertEquals("148 km", content(snapshot = snap(listOf(obs(148.4)))).hero!!.distanceText)
        assertEquals("9.3 mi", content(snapshot = snap(listOf(obs(15.0))), unit = DistanceUnit.MI).hero!!.distanceText)
    }

    @Test
    fun `tracked but not reporting is nothing inside`() {
        val s = snap(noData = listOf(TrackedAircraft(1, "VLG12", com.flightradius.app.domain.IdentifierType.CALLSIGN)))
        assertEquals(WidgetEmpty.NOTHING_INSIDE, content(snapshot = s).empty)
    }

    private fun nb(km: Double, match: Boolean) = NearbyAircraft(
        "abc123", null, AircraftClass.HELICOPTER, 0.0, 0.0, km, 10.0, altitudeM = 400.0,
        registration = "EC-KCM", matchesRule = match
    )

    @Test
    fun `nothing tracked with watch on falls back to the nearest nearby`() {
        val s = snap(nearby = listOf(nb(3.0, true)), radius = 25.0)
        val h = content(snapshot = s, tracked = 0, watch = true).hero!!
        assertEquals(HeroState.NEARBY_MATCH, h.state)
        assertEquals("EC-KCM", h.name)
        assertEquals(HeroState.NEARBY, content(snapshot = snap(nearby = listOf(nb(3.0, false)), radius = 25.0),
            tracked = 0, watch = true).hero!!.state)
    }

    @Test
    fun `watch on nothing tracked nothing nearby is nothing inside`() {
        val c = content(snapshot = snap(radius = 25.0), tracked = 0, watch = true)
        assertEquals(WidgetEmpty.NOTHING_INSIDE, c.empty)
        assertEquals(WidgetEmpty.LOADING, content(snapshot = null, tracked = 0, watch = true).empty)
    }

    @Test
    fun `tracked wins over nearby`() {
        val s = snap(listOf(obs(90.0)), listOf(nb(1.0, true)), radius = 25.0)
        assertEquals("IBE3174", content(snapshot = s, watch = true).hero!!.name)
    }

    @Test
    fun `monitoring flag and absolute update time`() {
        val on = content(snapshot = snap(listOf(obs(3.0))))
        assertTrue(on.monitoring)
        assertEquals("12:34", on.updated)
        val off = content(status = MonitoringStatus.STOPPED, snapshot = snap(listOf(obs(3.0))))
        assertFalse(off.monitoring)
        assertNull(content(snapshot = null).updated)
    }

    @Test
    fun `a fresh process renders monitoring off`() {
        assertFalse(WidgetContent.Off.monitoring)
        assertNull(WidgetContent.Off.hero)
        assertEquals(WidgetContent.Off, content(status = MonitoringStatus.STOPPED, tracked = 0))
    }

    @Test
    fun `equal inputs give equal content so redundant updates can be skipped`() {
        val s = snap(listOf(obs(3.0)))
        assertEquals(content(snapshot = s), content(snapshot = s, now = 2_500L))
    }
}
