package com.flightradius.app.domain

import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Radar chirp cadence: interval between ticks based on the closest aircraft
 * relative to its alert radius.
 *
 * - Silent (null) when nothing is tracked or the closest aircraft is more
 *   than 2x its radius away.
 * - Otherwise interpolates 250ms (at the position) .. 4000ms (at 2x radius)
 *   with a r^1.5 curve — faster approach = faster chirp. Monotonic in d.
 */
object RadarCadence {

    const val MIN_INTERVAL_MS = 250L
    const val MAX_INTERVAL_MS = 4000L

    fun intervalMs(closestKm: Double?, radiusKm: Double): Long? {
        if (closestKm == null || radiusKm <= 0.0) return null
        if (closestKm > 2 * radiusKm) return null
        val r = (closestKm / (2 * radiusKm)).coerceIn(0.0, 1.0)
        return (MIN_INTERVAL_MS + (MAX_INTERVAL_MS - MIN_INTERVAL_MS) * r.pow(1.5))
            .roundToLong()
            .coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)
    }
}
