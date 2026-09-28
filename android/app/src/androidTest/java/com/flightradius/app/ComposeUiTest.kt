package com.flightradius.app

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flightradius.app.domain.AircraftObservation
import com.flightradius.app.domain.DistanceUnit
import com.flightradius.app.ui.alerts.ProximityAlertSheet
import com.flightradius.app.ui.radar.AircraftCard
import com.flightradius.app.ui.radar.EmptyState
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
    fun radarEmptyStateShowsCta() {
        var clicked = false
        compose.setContent {
            FlightRadiusTheme {
                EmptyState(onAdd = { clicked = true })
            }
        }
        compose.onNodeWithText("No aircraft tracked yet").assertIsDisplayed()
        compose.onNodeWithText("Add aircraft").assertIsDisplayed().performClick()
        assertTrue(clicked)
    }

    @Test
    fun rankedListRendersCardsInDistanceOrder() {
        val ranked = listOf(
            obs(1, "CLOSE1", 3.0),
            obs(2, "MID002", 30.0),
            obs(3, "FAR003", 90.0)
        )
        compose.setContent {
            FlightRadiusTheme {
                Column {
                    for (o in ranked) {
                        AircraftCard(
                            obs = o, unit = DistanceUnit.KM,
                            fleetColors = emptyList(), snoozed = false,
                            nowMs = System.currentTimeMillis(),
                            onClick = {}, onSnooze = {},
                            onDismissAlert = {}, onEdit = {}
                        )
                    }
                }
            }
        }
        val nodes = listOf("CLOSE1", "MID002", "FAR003").map {
            compose.onNodeWithText(it, substring = true)
                .assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot.top
        }
        // Cards appear top-to-bottom in distance order.
        assertEquals(
            nodes.sorted(), nodes
        )
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
        compose.onNodeWithText("PROXIMITY ALERT").assertIsDisplayed()
        compose.onNodeWithText("IBE3174").assertIsDisplayed()
        compose.onNodeWithText("+1 more").assertIsDisplayed()
        compose.onNodeWithText("Dismiss").performClick()
        assertTrue(dismissed)
    }
}
