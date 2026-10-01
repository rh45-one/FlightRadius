package com.flightradius.app.ui.format

import com.flightradius.app.domain.AircraftObservation
import com.flightradius.app.domain.AlertText
import com.flightradius.app.domain.DistanceUnit
import java.util.Locale
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

    /** Radius setting: "10 km" for whole values, one decimal otherwise ("0,5 km"). */
    fun radius(km: Double, unit: DistanceUnit): String {
        val v = Math.round(kmToUnit(km, unit) * 10) / 10.0
        val number = if (v == v.toLong().toDouble()) v.toLong().toString()
        else String.format(java.util.Locale.getDefault(), "%.1f", v)
        return "$number ${distanceUnitLabel(unit)}"
    }

    /** Distance number only ("12.4") for animated/hero displays. */
    fun distanceNumber(km: Double, unit: DistanceUnit): String =
        "%.1f".format(kmToUnit(km, unit))

    /** Big glance number: one decimal below 10, whole number from 10 up. */
    fun glanceNumber(km: Double, unit: DistanceUnit): String {
        val v = kmToUnit(km, unit)
        val oneDecimal = Math.round(v * 10) / 10.0
        return if (oneDecimal < 10.0) String.format(Locale.getDefault(), "%.1f", v)
        else String.format(Locale.getDefault(), "%.0f", v)
    }

    /** Altitude limit for rules: whole metres, or feet for miles users ("1,500 m"). */
    fun altitudeLimit(m: Double, unit: DistanceUnit): String =
        if (unit == DistanceUnit.MI) String.format(Locale.getDefault(), "%,d ft", (m * M_TO_FT).roundToInt())
        else String.format(Locale.getDefault(), "%,d m", m.roundToInt())

    const val M_TO_FT = 3.28084

    fun speed(mps: Double?, unit: DistanceUnit): String? =
        mps?.let { AlertText.formatSpeed(it * 3.6, unit) }

    fun speedKmh(kmh: Double?, unit: DistanceUnit): String? =
        kmh?.let { AlertText.formatSpeed(it, unit) }

    fun altitude(m: Double?, unit: DistanceUnit): String? =
        AlertText.formatAltitude(m, unit)

    /** "045° NE" */
    fun bearing(deg: Double): String =
        "%03d° %s".format(deg.roundToInt() % 360, Words.compass(deg))

    /** "NE 045°" (cardinal first) for compact glance labels. */
    fun bearingShort(deg: Double): String =
        "%s %03d°".format(Words.compass(deg), deg.roundToInt() % 360)

    fun heading(deg: Double?): String? =
        deg?.let { "%03d°".format(it.roundToInt() % 360) }

    fun callsign(o: AircraftObservation): String = AlertText.displayName(o)

    /** "12 s ago" / "3 min ago" / "now" for last-contact/snapshot labels. */
    fun age(nowMs: Long, thenMs: Long): String {
        val sec = ((nowMs - thenMs) / 1000).coerceAtLeast(0)
        return when {
            sec < 2 -> Words.get(W.AGE_NOW)
            sec < 60 -> Words.get(W.AGE_S, sec.toInt())
            sec < 3600 -> Words.get(W.AGE_MIN, (sec / 60).toInt())
            else -> Words.get(W.AGE_H, (sec / 3600).toInt())
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

    /** Compact duration: "45 s", "2 min 30 s", "3 h 5 min". */
    fun duration(totalSec: Long): String {
        val sec = totalSec.coerceAtLeast(0)
        return when {
            sec < 60 -> "$sec s"
            sec < 3600 -> if (sec % 60 == 0L) "${sec / 60} min" else "${sec / 60} min ${sec % 60} s"
            else -> if (sec % 3600 < 60) "${sec / 3600} h" else "${sec / 3600} h ${(sec % 3600) / 60} min"
        }
    }

    /** Coverage estimate: "< 1 h", "7.5 h", "1 day+". */
    fun hours(h: Double): String = when {
        h < 1.0 -> Words.get(W.COVERAGE_LT1)
        h >= 24.0 -> Words.get(W.COVERAGE_DAY)
        else -> String.format(Locale.getDefault(), "%.1f h", h)
            .replace(Regex("[.,]0 h$"), " h")
    }

    /** "▲ approaching · 380 km/h" / "▼ receding · …" / "steady" (<5 km/h). */
    fun closing(o: AircraftObservation, unit: DistanceUnit): String? =
        o.closingSpeedKmh?.let { k ->
            when {
                kotlin.math.abs(k) < AlertText.STEADY_KMH -> Words.get(W.TREND_STEADY)
                else -> {
                    val dir = if (k >= 0) Words.get(W.ARROW_APPROACHING) else Words.get(W.ARROW_RECEDING)
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
                a < AlertText.STEADY_KMH -> Words.get(W.TREND_STEADY) to ClosingTrend.STEADY
                k >= 0 -> Words.get(W.TREND_APPROACHING) to ClosingTrend.APPROACHING
                else -> Words.get(W.TREND_RECEDING) to ClosingTrend.RECEDING
            }
            ClosingParts(dir, AlertText.formatSpeed(a, unit), trend)
        }
}
