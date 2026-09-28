package com.flightradius.app.domain

import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min
import kotlin.math.pow
import kotlinx.coroutines.delay

object Backoff {

    const val MAX_DELAY_MS = 5 * 60_000L
    private const val MAX_EXPONENT = 30

    /** min(base * 2^failures, 5min); failures capped to avoid overflow. */
    fun nextDelayMs(baseIntervalMs: Long, consecutiveFailures: Int): Long {
        val exp = consecutiveFailures.coerceIn(0, MAX_EXPONENT)
        val delay = baseIntervalMs.toDouble() * 2.0.pow(exp)
        return min(delay.toLong(), MAX_DELAY_MS)
    }
}

/**
 * Retries [block] up to [maxAttempts] times total. Between attempts it waits
 * `initialDelayMs * factor^n` capped at [maxDelayMs], optionally transformed
 * by [jitter] (e.g. `{ base -> base / 2 + Random.nextLong(base / 2) }`).
 *
 * - Retries while [shouldRetry] returns true for the result AND on thrown
 *   exceptions (except CancellationException, which is always rethrown).
 * - Returns the last result when attempts are exhausted or [shouldRetry]
 *   is false; rethrows the last exception when attempts are exhausted.
 */
suspend fun <T> retryWithBackoff(
    maxAttempts: Int,
    initialDelayMs: Long = 1_000L,
    factor: Double = 2.0,
    maxDelayMs: Long = 8_000L,
    jitter: (Long) -> Long = { it },
    shouldRetry: (T) -> Boolean = { false },
    block: suspend () -> T
): T {
    require(maxAttempts >= 1) { "maxAttempts must be >= 1" }
    var attempt = 0
    var delayMs = initialDelayMs
    while (true) {
        attempt++
        val result = try {
            block()
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            if (attempt >= maxAttempts) throw t
            delay(jitter(delayMs))
            delayMs = (delayMs * factor).toLong().coerceAtMost(maxDelayMs)
            continue
        }
        if (attempt >= maxAttempts || !shouldRetry(result)) return result
        delay(jitter(delayMs))
        delayMs = (delayMs * factor).toLong().coerceAtMost(maxDelayMs)
    }
}
