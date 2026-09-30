package com.flightradius.app

import android.Manifest
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.flightradius.app.data.prefs.DataSource
import com.flightradius.app.data.prefs.LocationMode
import com.flightradius.app.data.prefs.SettingsRepository
import com.flightradius.app.domain.AirspaceRule
import com.flightradius.app.service.MonitoringController
import com.flightradius.app.service.MonitoringStateRepository
import com.flightradius.app.service.MonitoringStatus
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

/**
 * Airspace watch with an empty tracked list: the cycle must still run (not
 * idle with NO_AIRCRAFT), issue exactly one area query and never the
 * icao24-filtered tracked query.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AirspaceCycleTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val permissionRule: TestRule =
        if (Build.VERSION.SDK_INT >= 33) {
            GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
        } else object : TestRule {
            override fun apply(base: Statement, d: Description) = base
        }

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var stateRepository: MonitoringStateRepository
    @Inject lateinit var controller: MonitoringController
    @Inject lateinit var runtimeSettings: com.flightradius.app.data.prefs.RuntimeSettings
    @Inject lateinit var aircraftRepository: com.flightradius.app.data.repo.AircraftRepository

    private lateinit var server: MockWebServer
    private val statesRequests = CopyOnWriteArrayList<RecordedRequest>()
    @Volatile private var failArea = false

    // Fix (52.0, 13.0): one airborne aircraft ~3 km north, one on the ground.
    private val body = """
        {"time":1700000000,"states":[
          ["a1b2c3","HELI1   ","Spain",1700000000,1700000000,13.0,52.027,450.0,false,45.0,200.0,0.0,null,460.0,null,false,0,8],
          ["a1b2c4","TAXI2   ","Spain",1700000000,1700000000,13.0,52.01,0.0,true,0.0,0.0,0.0,null,0.0,null,false,0,0]
        ]}
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.url.encodedPath.endsWith("/states/all")) {
                    statesRequests += request
                    if (failArea && request.url.queryParameter("lamin") != null) {
                        return MockResponse.Builder().code(500).body("boom").build()
                    }
                    return MockResponse.Builder().code(200)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("X-Rate-Limit-Remaining", "300")
                        .body(body).build()
                }
                return MockResponse.Builder().code(200).body("{}").build()
            }
        }
        server.start()
        TestOpenSkyEndpointsModule.apiBase = server.url("/api/")
        hiltRule.inject()
        runBlocking {
            settingsRepository.setDataSource(DataSource.DIRECT)
            settingsRepository.setLocationMode(LocationMode.MANUAL)
            settingsRepository.setManualLocation(52.0, 13.0)
            settingsRepository.setMonitoringIntervalSec(10)
            settingsRepository.setAdaptiveCredits(false)
            settingsRepository.setAirspaceWatch(true)
            settingsRepository.setAirspaceRadiusKm(25.0)
            settingsRepository.setAirspaceRules(
                listOf(AirspaceRule("r", "Low and close", false, 5.0, 1_500.0)))
            // RuntimeSettings applies DataStore asynchronously; wait for DIRECT to take effect.
            withTimeout(10_000) {
                while (runtimeSettings.dataSource != DataSource.DIRECT) delay(50)
            }
        }
    }

    @After
    fun tearDown() {
        runCatching { controller.stop() }
        // DataStore outlives the test; don't leak airspace watch into other tests.
        runBlocking {
            settingsRepository.setAirspaceWatch(false)
            settingsRepository.setAdaptiveCredits(true)
            for (a in aircraftRepository.getAll()) aircraftRepository.remove(a.id)
            settingsRepository.setAirspaceRules(AirspaceRule.DEFAULTS)
        }
        server.close()
    }

    @Test
    fun watchWithoutTrackedAircraftRunsAreaCycleOnly() {
        runBlocking {
            controller.start(fromUser = true)
            val snapshot = withTimeout(60_000) {
                stateRepository.state.first {
                    it.status == MonitoringStatus.RUNNING &&
                        it.lastSnapshot?.nearby?.isNotEmpty() == true
                }.lastSnapshot!!
            }
            assertEquals(25.0, snapshot.airspaceRadiusKm!!, 0.0)
            assertEquals(listOf("a1b2c3"), snapshot.nearby.map { it.icao24 })
            assertEquals(com.flightradius.app.domain.AircraftClass.HELICOPTER,
                snapshot.nearby.single().cls)
            assertTrue(snapshot.ranked.isEmpty())

            assertTrue(statesRequests.isNotEmpty())
            for (r in statesRequests) {
                val url = r.url
                assertTrue(url.queryParameter("lamin") != null && url.queryParameter("lomax") != null)
                assertTrue(url.queryParameter("extended") == "1")
                assertEquals(null, url.queryParameter("icao24"))
            }
            // Watch on, nothing tracked: one credit per cycle (the area query).
            assertEquals(1, stateRepository.state.value.creditsPerCycle)

            staleNearbyPhase()

            controller.stop()
            withTimeout(15_000) {
                stateRepository.state.first { it.status == MonitoringStatus.STOPPED }
            }
            assertFalse(statesRequests.isEmpty())
        }
    }

    /** Phase 2 (same Hilt graph: only one secure DataStore may exist per process). */
    private suspend fun staleNearbyPhase() {
        aircraftRepository.add("a9a9a9", com.flightradius.app.domain.IdentifierType.ICAO24)
        settingsRepository.setAirspaceRules(
            listOf(AirspaceRule("r", "Low and close", true, 5.0, 1_500.0)))
        val fresh = try {
            withTimeout(60_000) {
                stateRepository.state.first {
                    val snap = it.lastSnapshot
                    snap != null && snap.noData.isNotEmpty() &&
                        snap.nearby.singleOrNull()?.matchesRule == true
                }.lastSnapshot!!
            }
        } catch (e: Exception) {
            throw AssertionError("no fresh snapshot: ${stateRepository.state.value} " +
                statesRequests.map { it.url.toString() }, e)
        }
        assertFalse(fresh.nearbyStale)

        failArea = true
        val stale = try {
            withTimeout(60_000) {
                stateRepository.state.first { it.lastSnapshot?.nearbyStale == true }.lastSnapshot!!
            }
        } catch (e: Exception) {
            throw AssertionError("no stale snapshot: ${stateRepository.state.value} " +
                statesRequests.map { it.url.toString() }, e)
        }
        // The carried list keeps the helicopter (distance re-projected) but is marked stale.
        assertEquals(listOf("a1b2c3"), stale.nearby.map { it.icao24 })
        assertTrue(stale.nearby.single().matchesRule)
        assertEquals(25.0, stale.airspaceRadiusKm!!, 0.0)
    }
}
