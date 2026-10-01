package com.flightradius.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flightradius.app.service.AlertActionReceiver
import com.flightradius.app.service.MonitoringStateRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The notification actions only work if the receiver is declared in the manifest. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AlertActionReceiverTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    @Inject lateinit var stateRepository: MonitoringStateRepository

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun setUp() = hiltRule.inject()

    @Test
    fun receiverIsDeclaredForEveryAction() {
        for (action in listOf(
            AlertActionReceiver.ACTION_SNOOZE,
            AlertActionReceiver.ACTION_DISMISS,
            AlertActionReceiver.ACTION_MUTE_NEARBY
        )) {
            val receivers = context.packageManager
                .queryBroadcastReceivers(AlertActionReceiver.intent(context, action), 0)
            assertTrue(
                "no receiver resolves $action",
                receivers.any { it.activityInfo.name == AlertActionReceiver::class.java.name }
            )
            assertTrue(receivers.none { it.activityInfo.exported })
        }
    }

    @Test
    fun snoozeBroadcastIsDeliveredAndRecorded() = runBlocking {
        context.sendBroadcast(
            AlertActionReceiver.intent(context, AlertActionReceiver.ACTION_SNOOZE)
                .putExtra(AlertActionReceiver.EXTRA_AIRCRAFT_ID, 42L)
                .putExtra(AlertActionReceiver.EXTRA_SNOOZE_MINUTES, 30L)
        )
        withTimeout(10_000) {
            while (stateRepository.snoozes.value[42L] == null) delay(50)
        }
        val until = stateRepository.snoozes.value.getValue(42L)
        assertTrue(until > System.currentTimeMillis() + 25 * 60_000L)
        assertEquals(1, stateRepository.snoozes.value.size)
    }
}
