package com.flightradius.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NextDelayPolicyTest {

    @Test
    fun `success waits the interval`() {
        assertEquals(
            15_000L,
            NextDelayPolicy.delayMs(
                success = true, retryable = true,
                intervalMs = 15_000L, consecutiveFailures = 3)
        )
    }

    @Test
    fun `retryable failure backs off exponentially`() {
        assertEquals(30_000L, NextDelayPolicy.delayMs(false, true, 15_000L, 1))
        assertEquals(60_000L, NextDelayPolicy.delayMs(false, true, 15_000L, 2))
        // Capped at Backoff.MAX_DELAY_MS.
        assertEquals(
            Backoff.MAX_DELAY_MS,
            NextDelayPolicy.delayMs(false, true, 15_000L, 20)
        )
    }

    @Test
    fun `non-retryable failure still polls at the interval`() {
        // BadRequest / LocalNetworkPermissionRequired: surface the error but
        // keep looping at the normal cadence.
        assertEquals(
            15_000L,
            NextDelayPolicy.delayMs(false, false, 15_000L, 9)
        )
    }
}
