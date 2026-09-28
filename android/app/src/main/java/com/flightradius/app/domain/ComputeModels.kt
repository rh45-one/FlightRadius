package com.flightradius.app.domain

/**
 * Domain-friendly, fully-nullable view of one backend distance result
 * (a mapped `DistanceResult` DTO). All fields may be absent/malformed;
 * [SnapshotBuilder] guards accordingly.
 */
data class ComputeResultEntry(
    val callsign: String? = null,
    val icao24: String? = null,
    val distanceKm: Double? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val altitudeM: Double? = null,
    val velocityMps: Double? = null,
    val headingDeg: Double? = null,
    /** Unix seconds. */
    val lastContactSec: Double? = null
)

data class GroupOutcome(
    val groupName: String,
    val closest: ComputeResultEntry?,
    val membersRanked: List<ComputeResultEntry>,
    val missing: List<String>
)

/** Domain view of a POST /api/distance/compute response. */
data class ComputeOutcome(
    val results: List<ComputeResultEntry>,
    val missing: List<String>,
    val closest: ComputeResultEntry?,
    val groups: List<GroupOutcome>
)
