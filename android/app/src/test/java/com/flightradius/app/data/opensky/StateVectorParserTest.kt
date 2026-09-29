package com.flightradius.app.data.opensky

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StateVectorParserTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun parse(body: String) = StateVectorParser.parse(json, body)

    @Test
    fun `parses a complete extended state vector`() {
        val snapshot = parse(
            """{"time":1700000005,"states":[["3C6444","DLH9U   ","Germany",1700000000,1700000004,
                8.5,50.1,10972.8,false,230.5,271.3,-1.2,null,11200.0,"1000",false,0,4]]}"""
        )

        assertEquals(1700000005L, snapshot.timeSec)
        val s = snapshot.states.single()
        assertEquals("3c6444", s.icao24)
        assertEquals("DLH9U", s.callsign)
        assertEquals("Germany", s.originCountry)
        assertEquals(1700000004L, s.lastContactSec)
        assertEquals(8.5, s.lon!!, 1e-9)
        assertEquals(50.1, s.lat!!, 1e-9)
        assertEquals(10972.8, s.altitudeM!!, 1e-9)
        assertEquals(false, s.onGround)
        assertEquals(230.5, s.velocityMps!!, 1e-9)
        assertEquals(271.3, s.trackDeg!!, 1e-9)
        assertEquals(-1.2, s.verticalRateMps!!, 1e-9)
        assertEquals("1000", s.squawk)
        assertEquals(0, s.positionSource)
        assertEquals(4, s.category)
    }

    @Test
    fun `missing columns, nulls and wrong types become null`() {
        val s = parse(
            """{"time":1,"states":[["abc123","   ",null,null,"soon","8.5",null,null]]}"""
        ).states.single()

        assertNull(s.callsign)
        assertNull(s.lastContactSec)
        assertNull("numeric strings are not numbers", s.lon)
        assertNull(s.lat)
        assertNull(s.altitudeM)
        assertNull(s.category)
    }

    @Test
    fun `geometric altitude is the fallback when barometric is absent`() {
        val s = parse(
            """{"states":[["abc123","X",null,0,0,1.0,2.0,null,false,0,0,0,null,900.0]]}"""
        ).states.single()
        assertEquals(900.0, s.altitudeM!!, 1e-9)
    }

    @Test
    fun `out-of-range coordinates are dropped`() {
        val s = parse("""{"states":[["abc123","X",null,0,0,200.0,-95.0]]}""").states.single()
        assertNull(s.lon)
        assertNull(s.lat)
    }

    @Test
    fun `rows without an icao24 or that are not arrays are skipped`() {
        val snapshot = parse(
            """{"states":[[null,"A"],["","B"],"garbage",42,["def456","C"]]}"""
        )
        assertEquals(listOf("def456"), snapshot.states.map { it.icao24 })
    }

    @Test
    fun `null or absent states means an empty snapshot`() {
        assertTrue(parse("""{"time":5,"states":null}""").states.isEmpty())
        assertTrue(parse("""{"time":5}""").states.isEmpty())
    }

    @Test(expected = SerializationException::class)
    fun `a non-object body is malformed`() {
        parse("""[1,2,3]""")
    }
}
