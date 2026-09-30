package com.flightradius.app.ui.detail

import com.flightradius.app.domain.AircraftClass
import com.flightradius.app.domain.AircraftMeta
import com.flightradius.app.domain.AlertText
import com.flightradius.app.domain.MonitoringSnapshot

enum class DetailKind(val route: String) {
    TRACKED("tracked"), NEARBY("nearby");

    companion object {
        fun fromRoute(s: String?) = entries.firstOrNull { it.route == s } ?: NEARBY
    }
}

/** Everything the details screen shows about one aircraft. */
data class DetailData(
    val kind: DetailKind,
    /** "t:<aircraftId>" or "n:<icao24>" (shared-element + map key). */
    val key: String,
    val aircraftId: Long?,
    val name: String,
    val cls: AircraftClass?,
    val distanceKm: Double,
    val bearingDeg: Double,
    val altitudeM: Double?,
    val velocityMps: Double?,
    val headingDeg: Double?,
    val verticalRateMps: Double?,
    val closingSpeedKmh: Double?,
    val radiusKm: Double?,
    val registration: String?,
    val model: String?,
    val operator: String?,
    val icao24: String?,
    val lastContactSec: Double?,
    val matchesRule: Boolean,
    val lastSeenMs: Long
)

object DetailLookup {

    fun key(kind: DetailKind, id: String) = if (kind == DetailKind.TRACKED) "t:$id" else "n:$id"

    /**
     * Finds the aircraft in [snapshot]. A nearby aircraft that has since been
     * tracked (same icao24 in `ranked`) resolves to its tracked data.
     * Null when it is no longer reporting (callers keep the last values).
     */
    fun find(
        kind: DetailKind,
        id: String,
        snapshot: MonitoringSnapshot?,
        meta: AircraftMeta? = null
    ): DetailData? {
        snapshot ?: return null
        val tracked = when (kind) {
            DetailKind.TRACKED -> snapshot.ranked.firstOrNull { it.aircraftId.toString() == id }
            DetailKind.NEARBY -> snapshot.ranked.firstOrNull { it.icao24.equals(id, ignoreCase = true) }
        }
        if (tracked != null) {
            return DetailData(
                kind = DetailKind.TRACKED, key = "t:${tracked.aircraftId}",
                aircraftId = tracked.aircraftId, name = AlertText.displayName(tracked),
                cls = meta?.cls?.takeIf { it != AircraftClass.UNKNOWN },
                distanceKm = tracked.distanceKm, bearingDeg = tracked.bearingDeg,
                altitudeM = tracked.altitudeM, velocityMps = tracked.velocityMps,
                headingDeg = tracked.headingDeg, verticalRateMps = null,
                closingSpeedKmh = tracked.closingSpeedKmh, radiusKm = tracked.effectiveRadiusKm,
                registration = meta?.registration, model = meta?.model ?: meta?.typecode,
                operator = meta?.operator, icao24 = tracked.icao24,
                lastContactSec = tracked.lastContactSec, matchesRule = false,
                lastSeenMs = snapshot.timeMs
            )
        }
        if (kind == DetailKind.TRACKED) return null
        val a = snapshot.nearby.firstOrNull { it.icao24.equals(id, ignoreCase = true) } ?: return null
        return DetailData(
            kind = DetailKind.NEARBY, key = "n:${a.icao24}", aircraftId = null, name = a.displayName,
            cls = a.cls, distanceKm = a.distanceKm, bearingDeg = a.bearingDeg,
            altitudeM = a.altitudeM, velocityMps = a.velocityMps, headingDeg = a.trackDeg,
            verticalRateMps = a.verticalRateMps, closingSpeedKmh = null, radiusKm = null,
            registration = a.registration ?: meta?.registration,
            model = a.model ?: meta?.model ?: a.typecode ?: meta?.typecode,
            operator = meta?.operator, icao24 = a.icao24, lastContactSec = a.lastContactSec,
            matchesRule = a.matchesRule, lastSeenMs = snapshot.timeMs
        )
    }
}
