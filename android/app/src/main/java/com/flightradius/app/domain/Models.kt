package com.flightradius.app.domain

enum class IdentifierType { CALLSIGN, ICAO24 }

enum class LocationSource { GPS, MANUAL }

enum class DistanceUnit { KM, MI }

data class TrackedAircraft(
    val id: Long,
    /** Normalized identifier: uppercase callsign or lowercase icao24. */
    val identifier: String,
    val type: IdentifierType,
    val notes: String? = null,
    val alertRadiusKm: Double? = null,
    val createdAt: Long = 0L
)

/**
 * A named group of aircraft. [memberIds] are [TrackedAircraft.id]s.
 * [alertRadiusKm] overrides the global radius for all members (see
 * [effectiveRadiusKm] — aircraft-level override wins, otherwise the MAXIMUM
 * of member fleet radii applies, i.e. earliest warning).
 */
data class Fleet(
    val id: Long,
    val name: String,
    val colorArgb: Int,
    val alertRadiusKm: Double? = null,
    val memberIds: Set<Long> = emptySet()
)

/** Last known user position. [timeMs] is epoch milliseconds. */
data class UserFix(
    val lat: Double,
    val lon: Double,
    val accuracyM: Double? = null,
    val timeMs: Long,
    val source: LocationSource
)

/**
 * One tracked aircraft resolved to a live position for a snapshot.
 * [lastContactSec] is unix seconds; [closingSpeedKmh] positive = approaching.
 */
data class AircraftObservation(
    val aircraftId: Long,
    val callsign: String?,
    val icao24: String?,
    val distanceKm: Double,
    val lat: Double,
    val lon: Double,
    val altitudeM: Double? = null,
    val velocityMps: Double? = null,
    val headingDeg: Double? = null,
    val lastContactSec: Double? = null,
    val bearingDeg: Double,
    val closingSpeedKmh: Double? = null,
    val effectiveRadiusKm: Double
)

data class FleetStatus(
    val fleet: Fleet,
    val closest: AircraftObservation?,
    val membersRanked: List<AircraftObservation>,
    val missing: List<TrackedAircraft>
)

data class MonitoringSnapshot(
    val timeMs: Long,
    val fix: UserFix,
    /** Observations sorted by distance ascending. */
    val ranked: List<AircraftObservation>,
    /** Tracked aircraft with no usable live position this cycle. */
    val noData: List<TrackedAircraft>,
    val fleets: List<FleetStatus>,
    val closest: AircraftObservation?,
    /** Non-tracked aircraft around the user (airspace watch), nearest first. */
    val nearby: List<NearbyAircraft> = emptyList(),
    /** Radius of the airspace query; null when airspace watch is off. */
    val airspaceRadiusKm: Double? = null,
    /** True when [nearby] was carried over because the area query failed. */
    val nearbyStale: Boolean = false
)
