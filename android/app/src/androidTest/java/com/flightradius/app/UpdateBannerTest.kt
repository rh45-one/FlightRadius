package com.flightradius.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flightradius.app.ui.theme.FlightRadiusTheme
import com.flightradius.app.ui.update.UpdateBanner
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateBannerTest {

    @get:Rule
    val compose = createComposeRule()

    private var dismissed by mutableIntStateOf(0)
    private var opened by mutableIntStateOf(0)

    private fun show() {
        dismissed = 0; opened = 0
        compose.mainClock.autoAdvance = true
        compose.setContent {
            FlightRadiusTheme {
                UpdateBanner("0.2.0", onOpen = { opened++ }, onDismiss = { dismissed++ })
            }
        }
    }

    @Test
    fun showsTextAndOpensOnTap() {
        show()
        compose.onNodeWithText("Version 0.2.0 is available").assertIsDisplayed()
        compose.onNodeWithText("Tap to download").assertIsDisplayed()
        compose.onNodeWithText("Version 0.2.0 is available").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun closeButtonDismisses() {
        show()
        compose.onNodeWithContentDescription("Dismiss").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals(1, dismissed)
    }

    @Test
    fun autoHidesAfterTenSeconds() {
        show()
        compose.mainClock.advanceTimeBy(9_000)
        assertEquals(0, dismissed)
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        assertEquals(1, dismissed)
    }
}
