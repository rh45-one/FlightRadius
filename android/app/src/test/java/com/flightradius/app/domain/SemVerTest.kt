package com.flightradius.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {
    @Test fun `equal versions`() {
        assertEquals(0, SemVer.compare("0.1.0", "0.1.0"))
        assertFalse(SemVer.isNewer("0.1.0", "0.1.0"))
    }

    @Test fun `greater and lesser`() {
        assertTrue(SemVer.isNewer("0.2.0", "0.1.0"))
        assertTrue(SemVer.isNewer("1.0.0", "0.99.99"))
        assertTrue(SemVer.isNewer("0.1.10", "0.1.9"))
        assertFalse(SemVer.isNewer("0.1.0", "0.2.0"))
        assertFalse(SemVer.isNewer("0.0.9", "0.1.0"))
    }

    @Test fun `leading v is ignored`() {
        assertEquals(0, SemVer.compare("v1.2.3", "1.2.3"))
        assertTrue(SemVer.isNewer("v0.2.0", "0.1.0"))
        assertEquals("1.2.3", SemVer.strip("v1.2.3"))
    }

    @Test fun `malformed never means update`() {
        for (bad in listOf("", "latest", "1.2", "1.2.3.4", "1.2.x", "v", "android-v13.5.1", "1.2.3-rc1")) {
            assertNull(bad, SemVer.compare(bad, "0.1.0"))
            assertFalse(bad, SemVer.isNewer(bad, "0.1.0"))
            assertFalse(bad, SemVer.isNewer("9.9.9", bad))
        }
        assertFalse(SemVer.isNewer(null, "0.1.0"))
    }
}
