package com.flightradius.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotBuilderTest {

    private val fix = UserFix(50.0, 8.0, 10.0, 1_000_000L, LocationSource.GPS)

    private fun ac(
        id: Long,
        ident: String,
        type: IdentifierType = IdentifierType.CALLSIGN,
        createdAt: Long = id
    ) = TrackedAircraft(id, ident, type, createdAt = createdAt)

    private fun res(
        callsign: String? = null,
        icao24: String? = null,
        distance: Double? = 10.0,
        lat: Double? = 50.1,
        lon: Double? = 8.1,
        lastContact: Double? = null
    ) = ComputeResultEntry(
        callsign = callsign, icao24 = icao24, distanceKm = distance,
        lat = lat, lon = lon, lastContactSec = lastContact
    )

    @Test
    fun `matches callsign aircraft case-insensitively`() {
        val s = SnapshotBuilder.build(
            2_000_000L, fix,
            results = listOf(res(callsign = "dlh123")),
            tracked = listOf(ac(1, "DLH123")),
            fleets = emptyList(), globalRadiusKm = 25.0
        )
        assertEquals(1, s.ranked.size)
        assertEquals("DLH123", s.ranked[0].callsign)
        assertTrue(s.noData.isEmpty())
    }

    @Test
    fun `matches icao24 aircraft lowercase`() {
        val s = SnapshotBuilder.build(
            2_000_000L, fix,
            results = listOf(res(callsign = "DLH123", icao24 = "ABCDEF")),
            tracked = listOf(ac(1, "abcdef", IdentifierType.ICAO24)),
            fleets = emptyList(), globalRadiusKm = 25.0
        )
        assertEquals("abcdef", s.ranked[0].icao24)
    }

    @Test
    fun `dedupes two tracked entries resolving to same icao24`() {
        val tracked = listOf(
            ac(1, "DLH123", IdentifierType.CALLSIGN, createdAt = 10),
            ac(2, "abcdef", IdentifierType.ICAO24, createdAt = 20)
        )
        val s = SnapshotBuilder.build(
            2_000_000L, fix,
            results = listOf(res(callsign = "DLH123", icao24 = "ABCDEF")),
            tracked = tracked, fleets = emptyList(), globalRadiusKm = 25.0
        )
        assertEquals(1, s.ranked.size)
        assertEquals(1L, s.ranked[0].aircraftId) // first by createdAt wins
        assertTrue(s.noData.isEmpty())           // merged, not missing
    }

    @Test
    fun `ranks ascending with identifier tie-break`() {
        val s = SnapshotBuilder.build(
            2_000_000L, fix,
            results = listOf(
                res(callsign = "BBB200", distance = 10.0),
                res(callsign = "AAA100", distance = 10.0),
                res(callsign = "CCC300", distance = 5.0)
            ),
            tracked = listOf(ac(1, "AAA100"), ac(2, "BBB200"), ac(3, "CCC300")),
            fleets = emptyList(), globalRadiusKm = 25.0
        )
        assertEquals(listOf("CCC300", "AAA100", "BBB200"), s.ranked.map { it.callsign })
        assertEquals("CCC300", s.closest?.callsign)
    }

    @Test
    fun `unresolved tracked aircraft land in noData`() {
        val s = SnapshotBuilder.build(
            2_000_000L, fix,
            results = emptyList(),
            tracked = listOf(ac(1, "AAA100")),
            fleets = emptyList(), globalRadiusKm = 25.0
        )
        assertEquals(listOf(1L), s.noData.map { it.id })
        assertNull(s.closest)
    }

    @Test
    fun `non-finite results are skipped`() {
        val s = SnapshotBuilder.build(
            2_000_000L, fix,
            results = listOf(
                res(callsign = "AAA100", distance = Double.NaN),
                res(callsign = "BBB200", distance = 10.0, lat = Double.POSITIVE_INFINITY),
                res(callsign = "CCC300", distance = 10.0)
            ),
            tracked = listOf(ac(1, "AAA100"), ac(2, "BBB200"), ac(3, "CCC300")),
            fleets = emptyList(), globalRadiusKm = 25.0
        )
        assertEquals(listOf("CCC300"), s.ranked.map { it.callsign })
        assertEquals(setOf(1L, 2L), s.noData.map { it.id }.toSet())
    }

    @Test
    fun `closing speed positive when approaching`() {
        val prev = MonitoringSnapshot(
            timeMs = 990_000L, fix = fix,
            ranked = listOf(
                AircraftObservation(1, "AAA100", null, 20.0, 50.1, 8.1,
                    lastContactSec = 990.0, bearingDeg = 0.0, effectiveRadiusKm = 25.0)
            ),
            noData = emptyList(), fleets = emptyList(), closest = null
        )
        val s = SnapshotBuilder.build(
            1_000_000L, fix,
            results = listOf(res(callsign = "AAA100", distance = 10.0, lastContact = 1000.0)),
            tracked = listOf(ac(1, "AAA100")),
            fleets = emptyList(), globalRadiusKm = 25.0, previous = prev
        )
        // (20 - 10) km over 10s = 3600 km/h approaching
        assertEquals(3600.0, s.ranked[0].closingSpeedKmh!!, 1e-6)
    }

    @Test
    fun `closing speed negative when receding`() {
        val prev = MonitoringSnapshot(
            timeMs = 990_000L, fix = fix,
            ranked = listOf(
                AircraftObservation(1, "AAA100", null, 10.0, 50.1, 8.1,
                    lastContactSec = 990.0, bearingDeg = 0.0, effectiveRadiusKm = 25.0)
            ),
            noData = emptyList(), fleets = emptyList(), closest = null
        )
        val s = SnapshotBuilder.build(
            1_000_000L, fix,
            results = listOf(res(callsign = "AAA100", distance = 20.0, lastContact = 1000.0)),
            tracked = listOf(ac(1, "AAA100")),
            fleets = emptyList(), globalRadiusKm = 25.0, previous = prev
        )
        assertTrue(s.ranked[0].closingSpeedKmh!! < 0)
    }

    @Test
    fun `speed and heading fall back to derived motion`() {
        val prev = MonitoringSnapshot(
            timeMs = 990_000L, fix = fix,
            ranked = listOf(
                AircraftObservation(1, "AAA100", null, 10.0, 50.0, 8.0,
                    lastContactSec = 990.0, bearingDeg = 0.0, effectiveRadiusKm = 25.0)
            ),
            noData = emptyList(), fleets = emptyList(), closest = null
        )
        val s = SnapshotBuilder.build(
            1_000_000L, fix,
            results = listOf(
                res(callsign = "AAA100", distance = 10.0, lat = 50.1, lon = 8.0,
                    lastContact = 1000.0) // no velocity/heading fields
            ),
            tracked = listOf(ac(1, "AAA100")),
            fleets = emptyList(), globalRadiusKm = 25.0, previous = prev
        )
        val obs = s.ranked[0]
        assertTrue(obs.velocityMps!! > 1000.0)     // ~11.1 km in 10 s
        assertEquals(0.0, obs.headingDeg!!, 5.0)   // due north
    }

    @Test
    fun `backend speed wins over derived`() {
        val prev = MonitoringSnapshot(
            timeMs = 990_000L, fix = fix,
            ranked = listOf(
                AircraftObservation(1, "AAA100", null, 10.0, 50.0, 8.0,
                    lastContactSec = 990.0, bearingDeg = 0.0, effectiveRadiusKm = 25.0)
            ),
            noData = emptyList(), fleets = emptyList(), closest = null
        )
        val s = SnapshotBuilder.build(
            1_000_000L, fix,
            results = listOf(
                res(callsign = "AAA100", distance = 10.0, lastContact = 1000.0)
                    .copy(velocityMps = 250.0, headingDeg = 123.0)
            ),
            tracked = listOf(ac(1, "AAA100")),
            fleets = emptyList(), globalRadiusKm = 25.0, previous = prev
        )
        assertEquals(250.0, s.ranked[0].velocityMps!!, 1e-9)
        assertEquals(123.0, s.ranked[0].headingDeg!!, 1e-9)
    }

    @Test
    fun `fleet status is computed locally`() {
        val fleet = Fleet(9, "VIP", 0, memberIds = setOf(1, 2, 3))
        val s = SnapshotBuilder.build(
            2_000_000L, fix,
            results = listOf(
                res(callsign = "AAA100", distance = 10.0),
                res(callsign = "BBB200", distance = 5.0)
            ),
            tracked = listOf(ac(1, "AAA100"), ac(2, "BBB200"), ac(3, "ZZZ999")),
            fleets = listOf(fleet), globalRadiusKm = 25.0
        )
        val status = s.fleets.single()
        assertEquals(listOf("BBB200", "AAA100"), status.membersRanked.map { it.callsign })
        assertEquals("BBB200", status.closest?.callsign)
        assertEquals(listOf(3L), status.missing.map { it.id })
    }
}
