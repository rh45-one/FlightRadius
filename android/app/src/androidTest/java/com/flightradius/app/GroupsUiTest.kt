package com.flightradius.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.activity.compose.setContent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flightradius.app.data.prefs.AppSettings
import com.flightradius.app.data.prefs.DataSource
import com.flightradius.app.data.prefs.RuntimeSettings
import com.flightradius.app.data.prefs.SettingsRepository
import com.flightradius.app.data.repo.AircraftRepository
import com.flightradius.app.data.repo.FleetRepository
import com.flightradius.app.domain.IdentifierType
import com.flightradius.app.ui.aircraft.AircraftScreen
import com.flightradius.app.ui.theme.FlightRadiusTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Groups in the Aircraft tab against the real repositories and an in-memory
 * database. The data source is the (unreachable) backend so no OpenSky
 * credentials or network are involved.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class GroupsUiTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<HiltTestActivity>()

    @Inject lateinit var fleets: FleetRepository
    @Inject lateinit var aircraftRepo: AircraftRepository
    @Inject lateinit var settingsRepo: SettingsRepository
    @Inject lateinit var runtime: RuntimeSettings

    @Before
    fun setUp() = runBlocking {
        hiltRule.inject()
        settingsRepo.setDataSource(DataSource.BACKEND)
        settingsRepo.setBackendBaseUrl("http://127.0.0.1:1/")
        withTimeout(10_000) { while (runtime.dataSource != DataSource.BACKEND) delay(50) }
        // Group ids restart at 1 in every in-memory database, but the folded state
        // lives in the real DataStore: start every test expanded.
        for (id in 1L..5L) settingsRepo.setGroupCollapsed(id, false)
    }

    private fun show() {
        compose.setContent {
            FlightRadiusTheme { AircraftScreen(settings = AppSettings()) }
        }
        compose.waitForIdle()
    }

    private fun groupMembers(name: String): Set<Long> = runBlocking {
        fleets.getAll().first { it.name == name }.memberIds
    }

    private fun addAircraft(vararg ids: String): List<Long> = runBlocking {
        ids.map { aircraftRepo.add(it, IdentifierType.CALLSIGN)!! }
    }

    @Test
    fun addAircraftWithAGroupFilesItUnderTheFolder() {
        val g = runBlocking { fleets.getOrCreate("Pegasus")!! }
        show()
        compose.onNodeWithContentDescription("Add aircraft").performClick()
        // [0] is the search box behind the sheet; [1] the identifier field.
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("TEST1")
        compose.onNodeWithTag("group-picker").performClick()
        compose.onNodeWithTag("group-option-$g").performClick()
        compose.onNodeWithText("Add").performClick()
        compose.waitUntil(10_000) { runBlocking { fleets.getAll().first().memberIds.isNotEmpty() } }
        compose.waitForIdle()
        assertEquals(1, groupMembers("Pegasus").size)
        compose.onNodeWithText("1 aircraft").assertIsDisplayed()
        compose.onNodeWithTag("aircraft-row-TEST1").assertIsDisplayed()
    }

    @Test
    fun bulkAddWithAGroupFilesEveryAircraft() {
        val g = runBlocking { fleets.getOrCreate("Pegasus")!! }
        show()
        compose.onNodeWithText("Bulk add").performClick()
        compose.onNodeWithText("Callsigns or ICAO24s, separated by commas or newlines")
            .performTextInput("AAA111, BBB222")
        compose.onNodeWithTag("group-picker").performClick()
        compose.onNodeWithTag("group-option-$g").performClick()
        compose.onNodeWithText("Parse").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Add 2").performClick()
        compose.waitUntil(10_000) { runBlocking { fleets.getAll().first().memberIds.size == 2 } }
        assertEquals(2, groupMembers("Pegasus").size)
    }

    @Test
    fun newGroupFromThePickerCreatesAndSelectsIt() {
        show()
        compose.onNodeWithContentDescription("Add aircraft").performClick()
        compose.onNodeWithTag("group-picker").performClick()
        compose.onNodeWithTag("group-option-new").performClick()
        compose.onNodeWithTag("group-name").performTextInput("Fresh")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10_000) { runBlocking { fleets.getAll().any { it.name == "Fresh" } } }
        compose.waitForIdle()
        // The picker now shows the new group as the selection.
        compose.onNodeWithTag("group-picker").assertIsDisplayed()
        compose.onNode(hasTestTag("group-picker") and hasText("Fresh")).assertIsDisplayed()
    }

    @Test
    fun longPressSelectMoveAndUndo() {
        val ids = addAircraft("AAA111", "BBB222")
        val g = runBlocking { fleets.getOrCreate("Pegasus")!! }
        show()
        compose.onNodeWithTag("aircraft-row-AAA111").performTouchInput { longClick() }
        compose.onNodeWithText("1 selected").assertIsDisplayed()
        compose.onNodeWithTag("aircraft-row-BBB222").performClick()
        compose.onNodeWithText("2 selected").assertIsDisplayed()
        compose.onNodeWithTag("move-to-group").performClick()
        compose.onNodeWithTag("move-to-$g").performClick()
        compose.waitUntil(10_000) { runBlocking { fleets.getAll().first().memberIds.size == 2 } }
        assertEquals(ids.toSet(), groupMembers("Pegasus"))
        compose.onNodeWithText("Moved 2 aircraft to Pegasus").assertIsDisplayed()
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(10_000) { runBlocking { fleets.getAll().first().memberIds.isEmpty() } }
        assertTrue(groupMembers("Pegasus").isEmpty())
    }

    @Test
    fun collapsedStateSurvivesRecreation() {
        val ids = addAircraft("AAA111")
        val g = runBlocking { fleets.getOrCreate("Pegasus")!! }
        runBlocking { fleets.setGroup(ids, g) }
        show()
        compose.onNodeWithTag("aircraft-row-AAA111").assertIsDisplayed()
        compose.onNodeWithTag("group-header-$g").performClick()
        compose.waitUntil(10_000) {
            runBlocking { g in settingsRepo.settings.first().collapsedGroupIds }
        }
        compose.waitForIdle()
        compose.onAllNodes(hasTestTag("aircraft-row-AAA111")).assertCountEquals(0)
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                FlightRadiusTheme { AircraftScreen(settings = AppSettings()) }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("group-header-$g").assertIsDisplayed()
        compose.onAllNodes(hasTestTag("aircraft-row-AAA111")).assertCountEquals(0)
    }
}
