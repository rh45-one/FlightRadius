package com.flightradius.app.domain

/**
 * One OpenSky state vector. Only [icao24] is guaranteed; every other field
 * may be absent in real data and must be treated as optional.
 */
data class StateVector(
    /** Lowercase 24-bit transponder address in hex. */
    val icao24: String,
    /** Trimmed, uppercase callsign; null when not broadcast. */
    val callsign: String? = null,
    val originCountry: String? = null,
    /** Unix seconds of the last message received from the transponder. */
    val lastContactSec: Long? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val baroAltitudeM: Double? = null,
    val onGround: Boolean? = null,
    val velocityMps: Double? = null,
    /** True track over ground, degrees clockwise from north. */
    val trackDeg: Double? = null,
    val verticalRateMps: Double? = null,
    val geoAltitudeM: Double? = null,
    val squawk: String? = null,
    /** 0 ADS-B, 1 ASTERIX, 2 MLAT, 3 FLARM. */
    val positionSource: Int? = null,
    /** ADS-B emitter category (only with `extended=1`); 0/1 = unknown. */
    val category: Int? = null
) {
    /** Barometric altitude when known, else geometric. */
    val altitudeM: Double? get() = baroAltitudeM ?: geoAltitudeM
}

/** One `/states/all` response. */
data class StatesSnapshot(
    /** Unix seconds the vectors are associated with. */
    val timeSec: Long?,
    val states: List<StateVector>
)
