package com.flightradius.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSkyPricingTest {

    @Test
    fun `global and icao24 queries follow the documented prices`() {
        assertEquals(4, OpenSkyPricing.credits(StatesQuery.Global))
        assertEquals(0, OpenSkyPricing.credits(StatesQuery.ByIcao24(emptyList())))
        assertEquals(1, OpenSkyPricing.credits(StatesQuery.ByIcao24(List(100) { "a$it" })))
        assertEquals(2, OpenSkyPricing.credits(StatesQuery.ByIcao24(List(101) { "a$it" })))
    }

    @Test
    fun `area tiers are inclusive at their upper bound`() {
        assertEquals(1, OpenSkyPricing.areaCredits(25.0))
        assertEquals(2, OpenSkyPricing.areaCredits(25.01))
        assertEquals(2, OpenSkyPricing.areaCredits(100.0))
        assertEquals(3, OpenSkyPricing.areaCredits(400.0))
        assertEquals(4, OpenSkyPricing.areaCredits(400.01))
    }

    @Test
    fun `a 50 km radius box at mid latitudes costs one credit`() {
        val box = BoundingBox.around(40.4, -3.7, 50.0)
        assertEquals(0.898, box.latMax - box.latMin, 0.01)
        assertTrue(box.areaSqDeg < 25.0)
        assertEquals(1, OpenSkyPricing.credits(StatesQuery.ByArea(box)))
    }

    @Test
    fun `boxes are clamped to valid coordinates`() {
        val polar = BoundingBox.around(89.9, 179.9, 100.0)
        assertEquals(90.0, polar.latMax, 1e-9)
        assertEquals(180.0, polar.lonMax, 1e-9)
        assertTrue(polar.lonMin >= -180.0)
    }
}
