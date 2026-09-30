package com.flightradius.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AircraftClassifierTest {

    private fun meta(cls: AircraftClass) = AircraftMeta(cls)

    @Test
    fun `every ads-b category branch`() {
        val expected = mapOf(
            2 to AircraftClass.LIGHT, 3 to AircraftClass.AIRLINER, 4 to AircraftClass.AIRLINER,
            5 to AircraftClass.AIRLINER, 6 to AircraftClass.AIRLINER,
            7 to AircraftClass.MILITARY, 8 to AircraftClass.HELICOPTER,
            9 to AircraftClass.GLIDER, 10 to AircraftClass.BALLOON,
            12 to AircraftClass.LIGHT, 14 to AircraftClass.DRONE
        )
        for ((cat, cls) in expected) {
            assertEquals("category $cat", cls, AircraftClassifier.classify(cat, null))
        }
    }

    @Test
    fun `category wins over the stored class`() {
        assertEquals(AircraftClass.HELICOPTER,
            AircraftClassifier.classify(8, meta(AircraftClass.LIGHT)))
    }

    @Test
    fun `unknown categories fall through to the database then unknown`() {
        for (cat in listOf(null, 0, 1, 11, 13, 15, 20)) {
            assertEquals(AircraftClass.LIGHT, AircraftClassifier.classify(cat, meta(AircraftClass.LIGHT)))
            assertEquals(AircraftClass.UNKNOWN, AircraftClassifier.classify(cat, null))
        }
        assertEquals(AircraftClass.UNKNOWN,
            AircraftClassifier.classify(0, meta(AircraftClass.UNKNOWN)))
    }

    @Test
    fun `database class strings`() {
        fun c(raw: String?, wtc: String? = null) = DbClassMapper.fromClassString(raw, wtc)
        assertEquals(AircraftClass.LIGHT, c("L1P"))
        assertEquals(AircraftClass.AIRLINER, c("L2J"))
        assertEquals(AircraftClass.HELICOPTER, c("H1T"))
        assertEquals(AircraftClass.HELICOPTER, c("G1P"))
        assertEquals(AircraftClass.HELICOPTER, c("T2T"))
        assertEquals(AircraftClass.LIGHT, c("S1P"))
        assertEquals(AircraftClass.LIGHT, c("A1E"))
        assertEquals(AircraftClass.AIRLINER, c("L2T"))
        assertEquals(AircraftClass.LIGHT, c("L1T", "L"))
        assertEquals(AircraftClass.LIGHT, c("L2J", "L"))
        assertNull(c(""))
        assertNull(c(null))
        assertNull(c("L1R"))
        assertNull(c("X1P"))
    }

    @Test
    fun `empty class falls back to doc8643 typecode including light jets`() {
        val doc = mapOf(
            "EC35" to Doc8643Type("H2T", "L"),
            "A320" to Doc8643Type("L2J", "M"),
            "C25A" to Doc8643Type("L2J", "L")
        )
        assertEquals(AircraftClass.HELICOPTER, DbClassMapper.classify("", "ec35", null, doc))
        assertEquals(AircraftClass.AIRLINER, DbClassMapper.classify(null, "A320", null, doc))
        assertEquals(AircraftClass.LIGHT, DbClassMapper.classify("", "C25A", null, doc))
        assertEquals(AircraftClass.LIGHT, DbClassMapper.classify("L2J", "C25A", null, doc))
    }

    @Test
    fun `category description fallback`() {
        fun d(text: String?) = DbClassMapper.classify("", null, text, emptyMap())
        assertEquals(AircraftClass.HELICOPTER, d("Rotorcraft"))
        assertEquals(AircraftClass.LIGHT, d("Light (< 15500 lbs)"))
        assertEquals(AircraftClass.GLIDER, d("Glider / sailplane"))
        assertEquals(AircraftClass.AIRLINER, d("Large (75000 to 300000 lbs)"))
        assertEquals(AircraftClass.AIRLINER, d("Heavy (> 300000 lbs)"))
        assertEquals(AircraftClass.AIRLINER, d("Small (15500 to 75000 lbs)"))
        assertEquals(AircraftClass.DRONE, d("UAV"))
        assertEquals(AircraftClass.BALLOON, d("Lighter-than-air"))
        assertEquals(AircraftClass.UNKNOWN, d("No ADS-B Emitter Category Information"))
        assertEquals(AircraftClass.UNKNOWN, d("Surface Vehicle - Service Vehicle"))
        assertEquals(AircraftClass.UNKNOWN, d(""))
        assertEquals(AircraftClass.UNKNOWN, d(null))
    }

    @Test
    fun `class codes round trip`() {
        for (c in AircraftClass.entries) assertEquals(c, AircraftClass.fromCode(c.code))
        assertEquals(AircraftClass.UNKNOWN, AircraftClass.fromCode(99))
    }
}
