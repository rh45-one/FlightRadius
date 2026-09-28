package com.flightradius.app.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A position fix with a timestamp, used for motion derivation.
 * [epochSec] is unix time in seconds (fractional allowed).
 */
data class TimedPosition(val lat: Double, val lon: Double, val epochSec: Double)

/** Ground speed (m/s) and track (deg, 0..360) derived from two positions. */
data class DerivedMotion(val speedMps: Double, val headingDeg: Double)

object Geo {

    const val EARTH_RADIUS_KM = 6371.0

    /** Great-circle distance in km (haversine, R = 6371.0 — same as backend). */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Initial bearing from (lat1, lon1) to (lat2, lon2) in degrees 0..360. */
    fun initialBearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        val deg = Math.toDegrees(atan2(y, x))
        return (deg + 360.0) % 360.0
    }

    /**
     * Derives ground speed (m/s) and track (deg) between two timestamped
     * positions. Returns null when the time delta is < 1s (or non-positive).
     */
    fun deriveSpeedHeading(prev: TimedPosition, cur: TimedPosition): DerivedMotion? {
        val dt = cur.epochSec - prev.epochSec
        if (dt < 1.0) return null
        val distanceM = distanceKm(prev.lat, prev.lon, cur.lat, cur.lon) * 1000.0
        return DerivedMotion(
            speedMps = distanceM / dt,
            headingDeg = initialBearingDeg(prev.lat, prev.lon, cur.lat, cur.lon)
        )
    }
}
