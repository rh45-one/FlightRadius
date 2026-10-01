package com.flightradius.app.data.aircraftdb

import com.flightradius.app.data.prefs.AircraftDbMeta
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoredMetaTest {
    private val meta = AircraftDbMeta("k", null, 1L, 593_000, 467_000, 1L)

    @Test fun `meta with rows is trusted`() = assertTrue(restoredMetaIsValid(meta, true))

    @Test fun `meta without rows (restored backup, db excluded) is not`() =
        assertFalse(restoredMetaIsValid(meta, false))

    @Test fun `no meta is never valid`() {
        assertFalse(restoredMetaIsValid(null, true))
        assertFalse(restoredMetaIsValid(null, false))
    }
}
