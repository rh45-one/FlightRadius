package com.flightradius.app.domain

/**
 * Resolves the alert radius for a tracked aircraft.
 *
 * Precedence:
 * 1. Per-aircraft override ([TrackedAircraft.alertRadiusKm]) wins if set.
 * 2. Otherwise the MAXIMUM radius among fleets containing the aircraft
 *    (that have a fleet-level radius set). MAX is deliberate: the largest
 *    radius triggers the earliest warning, which is the safe behaviour.
 * 3. Otherwise [globalKm].
 */
fun effectiveRadiusKm(
    aircraft: TrackedAircraft,
    fleets: List<Fleet>,
    globalKm: Double
): Double {
    aircraft.alertRadiusKm?.let { return it }
    val fleetMax = fleets
        .filter { aircraft.id in it.memberIds && it.alertRadiusKm != null }
        .mapNotNull { it.alertRadiusKm }
        .maxOrNull()
    return fleetMax ?: globalKm
}
