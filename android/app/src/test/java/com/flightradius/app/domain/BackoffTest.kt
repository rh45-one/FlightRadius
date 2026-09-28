package com.flightradius.app.domain

import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackoffTest {

    @Test
    fun `nextDelayMs doubles up to the cap`() {
        assertEquals(15_000L, Backoff.nextDelayMs(15_000, 0))
        assertEquals(30_000L, Backoff.nextDelayMs(15_000, 1))
        assertEquals(60_000L, Backoff.nextDelayMs(15_000, 2))
        assertEquals(300_000L, Backoff.nextDelayMs(15_000, 10)) // capped at 5min
        assertEquals(300_000L, Backoff.nextDelayMs(15_000, 100)) // huge exp, no overflow
    }

    @Test
    fun `retryWithBackoff retries on retryable result then succeeds`() = runTest {
        val attempts = AtomicInteger()
        val result: String = retryWithBackoff(
            maxAttempts = 3,
            initialDelayMs = 100,
            shouldRetry = { it == "retry" }
        ) {
            if (attempts.incrementAndGet() < 3) "retry" else "ok"
        }
        assertEquals("ok", result)
        assertEquals(3, attempts.get())
    }

    @Test
    fun `retryWithBackoff does not retry when shouldRetry is false`() = runTest {
        val attempts = AtomicInteger()
        val result: String = retryWithBackoff(
            maxAttempts = 3,
            shouldRetry = { false }
        ) {
            attempts.incrementAndGet(); "done"
        }
        assertEquals("done", result)
        assertEquals(1, attempts.get())
    }

    @Test
    fun `retryWithBackoff rethrows after exhausting attempts`() = runTest {
        val attempts = AtomicInteger()
        try {
            retryWithBackoff<String>(maxAttempts = 2) {
                attempts.incrementAndGet()
                throw java.io.IOException("boom")
            }
            error("should have thrown")
        } catch (e: java.io.IOException) {
            assertEquals(2, attempts.get())
        }
    }

    @Test
    fun `retryWithBackoff never swallows CancellationException`() = runTest {
        try {
            retryWithBackoff<String>(maxAttempts = 5) {
                throw CancellationException("cancelled")
            }
            error("should have rethrown")
        } catch (e: CancellationException) {
            // expected
        }
    }

    @Test
    fun `retryWithBackoff caps delay at maxDelayMs`() = runTest {
        val delays = mutableListOf<Long>()
        retryWithBackoff(
            maxAttempts = 3,
            initialDelayMs = 5_000,
            factor = 2.0,
            maxDelayMs = 8_000,
            jitter = { d -> delays += d; 0L }, // record, sleep nothing
            shouldRetry = { true }
        ) { "x" }
        assertEquals(listOf(5_000L, 8_000L), delays)
    }
}
