package com.flightradius.app.data.opensky

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CreditTrackerTest {

    private val time = FakeTime()
    private val tracker = CreditTracker(time)

    @Test
    fun `responses update the balance and clear any block`() {
        tracker.onRateLimited(retryAfterSec = 60, authenticated = true)
        tracker.onResponse(remaining = 3_210, authenticated = true)

        val state = tracker.state.value
        assertEquals(3_210, state.remaining)
        assertEquals(time.nowMs, state.observedAtMs)
        assertEquals(4_000, state.dailyQuota)
        assertNull(tracker.blockedForSec())
    }

    @Test
    fun `a response without the header keeps the last balance`() {
        tracker.onResponse(remaining = 100, authenticated = false)
        tracker.onResponse(remaining = null, authenticated = false)
        assertEquals(100, tracker.state.value.remaining)
    }

    @Test
    fun `rate limiting blocks for the hinted time, else a default window`() {
        tracker.onRateLimited(retryAfterSec = 90, authenticated = false)
        assertEquals(90L, tracker.blockedForSec())
        time.nowMs += 89_500
        assertEquals(1L, tracker.blockedForSec())
        time.nowMs += 500
        assertNull(tracker.blockedForSec())

        tracker.onRateLimited(retryAfterSec = null, authenticated = false)
        assertEquals(CreditTracker.DEFAULT_BLOCK_MS / 1000, tracker.blockedForSec())
    }

    @Test
    fun `reset forgets the old bucket`() {
        tracker.onResponse(remaining = 5, authenticated = true)
        tracker.reset()
        assertEquals(CreditState(), tracker.state.value)
    }
}
