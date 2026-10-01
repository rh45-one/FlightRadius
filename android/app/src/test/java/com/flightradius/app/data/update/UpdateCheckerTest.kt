package com.flightradius.app.data.update

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateCheckerTest {

    // Shape of GitHub's GET /repos/{owner}/{repo}/releases/latest (trimmed, extra fields kept).
    private val sample = """
        {
          "url": "https://api.github.com/repos/rh45-one/FlightRadius/releases/1",
          "id": 1,
          "tag_name": "v0.2.0",
          "target_commitish": "main",
          "name": "FlightRadius 0.2.0",
          "draft": false,
          "prerelease": false,
          "created_at": "2026-10-02T10:00:00Z",
          "published_at": "2026-10-02T10:05:00Z",
          "html_url": "https://github.com/rh45-one/FlightRadius/releases/tag/v0.2.0",
          "author": {"login": "github-actions[bot]", "id": 41898282},
          "assets": [
            {"id": 11, "name": "FlightRadius-0.2.0-arm64-v8a.apk", "size": 17663265,
             "content_type": "application/vnd.android.package-archive",
             "browser_download_url": "https://github.com/rh45-one/FlightRadius/releases/download/v0.2.0/FlightRadius-0.2.0-arm64-v8a.apk"},
            {"id": 12, "name": "FlightRadius-0.2.0-universal.apk", "size": 53815240,
             "browser_download_url": "https://github.com/rh45-one/FlightRadius/releases/download/v0.2.0/FlightRadius-0.2.0-universal.apk"}
          ],
          "body": "notes"
        }
    """.trimIndent()

    private lateinit var server: MockWebServer

    @Before fun start() { server = MockWebServer(); server.start() }
    @After fun stop() { server.close() }

    private fun checker(current: String = "0.1.0") = UpdateChecker(
        OkHttpClient(), server.url("/").toString().trimEnd('/'), "rh45-one/FlightRadius", current)

    private fun respond(code: Int, body: String = "") =
        server.enqueue(MockResponse.Builder().code(code).body(body).build())

    @Test fun `release json parses with assets and strips the v`() {
        val r = Json { ignoreUnknownKeys = true }.decodeFromString<ReleaseInfo>(sample)
        assertEquals("v0.2.0", r.tag)
        assertEquals("0.2.0", r.version)
        assertEquals("FlightRadius 0.2.0", r.name)
        assertEquals("2026-10-02T10:05:00Z", r.publishedAt)
        assertEquals("https://github.com/rh45-one/FlightRadius/releases/tag/v0.2.0", r.htmlUrl)
        assertEquals(2, r.assets.size)
        assertEquals("FlightRadius-0.2.0-arm64-v8a.apk", r.assets[0].name)
        assertTrue(r.assets[1].url.endsWith("universal.apk"))
    }

    @Test fun `newer release is available and the request is shaped correctly`() = runBlocking {
        respond(200, sample)
        val result = checker().check()
        assertTrue(result is UpdateCheckResult.Available)
        assertEquals("0.2.0", (result as UpdateCheckResult.Available).release.version)
        val req = server.takeRequest()
        assertEquals("/repos/rh45-one/FlightRadius/releases/latest", req.url.encodedPath)
        assertEquals("application/vnd.github+json", req.headers["Accept"])
        assertEquals("2022-11-28", req.headers["X-GitHub-Api-Version"])
    }

    @Test fun `same or older release is up to date`() = runBlocking {
        respond(200, sample)
        assertEquals(UpdateCheckResult.UpToDate("0.2.0"), checker("0.2.0").check())
        respond(200, sample)
        assertEquals(UpdateCheckResult.UpToDate("0.3.0"), checker("0.3.0").check())
    }

    @Test fun `404 means no releases yet so up to date`() = runBlocking {
        respond(404, """{"message":"Not Found"}""")
        assertEquals(UpdateCheckResult.UpToDate("0.1.0"), checker().check())
    }

    @Test fun `403 and 429 are rate limited failures`() = runBlocking {
        respond(403, """{"message":"API rate limit exceeded"}""")
        val a = checker().check()
        assertTrue(a is UpdateCheckResult.Failed && a.rateLimited)
        respond(429)
        val b = checker().check()
        assertTrue(b is UpdateCheckResult.Failed && b.rateLimited)
    }

    @Test fun `server error and garbage are plain failures`() = runBlocking {
        respond(500)
        val a = checker().check()
        assertTrue(a is UpdateCheckResult.Failed && !a.rateLimited)
        respond(200, "not json")
        assertTrue(checker().check() is UpdateCheckResult.Failed)
    }

    @Test fun `non semver tag is never an update`() = runBlocking {
        respond(200, sample.replace("v0.2.0", "android-v13.5.1"))
        assertTrue(checker().check() is UpdateCheckResult.UpToDate)
    }

    @Test fun `unreachable host is a failure not a crash`() = runBlocking {
        val c = checker()
        server.close()
        assertTrue(c.check() is UpdateCheckResult.Failed)
    }
}
