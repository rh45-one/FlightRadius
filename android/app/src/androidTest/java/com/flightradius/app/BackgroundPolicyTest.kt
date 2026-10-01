package com.flightradius.app

import android.Manifest
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.flightradius.app.data.prefs.DataSource
import com.flightradius.app.data.prefs.LocationMode
import com.flightradius.app.data.prefs.RuntimeSettings
import com.flightradius.app.data.prefs.SettingsRepository
import com.flightradius.app.data.repo.AircraftRepository
import com.flightradius.app.di.ProcessLifecycle
import com.flightradius.app.domain.IdentifierType
import com.flightradius.app.service.BackgroundPolicy
import com.flightradius.app.service.MonitoringController
import com.flightradius.app.service.MonitoringStateRepository
import com.flightradius.app.service.MonitoringStatus
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
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
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

/** "Run in the background" off: leaving the app stops monitoring, returning restarts it. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BackgroundPolicyTest {

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
    @Inject lateinit var aircraftRepository: AircraftRepository
    @Inject lateinit var stateRepository: MonitoringStateRepository
    @Inject lateinit var controller: MonitoringController
    @Inject lateinit var runtimeSettings: RuntimeSettings
    @Inject lateinit var policy: BackgroundPolicy
    @Inject @ProcessLifecycle lateinit var lifecycle: androidx.lifecycle.Lifecycle

    private lateinit var server: MockWebServer
    private val registry get() = lifecycle as LifecycleRegistry

    private val computeJson = """
        {"results":[{"callsign":"IBE3174","icao24":"4ca123","distance_km":50.0,
          "lat":52.4,"lon":13.0,"altitude_m":10500,"last_contact":null}],
         "missing":[],
         "closest":{"callsign":"IBE3174","icao24":"4ca123","distance_km":50.0,
          "lat":52.4,"lon":13.0,"altitude_m":10500},
         "groups":[]}
    """.trimIndent()

    @Before
    fun setUp() {
        runBlocking {
            hiltRule.inject()
            server = MockWebServer()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse.Builder().code(200)
                        .addHeader("Content-Type", "application/json")
                        .body(if (request.url.encodedPath.startsWith("/api/distance/compute")) computeJson else "{}")
                        .build()
            }
            server.start()
            settingsRepository.setDataSource(DataSource.BACKEND)
            settingsRepository.setBackendBaseUrl(server.url("/").toString())
            settingsRepository.setLocationMode(LocationMode.MANUAL)
            settingsRepository.setManualLocation(52.0, 13.0)
            settingsRepository.setMonitoringIntervalSec(10)
            withTimeout(10_000) {
                while (runtimeSettings.baseUrl.port != server.port ||
                    runtimeSettings.dataSource != DataSource.BACKEND) delay(50)
            }
            aircraftRepository.add("IBE3174", IdentifierType.CALLSIGN)
            registry.currentState = Lifecycle.State.STARTED // app visible
            policy.start()
        }
    }

    @After
    fun tearDown() {
        runCatching { controller.stop() }
        server.close()
        runBlocking { settingsRepository.setBackgroundMonitoring(true) }
    }

    private suspend fun awaitStatus(vararg wanted: MonitoringStatus) = withTimeout(30_000) {
        stateRepository.state.first { it.status in wanted }
    }

    private suspend fun startAndWaitRunning() {
        controller.start(fromUser = true)
        awaitStatus(MonitoringStatus.RUNNING)
    }

    @Test
    fun backgroundOffStopsOnLeavingKeepsDesiredAndRestartsOnReturn() {
        runBlocking {
        settingsRepository.setBackgroundMonitoring(false)
        startAndWaitRunning()

        registry.currentState = Lifecycle.State.CREATED // ON_STOP
        awaitStatus(MonitoringStatus.STOPPED)
        assertTrue(settingsRepository.settings.first().monitoringDesired)

        registry.currentState = Lifecycle.State.STARTED // ON_START
        awaitStatus(MonitoringStatus.RUNNING)
        }
    }

    @Test
    fun backgroundOnIgnoresLeavingTheApp() {
        runBlocking {
        settingsRepository.setBackgroundMonitoring(true)
        startAndWaitRunning()

        registry.currentState = Lifecycle.State.CREATED
        delay(3_000)
        assertEquals(MonitoringStatus.RUNNING, stateRepository.state.value.status)
        assertTrue(settingsRepository.settings.first().monitoringDesired)
        }
    }

    @Test
    fun aUserStopIsNotUndoneByReturning() {
        runBlocking {
        settingsRepository.setBackgroundMonitoring(false)
        startAndWaitRunning()
        controller.stop()
        awaitStatus(MonitoringStatus.STOPPED)
        withTimeout(10_000) {
            while (settingsRepository.settings.first().monitoringDesired) delay(50)
        }

        registry.currentState = Lifecycle.State.CREATED
        registry.currentState = Lifecycle.State.STARTED
        delay(3_000)
        assertEquals(MonitoringStatus.STOPPED, stateRepository.state.value.status)
        }
    }
}
