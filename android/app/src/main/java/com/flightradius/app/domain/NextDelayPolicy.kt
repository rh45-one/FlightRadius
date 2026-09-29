package com.flightradius.app.domain

import kotlin.math.max

/**
 * Picks the delay before the next monitoring cycle.
 *
 * - success -> the planned interval
 * - server said "retry after N s" (credits exhausted) -> at least N s
 * - retryable failure -> exponential backoff min(interval*2^f, 5min)
 * - non-retryable failure -> still loop at the interval (surface the error;
 *   e.g. BadRequest / LocalNetworkPermissionRequired won't fix itself but
 *   the user may correct settings)
 */
object NextDelayPolicy {
    fun delayMs(
        success: Boolean,
        retryable: Boolean,
        intervalMs: Long,
        consecutiveFailures: Int,
        retryAfterMs: Long? = null
    ): Long = when {
        success -> intervalMs
        retryAfterMs != null -> max(intervalMs, retryAfterMs)
        retryable -> Backoff.nextDelayMs(intervalMs, consecutiveFailures)
        else -> intervalMs
    }
}
