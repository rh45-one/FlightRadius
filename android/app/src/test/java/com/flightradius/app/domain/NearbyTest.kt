package com.flightradius.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyTest {

    private val fix = UserFix(40.0, -3.0, null, 0L, LocationSource.MANUAL)

    /** ~[km] north of the fix. */
    private fun sv(
        id: String,
        km: Double,
        alt: Double? = 500.0,
        onGround: Boolean? = false,
        category: Int? = null,
        lat: Double? = 40.0 + km / 111.195,
        lon: Double? = -3.0
    ) = StateVector(
        icao24 = id, callsign = id.uppercase(), lat = lat, lon = lon,
        baroAltitudeM = alt, onGround = onGround, category = category
    )

    private fun build(
        states: List<StateVector>,
        tracked: Set<String> = emptySet(),
        radius: Double = 25.0,
        meta: Map<String, AircraftMeta> = emptyMap(),
        rules: List<AirspaceRule> = emptyList()
    ) = NearbyBuilder.build(fix, states, tracked, radius, meta, rules)

    // ---- NearbyBuilder ----

    @Test
    fun `ground null position tracked and beyond radius are dropped`() {
        val result = build(
            states = listOf(
                sv("a00001", 3.0),
                sv("a00002", 4.0, onGround = true),
                sv("a00003", 5.0, lat = null),
                sv("a00004", 6.0, lon = null),
                sv("a00005", 7.0),
                sv("a00006", 30.0)
            ),
            tracked = setOf("a00005")
        )
        assertEquals(listOf("a00001"), result.map { it.icao24 })
    }

    @Test
    fun `sorted by distance and null on-ground is kept`() {
        val result = build(
            listOf(sv("a00001", 20.0), sv("a00002", 2.0), sv("a00003", 9.0, onGround = null))
        )
        assertEquals(listOf("a00002", "a00003", "a00001"), result.map { it.icao24 })
        assertEquals(2.0, result[0].distanceKm, 0.05)
        assertEquals(0.0, result[0].bearingDeg, 0.5)
    }

    @Test
    fun `class comes from category then metadata and metadata is attached`() {
        val meta = mapOf(
            "a00001" to AircraftMeta(AircraftClass.LIGHT, "C172", "EC-ABC", "Cessna 172"),
            "a00002" to AircraftMeta(AircraftClass.LIGHT)
        )
        val r = build(
            listOf(sv("a00001", 1.0), sv("a00002", 2.0, category = 8), sv("a00003", 3.0)),
            meta = meta
        ).associateBy { it.icao24 }
        assertEquals(AircraftClass.LIGHT, r["a00001"]!!.cls)
        assertEquals("EC-ABC", r["a00001"]!!.registration)
        assertEquals("C172", r["a00001"]!!.typecode)
        assertEquals(AircraftClass.HELICOPTER, r["a00002"]!!.cls)
        assertEquals(AircraftClass.UNKNOWN, r["a00003"]!!.cls)
    }

    @Test
    fun `matchesRule uses enabled rules only`() {
        val on = AirspaceRule("on", "on", true, 5.0, 1_500.0)
        val off = AirspaceRule("off", "off", false, 50.0, null)
        val r = build(listOf(sv("a00001", 3.0), sv("a00002", 12.0)), rules = listOf(on, off))
            .associateBy { it.icao24 }
        assertTrue(r["a00001"]!!.matchesRule)
        assertFalse(r["a00002"]!!.matchesRule)
    }

    @Test
    fun `display name prefers callsign then registration then icao24`() {
        val base = NearbyAircraft("abc123", null, AircraftClass.UNKNOWN, 0.0, 0.0, 1.0, 0.0)
        assertEquals("abc123", base.displayName)
        assertEquals("EC-KZX", base.copy(registration = "EC-KZX").displayName)
        assertEquals("IBE1", base.copy(registration = "EC-KZX", callsign = "IBE1").displayName)
    }

    // ---- rule matching ----

    private val low = AirspaceRule("r", "r", true, 5.0, 1_500.0)

    @Test
    fun `distance and altitude bounds are inclusive`() {
        assertTrue(low.matches(5.0, 1_500.0, AircraftClass.LIGHT))
        assertFalse(low.matches(5.01, 100.0, AircraftClass.LIGHT))
        assertFalse(low.matches(1.0, 1_500.1, AircraftClass.LIGHT))
    }

    @Test
    fun `null altitude fails only when a ceiling is set`() {
        assertFalse(low.matches(1.0, null, AircraftClass.LIGHT))
        assertTrue(low.copy(maxAltitudeM = null).matches(1.0, null, AircraftClass.LIGHT))
    }

    @Test
    fun `empty classes mean any and a set filters`() {
        assertTrue(low.matches(1.0, 100.0, AircraftClass.UNKNOWN))
        val heli = low.copy(classes = setOf(AircraftClass.HELICOPTER))
        assertTrue(heli.matches(1.0, 100.0, AircraftClass.HELICOPTER))
        assertFalse(heli.matches(1.0, 100.0, AircraftClass.LIGHT))
        assertFalse(heli.matches(1.0, 100.0, AircraftClass.UNKNOWN))
    }

    @Test
    fun `default rule ships disabled`() {
        val d = AirspaceRule.DEFAULTS.single()
        assertFalse(d.enabled)
        assertEquals(5.0, d.radiusKm, 0.0)
        assertEquals(1_500.0, d.maxAltitudeM!!, 0.0)
        assertTrue(d.classes.isEmpty())
    }

    // ---- refresh (carried-forward nearby) ----

    @Test
    fun `refresh recomputes distance bearing and rule match from the new fix`() {
        val rule = AirspaceRule("r", "r", true, 5.0, 1_500.0)
        val old = NearbyAircraft(
            "a00001", "X", AircraftClass.HELICOPTER, 40.03, -3.0, 3.3, 0.0, altitudeM = 400.0,
            matchesRule = true
        )
        val moved = UserFix(40.06, -3.0, null, 0L, LocationSource.MANUAL)
        val r = NearbyBuilder.refresh(moved, listOf(old), 25.0, listOf(rule)).single()
        assertEquals(3.3, r.distanceKm, 0.1)
        assertEquals(180.0, r.bearingDeg, 0.5)
        assertTrue(r.matchesRule)
        val far = UserFix(40.5, -3.0, null, 0L, LocationSource.MANUAL)
        assertTrue(NearbyBuilder.refresh(far, listOf(old), 25.0, listOf(rule)).isEmpty())
        val mid = UserFix(40.0, -3.0, null, 0L, LocationSource.MANUAL)
        assertEquals(3.3, NearbyBuilder.refresh(mid, listOf(old), 25.0, emptyList()).single().distanceKm, 0.1)
        assertFalse(NearbyBuilder.refresh(mid, listOf(old), 25.0, emptyList()).single().matchesRule)
    }
}
