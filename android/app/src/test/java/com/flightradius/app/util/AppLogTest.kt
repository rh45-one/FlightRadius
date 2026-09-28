package com.flightradius.app.util

import com.flightradius.app.util.log.AppLog
import com.flightradius.app.util.log.LogLevel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppLogTest {

    @Before
    fun setUp() {
        AppLog.clear()
        AppLog.debugEnabled = { true }
        AppLog.clock = { 1_000L }
    }

    @After
    fun tearDown() {
        AppLog.clear()
        AppLog.debugEnabled = { false }
        AppLog.clock = { System.currentTimeMillis() }
    }

    @Test
    fun `ring buffer caps at capacity`() {
        repeat(AppLog.CAPACITY + 50) { AppLog.w("T", "msg$it") }
        val entries = AppLog.entries.value
        assertEquals(AppLog.CAPACITY, entries.size)
        // Oldest entries dropped; newest retained.
        assertEquals("msg${AppLog.CAPACITY + 49}", entries.last().message)
        assertEquals("msg50", entries.first().message)
    }

    @Test
    fun `sensitive field keys are redacted`() {
        AppLog.w(
            "T", "auth",
            "api_secret" to "s3cret",
            "password" to "hunter2",
            "access_token" to "tok",
            "client_id" to "cid",
            "username" to "bob",
            "Authorization" to "Bearer x",
            "safe_key" to "visible"
        )
        val fields = AppLog.entries.value.single().fields
        assertEquals("***", fields["api_secret"])
        assertEquals("***", fields["password"])
        assertEquals("***", fields["access_token"])
        assertEquals("***", fields["client_id"])
        assertEquals("***", fields["username"])
        assertEquals("***", fields["Authorization"])
        assertEquals("visible", fields["safe_key"])
    }

    @Test
    fun `debug and info are dropped when debug disabled`() {
        AppLog.debugEnabled = { false }
        AppLog.d("T", "hidden debug")
        AppLog.i("T", "hidden info")
        assertTrue(AppLog.entries.value.isEmpty())
    }

    @Test
    fun `warn and error are buffered regardless of debug flag`() {
        AppLog.debugEnabled = { false }
        AppLog.w("T", "warn!")
        AppLog.e("T", "err!", throwable = IllegalStateException("boom"))
        val entries = AppLog.entries.value
        assertEquals(2, entries.size)
        assertEquals(LogLevel.WARN, entries[0].level)
        assertEquals(LogLevel.ERROR, entries[1].level)
        assertTrue(entries[1].throwable!!.contains("IllegalStateException"))
    }

    @Test
    fun `clear empties the buffer`() {
        AppLog.w("T", "x")
        AppLog.clear()
        assertTrue(AppLog.entries.value.isEmpty())
    }
}
