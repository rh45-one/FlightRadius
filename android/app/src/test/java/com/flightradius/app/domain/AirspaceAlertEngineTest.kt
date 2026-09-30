package com.flightradius.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AirspaceAlertEngineTest {

    private val engine = AirspaceAlertEngine()
    private val rule = AirspaceRule("r", "Low and close", true, 5.0, 1_500.0)
    private val rules = listOf(rule)

    private fun ac(
        id: String,
        km: Double,
        alt: Double? = 400.0,
        cls: AircraftClass = AircraftClass.HELICOPTER,
        lastContactSec: Double? = null
    ) = NearbyAircraft(id, id.uppercase(), cls, 40.0, -3.0, km, 0.0,
        altitudeM = alt, lastContactSec = lastContactSec)

    private fun eval(
        now: Long,
        nearby: List<NearbyAircraft>,
        prev: Map<String, NearbyAlertState> = emptyMap(),
        rs: List<AirspaceRule> = rules,
        muted: Long = 0L
    ) = engine.evaluate(now, nearby, rs, prev, muted)

    @Test
    fun `entering a rule emits one alert`() {
        val r = eval(1_000_000L, listOf(ac("a1", 3.0)))
        assertEquals(1, r.alerts.size)
        assertEquals("a1", r.alerts[0].aircraft.icao24)
        assertEquals("r", r.alerts[0].rule.id)
        assertTrue(r.states["a1"]!!.inside)
    }

    @Test
    fun `outside the rule or disabled rules do nothing`() {
        assertTrue(eval(0L, listOf(ac("a1", 8.0))).alerts.isEmpty())
        assertTrue(eval(0L, listOf(ac("a1", 3.0)), rs = listOf(rule.copy(enabled = false))).alerts.isEmpty())
        assertTrue(eval(0L, listOf(ac("a1", 3.0, alt = 3_000.0))).alerts.isEmpty())
    }

    @Test
    fun `no re alert while jittering inside the hysteresis band`() {
        val t0 = 1_000_000L
        var s = eval(t0, listOf(ac("a1", 3.0))).states
        // 5.6 km is outside the rule (5) but inside the exit threshold (5.75).
        val r2 = eval(t0 + 20_000, listOf(ac("a1", 5.6)), s)
        assertTrue(r2.alerts.isEmpty())
        assertTrue(r2.states["a1"]!!.inside)
        s = r2.states
        val r3 = eval(t0 + 40_000, listOf(ac("a1", 4.0)), s)
        assertTrue(r3.alerts.isEmpty())
    }

    @Test
    fun `altitude hysteresis adds 150 m`() {
        val t0 = 1_000_000L
        val s = eval(t0, listOf(ac("a1", 3.0, alt = 1_400.0))).states
        assertTrue(eval(t0 + 10_000, listOf(ac("a1", 3.0, alt = 1_640.0)), s).states["a1"]!!.inside)
        assertFalse(eval(t0 + 10_000, listOf(ac("a1", 3.0, alt = 1_660.0)), s).states["a1"]!!.inside)
    }

    @Test
    fun `leaving clears inside and cooldown blocks a quick return`() {
        val t0 = 1_000_000L
        val entered = eval(t0, listOf(ac("a1", 3.0)))
        val left = eval(t0 + 60_000, listOf(ac("a1", 9.0)), entered.states)
        assertFalse(left.states["a1"]!!.inside)
        // Re-enter after 2 min: still inside the 10 min cooldown.
        val back = eval(t0 + 120_000, listOf(ac("a1", 3.0)), left.states)
        assertTrue(back.alerts.isEmpty())
        assertTrue(back.states["a1"]!!.inside)
        // After the cooldown it alerts again.
        val leftAgain = eval(t0 + 200_000, listOf(ac("a1", 9.0)), back.states)
        val later = eval(t0 + 11 * 60_000, listOf(ac("a1", 3.0)), leftAgain.states)
        assertEquals(1, later.alerts.size)
    }

    @Test
    fun `mute suppresses alerts but still tracks inside`() {
        val t0 = 1_000_000L
        val r = eval(t0, listOf(ac("a1", 3.0)), muted = t0 + 60_000)
        assertTrue(r.alerts.isEmpty())
        assertTrue(r.states["a1"]!!.inside)
        // Unmuted later but already inside: no retroactive alert.
        val r2 = eval(t0 + 120_000, listOf(ac("a1", 3.0)), r.states)
        assertTrue(r2.alerts.isEmpty())
    }

    @Test
    fun `stale observations keep state and do not alert`() {
        val now = 1_000_000_000L
        val staleSec = (now - 300_000) / 1000.0
        val r = eval(now, listOf(ac("a1", 3.0, lastContactSec = staleSec)))
        assertTrue(r.alerts.isEmpty())
        assertFalse(r.states["a1"]?.inside ?: false)
    }

    @Test
    fun `absent aircraft are forgotten after ten minutes`() {
        val t0 = 1_000_000L
        val s = eval(t0, listOf(ac("a1", 3.0))).states
        assertTrue(eval(t0 + 5 * 60_000, emptyList(), s).states["a1"]!!.inside)
        val forgotten = eval(t0 + 11 * 60_000, emptyList(), s)
        assertFalse(forgotten.states["a1"]!!.inside)
        assertEquals(t0, forgotten.states["a1"]!!.lastAlertAtMs)
    }

    @Test
    fun `alerts are sorted nearest first and class filter applies`() {
        val heliOnly = rule.copy(classes = setOf(AircraftClass.HELICOPTER))
        val r = eval(
            0L,
            listOf(
                ac("far", 4.5), ac("near", 1.0),
                ac("plane", 2.0, cls = AircraftClass.AIRLINER)
            ),
            rs = listOf(heliOnly)
        )
        assertEquals(listOf("near", "far"), r.alerts.map { it.aircraft.icao24 })
    }
}
