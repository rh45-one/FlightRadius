package com.flightradius.app.domain

import kotlin.math.ceil
import kotlin.math.max

/**
 * Chooses the monitoring interval so the OpenSky credit bucket lasts until
 * it refills. Pure: every input is explicit.
 *
 * OpenSky documents a daily refill but not its boundary; we assume UTC
 * midnight. The `X-Rate-Limit-Remaining` header on every response keeps the
 * estimate honest, so a wrong guess only shifts the spread slightly.
 */
object CreditPlanner {
    const val ANONYMOUS_DAILY = 400
    const val STANDARD_DAILY = 4_000
    const val FEEDER_DAILY = 8_000

    /** Kept back for user-initiated checks (validation, verify, refresh). */
    const val RESERVE = 20

    private const val DAY_MS = 86_400_000L

    fun dailyQuota(authenticated: Boolean, observedRemaining: Int?): Int = when {
        !authenticated -> ANONYMOUS_DAILY
        (observedRemaining ?: 0) > STANDARD_DAILY -> FEEDER_DAILY
        else -> STANDARD_DAILY
    }

    fun nextUtcMidnightMs(nowMs: Long): Long = (nowMs / DAY_MS + 1) * DAY_MS

    /**
     * @param userIntervalSec the configured interval (already ≥ the floor)
     * @param adaptive stretch credits to last until [resetAtMs]
     * @param remaining last observed balance, null when unknown
     * @param creditsPerCycle expected spend per cycle (0 = free cycle)
     * @return seconds until the next cycle, never below [userIntervalSec]
     */
    fun intervalSec(
        userIntervalSec: Int,
        adaptive: Boolean,
        remaining: Int?,
        creditsPerCycle: Int,
        nowMs: Long,
        resetAtMs: Long = nextUtcMidnightMs(nowMs)
    ): Int {
        if (!adaptive || remaining == null || creditsPerCycle <= 0) return userIntervalSec
        // Bounded by one day, so the Int conversions below cannot overflow.
        val secondsLeft = ((resetAtMs - nowMs) / 1000).coerceIn(1L, DAY_MS / 1000).toInt()
        val usable = remaining - RESERVE
        if (usable < creditsPerCycle) return max(userIntervalSec, secondsLeft)
        val cycles = usable / creditsPerCycle
        val spread = ceil(secondsLeft.toDouble() / cycles).toInt()
        return max(userIntervalSec, spread)
    }

    /** Hours the balance lasts at [creditsPerCycle] every [intervalSec]. */
    fun coverageHours(remaining: Int, creditsPerCycle: Int, intervalSec: Int): Double? {
        if (creditsPerCycle <= 0 || intervalSec <= 0) return null
        val cycles = max(0, remaining - RESERVE) / creditsPerCycle
        return cycles * intervalSec / 3600.0
    }
}
