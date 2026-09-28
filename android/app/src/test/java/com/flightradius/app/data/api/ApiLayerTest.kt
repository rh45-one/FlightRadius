package com.flightradius.app.data.api

import com.flightradius.app.data.db.AircraftDao
import com.flightradius.app.data.db.FleetDao
import com.flightradius.app.data.db.FleetEntity
import com.flightradius.app.data.db.FleetMemberEntity
import com.flightradius.app.data.db.FleetWithMembers
import com.flightradius.app.data.db.TrackedAircraftEntity
import com.flightradius.app.data.repo.AircraftRepository
import com.flightradius.app.data.repo.FleetRepository
import com.flightradius.app.data.repo.FlightRadiusRepository
import com.flightradius.app.domain.IdentifierType
import com.flightradius.app.domain.LocationSource
import com.flightradius.app.domain.TrackedAircraft
import com.flightradius.app.domain.UserFix
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

private class FakeAircraftDao : AircraftDao {
    val items = mutableListOf<TrackedAircraftEntity>()
    override fun observeAll(): Flow<List<TrackedAircraftEntity>> = flowOf(items.toList())
    override suspend fun getAll() = items.toList()
    override suspend fun insertIgnore(entity: TrackedAircraftEntity): Long {
        if (items.any { it.identifier == entity.identifier }) return -1
        val id = (items.maxOfOrNull { it.id } ?: 0) + 1
        items += entity.copy(id = id)
        return id
    }
    override suspend fun findIdByIdentifier(identifier: String) =
        items.firstOrNull { it.identifier == identifier }?.id
    override suspend fun findByIdentifier(identifier: String) =
        items.firstOrNull { it.identifier == identifier }
    override suspend fun update(entity: TrackedAircraftEntity) {
        items.replaceAll { if (it.id == entity.id) entity else it }
    }
    override suspend fun deleteById(id: Long) { items.removeAll { it.id == id } }
}

private class FakeFleetDao : FleetDao {
    val fleets = mutableListOf<FleetEntity>()
    val members = mutableListOf<FleetMemberEntity>()
    private fun withMembers() = fleets.map { f ->
        FleetWithMembers(f, members.filter { it.fleetId == f.id }.map { it.aircraftId })
    }
    override fun observeFleetsWithMembers(): Flow<List<FleetWithMembers>> = flowOf(withMembers())
    override suspend fun getFleetsWithMembers() = withMembers()
    override suspend fun findByName(name: String) = fleets.firstOrNull { it.name == name }
    override suspend fun findById(id: Long) = fleets.firstOrNull { it.id == id }
    override suspend fun insertIgnore(entity: FleetEntity): Long {
        if (fleets.any { it.name == entity.name }) return -1
        val id = (fleets.maxOfOrNull { it.id } ?: 0) + 1
        fleets += entity.copy(id = id)
        return id
    }
    override suspend fun update(entity: FleetEntity) {
        fleets.replaceAll { if (it.id == entity.id) entity else it }
    }
    override suspend fun deleteById(fleetId: Long) {
        fleets.removeAll { it.id == fleetId }
        members.removeAll { it.fleetId == fleetId }
    }
    override suspend fun addMember(member: FleetMemberEntity): Long {
        if (members.none { it.fleetId == member.fleetId && it.aircraftId == member.aircraftId }) {
            members += member
        }
        return 1
    }
    override suspend fun removeMember(fleetId: Long, aircraftId: Long) {
        members.removeAll { it.fleetId == fleetId && it.aircraftId == aircraftId }
    }
    override suspend fun clearMembers(fleetId: Long) {
        members.removeAll { it.fleetId == fleetId }
    }
}

class ApiLayerTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
    }

    private lateinit var server: MockWebServer
    private lateinit var api: FlightRadiusApi
    private lateinit var repo: FlightRadiusRepository

    private fun buildClient(callTimeoutMs: Long = 10_000) = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .callTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)
        .build()

    private fun buildApi(client: OkHttpClient): FlightRadiusApi =
        Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(FlightRadiusApi::class.java)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = buildApi(buildClient())
        repo = FlightRadiusRepository(
            api, json,
            AircraftRepository(FakeAircraftDao()),
            FleetRepository(FakeFleetDao()),
            ioErrorMapper = IoErrorMapper { ApiError.Network(it.message) }
        )
    }

    @After
    fun tearDown() = server.close()

    private val fix = UserFix(50.0, 8.0, 10.0, 1_700_000_000_000L, LocationSource.GPS)
    private val tracked = listOf(
        TrackedAircraft(1, "DLH123", IdentifierType.CALLSIGN),
        TrackedAircraft(2, "abc123", IdentifierType.ICAO24)
    )

    private fun computeJson(distance: Double = 12.5) = """
        {
          "results": [
            {"callsign":"DLH123","icao24":"abc123","distance_km":$distance,
             "lat":50.05,"lon":8.1,"altitude_m":10500,"last_update":"2026-09-28T10:00:00Z",
             "velocity_mps":240.5,"heading_deg":90.0,"last_contact":1700000000}
          ],
          "missing":[],
          "closest":{"callsign":"DLH123","icao24":"abc123","distance_km":$distance,
             "lat":50.05,"lon":8.1,"altitude_m":10500},
          "groups":[{"group_name":"VIP","closest_aircraft":null,
             "members_ranked":[],"missing":[]}]
        }
    """.trimIndent()

    @Test
    fun `compute happy path maps results and request body`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(computeJson()).build())
        val result = repo.compute(fix, tracked, emptyList())
        assertTrue(result is ApiResult.Success)
        val outcome = (result as ApiResult.Success).data
        assertEquals(1, outcome.results.size)
        assertEquals(12.5, outcome.results[0].distanceKm!!, 1e-9)
        assertEquals(240.5, outcome.results[0].velocityMps!!, 1e-9)
        assertEquals(1700000000.0, outcome.results[0].lastContactSec!!, 1e-9)

        val recorded = server.takeRequest()!!
        assertEquals("/api/distance/compute", recorded.url.encodedPath)
        val body = recorded.body?.utf8() ?: ""
        assertTrue(body.contains("\"callsigns\":[\"DLH123\"]"))
        assertTrue(body.contains("\"icao24s\":[\"abc123\"]"))
    }

    @Test
    fun `missing null and unknown fields parse with defaults`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(200).body(
                """{"results":[{"callsign":"DLH123","surprise_field":{"x":1}}],
                   "unexpected_top":"yes"}""".trimIndent()
            ).build()
        )
        val result = repo.compute(fix, tracked, emptyList())
        assertTrue(result is ApiResult.Success)
        val entry = (result as ApiResult.Success).data.results[0]
        assertEquals("DLH123", entry.callsign)
        assertEquals(null, entry.distanceKm)
        assertEquals(null, entry.icao24)
    }

    @Test
    fun `malformed JSON produces Failure Malformed`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(200).body("this is <not> json").build()
        )
        val result = repo.compute(fix, tracked, emptyList())
        assertTrue(result is ApiResult.Failure)
        assertTrue((result as ApiResult.Failure).error is ApiError.Malformed)
    }

    @Test
    fun `error status classification`() = runTest {
        fun check(code: Int, body: String = "{}", expected: (ApiError) -> Boolean) {
            server.enqueue(MockResponse.Builder().code(code).body(body).build())
            val r = kotlinx.coroutines.runBlocking {
                safeApiCall(json) { api.health() }
            }
            assertTrue("code $code -> ${(r as? ApiResult.Failure)?.error}",
                r is ApiResult.Failure && expected(r.error))
        }
        check(400, """{"error":"bad callsign","status":400}""") { it is ApiError.BadRequest }
        check(429) { it is ApiError.RateLimited }
        check(502, """{"error":"OpenSky unavailable"}""") { it is ApiError.OpenSkyUnavailable }
        check(504) { it is ApiError.OpenSkyTimeout }
        check(503) { it is ApiError.Server }
        check(418) { it is ApiError.Http }
    }

    @Test
    fun `timeout produces Timeout failure without crash`() = runTest {
        val shortClient = buildClient(callTimeoutMs = 300)
        val shortApi = buildApi(shortClient)
        server.enqueue(
            MockResponse.Builder().onRequestStart(SocketEffect.Stall).build()
        )
        val result = safeApiCall(json) { shortApi.health() }
        assertTrue(result is ApiResult.Failure)
        assertTrue((result as ApiResult.Failure).error is ApiError.Timeout)
    }

    @Test
    fun `repository compute retries once on 503 then succeeds`() = runTest {
        server.enqueue(MockResponse.Builder().code(503).body("{}").build())
        server.enqueue(MockResponse.Builder().code(200).body(computeJson()).build())
        val result = repo.compute(fix, tracked, emptyList())
        assertTrue(result is ApiResult.Success)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `repository compute does not retry on 400`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(400).body("""{"error":"bad"}""").build()
        )
        val result = repo.compute(fix, tracked, emptyList())
        assertTrue(result is ApiResult.Failure)
        assertTrue((result as ApiResult.Failure).error is ApiError.BadRequest)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `base url interceptor rewrites scheme host port and path prefix`() = runTest {
        val target = server.url("/sub/")
        val client = buildClient().newBuilder()
            .addInterceptor(BaseUrlInterceptor { target })
            .build()
        val placeholderApi = Retrofit.Builder()
            .baseUrl("http://placeholder.invalid/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(FlightRadiusApi::class.java)

        server.enqueue(
            MockResponse.Builder().code(200).body("""{"status":"ok"}""").build()
        )
        val r = safeApiCall(json) { placeholderApi.health() }
        assertTrue(r is ApiResult.Success)
        assertEquals("/sub/api/health", server.takeRequest()!!.url.encodedPath)
    }

    @Test
    fun `validate callsigns maps statuses`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(200).body(
                """{"status":"ok","results":[
                    {"callsign":"DLH123","status":"valid"},
                    {"callsign":"ZZZ999","status":"no-data"}]}""".trimIndent()
            ).build()
        )
        val r = repo.validateCallsigns(listOf("DLH123", "ZZZ999"))
        assertTrue(r is ApiResult.Success)
        val results = (r as ApiResult.Success).data.results
        assertEquals("valid", results[0].status)
        assertEquals("no-data", results[1].status)
    }
}
