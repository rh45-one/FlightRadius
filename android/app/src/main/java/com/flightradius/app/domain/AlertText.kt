package com.flightradius.app.domain

import kotlin.math.roundToInt

/** Human-readable formatting for observations (notifications, lists). */
object AlertText {

    private const val KM_TO_MI = 0.621371
    private const val M_TO_FT = 3.28084
    /** |closing| below this counts as "steady" (no trend arrow). */
    const val STEADY_KMH = 5.0

    private val CARDINALS = arrayOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
    )

    /** 16-point compass label for a bearing in degrees. */
    fun cardinal(bearingDeg: Double): String {
        val idx = ((bearingDeg % 360 + 360) % 360 / 22.5).roundToInt() % 16
        return CARDINALS[idx]
    }

    fun formatDistance(km: Double, unit: DistanceUnit): String = when (unit) {
        DistanceUnit.KM -> "%.1f km".format(km)
        DistanceUnit.MI -> "%.1f mi".format(km * KM_TO_MI)
    }

    fun formatSpeed(kmh: Double, unit: DistanceUnit): String = when (unit) {
        DistanceUnit.KM -> "%d km/h".format(kmh.roundToInt())
        DistanceUnit.MI -> "%d mph".format((kmh * KM_TO_MI).roundToInt())
    }

    /** FL (flight level) when >= ~5000 ft, else altitude in m or ft. */
    fun formatAltitude(altitudeM: Double?, unit: DistanceUnit): String? {
        val m = altitudeM ?: return null
        val ft = m * M_TO_FT
        if (ft >= 5000) return "FL%03d".format((ft / 100).roundToInt())
        return when (unit) {
            DistanceUnit.KM -> "%d m".format(m.roundToInt())
            DistanceUnit.MI -> "%d ft".format(ft.roundToInt())
        }
    }

    fun displayName(o: AircraftObservation): String =
        o.callsign ?: o.icao24 ?: "?"

    /** e.g. "✈ IBE3174 within 25 km" */
    fun alertTitle(o: AircraftObservation, unit: DistanceUnit): String =
        "✈ ${displayName(o)} within ${formatDistance(o.effectiveRadiusKm, unit)}"

    /**
     * e.g. "12.4 km · bearing 045° NE · closing 380 km/h · FL350" —
     * segments are omitted when data is absent.
     */
    fun alertBody(o: AircraftObservation, unit: DistanceUnit): String =
        buildList {
            add(formatDistance(o.distanceKm, unit))
            add("bearing %03d° %s".format(o.bearingDeg.roundToInt() % 360, cardinal(o.bearingDeg)))
            o.closingSpeedKmh?.let {
                add(if (kotlin.math.abs(it) < STEADY_KMH) "steady"
                    else "closing " + formatSpeed(it, unit))
            }
            formatAltitude(o.altitudeM, unit)?.let { add(it) }
        }.joinToString(" · ")

    /**
     * Compact status line for the persistent notification:
     * "IBE3174 · 12.4 km · ▲ approaching" / "▲" approaching, "▼" receding.
     */
    fun statusLine(o: AircraftObservation, unit: DistanceUnit): String =
        buildList {
            add(displayName(o))
            add(formatDistance(o.distanceKm, unit))
            o.closingSpeedKmh?.let {
                add(when {
                    kotlin.math.abs(it) < STEADY_KMH -> "steady"
                    it >= 0 -> "▲ approaching"
                    else -> "▼ receding"
                })
            }
        }.joinToString(" · ")
}
