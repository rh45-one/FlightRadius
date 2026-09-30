package com.flightradius.app.ui.radar

import com.flightradius.app.data.opensky.CreditState
import com.flightradius.app.domain.AircraftObservation
import com.flightradius.app.domain.IdentifierType
import com.flightradius.app.domain.LocationSource
import com.flightradius.app.domain.MonitoringSnapshot
import com.flightradius.app.domain.OpenSkyStatus
import com.flightradius.app.domain.TrackedAircraft
import com.flightradius.app.domain.UserFix
import com.flightradius.app.location.LocationStatus
import com.flightradius.app.service.MonitoringStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlanceTest {

    private fun obs(id: Long, distKm: Double, radiusKm: Double = 25.0) = AircraftObservation(
        aircraftId = id, callsign = "AC$id", icao24 = null,
        distanceKm = distKm, lat = 0.0, lon = 0.0,
        bearingDeg = 90.0, effectiveRadiusKm = radiusKm
    )

    private fun snapshot(
        timeMs: Long = 0L,
        ranked: List<AircraftObservation> = emptyList(),
        noData: List<TrackedAircraft> = emptyList()
    ) = MonitoringSnapshot(
        timeMs = timeMs,
        fix = UserFix(0.0, 0.0, null, timeMs, LocationSource.GPS),
        ranked = ranked,
        noData = noData,
        fleets = emptyList(),
        closest = ranked.firstOrNull()
    )

    private fun tracked(id: Long) =
        TrackedAircraft(id, "TRK$id", IdentifierType.CALLSIGN)

    // ---- zoneOf ----

    @Test
    fun `zone boundaries are inclusive at r and 2r`() {
        assertEquals(Zone.INSIDE, zoneOf(0.0, 25.0))
        assertEquals(Zone.INSIDE, zoneOf(25.0, 25.0))
        assertEquals(Zone.NEAR, zoneOf(25.0001, 25.0))
        assertEquals(Zone.NEAR, zoneOf(50.0, 25.0))
        assertEquals(Zone.CLEAR, zoneOf(50.0001, 25.0))
    }

    // ---- glanceOf ----

    @Test
    fun `nothing tracked is NoAircraft even with a snapshot`() {
        assertEquals(Glance.NoAircraft, glanceOf(0, null, 0L, 15))
        assertEquals(
            Glance.NoAircraft,
            glanceOf(0, snapshot(ranked = listOf(obs(1, 5.0))), 0L, 15)
        )
    }

    @Test
    fun `tracked without snapshot is Loading`() {
        assertEquals(Glance.Loading, glanceOf(2, null, 0L, 15))
    }

    @Test
    fun `empty ranked is NoneAirborne with not reporting count`() {
        val g = glanceOf(3, snapshot(noData = listOf(tracked(1), tracked(2))), 0L, 15)
        assertEquals(Glance.NoneAirborne(2), g)
    }

    @Test
    fun `nearest picks the closest observation`() {
        val near = obs(2, 10.0)
        val g = glanceOf(2, snapshot(ranked = listOf(obs(1, 80.0), near)), 1_000L, 15)
        g as Glance.Nearest
        assertEquals(near, g.obs)
        assertEquals(Zone.INSIDE, g.zone)
        assertEquals(0, g.alsoInside)
    }

    @Test
    fun `alsoInside counts other inside observations only`() {
        val ranked = listOf(
            obs(1, 5.0),
            obs(2, 20.0),
            obs(3, 25.0),
            obs(4, 26.0),
            obs(5, 200.0)
        )
        val g = glanceOf(5, snapshot(ranked = ranked), 0L, 15) as Glance.Nearest
        assertEquals(obs(1, 5.0), g.obs)
        assertEquals(2, g.alsoInside)
    }

    @Test
    fun `alsoInside respects per aircraft radius`() {
        val ranked = listOf(obs(1, 30.0, radiusKm = 25.0), obs(2, 40.0, radiusKm = 50.0))
        val g = glanceOf(2, snapshot(ranked = ranked), 0L, 15) as Glance.Nearest
        assertEquals(Zone.NEAR, g.zone)
        assertEquals(1, g.alsoInside)
    }

    @Test
    fun `zone of nearest reflects near and clear`() {
        val near = glanceOf(1, snapshot(ranked = listOf(obs(1, 40.0))), 0L, 15) as Glance.Nearest
        assertEquals(Zone.NEAR, near.zone)
        val clear = glanceOf(1, snapshot(ranked = listOf(obs(1, 90.0))), 0L, 15) as Glance.Nearest
        assertEquals(Zone.CLEAR, clear.zone)
    }

    @Test
    fun `stale boundary uses max of 60 s and twice the interval`() {
        val s = snapshot(timeMs = 0L, ranked = listOf(obs(1, 5.0)))
        val at60 = glanceOf(1, s, 60_000L, 15) as Glance.Nearest
        assertFalse(at60.stale)
        assertEquals(60_000L, at60.snapshotAgeMs)
        assertTrue((glanceOf(1, s, 60_001L, 15) as Glance.Nearest).stale)

        assertFalse((glanceOf(1, s, 120_000L, 60) as Glance.Nearest).stale)
        assertTrue((glanceOf(1, s, 120_001L, 60) as Glance.Nearest).stale)
        assertFalse((glanceOf(1, s, 60_000L, 10) as Glance.Nearest).stale)
    }

    @Test
    fun `negative snapshot age is clamped`() {
        val s = snapshot(timeMs = 10_000L, ranked = listOf(obs(1, 5.0)))
        val g = glanceOf(1, s, 5_000L, 15) as Glance.Nearest
        assertEquals(0L, g.snapshotAgeMs)
        assertFalse(g.stale)
    }

    // ---- statusSummary ----

    private fun summary(
        status: MonitoringStatus = MonitoringStatus.RUNNING,
        openSky: OpenSkyStatus = OpenSkyStatus.OK,
        location: LocationStatus = LocationStatus.Fix(0, 10.0),
        online: Boolean = true,
        lan: Boolean = false,
        credits: CreditState = CreditState(),
        lastSuccess: Long? = 1_000L,
        backendMode: Boolean = false
    ) = statusSummary(
        status, openSky, location, online, lan, credits, lastSuccess, 5_000L, backendMode
    )

    @Test
    fun `location problems win over everything`() {
        val everythingWrong = { loc: LocationStatus ->
            summary(
                status = MonitoringStatus.ERROR,
                openSky = OpenSkyStatus.AUTH_FAILED,
                location = loc, online = false, lan = true
            )
        }
        everythingWrong(LocationStatus.ProviderDisabled).let {
            assertEquals(StatusKind.LOCATION_OFF, it.kind)
            assertEquals(Tone.PROBLEM, it.tone)
            assertEquals(StatusAction.OPEN_SETTINGS, it.action)
        }
        everythingWrong(LocationStatus.PermissionDenied).let {
            assertEquals(StatusKind.LOCATION_PERMISSION, it.kind)
            assertEquals(StatusAction.OPEN_SETTINGS, it.action)
        }
        everythingWrong(LocationStatus.PlayServicesUnavailable).let {
            assertEquals(StatusKind.PLAY_SERVICES_MISSING, it.kind)
            assertEquals(Tone.PROBLEM, it.tone)
        }
    }

    @Test
    fun `lan blocked beats offline and opensky`() {
        val s = summary(lan = true, online = false, openSky = OpenSkyStatus.AUTH_FAILED)
        assertEquals(StatusKind.LAN_BLOCKED, s.kind)
        assertEquals(Tone.PROBLEM, s.tone)
        assertEquals(StatusAction.START_MONITORING, s.action)
    }

    @Test
    fun `offline beats opensky failures`() {
        val s = summary(online = false, openSky = OpenSkyStatus.AUTH_FAILED)
        assertEquals(StatusKind.OFFLINE_RESUME, s.kind)
        assertEquals(Tone.WARNING, s.tone)
        assertEquals(StatusAction.NONE, s.action)
    }

    @Test
    fun `opensky failures map to kinds`() {
        summary(openSky = OpenSkyStatus.AUTH_FAILED, status = MonitoringStatus.ERROR).let {
            assertEquals(StatusKind.AUTH_FAILED, it.kind)
            assertEquals(Tone.PROBLEM, it.tone)
            assertEquals(StatusAction.OPEN_SETTINGS, it.action)
        }
        summary(openSky = OpenSkyStatus.RATE_LIMITED).let {
            assertEquals(StatusKind.OUT_OF_CREDITS, it.kind)
            assertEquals(Tone.WARNING, it.tone)
        }
        for (st in listOf(
            OpenSkyStatus.UNAVAILABLE, OpenSkyStatus.UNREACHABLE, OpenSkyStatus.TIMEOUT
        )) {
            assertEquals(StatusKind.UNREACHABLE_OPENSKY, summary(openSky = st).kind)
            assertEquals(
                StatusKind.UNREACHABLE_BACKEND,
                summary(openSky = st, backendMode = true).kind
            )
            assertEquals(Tone.WARNING, summary(openSky = st).tone)
        }
    }

    @Test
    fun `error status beats running states`() {
        val s = summary(status = MonitoringStatus.ERROR)
        assertEquals(StatusKind.ERROR, s.kind)
        assertEquals(Tone.PROBLEM, s.tone)
        assertEquals(StatusAction.RETRY, s.action)
    }

    @Test
    fun `running is live with age and low credits note`() {
        val live = summary()
        assertEquals(StatusKind.LIVE, live.kind)
        assertEquals(Tone.LIVE, live.tone)
        assertEquals(1_000L, live.updatedAtMs)
        assertNull(live.creditsLeft)

        val plenty = summary(credits = CreditState(remaining = 300, authenticated = false))
        assertNull(plenty.creditsLeft)

        val low = summary(credits = CreditState(remaining = 99, authenticated = false))
        assertEquals(99, low.creditsLeft)
        val boundary = summary(credits = CreditState(remaining = 100, authenticated = false))
        assertNull(boundary.creditsLeft)
    }

    @Test
    fun `remaining monitoring states`() {
        val cases = mapOf(
            MonitoringStatus.PAUSED to (Tone.WARNING to StatusKind.PAUSED),
            MonitoringStatus.STARTING to (Tone.NEUTRAL to StatusKind.STARTING),
            MonitoringStatus.WAITING_FOR_LOCATION to (Tone.NEUTRAL to StatusKind.FINDING_LOCATION),
            MonitoringStatus.DEFERRED_DOZE to (Tone.WARNING to StatusKind.WAITING_BATTERY),
            MonitoringStatus.OFFLINE to (Tone.WARNING to StatusKind.OFFLINE),
            MonitoringStatus.STOPPED to (Tone.NEUTRAL to StatusKind.STOPPED)
        )
        for ((status, expected) in cases) {
            val s = summary(status = status)
            assertEquals(expected.first, s.tone)
            assertEquals(expected.second, s.kind)
            assertEquals(StatusAction.NONE, s.action)
        }
    }

    @Test
    fun `manual and searching locations are not problems`() {
        assertEquals(StatusKind.LIVE, summary(location = LocationStatus.Manual).kind)
        assertEquals(StatusKind.LIVE, summary(location = LocationStatus.Searching).kind)
    }
}
