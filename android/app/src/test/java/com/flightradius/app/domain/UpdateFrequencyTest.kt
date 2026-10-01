package com.flightradius.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateFrequencyTest {
    private val day = 24L * 60 * 60 * 1000

    @Test fun `on launch is always due`() {
        assertTrue(UpdateFrequency.ON_LAUNCH.isDue(1_000, 999))
        assertTrue(UpdateFrequency.ON_LAUNCH.isDue(1_000, 0))
    }

    @Test fun `never is never due`() {
        assertFalse(UpdateFrequency.NEVER.isDue(100 * day, 0))
    }

    @Test fun `daily boundary`() {
        val last = 10 * day
        assertFalse(UpdateFrequency.DAILY.isDue(last + day - 1, last))
        assertTrue(UpdateFrequency.DAILY.isDue(last + day, last))
    }

    @Test fun `weekly boundary`() {
        val last = 10 * day
        assertFalse(UpdateFrequency.WEEKLY.isDue(last + 7 * day - 1, last))
        assertTrue(UpdateFrequency.WEEKLY.isDue(last + 7 * day, last))
    }

    @Test fun `never checked yet is due and a backwards clock is too`() {
        assertTrue(UpdateFrequency.DAILY.isDue(5 * day, 0))
        assertTrue(UpdateFrequency.WEEKLY.isDue(5 * day, 6 * day))
    }
}
