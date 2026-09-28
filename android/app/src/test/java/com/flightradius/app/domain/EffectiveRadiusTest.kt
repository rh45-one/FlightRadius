package com.flightradius.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class EffectiveRadiusTest {

    private fun ac(id: Long, radius: Double? = null) =
        TrackedAircraft(id, "CS$id", IdentifierType.CALLSIGN, alertRadiusKm = radius)

    private fun fleet(id: Long, radius: Double?, members: Set<Long>) =
        Fleet(id, "F$id", 0, alertRadiusKm = radius, memberIds = members)

    @Test
    fun `aircraft override wins over fleet and global`() {
        val a = ac(1, radius = 5.0)
        val f = fleet(1, 50.0, setOf(1))
        assertEquals(5.0, effectiveRadiusKm(a, listOf(f), 25.0), 1e-9)
    }

    @Test
    fun `max of member fleet radii applies when no aircraft override`() {
        val a = ac(1)
        val f1 = fleet(1, 30.0, setOf(1))
        val f2 = fleet(2, 60.0, setOf(1))
        val f3 = fleet(3, null, setOf(1)) // no radius -> ignored
        assertEquals(60.0, effectiveRadiusKm(a, listOf(f1, f2, f3), 25.0), 1e-9)
    }

    @Test
    fun `fleets not containing the aircraft are ignored`() {
        val a = ac(1)
        val f = fleet(1, 60.0, setOf(99))
        assertEquals(25.0, effectiveRadiusKm(a, listOf(f), 25.0), 1e-9)
    }

    @Test
    fun `global fallback when nothing overrides`() {
        assertEquals(25.0, effectiveRadiusKm(ac(1), emptyList(), 25.0), 1e-9)
    }
}
