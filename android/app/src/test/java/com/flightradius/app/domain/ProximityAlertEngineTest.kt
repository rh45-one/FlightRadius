package com.flightradius.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProximityAlertEngineTest {

    private val engine = ProximityAlertEngine()

    private fun obs(
        id: Long,
        distanceKm: Double,
        radiusKm: Double = 25.0,
        lastContactSec: Double? = null
    ) = AircraftObservation(
        aircraftId = id, callsign = "CS$id", icao24 = null,
        distanceKm = distanceKm, lat = 50.0, lon = 8.0,
        lastContactSec = lastContactSec, bearingDeg = 0.0,
        effectiveRadiusKm = radiusKm
    )

    @Test
    fun `entering radius emits one alert`() {
        val r = engine.evaluate(1_000_000L, listOf(obs(1, 10.0)), emptyMap(), emptyMap())
        assertEquals(1, r.alerts.size)
        assertEquals(1L, r.alerts[0].observation.aircraftId)
        assertEquals(AlertZone.INSIDE, r.states[1]!!.zone)
        assertEquals(1_000_000L, r.states[1]!!.lastAlertAtMs)
    }

    @Test
    fun `staying inside does not re-alert`() {
        val r1 = engine.evaluate(1_000_000L, listOf(obs(1, 10.0)), emptyMap(), emptyMap())
        val r2 = engine.evaluate(1_100_000L, listOf(obs(1, 12.0)), r1.states, emptyMap())
        assertEquals(0, r2.alerts.size)
        assertEquals(AlertZone.INSIDE, r2.states[1]!!.zone)
        assertEquals(1_000_000L, r2.states[1]!!.lastAlertAtMs)
    }

    @Test
    fun `jitter between radius and exit threshold never exits`() {
        // radius 25, exit threshold 25 + 3.75 = 28.75
        var states = emptyMap<Long, AircraftAlertState>()
        states = engine.evaluate(1_000_000L, listOf(obs(1, 10.0)), states, emptyMap()).states
        states = engine.evaluate(1_100_000L, listOf(obs(1, 27.0)), states, emptyMap()).states
        val r3 = engine.evaluate(1_200_000L, listOf(obs(1, 24.0)), states, emptyMap())
        assertEquals(AlertZone.INSIDE, r3.states[1]!!.zone) // still inside
        assertEquals(0, r3.alerts.size)                     // no re-alert
    }

    @Test
    fun `exit beyond margin then re-enter after cooldown alerts`() {
        var states = emptyMap<Long, AircraftAlertState>()
        states = engine.evaluate(0L, listOf(obs(1, 10.0)), states, emptyMap()).states
        // exit: > 28.75
        states = engine.evaluate(60_000L, listOf(obs(1, 30.0)), states, emptyMap()).states
        assertEquals(AlertZone.OUTSIDE, states[1]!!.zone)
        // re-enter after 5min cooldown
        val r = engine.evaluate(60_000L + 6 * 60_000L, listOf(obs(1, 10.0)), states, emptyMap())
        assertEquals(1, r.alerts.size)
    }

    @Test
    fun `re-enter within cooldown becomes INSIDE but stays silent`() {
        var states = emptyMap<Long, AircraftAlertState>()
        states = engine.evaluate(0L, listOf(obs(1, 10.0)), states, emptyMap()).states
        states = engine.evaluate(10_000L, listOf(obs(1, 30.0)), states, emptyMap()).states
        val r = engine.evaluate(20_000L, listOf(obs(1, 10.0)), states, emptyMap())
        assertEquals(0, r.alerts.size)
        assertEquals(AlertZone.INSIDE, r.states[1]!!.zone)
        assertEquals(0L, r.states[1]!!.lastAlertAtMs) // unchanged
    }

    @Test
    fun `snooze suppresses alert but zone still updates`() {
        val r = engine.evaluate(
            1_000_000L, listOf(obs(1, 10.0)),
            emptyMap(), mapOf(1L to 2_000_000L)
        )
        assertEquals(0, r.alerts.size)
        assertEquals(AlertZone.INSIDE, r.states[1]!!.zone)
        assertNull(r.states[1]!!.lastAlertAtMs)
    }

    @Test
    fun `absent aircraft keeps state`() {
        var states = engine.evaluate(0L, listOf(obs(1, 10.0)), emptyMap(), emptyMap()).states
        val r = engine.evaluate(60_000L, emptyList(), states, emptyMap())
        assertEquals(AlertZone.INSIDE, r.states[1]!!.zone) // unchanged
    }

    @Test
    fun `absent longer than forgetAfter resets to OUTSIDE keeping lastAlertAt`() {
        var states = engine.evaluate(0L, listOf(obs(1, 10.0)), emptyMap(), emptyMap()).states
        val r = engine.evaluate(11 * 60_000L, emptyList(), states, emptyMap())
        assertEquals(AlertZone.OUTSIDE, r.states[1]!!.zone)
        assertEquals(0L, r.states[1]!!.lastAlertAtMs)
    }

    @Test
    fun `stale observation does not enter or exit`() {
        val now = 300_000L
        val staleContact = (now - 200_000L) / 1000.0 // 200s ago > 120s stale
        // would enter if fresh:
        val r1 = engine.evaluate(now, listOf(obs(1, 10.0, lastContactSec = staleContact)), emptyMap(), emptyMap())
        assertEquals(AlertZone.OUTSIDE, r1.states[1]!!.zone)
        assertEquals(0, r1.alerts.size)
        // would exit if fresh — and elapsed since lastSeen stays < forgetAfter:
        var states = engine.evaluate(0L, listOf(obs(1, 10.0)), emptyMap(), emptyMap()).states
        val r2 = engine.evaluate(now, listOf(obs(1, 30.0, lastContactSec = staleContact)), states, emptyMap())
        assertEquals(AlertZone.INSIDE, r2.states[1]!!.zone)
    }

    @Test
    fun `multiple alerts sorted by distance`() {
        val r = engine.evaluate(
            1_000_000L,
            listOf(obs(1, 20.0), obs(2, 5.0), obs(3, 12.0)),
            emptyMap(), emptyMap()
        )
        assertEquals(listOf(2L, 3L, 1L), r.alerts.map { it.observation.aircraftId })
    }

    @Test
    fun `no alert when beyond radius`() {
        val r = engine.evaluate(1_000_000L, listOf(obs(1, 40.0)), emptyMap(), emptyMap())
        assertEquals(0, r.alerts.size)
        assertEquals(AlertZone.OUTSIDE, r.states[1]!!.zone)
    }
}
