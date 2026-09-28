package com.flightradius.app.ui.format

import com.flightradius.app.domain.AircraftObservation
import com.flightradius.app.domain.AlertText
import com.flightradius.app.domain.DistanceUnit
import kotlin.math.roundToInt

/**
 * Display formatting for the UI. Pure — JVM-testable. Distance/speed/
 * altitude delegate to [AlertText] so notification and UI text agree.
 */
object Format {

    const val KM_TO_MI = 0.621371

    /** km -> display value in [unit] (unrounded). */
    fun kmToUnit(km: Double, unit: DistanceUnit): Double =
        if (unit == DistanceUnit.MI) km * KM_TO_MI else km

    fun distanceUnitLabel(unit: DistanceUnit): String =
        if (unit == DistanceUnit.KM) "km" else "mi"

    fun distance(km: Double, unit: DistanceUnit): String =
        AlertText.formatDistance(km, unit)

    /** Distance number only ("12.4") for animated/hero displays. */
    fun distanceNumber(km: Double, unit: DistanceUnit): String =
        "%.1f".format(kmToUnit(km, unit))

    fun speed(mps: Double?, unit: DistanceUnit): String? =
        mps?.let { AlertText.formatSpeed(it * 3.6, unit) }

    fun speedKmh(kmh: Double?, unit: DistanceUnit): String? =
        kmh?.let { AlertText.formatSpeed(it, unit) }

    fun altitude(m: Double?, unit: DistanceUnit): String? =
        AlertText.formatAltitude(m, unit)

    /** "045° NE" */
    fun bearing(deg: Double): String =
        "%03d° %s".format(deg.roundToInt() % 360, AlertText.cardinal(deg))

    fun heading(deg: Double?): String? =
        deg?.let { "%03d°".format(it.roundToInt() % 360) }

    fun callsign(o: AircraftObservation): String = AlertText.displayName(o)

    /** "12 s ago" / "3 min ago" / "now" for last-contact/snapshot labels. */
    fun age(nowMs: Long, thenMs: Long): String {
        val sec = ((nowMs - thenMs) / 1000).coerceAtLeast(0)
        return when {
            sec < 2 -> "now"
            sec < 60 -> "$sec s ago"
            sec < 3600 -> "${sec / 60} min ago"
            else -> "${sec / 3600} h ago"
        }
    }

    /** "in 12 s" countdown for next-cycle labels. */
    fun countdown(nowMs: Long, atMs: Long): String {
        val sec = ((atMs - nowMs) / 1000).coerceAtLeast(0)
        return when {
            sec < 60 -> "${sec}s"
            sec < 3600 -> "${sec / 60}m ${sec % 60}s"
            else -> "${sec / 3600}h"
        }
    }

    /** "▲ approaching · 380 km/h" / "▼ receding · …" / "steady" (<5 km/h). */
    fun closing(o: AircraftObservation, unit: DistanceUnit): String? =
        o.closingSpeedKmh?.let { k ->
            when {
                kotlin.math.abs(k) < AlertText.STEADY_KMH -> "steady"
                else -> {
                    val dir = if (k >= 0) "▲ approaching" else "▼ receding"
                    "$dir · ${AlertText.formatSpeed(kotlin.math.abs(k), unit)}"
                }
            }
        }

    /** True when the closing speed is below the "steady" threshold —
     *  callers use a neutral color. */
    fun closingIsSteady(o: AircraftObservation): Boolean =
        o.closingSpeedKmh?.let { kotlin.math.abs(it) < AlertText.STEADY_KMH } == true

    enum class ClosingTrend { APPROACHING, RECEDING, STEADY }

    /** Stat-slot form of the closing indicator: direction word + bare speed. */
    data class ClosingParts(
        val direction: String,
        val speed: String,
        val trend: ClosingTrend
    )

    fun closingParts(o: AircraftObservation, unit: DistanceUnit): ClosingParts? =
        o.closingSpeedKmh?.let { k ->
            val a = kotlin.math.abs(k)
            val (dir, trend) = when {
                a < AlertText.STEADY_KMH -> "steady" to ClosingTrend.STEADY
                k >= 0 -> "approaching" to ClosingTrend.APPROACHING
                else -> "receding" to ClosingTrend.RECEDING
            }
            ClosingParts(dir, AlertText.formatSpeed(a, unit), trend)
        }
}
