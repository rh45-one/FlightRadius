package com.flightradius.app.domain

/**
 * Picks the delay before the next monitoring cycle.
 *
 * - success -> the configured interval
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
        consecutiveFailures: Int
    ): Long = when {
        success -> intervalMs
        retryable -> Backoff.nextDelayMs(intervalMs, consecutiveFailures)
        else -> intervalMs
    }
}
