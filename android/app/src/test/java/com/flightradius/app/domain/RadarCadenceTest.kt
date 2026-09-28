package com.flightradius.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarCadenceTest {

    @Test
    fun `silent when nothing closest`() {
        assertNull(RadarCadence.intervalMs(null, 25.0))
    }

    @Test
    fun `silent beyond twice the radius`() {
        assertNull(RadarCadence.intervalMs(51.0, 25.0))
        assertEquals(4000L, RadarCadence.intervalMs(50.0, 25.0))
    }

    @Test
    fun `bounds are 250 to 4000`() {
        assertEquals(250L, RadarCadence.intervalMs(0.0, 25.0))
        val v = RadarCadence.intervalMs(25.0, 25.0)!!
        assertTrue(v in 250..4000)
    }

    @Test
    fun `interval grows monotonically with distance`() {
        var last = 0L
        for (d in 0..50) {
            val v = RadarCadence.intervalMs(d.toDouble(), 25.0)!!
            assertTrue("d=$d", v >= last)
            last = v
        }
        assertEquals(4000L, last)
    }
}
