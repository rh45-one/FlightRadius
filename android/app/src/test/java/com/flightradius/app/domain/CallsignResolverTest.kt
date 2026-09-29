package com.flightradius.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallsignResolverTest {

    private val minute = 60_000L
    private val resolver = CallsignResolver()

    private fun state(icao: String, callsign: String?) = StateVector(icao24 = icao, callsign = callsign)

    @Test
    fun `a never-searched callsign is searched immediately`() {
        assertTrue(resolver.shouldSearch(0, setOf("IBE1")))
        assertFalse(resolver.shouldSearch(0, emptySet()))
    }

    @Test
    fun `a global snapshot resolves wanted callsigns only`() {
        resolver.learnFromGlobal(0, listOf(state("aaa111", "IBE1"), state("bbb222", "OTHER")), setOf("IBE1"))

        assertEquals("aaa111", resolver.icao24For("IBE1"))
        assertNull(resolver.icao24For("OTHER"))
        assertEquals(emptySet<String>(), resolver.unresolved(listOf("IBE1")))
    }

    @Test
    fun `unresolved callsigns back off exponentially up to the cap`() {
        val wanted = setOf("GONE1")
        resolver.learnFromGlobal(0, emptyList(), wanted)
        assertFalse(resolver.shouldSearch(9 * minute, wanted))
        assertTrue(resolver.shouldSearch(10 * minute, wanted))

        resolver.learnFromGlobal(10 * minute, emptyList(), wanted)
        assertFalse(resolver.shouldSearch(29 * minute, wanted))
        assertTrue(resolver.shouldSearch(30 * minute, wanted))

        repeat(5) { resolver.learnFromGlobal(30 * minute, emptyList(), wanted) }
        assertFalse(resolver.shouldSearch(89 * minute, wanted))
        assertTrue("capped at one hour", resolver.shouldSearch(90 * minute, wanted))
    }

    @Test
    fun `a newly added callsign bypasses the back-off`() {
        resolver.learnFromGlobal(0, emptyList(), setOf("GONE1"))
        assertTrue(resolver.shouldSearch(1 * minute, setOf("GONE1", "NEW1")))
    }

    @Test
    fun `a reassigned airframe drops the mapping and allows an immediate search`() {
        resolver.learnFromGlobal(0, listOf(state("aaa111", "IBE1")), setOf("IBE1"))
        resolver.reconcile(minute, listOf(state("aaa111", "IBE2")))

        assertNull(resolver.icao24For("IBE1"))
        assertTrue(resolver.shouldSearch(minute, resolver.unresolved(listOf("IBE1"))))
    }

    @Test
    fun `a mapping survives short gaps but is forgotten after 30 minutes`() {
        resolver.learnFromGlobal(0, listOf(state("aaa111", "IBE1")), setOf("IBE1"))
        resolver.reconcile(20 * minute, emptyList())
        assertEquals("aaa111", resolver.icao24For("IBE1"))

        resolver.reconcile(31 * minute, emptyList())
        assertNull(resolver.icao24For("IBE1"))
    }

    @Test
    fun `seeing the airframe refreshes the mapping, even without a callsign`() {
        resolver.learnFromGlobal(0, listOf(state("aaa111", "IBE1")), setOf("IBE1"))
        resolver.reconcile(25 * minute, listOf(state("aaa111", null)))
        resolver.reconcile(50 * minute, emptyList())
        assertEquals("aaa111", resolver.icao24For("IBE1"))
    }

    @Test
    fun `untracked callsigns are forgotten`() {
        resolver.learnFromGlobal(0, listOf(state("aaa111", "IBE1")), setOf("IBE1"))
        resolver.retainOnly(emptySet())
        assertNull(resolver.icao24For("IBE1"))
    }
}
