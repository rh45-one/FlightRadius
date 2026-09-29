package com.flightradius.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CreditPlannerTest {

    private val midnight = 1_790_000_000_000L - 1_790_000_000_000L % 86_400_000L + 86_400_000L
    /** 12 hours before the refill. */
    private val noon = midnight - 12 * 3_600_000L

    @Test
    fun `daily quota is inferred from auth and the observed balance`() {
        assertEquals(400, CreditPlanner.dailyQuota(authenticated = false, observedRemaining = 390))
        assertEquals(4_000, CreditPlanner.dailyQuota(authenticated = true, observedRemaining = 3_000))
        assertEquals(8_000, CreditPlanner.dailyQuota(authenticated = true, observedRemaining = 7_500))
    }

    @Test
    fun `next UTC midnight is strictly in the future`() {
        assertEquals(midnight, CreditPlanner.nextUtcMidnightMs(noon))
        assertEquals(midnight + 86_400_000L, CreditPlanner.nextUtcMidnightMs(midnight))
    }

    @Test
    fun `fixed mode, unknown balance or free cycles keep the user interval`() {
        assertEquals(15, plan(adaptive = false, remaining = 10))
        assertEquals(15, plan(remaining = null))
        assertEquals(15, plan(remaining = 10, perCycle = 0))
    }

    @Test
    fun `a plentiful balance keeps the user interval`() {
        // 12 h at 15 s = 2 880 cycles; 4 000 credits cover that.
        assertEquals(15, plan(remaining = 4_000))
    }

    @Test
    fun `a tight balance is spread evenly until the refill`() {
        // (1 000 - 20 reserve) credits over 43 200 s -> one cycle every 45 s.
        assertEquals(45, plan(remaining = 1_000))
        // Two credits per cycle halves the number of cycles.
        assertEquals(89, plan(remaining = 1_000, perCycle = 2))
    }

    @Test
    fun `below the reserve the next cycle waits for the refill`() {
        assertEquals(43_200, plan(remaining = CreditPlanner.RESERVE))
    }

    @Test
    fun `coverage hours reflect the reserve`() {
        assertEquals(2.0, CreditPlanner.coverageHours(740, 1, 10)!!, 1e-9)
        assertNull(CreditPlanner.coverageHours(740, 0, 10))
    }

    private fun plan(adaptive: Boolean = true, remaining: Int?, perCycle: Int = 1) =
        CreditPlanner.intervalSec(
            userIntervalSec = 15,
            adaptive = adaptive,
            remaining = remaining,
            creditsPerCycle = perCycle,
            nowMs = noon,
            resetAtMs = midnight
        )
}
