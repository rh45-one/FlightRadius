package com.flightradius.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flightradius.app.domain.DistanceUnit
import com.flightradius.app.ui.aircraft.BulkAddContent
import com.flightradius.app.ui.components.FormBottomSheet
import com.flightradius.app.ui.settings.DefaultRadiusRow
import com.flightradius.app.ui.theme.FlightRadiusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
class RadiusSettingsTest {

    @get:Rule
    val compose = createComposeRule()

    private var radiusKm by mutableDoubleStateOf(25.0)

    @Test
    fun settingsDialogSetsAnExactRadius() {
        radiusKm = 25.0
        compose.setContent {
            FlightRadiusTheme {
                DefaultRadiusRow(radiusKm, DistanceUnit.KM) { radiusKm = it }
            }
        }
        compose.onNodeWithTag("default-radius-value").assertTextEquals("25 km")
        compose.onNodeWithTag("default-radius-value").performClick()
        compose.onNodeWithTag("radius-dialog-field").performTextClearance()
        compose.onNodeWithTag("radius-dialog-field").performTextInput("abc")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Enter a value between 0.5 km and 500 km.").assertIsDisplayed()
        compose.onNodeWithTag("radius-dialog-field").performTextClearance()
        compose.onNodeWithTag("radius-dialog-field").performTextInput("10")
        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()
        assertEquals(10.0, radiusKm, 1e-9)
        compose.onNodeWithTag("default-radius-value").assertTextEquals("10 km")
    }

    private var added: List<Double?> = emptyList()

    private fun showBulk() {
        added = emptyList()
        compose.setContent {
            FlightRadiusTheme {
                FormBottomSheet(onDismiss = {}) {
                    BulkAddContent(
                        existingIdentifiers = { emptySet() },
                        validateCallsigns = { null },
                        addBulk = { _, radius, _, done -> added = added + radius; done() },
                        onDone = {},
                        unit = DistanceUnit.KM,
                        defaultRadiusKm = 25.0
                    )
                }
            }
        }
        compose.onNodeWithText("Callsigns or ICAO24s, separated by commas or newlines")
            .performTextInput("IBE3174, DLH100")
        compose.onNodeWithText("Parse").performClick()
        compose.waitForIdle()
    }

    @Test
    fun bulkDefaultRadiusPassesNull() {
        showBulk()
        compose.onNodeWithText("Default (25 km)").assertIsDisplayed()
        compose.onNodeWithText("Add 2").performClick()
        assertEquals(listOf<Double?>(null), added)
    }

    @Test
    fun bulkCustomRadiusPassesTheValue() {
        showBulk()
        compose.onNodeWithText("Custom").performClick()
        compose.onNodeWithTag("bulk-radius-field").performTextClearance()
        compose.onNodeWithTag("bulk-radius-field").performTextInput("10")
        compose.onNodeWithText("Add 2").assertIsEnabled().performClick()
        assertEquals(listOf<Double?>(10.0), added)
    }

    @Test
    fun bulkInvalidCustomRadiusDisablesAdd() {
        showBulk()
        compose.onNodeWithText("Custom").performClick()
        compose.onNodeWithTag("bulk-radius-field").performTextClearance()
        compose.onNodeWithTag("bulk-radius-field").performTextInput("abc")
        compose.onNodeWithText("Add 2").assertIsNotEnabled()
        compose.onNodeWithTag("bulk-radius-field").performTextClearance()
        compose.onNodeWithTag("bulk-radius-field").performTextInput("10,5")
        compose.onNodeWithText("Add 2").assertIsEnabled()
    }
}
