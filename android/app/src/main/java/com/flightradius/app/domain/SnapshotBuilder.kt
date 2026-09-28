package com.flightradius.app.domain

/**
 * Builds a [MonitoringSnapshot] from raw compute results plus the local
 * tracked list/fleets. Pure Kotlin — no Android dependencies.
 *
 * Matching rules:
 * - CALLSIGN aircraft match `result.callsign` (case-insensitive, upper).
 * - ICAO24 aircraft match `result.icao24` (case-insensitive, lower).
 * - If two tracked entries resolve to the same aircraft (same icao24), the
 *   earliest-created one keeps the observation; the other is silently merged
 *   (it is neither ranked nor reported as no-data — it *has* data).
 * - Results with non-finite distance/lat/lon are skipped (treated as no data
 *   for the tracked entry).
 *
 * Speed/heading come from the backend fields when present, otherwise are
 * derived from the previous snapshot's position for the same aircraft.
 * Closing speed = (prevDistance - curDistance)/dt in km/h (positive =
 * approaching); dt uses lastContact deltas when both sides have one, else
 * the snapshot-time delta; null when there is no previous or dt < 1s.
 */
object SnapshotBuilder {

    fun build(
        timeMs: Long,
        fix: UserFix,
        results: List<ComputeResultEntry>,
        tracked: List<TrackedAircraft>,
        fleets: List<Fleet>,
        globalRadiusKm: Double,
        previous: MonitoringSnapshot? = null
    ): MonitoringSnapshot {
        val emittedIcao24 = HashSet<String>()
        val observations = ArrayList<AircraftObservation>()
        val noData = ArrayList<TrackedAircraft>()

        // Identifiers are unique per tracked entry, and a shared result is
        // legal (callsign + icao24 entries can resolve to the same aircraft);
        // the emittedIcao24 check below merges those silently.
        val sortedTracked = tracked.sortedBy { it.createdAt }

        for (aircraft in sortedTracked) {
            val result = results.firstOrNull { matches(it, aircraft) }

            if (result == null || !isUsable(result)) {
                noData += aircraft
                continue
            }

            val icao = result.icao24?.lowercase()
            if (icao != null && !emittedIcao24.add(icao)) {
                // Same physical aircraft already emitted via another tracked
                // entry — silently merge (it is not missing).
                continue
            }

            val prevObs = findPrevious(previous, aircraft.id, icao)
            val prevTimeMs = previous?.timeMs

            // dt in seconds: prefer lastContact deltas, else snapshot delta.
            val dtSec = if (
                result.lastContactSec != null && prevObs?.lastContactSec != null
            ) {
                result.lastContactSec - prevObs.lastContactSec
            } else if (prevTimeMs != null) {
                (timeMs - prevTimeMs) / 1000.0
            } else {
                null
            }

            val derived = if (prevObs != null && dtSec != null && dtSec >= 1.0) {
                Geo.deriveSpeedHeading(
                    TimedPosition(prevObs.lat, prevObs.lon, prevObs.lastContactSec ?: prevTimeMs!! / 1000.0),
                    TimedPosition(result.lat!!, result.lon!!, result.lastContactSec ?: timeMs / 1000.0)
                )
            } else {
                null
            }

            val closingSpeedKmh = if (prevObs != null && dtSec != null && dtSec >= 1.0) {
                (prevObs.distanceKm - result.distanceKm!!) / dtSec * 3600.0
            } else {
                null
            }

            observations += AircraftObservation(
                aircraftId = aircraft.id,
                callsign = result.callsign?.uppercase(),
                icao24 = icao,
                distanceKm = result.distanceKm!!,
                lat = result.lat!!,
                lon = result.lon!!,
                altitudeM = result.altitudeM?.takeIf { it.isFinite() },
                velocityMps = result.velocityMps ?: derived?.speedMps,
                headingDeg = result.headingDeg ?: derived?.headingDeg,
                lastContactSec = result.lastContactSec,
                bearingDeg = Geo.initialBearingDeg(fix.lat, fix.lon, result.lat, result.lon),
                closingSpeedKmh = closingSpeedKmh,
                effectiveRadiusKm = effectiveRadiusKm(aircraft, fleets, globalRadiusKm)
            )
        }

        val ranked = observations.sortedWith(
            compareBy({ it.distanceKm }, { it.icao24 ?: it.callsign ?: "" }, { it.aircraftId })
        )

        val noDataIds = noData.map { it.id }.toSet()

        val fleetStatuses = fleets.map { fleet ->
            // Members in the same order as `ranked` (distance ascending).
            val members = ranked.filter { it.aircraftId in fleet.memberIds }
            val missing = fleet.memberIds
                .mapNotNull { memberId -> sortedTracked.find { it.id == memberId } }
                .filter { it.id in noDataIds }
            FleetStatus(
                fleet = fleet,
                closest = members.firstOrNull(),
                membersRanked = members,
                missing = missing
            )
        }

        return MonitoringSnapshot(
            timeMs = timeMs,
            fix = fix,
            ranked = ranked,
            noData = noData,
            fleets = fleetStatuses,
            closest = ranked.firstOrNull()
        )
    }

    private fun matches(result: ComputeResultEntry, aircraft: TrackedAircraft): Boolean =
        when (aircraft.type) {
            IdentifierType.CALLSIGN ->
                result.callsign != null &&
                    result.callsign.trim().uppercase() == aircraft.identifier
            IdentifierType.ICAO24 ->
                result.icao24 != null &&
                    result.icao24.trim().lowercase() == aircraft.identifier
        }

    private fun isUsable(result: ComputeResultEntry): Boolean =
        result.distanceKm != null && result.distanceKm.isFinite() &&
            result.lat != null && result.lat.isFinite() &&
            result.lon != null && result.lon.isFinite()

    private fun findPrevious(
        previous: MonitoringSnapshot?,
        aircraftId: Long,
        icao24: String?
    ): AircraftObservation? {
        val ranked = previous?.ranked ?: return null
        if (icao24 != null) {
            ranked.firstOrNull { it.icao24 == icao24 }?.let { return it }
        }
        return ranked.firstOrNull { it.aircraftId == aircraftId }
    }
}
