package com.flightradius.app

import android.Manifest
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.flightradius.app.data.prefs.LocationMode
import com.flightradius.app.data.prefs.RuntimeSettings
import com.flightradius.app.data.prefs.SettingsRepository
import com.flightradius.app.data.repo.AircraftRepository
import com.flightradius.app.domain.IdentifierType
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.junit.rules.TestRule

/**
 * End-to-end monitoring smoke test: MockWebServer loopback as the backend
 * (loopback is intentionally NOT classified as a local-network host, so no
 * ACCESS_LOCAL_NETWORK grant is needed even on API 37).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MonitoringServiceTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val permissionRule: TestRule =
        if (Build.VERSION.SDK_INT >= 33) {
            GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            object : TestRule {
                override fun apply(base: Statement, d: Description) = base
            }
        }

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var aircraftRepository: AircraftRepository
    @Inject lateinit var stateRepository: MonitoringStateRepository
    @Inject lateinit var controller: MonitoringController
    @Inject lateinit var runtimeSettings: RuntimeSettings

    private lateinit var server: MockWebServer
    private val computeRequests = CopyOnWriteArrayList<RecordedRequest>()

    // Aircraft ~5 km north of the manual fix (52.0, 13.0); radius 25 km.
    private val computeJson = """
        {
          "results": [
            {"callsign":"IBE3174","icao24":"4ca123","distance_km":5.0,
             "lat":52.045,"lon":13.0,"altitude_m":10500,"last_contact":null}
          ],
          "missing":[],
          "closest":{"callsign":"IBE3174","icao24":"4ca123","distance_km":5.0,
             "lat":52.045,"lon":13.0,"altitude_m":10500},
          "groups":[]
        }
    """.trimIndent()

    @Before
    fun setUp() {
        runBlocking {
        hiltRule.inject()

        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return if (request.url.encodedPath
                        .startsWith("/api/distance/compute")
                ) {
                    computeRequests += request
                    MockResponse.Builder().code(200)
                        .addHeader("Content-Type", "application/json")
                        .body(computeJson)
                        .build()
                } else {
                    // postLocation and friends: benign 200.
                    MockResponse.Builder().code(200).body("{}").build()
                }
            }
        }
        server.start()

        // Point the app at the mock backend; wait until the hot settings
        // view reflects it (RuntimeSettings applies DataStore asynchronously).
        settingsRepository.setBackendBaseUrl(server.url("/").toString())
        settingsRepository.setLocationMode(LocationMode.MANUAL)
        settingsRepository.setManualLocation(52.0, 13.0)
        settingsRepository.setMonitoringIntervalSec(10)
        withTimeout(10_000) {
            while (runtimeSettings.baseUrl.port != server.port) delay(50)
        }

        aircraftRepository.add("IBE3174", IdentifierType.CALLSIGN)
        }
    }

    @After
    fun tearDown() {
        runCatching { controller.stop() }
        server.close()
    }

    @Test
    fun monitoringCycleProducesSnapshotAndAlert() {
        runBlocking {
        controller.start(fromUser = true)

        // The cycle hits the mock backend.
        val compute = withTimeout(45_000) {
            while (computeRequests.isEmpty()) delay(100)
            computeRequests.first()
        }
        assertTrue(
            compute.url.encodedPath.startsWith("/api/distance/compute"))
        assertTrue(compute.body!!.utf8().contains("IBE3174"))

        // State reaches RUNNING with a snapshot whose closest is IBE3174.
        withTimeout(20_000) {
            stateRepository.state.first {
                it.status == MonitoringStatus.RUNNING && it.lastSnapshot != null
            }
        }
        val snapshot = stateRepository.state.value.lastSnapshot!!
        assertEquals("IBE3174", snapshot.closest?.callsign)
        assertEquals(5.0, snapshot.closest!!.distanceKm, 1e-9)

        // 5 km < 25 km radius -> proximity alert emitted.
        withTimeout(10_000) {
            stateRepository.activeAlert.first { it != null }
        }
        assertEquals(1L, stateRepository.activeAlert.value!!.observation.aircraftId)

        controller.stop()
        withTimeout(15_000) {
            stateRepository.state.first { it.status == MonitoringStatus.STOPPED }
        }
        }
    }

    /**
     * Pause must stop the polling loop: after pausing, no new compute
     * requests for >2 monitoring intervals (one in-flight request is
     * tolerated). Resuming produces a new compute.
     */
    @Test
    fun pauseStopsPolling() {
        runBlocking {
        controller.start(fromUser = true)

        withTimeout(45_000) {
            while (computeRequests.isEmpty()) delay(100)
        }
        withTimeout(20_000) {
            stateRepository.state.first {
                it.status == MonitoringStatus.RUNNING && it.lastSnapshot != null
            }
        }

        controller.pause()
        withTimeout(10_000) {
            stateRepository.state.first {
                it.status == MonitoringStatus.PAUSED
            }
        }

        // Interval is 10 s; wait >2 intervals. A request that was already
        // in flight when the pause landed may still arrive.
        val countAtPause = computeRequests.size
        delay(22_000)
        assertTrue(
            "compute requests continued while paused: " +
                "$countAtPause -> ${computeRequests.size}",
            computeRequests.size <= countAtPause + 1
        )

        controller.resume()
        val afterPause = computeRequests.size
        withTimeout(20_000) {
            while (computeRequests.size <= afterPause) delay(100)
        }

        controller.stop()
        withTimeout(15_000) {
            stateRepository.state.first { it.status == MonitoringStatus.STOPPED }
        }
        }
    }
}
