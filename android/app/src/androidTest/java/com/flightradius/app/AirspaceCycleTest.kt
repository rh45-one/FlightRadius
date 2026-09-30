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

    private lateinit var server: MockWebServer
    private val statesRequests = CopyOnWriteArrayList<RecordedRequest>()

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
            settingsRepository.setAirspaceWatch(true)
            settingsRepository.setAirspaceRadiusKm(25.0)
            settingsRepository.setAirspaceRules(
                listOf(AirspaceRule("r", "Low and close", false, 5.0, 1_500.0)))
        }
    }

    @After
    fun tearDown() {
        runCatching { controller.stop() }
        // DataStore outlives the test; don't leak airspace watch into other tests.
        runBlocking {
            settingsRepository.setAirspaceWatch(false)
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

            controller.stop()
            withTimeout(15_000) {
                stateRepository.state.first { it.status == MonitoringStatus.STOPPED }
            }
            assertFalse(statesRequests.isEmpty())
        }
    }
}
