package com.flightradius.app.data.opensky

import com.flightradius.app.data.api.ApiError
import com.flightradius.app.data.api.ApiResult
import com.flightradius.app.data.opensky.OpenSkyHarness.Companion.ok
import com.flightradius.app.data.opensky.OpenSkyHarness.Companion.row
import com.flightradius.app.data.opensky.OpenSkyHarness.Companion.states
import com.flightradius.app.data.opensky.OpenSkyHarness.Companion.token
import com.flightradius.app.domain.BoundingBox
import com.flightradius.app.domain.StatesQuery
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenSkyClientTest {

    private lateinit var server: MockWebServer
    private lateinit var h: OpenSkyHarness

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        h = OpenSkyHarness(server)
    }

    @After
    fun tearDown() = server.close()

    private fun <T> ApiResult<T>.data(): T = (this as ApiResult.Success).data
    private fun ApiResult<*>.error(): ApiError = (this as ApiResult.Failure).error

    @Test
    fun `anonymous icao24 query sends filters, extended flag and no auth`() = runTest {
        server.enqueue(ok(states(row("abc123", "IBE1")), remaining = 395))

        val snapshot = h.client.states(StatesQuery.ByIcao24(listOf("ABC123", "def456"))).data()

        val request = server.takeRequest()
        assertEquals("/api/states/all", request.url.encodedPath)
        assertEquals(listOf("abc123", "def456"), request.url.queryParameterValues("icao24"))
        assertEquals("1", request.url.queryParameter("extended"))
        assertNull(request.headers["Authorization"])
        assertEquals("IBE1", snapshot.states.single().callsign)
        assertEquals(395, h.credits.state.value.remaining)
        assertEquals(false, h.credits.state.value.authenticated)
    }

    @Test
    fun `area query sends the bounding box`() = runTest {
        server.enqueue(ok(states()))

        h.client.states(StatesQuery.ByArea(BoundingBox(40.0, -4.0, 41.0, -3.0)))

        val url = server.takeRequest().url
        assertEquals("40.0000", url.queryParameter("lamin"))
        assertEquals("-4.0000", url.queryParameter("lomin"))
        assertEquals("41.0000", url.queryParameter("lamax"))
        assertEquals("-3.0000", url.queryParameter("lomax"))
    }

    @Test
    fun `more than 100 icao24s are split into chunks and merged`() = runTest {
        server.enqueue(ok(states(row("aaa000", "A"))))
        server.enqueue(ok(states(row("bbb000", "B"))))
        val ids = List(150) { "%06x".format(it) }

        val snapshot = h.client.states(StatesQuery.ByIcao24(ids)).data()

        assertEquals(100, server.takeRequest().url.queryParameterValues("icao24").size)
        assertEquals(50, server.takeRequest().url.queryParameterValues("icao24").size)
        assertEquals(listOf("aaa000", "bbb000"), snapshot.states.map { it.icao24 })
    }

    @Test
    fun `429 records the block window and later calls short-circuit`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(429)
                .addHeader("X-Rate-Limit-Retry-After-Seconds", "600").build()
        )

        val first = h.client.states(StatesQuery.Global).error()
        assertEquals(ApiError.RateLimited(600), first)
        assertEquals(0, h.credits.state.value.remaining)

        h.time.nowMs += 60_000
        val second = h.client.states(StatesQuery.Global).error()
        assertEquals(ApiError.RateLimited(540), second)
        assertEquals("no request while blocked", 1, server.requestCount)
    }

    @Test
    fun `bearer token is fetched once, cached and sent`() = runTest {
        h.credentials = OpenSkyCredentials("id", "secret")
        server.enqueue(token("tok-1"))
        server.enqueue(ok(states(), remaining = 3_999))
        server.enqueue(ok(states(), remaining = 3_998))

        h.client.states(StatesQuery.Global)
        h.client.states(StatesQuery.Global)

        val tokenRequest = server.takeRequest()
        assertEquals("/token", tokenRequest.url.encodedPath)
        val form = tokenRequest.body?.utf8().orEmpty()
        assertTrue(form.contains("grant_type=client_credentials"))
        assertTrue(form.contains("client_id=id"))
        assertEquals("Bearer tok-1", server.takeRequest().headers["Authorization"])
        assertEquals("Bearer tok-1", server.takeRequest().headers["Authorization"])
        assertEquals(3, server.requestCount)
        assertEquals(true, h.credits.state.value.authenticated)
    }

    @Test
    fun `a 401 refreshes the token once and retries`() = runTest {
        h.credentials = OpenSkyCredentials("id", "secret")
        server.enqueue(token("old"))
        server.enqueue(MockResponse.Builder().code(401).build())
        server.enqueue(token("new"))
        server.enqueue(ok(states(row("abc123", "IBE1"))))

        val snapshot = h.client.states(StatesQuery.Global).data()

        assertEquals(1, snapshot.states.size)
        server.takeRequest()
        assertEquals("Bearer old", server.takeRequest().headers["Authorization"])
        server.takeRequest()
        assertEquals("Bearer new", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `a rejected API client surfaces AuthFailed and is not retried every cycle`() = runTest {
        h.credentials = OpenSkyCredentials("id", "wrong")
        val rejected = MockResponse.Builder().code(401).body("""{"error":"invalid_client"}""").build()
        server.enqueue(rejected)

        assertEquals(ApiError.AuthFailed, h.client.states(StatesQuery.Global).error())
        assertEquals(ApiError.AuthFailed, h.client.states(StatesQuery.Global).error())
        assertEquals("only the first token request hits the server", 1, server.requestCount)

        // An explicit verification retries.
        server.enqueue(rejected)
        assertEquals(ApiError.AuthFailed, h.client.verifyCredentials().error())
        assertEquals(2, server.requestCount)

        // New credentials are tried immediately.
        h.credentials = OpenSkyCredentials("id", "right")
        server.enqueue(token("tok"))
        server.enqueue(ok(states()))
        assertTrue(h.client.states(StatesQuery.Global) is ApiResult.Success)
    }

    @Test
    fun `changing credentials invalidates the cached token`() = runTest {
        h.credentials = OpenSkyCredentials("id", "secret")
        server.enqueue(token("first"))
        server.enqueue(ok(states()))
        h.client.states(StatesQuery.Global)

        h.credentials = OpenSkyCredentials("id2", "secret2")
        server.enqueue(token("second"))
        server.enqueue(ok(states()))
        h.client.states(StatesQuery.Global)

        repeat(3) { server.takeRequest() }
        assertEquals("Bearer second", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `server errors and garbage are classified, never thrown`() = runTest {
        server.enqueue(MockResponse.Builder().code(503).build())
        assertTrue(h.client.states(StatesQuery.Global).error() is ApiError.OpenSkyUnavailable)

        server.enqueue(ok("<html>maintenance</html>"))
        assertTrue(h.client.states(StatesQuery.Global).error() is ApiError.Malformed)
    }

    @Test
    fun `verifying without credentials reports anonymous`() = runTest {
        assertEquals(false, h.client.verifyCredentials().data())
        assertEquals(0, server.requestCount)
    }
}
