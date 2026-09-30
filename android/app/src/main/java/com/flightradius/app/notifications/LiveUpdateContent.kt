package com.flightradius.app.notifications

import com.flightradius.app.domain.AircraftAlertState
import com.flightradius.app.domain.AlertZone
import com.flightradius.app.domain.DistanceUnit
import com.flightradius.app.domain.MonitoringSnapshot
import com.flightradius.app.ui.format.Format
import com.flightradius.app.ui.format.Words
import kotlin.math.roundToInt

/**
 * What the promoted "Live Update" shows while at least one tracked aircraft is
 * inside its radius (per the proximity-alert zones). Pure: no Android types.
 */
data class LiveUpdateContent(
    val name: String,
    val distanceKm: Double,
    val bearingDeg: Double,
    val radiusKm: Double,
    val trend: Format.ClosingTrend?,
    /** Other tracked aircraft inside their radius besides [name]. */
    val moreInside: Int,
    /** 0 at the edge of the radius, 100 overhead. */
    val progress: Int,
    /** Status-bar chip text, rounded like the dial ("3 km", "0.4 km"). */
    val shortText: String,
    val distanceText: String,
    val radiusText: String,
    val cardinal: String
) {
    /** "PEGASO1 · 3.0 km NE" */
    val title: String get() = "$name · $distanceText $cardinal"

    companion object {
        fun progressFor(distanceKm: Double, radiusKm: Double): Int =
            if (radiusKm <= 0.0) 0
            else ((1.0 - distanceKm / radiusKm) * 100.0).roundToInt().coerceIn(0, 100)

        fun shortText(km: Double, unit: DistanceUnit): String =
            "${Format.glanceNumber(km, unit)} ${Format.distanceUnitLabel(unit)}"

        fun from(
            snapshot: MonitoringSnapshot?,
            zones: Map<Long, AircraftAlertState>,
            unit: DistanceUnit
        ): LiveUpdateContent? {
            val inside = snapshot?.ranked
                ?.filter { zones[it.aircraftId]?.zone == AlertZone.INSIDE }
                ?.sortedBy { it.distanceKm }
                ?.takeIf { it.isNotEmpty() } ?: return null
            val o = inside.first()
            return LiveUpdateContent(
                name = o.callsign ?: o.icao24 ?: "?",
                distanceKm = o.distanceKm,
                bearingDeg = o.bearingDeg,
                radiusKm = o.effectiveRadiusKm,
                trend = Format.closingParts(o, unit)?.trend,
                moreInside = inside.size - 1,
                progress = progressFor(o.distanceKm, o.effectiveRadiusKm),
                shortText = shortText(o.distanceKm, unit),
                distanceText = Format.distance(o.distanceKm, unit),
                radiusText = shortText(o.effectiveRadiusKm, unit),
                cardinal = Words.compass(o.bearingDeg)
            )
        }
    }
}
