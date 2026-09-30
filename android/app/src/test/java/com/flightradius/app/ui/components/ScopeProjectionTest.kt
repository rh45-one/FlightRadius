package com.flightradius.app.ui.components

import com.flightradius.app.domain.AircraftClass
import com.flightradius.app.domain.NearbyAircraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class ScopeProjectionTest {

    private fun ac(id: String, km: Double, bearing: Double, match: Boolean = false) =
        NearbyAircraft(id, null, AircraftClass.LIGHT, 0.0, 0.0, km, bearing, matchesRule = match)

    @Test
    fun `north is up east is right south is down west is left on the rim track`() {
        val ring = 100f
        val r = ScopeProjection.RIM_FRACTION * ring
        fun o(b: Double) = ScopeProjection.rimOffset(b, ring)
        val n = o(0.0); assertEquals(0f, n.first, 0.01f); assertEquals(-r, n.second, 0.01f)
        val e = o(90.0); assertEquals(r, e.first, 0.01f); assertEquals(0f, e.second, 0.01f)
        val s = o(180.0); assertEquals(0f, s.first, 0.01f); assertEquals(r, s.second, 0.01f)
        val w = o(270.0); assertEquals(-r, w.first, 0.01f); assertEquals(0f, w.second, 0.01f)
    }

    @Test
    fun `every marker is on the rim track regardless of distance`() {
        val scope = DialScope(listOf(ac("a", 0.5, 33.0), ac("b", 24.0, 200.0), ac("c", 900.0, 310.0)))
        val ring = 200f
        for (m in ScopeProjection.markers(scope, ring, null)) {
            assertEquals(ScopeProjection.RIM_FRACTION * ring, hypot(m.dx, m.dy), 0.01f)
        }
    }

    @Test
    fun `angular distance wraps around north`() {
        assertEquals(10.0, ScopeProjection.angularDistance(355.0, 5.0), 1e-9)
        assertEquals(10.0, ScopeProjection.angularDistance(5.0, 355.0), 1e-9)
        assertEquals(180.0, ScopeProjection.angularDistance(0.0, 180.0), 1e-9)
        assertEquals(0.0, ScopeProjection.angularDistance(720.0, 0.0), 1e-9)
    }

    @Test
    fun `markers within eight degrees of the hero arrow are dropped`() {
        val list = listOf(
            ac("close", 1.0, 42.0), ac("edge", 2.0, 48.0), ac("outside", 3.0, 48.5),
            ac("wrap", 4.0, 352.0), ac("other", 5.0, 200.0)
        )
        val kept = ScopeProjection.markers(DialScope(list), 100f, heroBearingDeg = 40.0)
        // 42 and 48 are within 8 deg of 40; 48.5 is 8.5 deg away; 352 is 48 away.
        assertEquals(3, kept.size)
        val wrapped = ScopeProjection.markers(DialScope(list), 100f, heroBearingDeg = 356.0)
        assertEquals(4, wrapped.size) // 352 (4 deg away) dropped
    }

    @Test
    fun `excluded aircraft is skipped`() {
        val scope = DialScope(listOf(ac("hero", 1.0, 10.0), ac("x", 2.0, 100.0)), excludeIcao24 = "hero")
        assertEquals(1, ScopeProjection.markers(scope, 100f, null).size)
    }

    @Test
    fun `at most twenty nearest markers are kept and flags are preserved`() {
        val many = (1..35).map { ac("a%05d".format(it), it.toDouble(), it * 10.0, match = it == 3) }.shuffled()
        val markers = ScopeProjection.markers(DialScope(many), 100f, null)
        assertEquals(20, markers.size)
        assertEquals(1, markers.count { it.matchesRule })
        // Bearings 10..200 => the 21st nearest (210 deg) is not drawn.
        val farthestBearingKept = (1..20).maxOf { it * 10.0 }
        val (dx, dy) = ScopeProjection.rimOffset(farthestBearingKept, 100f)
        assertTrue(markers.any { kotlin.math.abs(it.dx - dx) < 0.01f && kotlin.math.abs(it.dy - dy) < 0.01f })
    }

    @Test
    fun `no hero bearing keeps everyone`() {
        val scope = DialScope(listOf(ac("a", 1.0, 10.0), ac("b", 2.0, 11.0)))
        assertEquals(2, ScopeProjection.markers(scope, 100f, null).size)
    }
}
