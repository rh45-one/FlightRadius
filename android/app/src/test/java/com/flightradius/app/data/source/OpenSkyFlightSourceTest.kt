package com.flightradius.app.data.source

import com.flightradius.app.data.api.ApiResult
import com.flightradius.app.data.opensky.OpenSkyHarness
import com.flightradius.app.data.opensky.OpenSkyHarness.Companion.ok
import com.flightradius.app.data.opensky.OpenSkyHarness.Companion.row
import com.flightradius.app.data.opensky.OpenSkyHarness.Companion.states
import com.flightradius.app.domain.CallsignResolver
import com.flightradius.app.domain.ComputeResultEntry
import com.flightradius.app.domain.Geo
import com.flightradius.app.domain.IdentifierType
import com.flightradius.app.domain.LocationSource
import com.flightradius.app.domain.TrackedAircraft
import com.flightradius.app.domain.UserFix
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenSkyFlightSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var h: OpenSkyHarness
    private lateinit var source: OpenSkyFlightSource

    private val fix = UserFix(40.4, -3.7, 5.0, 0L, LocationSource.MANUAL)
    private val ibe = TrackedAircraft(1, "IBE1", IdentifierType.CALLSIGN)
    private val hex = TrackedAircraft(2, "abc123", IdentifierType.ICAO24)

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        h = OpenSkyHarness(server)
        source = OpenSkyFlightSource(h.client, h.credits, CallsignResolver(), h.time)
    }

    @After
    fun tearDown() = server.close()

    private suspend fun fetch(vararg aircraft: TrackedAircraft): List<ComputeResultEntry> =
        (source.fetchTracked(fix, aircraft.toList(), emptyList()) as ApiResult.Success).data

    @Test
    fun `an unresolved callsign triggers one global search, then cheap icao24 polls`() = runTest {
        server.enqueue(ok(states(row("fff999", "IBE1"), row("eee888", "OTHER")), remaining = 3_996))
        server.enqueue(ok(states(row("fff999", "IBE1")), remaining = 3_995))

        val first = fetch(ibe)
        val second = fetch(ibe)

        assertTrue("global search", server.takeRequest().url.queryParameterValues("icao24").isEmpty())
        assertEquals(listOf("fff999"), server.takeRequest().url.queryParameterValues("icao24"))
        assertEquals(listOf("IBE1"), first.map { it.callsign })
        assertEquals(listOf("IBE1"), second.map { it.callsign })
        assertEquals(1, source.estimatedCreditsPerCycle(listOf(ibe)))
    }

    @Test
    fun `icao24-tracked aircraft never need a global search`() = runTest {
        server.enqueue(ok(states(row("abc123", "DLH4", lat = 40.5, lon = -3.6))))

        val entries = fetch(hex)

        assertEquals(listOf("abc123"), server.takeRequest().url.queryParameterValues("icao24"))
        val entry = entries.single()
        assertEquals(Geo.distanceKm(40.4, -3.7, 40.5, -3.6), entry.distanceKm!!, 1e-9)
        assertEquals(230.0, entry.velocityMps!!, 1e-9)
        assertEquals(90.0, entry.headingDeg!!, 1e-9)
        assertEquals(1789999999.0, entry.lastContactSec!!, 1e-9)
    }

    @Test
    fun `a global search is skipped when the balance can't afford it`() = runTest {
        h.credits.onResponse(remaining = 10, authenticated = true)
        server.enqueue(ok(states()))

        val entries = fetch(ibe, hex)

        assertEquals(listOf("abc123"), server.takeRequest().url.queryParameterValues("icao24"))
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `nothing is requested when no transponder is known and no search is due`() = runTest {
        server.enqueue(ok(states()))
        fetch(ibe) // global search finds nothing -> back-off starts
        assertEquals(1, server.requestCount)

        assertTrue(fetch(ibe).isEmpty())
        assertEquals("no request during back-off", 1, server.requestCount)
    }

    @Test
    fun `validation doubles as callsign resolution`() = runTest {
        server.enqueue(ok(states(row("fff999", "IBE1"))))
        server.enqueue(ok(states(row("fff999", "IBE1"))))

        val live = (source.liveCallsigns(listOf("IBE1", "NOPE")) as ApiResult.Success).data
        fetch(ibe)

        assertEquals(setOf("IBE1"), live)
        server.takeRequest()
        assertEquals(listOf("fff999"), server.takeRequest().url.queryParameterValues("icao24"))
    }

    @Test
    fun `positionless states are dropped`() = runTest {
        server.enqueue(ok("""{"states":[["abc123","DLH4",null,0,0,null,null]]}"""))
        assertTrue(fetch(hex).isEmpty())
    }
}
