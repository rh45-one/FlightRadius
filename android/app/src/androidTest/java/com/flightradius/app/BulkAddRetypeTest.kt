package com.flightradius.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flightradius.app.ui.aircraft.BulkAddContent
import com.flightradius.app.ui.components.FormBottomSheet
import com.flightradius.app.ui.theme.FlightRadiusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
class BulkAddRetypeTest {

    @get:Rule
    val compose = createComposeRule()

    private val validated = mutableListOf<List<String>>()

    private fun show() {
        validated.clear()
        compose.setContent {
            FlightRadiusTheme {
                FormBottomSheet(onDismiss = {}) {
                    BulkAddContent(
                        existingIdentifiers = { emptySet() },
                        validateCallsigns = { validated += it; emptySet() },
                        addBulk = { _, done -> done() },
                        onDone = {}
                    )
                }
            }
        }
        compose.onNodeWithText("Callsigns or ICAO24s, separated by commas or newlines")
            .performTextInput("ABC123, IBE3174")
        compose.onNodeWithText("Parse").performClick()
        compose.waitForIdle()
    }

    @Test
    fun togglingTheAmbiguousChipTwiceDoesNotCrash() {
        show()
        compose.onNodeWithText("ABC123").assertIsDisplayed()
        compose.onNodeWithTag("bulk-type-chip-0").performClick() // callsign -> ICAO24
        compose.waitForIdle()
        compose.onNodeWithText("abc123").assertIsDisplayed()
        compose.onNodeWithTag("bulk-type-chip-0").performClick() // and back (used to crash)
        compose.waitForIdle()
        compose.onNodeWithText("ABC123").assertIsDisplayed()
        compose.onNodeWithText("IBE3174").assertIsDisplayed()
        compose.onNodeWithText("Add 2").assertIsDisplayed()
    }

    @Test
    fun addAsIcao24MarksCallsignsInvalidAndKeepsHex() {
        show()
        compose.onNodeWithText("ICAO24").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Not a valid ICAO24").assertIsDisplayed()
        compose.onNodeWithText("abc123").assertIsDisplayed()
        compose.onNodeWithText("IBE3174").assertIsDisplayed()
        compose.onNodeWithText("Add 1").assertIsDisplayed()
        // Only the Parse call hit the network stub; switching to ICAO24 asked for nothing.
        assertEquals(1, validated.size)
    }
}
