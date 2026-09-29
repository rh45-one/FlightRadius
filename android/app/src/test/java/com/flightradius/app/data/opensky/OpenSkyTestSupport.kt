package com.flightradius.app.data.opensky

import com.flightradius.app.domain.TimeSource
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient

class FakeTime(var nowMs: Long = 1_790_000_000_000L) : TimeSource {
    override fun nowMs(): Long = nowMs
}

/** Wires a real [OpenSkyClient] against a [MockWebServer]. */
class OpenSkyHarness(
    val server: MockWebServer,
    credentials: OpenSkyCredentials? = null
) {
    val time = FakeTime()
    var credentials: OpenSkyCredentials? = credentials
    val json = Json { ignoreUnknownKeys = true; isLenient = true }
    val credits = CreditTracker(time)
    private val http = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()
    val endpoints = OpenSkyEndpoints(
        apiBase = server.url("/api/"),
        tokenUrl = server.url("/token")
    )
    val tokens = OpenSkyTokenProvider(http, endpoints, { this.credentials }, json, time)
    val client = OpenSkyClient(http, endpoints, tokens, credits, json)

    companion object {
        fun states(vararg rows: String) = """{"time":1790000000,"states":[${rows.joinToString(",")}]}"""

        fun row(icao: String, callsign: String?, lat: Double = 40.5, lon: Double = -3.6) =
            """["$icao",${callsign?.let { "\"$it  \"" } ?: "null"},"Spain",1789999990,1789999999,""" +
                """$lon,$lat,10000.0,false,230.0,90.0,0.0,null,10100.0,null,false,0,4]"""

        fun ok(body: String, remaining: Int? = null): MockResponse =
            MockResponse.Builder().code(200).body(body).apply {
                remaining?.let { addHeader("X-Rate-Limit-Remaining", it.toString()) }
            }.build()

        fun token(value: String = "tok-1", expiresIn: Int = 1800): MockResponse =
            ok("""{"access_token":"$value","expires_in":$expiresIn,"token_type":"Bearer"}""")
    }
}
