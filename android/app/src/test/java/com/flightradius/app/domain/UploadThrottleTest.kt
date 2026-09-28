package com.flightradius.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadThrottleTest {

    private fun gpsFix(lat: Double = 52.0, lon: Double = 13.0, t: Long = 0L) =
        UserFix(lat, lon, accuracyM = 10.0, timeMs = t, source = LocationSource.GPS)

    private fun manualFix(lat: Double = 52.0, lon: Double = 13.0) =
        UserFix(lat, lon, accuracyM = 0.0, timeMs = 0L, source = LocationSource.MANUAL)

    @Test
    fun `first fix always uploads`() {
        assertTrue(UploadThrottle.shouldUpload(0L, gpsFix(), null, null))
    }

    @Test
    fun `gps uploads when 30s elapsed`() {
        assertTrue(
            UploadThrottle.shouldUpload(31_000L, gpsFix(), gpsFix(), 0L))
    }

    @Test
    fun `gps uploads when moved 100m`() {
        // ~0.001 deg lat ~ 111m.
        val moved = gpsFix(lat = 52.001)
        assertTrue(
            UploadThrottle.shouldUpload(5_000L, moved, gpsFix(), 0L))
    }

    @Test
    fun `gps holds when fresh and unmoved`() {
        assertFalse(
            UploadThrottle.shouldUpload(10_000L, gpsFix(), gpsFix(), 0L))
    }

    @Test
    fun `manual uploads only on change`() {
        assertTrue(
            UploadThrottle.shouldUpload(1_000L, manualFix(53.0), manualFix(52.0), 0L))
        assertFalse(
            UploadThrottle.shouldUpload(1_000L, manualFix(52.0), manualFix(52.0), 0L))
        // Time elapsed alone doesn't re-upload a manual fix.
        assertFalse(
            UploadThrottle.shouldUpload(60_000L, manualFix(52.0), manualFix(52.0), 0L))
    }
}
