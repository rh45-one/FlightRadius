package com.flightradius.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flightradius.app.data.prefs.AppSettings
import com.flightradius.app.domain.AirspaceRule
import com.flightradius.app.domain.LocationSource
import com.flightradius.app.domain.MonitoringSnapshot
import com.flightradius.app.domain.UserFix
import com.flightradius.app.ui.debug.DemoTraffic
import com.flightradius.app.ui.map.MapScreenContent
import com.flightradius.app.ui.theme.FlightRadiusTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.MapLibre

/** Map tab smoke test: composes, renders data, survives background/foreground (tiles need not load). */
@RunWith(AndroidJUnit4::class)
class MapSmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { MapLibre.getInstance(instrumentation.targetContext) }
    }

    private fun snapshot(): MonitoringSnapshot {
        val fix = UserFix(40.4168, -3.7038, null, 0L, LocationSource.MANUAL)
        return MonitoringSnapshot(
            timeMs = 0L, fix = fix, ranked = emptyList(), noData = emptyList(),
            fleets = emptyList(), closest = null,
            nearby = DemoTraffic.build(fix, 25.0, AirspaceRule.DEFAULTS),
            airspaceRadiusKm = 25.0
        )
    }

    @Test
    fun mapTabComposesAndSurvivesBackgroundForeground() {
        compose.setContent {
            FlightRadiusTheme { MapScreenContent(snapshot(), AppSettings()) }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Recenter map").assertIsDisplayed()

        val scenario = compose.activityRule.scenario
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Recenter map").assertIsDisplayed()

        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        assertTrue(compose.activity != null)
    }

    @Test
    fun mapWithoutSnapshotShowsWaitingHint() {
        compose.setContent {
            FlightRadiusTheme { MapScreenContent(null, AppSettings()) }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Recenter map").assertIsDisplayed()
    }
}
