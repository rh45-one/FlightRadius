package com.flightradius.app

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flightradius.app.data.aircraftdb.DbState
import com.flightradius.app.data.prefs.AppSettings
import com.flightradius.app.data.prefs.ThemeMode
import com.flightradius.app.domain.AirspaceRule
import com.flightradius.app.domain.LocationSource
import com.flightradius.app.domain.UserFix
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onAllNodesWithText
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.flightradius.app.domain.MonitoringSnapshot
import com.flightradius.app.ui.debug.DemoTraffic
import com.flightradius.app.ui.detail.AircraftDetailContent
import com.flightradius.app.ui.detail.DetailKind
import com.flightradius.app.ui.detail.DetailLookup
import com.flightradius.app.ui.detail.DetailUi
import com.flightradius.app.ui.detail.LocalNavAnimatedScope
import com.flightradius.app.ui.detail.LocalSharedTransitionScope
import com.flightradius.app.ui.radar.NearbySection
import com.flightradius.app.ui.settings.AirspaceRulesContent
import com.flightradius.app.ui.settings.NearbyAirspaceSection
import com.flightradius.app.domain.AircraftObservation
import com.flightradius.app.domain.DistanceUnit
import com.flightradius.app.ui.alerts.ProximityAlertSheet
import com.flightradius.app.ui.radar.AlsoTrackingSection
import com.flightradius.app.ui.radar.Glance
import com.flightradius.app.ui.radar.GlanceDial
import com.flightradius.app.ui.radar.Zone
import com.flightradius.app.ui.settings.AppearanceRow
import com.flightradius.app.ui.theme.FlightRadiusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun obs(
        id: Long, callsign: String, distKm: Double
    ) = AircraftObservation(
        aircraftId = id, callsign = callsign, icao24 = "abc00$id",
        distanceKm = distKm, lat = 52.0 + distKm / 111, lon = 13.0,
        altitudeM = 10_000.0, velocityMps = 200.0, headingDeg = 90.0,
        lastContactSec = System.currentTimeMillis() / 1000.0,
        bearingDeg = 0.0, closingSpeedKmh = 100.0, effectiveRadiusKm = 25.0
    )

    @Test
    fun radarNoAircraftDialShowsPrompt() {
        compose.setContent {
            FlightRadiusTheme {
                GlanceDial(
                    glance = Glance.NoAircraft,
                    unit = DistanceUnit.KM,
                    nowMs = System.currentTimeMillis(),
                    dialSize = 300.dp
                )
            }
        }
        compose.onNodeWithText("Nothing to watch yet").assertIsDisplayed()
        compose.onNodeWithText("Add the aircraft you want to follow.").assertIsDisplayed()
    }

    @Test
    fun radarNearestDialDescribesClosestAircraft() {
        val nearest = obs(1, "IBE3174", 3.4)
        compose.setContent {
            FlightRadiusTheme {
                GlanceDial(
                    glance = Glance.Nearest(
                        obs = nearest, zone = Zone.INSIDE, alsoInside = 0,
                        stale = false, snapshotAgeMs = 0L),
                    unit = DistanceUnit.KM,
                    nowMs = System.currentTimeMillis(),
                    dialSize = 300.dp
                )
            }
        }
        compose.onNodeWithContentDescription("Inside radius", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("IBE3174", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun alsoTrackingListsRowsInGivenOrder() {
        val ranked = listOf(
            obs(1, "CLOSE1", 3.0),
            obs(2, "MID002", 30.0),
            obs(3, "FAR003", 90.0)
        )
        compose.setContent {
            FlightRadiusTheme {
                Column {
                    AlsoTrackingSection(
                        ranked = ranked,
                        unit = DistanceUnit.KM,
                        snoozes = emptyMap(),
                        nowMs = System.currentTimeMillis(),
                        onClick = {}
                    )
                }
            }
        }
        val tops = listOf("CLOSE1", "MID002", "FAR003").map {
            compose.onNodeWithText(it, substring = true)
                .assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot.top
        }
        assertEquals(tops.sorted(), tops)
        compose.onNodeWithText("3.0 km").assertIsDisplayed()
        compose.onNodeWithText("90.0 km").assertIsDisplayed()
    }

    @Test
    fun alertSheetShowsCallsignAndDismissInvokesCallback() {
        var dismissed = false
        compose.setContent {
            FlightRadiusTheme {
                ProximityAlertSheet(
                    obs = obs(1, "IBE3174", 8.2),
                    extraCount = 1,
                    unit = DistanceUnit.KM,
                    vibrationEnabled = false,
                    onDismiss = { dismissed = true },
                    onSnooze = {}
                )
            }
        }
        compose.onNodeWithText("Proximity alert").assertIsDisplayed()
        compose.onNodeWithText("IBE3174").assertIsDisplayed()
        compose.onNodeWithText("+1 more").assertIsDisplayed()
        compose.onNodeWithText("Dismiss").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun appearanceRowReportsSelectedMode() {
        var picked: ThemeMode? = null
        compose.setContent {
            FlightRadiusTheme {
                AppearanceRow(selected = ThemeMode.SYSTEM, onSelect = { picked = it })
            }
        }
        compose.onNodeWithText("System").assertIsDisplayed()
        compose.onNodeWithText("Light").assertIsDisplayed()
        compose.onNodeWithText("Dark").performClick()
        assertEquals(ThemeMode.DARK, picked)
    }

    @Test
    fun airspaceSwitchRevealsAreaAndAlertsRows() {
        compose.setContent {
            FlightRadiusTheme {
                var settings by remember { mutableStateOf(AppSettings()) }
                NearbyAirspaceSection(
                    settings = settings,
                    dbState = DbState.NotDownloaded,
                    onWatch = { settings = settings.copy(airspaceWatch = it) },
                    onRadius = {},
                    onOpenRules = {},
                    onOpenDb = {}
                )
            }
        }
        compose.onNodeWithText("Area").assertDoesNotExist()
        compose.onNodeWithText("Nearby alerts").assertDoesNotExist()
        compose.onNodeWithText("Aircraft database").assertIsDisplayed()
        compose.onNodeWithText("Not downloaded").assertIsDisplayed()
        compose.onNodeWithText("Watch the airspace around me").performClick()
        compose.onNodeWithText("Area").assertIsDisplayed()
        compose.onNodeWithText("Nearby alerts").assertIsDisplayed()
    }

    @Test
    fun rulesScreenTogglesARule() {
        var toggled: Pair<String, Boolean>? = null
        compose.setContent {
            FlightRadiusTheme {
                AirspaceRulesContent(
                    rules = AirspaceRule.DEFAULTS,
                    unit = DistanceUnit.KM,
                    onBack = {},
                    onToggle = { id, on -> toggled = id to on },
                    onEdit = {},
                    onAdd = {}
                )
            }
        }
        compose.onNodeWithText("Low and close").assertIsDisplayed()
        compose.onNodeWithText("Any aircraft", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Nearby alerts are off until you turn a rule on.", substring = true)
            .assertIsDisplayed()
        compose.onNode(isToggleable()).performClick()
        assertEquals(AirspaceRule.DEFAULT_ID to true, toggled)
    }

    @Test
    fun radarNearbySectionRendersDemoTraffic() {
        val fix = UserFix(40.4168, -3.7038, null, 0L, LocationSource.MANUAL)
        val enabled = AirspaceRule.DEFAULTS.map { it.copy(enabled = true) }
        val demo = DemoTraffic.build(fix, 25.0, enabled)
        compose.setContent {
            FlightRadiusTheme {
                Column { NearbySection(nearby = demo, unit = DistanceUnit.KM) }
            }
        }
        compose.onNodeWithText("Nearby").assertIsDisplayed()
        compose.onNodeWithText("PEGASO1").assertIsDisplayed()
        compose.onNodeWithText("Helicopter", substring = true).assertIsDisplayed()
        compose.onNodeWithText("RYR4412").assertExists()
        assertTrue(demo.first().matchesRule)
    }

    @Test
    fun tappingANearbyRowOpensDetailsAndBackReturns() {
        val fix = UserFix(40.4168, -3.7038, null, 0L, LocationSource.MANUAL)
        val demo = DemoTraffic.build(fix, 25.0, AirspaceRule.DEFAULTS)
        val snapshot = MonitoringSnapshot(
            timeMs = 0L, fix = fix, ranked = emptyList(), noData = emptyList(),
            fleets = emptyList(), closest = null, nearby = demo, airspaceRadiusKm = 25.0
        )
        compose.setContent {
            FlightRadiusTheme {
                val nav = rememberNavController()
                SharedTransitionLayout {
                    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                        NavHost(nav, startDestination = "radar") {
                            composable("radar") {
                                CompositionLocalProvider(LocalNavAnimatedScope provides this@composable) {
                                    NearbySection(nearby = demo, unit = DistanceUnit.KM,
                                        onClick = { nav.navigate("detail/${it.icao24}") })
                                }
                            }
                            composable("detail/{id}") { entry ->
                                CompositionLocalProvider(LocalNavAnimatedScope provides this@composable) {
                                    val id = entry.arguments?.getString("id")!!
                                    AircraftDetailContent(
                                        ui = DetailUi(DetailLookup.find(DetailKind.NEARBY, id, snapshot), true),
                                        unit = DistanceUnit.KM, nowMs = 0L,
                                        onBack = { nav.popBackStack() },
                                        onTrack = {}, onShowOnMap = {}, onEdit = {}, onSnooze = {}
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("PEGASO1").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Track this aircraft").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("PEGASO1").assertIsDisplayed()
        compose.onNodeWithText("Track this aircraft").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Track this aircraft").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("Nearby").assertIsDisplayed()
    }
}
