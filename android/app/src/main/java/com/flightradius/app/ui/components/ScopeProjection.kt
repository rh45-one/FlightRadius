package com.flightradius.app.ui.components

import com.flightradius.app.domain.NearbyAircraft
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Nearby traffic shown as bearing markers on the dial rim. */
data class DialScope(
    val nearby: List<NearbyAircraft>,
    /** Skipped (it is the hero shown in the centre). */
    val excludeIcao24: String? = null
)

/** Offset from the dial centre in the same unit as the ring radius (y grows downward). */
data class ScopeBlip(val dx: Float, val dy: Float, val matchesRule: Boolean)

/** Pure math for the rim markers: north-up, placed by bearing only. */
object ScopeProjection {

    const val MAX_BLIPS = 20

    /** Markers sit on a track just inside the ring. */
    const val RIM_FRACTION = 0.88f

    /** Markers this close (degrees) to the hero's arrow are dropped. */
    const val HERO_CLEARANCE_DEG = 8.0

    fun rimOffset(bearingDeg: Double, ringRadius: Float): Pair<Float, Float> {
        val r = RIM_FRACTION * ringRadius
        val b = Math.toRadians(bearingDeg)
        return (r * sin(b)).toFloat() to (-r * cos(b)).toFloat()
    }

    /** Smallest angle between two bearings, 0..180. */
    fun angularDistance(a: Double, b: Double): Double {
        val d = abs(((a - b) % 360.0 + 360.0) % 360.0)
        return if (d > 180.0) 360.0 - d else d
    }

    /**
     * Up to [MAX_BLIPS] markers, nearest aircraft first, skipping the excluded
     * aircraft and anything within [HERO_CLEARANCE_DEG] of [heroBearingDeg].
     */
    fun markers(scope: DialScope, ringRadius: Float, heroBearingDeg: Double?): List<ScopeBlip> =
        scope.nearby
            .asSequence()
            .filter { it.icao24 != scope.excludeIcao24 }
            .filter { heroBearingDeg == null || angularDistance(it.bearingDeg, heroBearingDeg) > HERO_CLEARANCE_DEG }
            .sortedBy { it.distanceKm }
            .take(MAX_BLIPS)
            .map { a ->
                val (dx, dy) = rimOffset(a.bearingDeg, ringRadius)
                ScopeBlip(dx, dy, a.matchesRule)
            }
            .toList()
}
