package com.flightradius.app

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.hasScrollAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flightradius.app.ui.aircraft.BulkAddContent
import com.flightradius.app.ui.components.FormBottomSheet
import com.flightradius.app.ui.theme.FlightRadiusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression: flinging past the end of the parsed list used to hand the leftover
 * velocity to the sheet, which was yanked past its expanded anchor and sprang back
 * (the "bouncing container"). The sheet must stay exactly where it is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
class BulkAddSheetScrollTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun flingingPastTheEndOfTheListDoesNotMoveTheSheet() {
        val sheetOffsets = mutableListOf<Float>()
        var sheetState: androidx.compose.material3.SheetState? = null
        compose.setContent {
            FlightRadiusTheme {
                val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                sheetState = state
                FormBottomSheet(onDismiss = {}, sheetState = state) {
                    BulkAddContent(
                        existingIdentifiers = { emptySet() },
                        validateCallsigns = { null },
                        addBulk = { _, _, done -> done() },
                        onDone = {}
                    )
                }
            }
        }
        val ids = (1001..1040).joinToString(",") { "IBE$it" }
        compose.onNodeWithText("Callsigns or ICAO24s, separated by commas or newlines")
            .performTextInput(ids)
        compose.onNodeWithText("Parse").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("IBE1001").assertIsDisplayed()
        compose.onNodeWithText("Add 40").assertIsDisplayed()

        compose.waitForIdle()
        val resting = sheetState!!.requireOffset()

        repeat(25) {
            compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
            // Sample every frame while any spring would be running.
            repeat(40) {
                compose.mainClock.advanceTimeByFrame()
                sheetOffsets += sheetState!!.requireOffset()
            }
            compose.waitForIdle()
            sheetOffsets += sheetState!!.requireOffset()
        }

        val drift = sheetOffsets.maxOf { kotlin.math.abs(it - resting) }
        assertTrue("sheet moved by up to $drift px while flinging the list", drift < 1f)
        assertEquals(SheetValue.Expanded, sheetState!!.currentValue)
        compose.onNodeWithText("Add 40").assertIsDisplayed()
        compose.onNodeWithText("IBE1040").assertIsDisplayed()
    }
}
