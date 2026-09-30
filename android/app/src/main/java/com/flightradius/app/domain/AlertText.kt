package com.flightradius.app.domain

import kotlin.math.roundToInt

/** Human-readable formatting for observations (notifications, lists). */
object AlertText {

    private const val KM_TO_MI = 0.621371
    private const val M_TO_FT = 3.28084
    /** |closing| below this counts as "steady" (no trend arrow). */
    const val STEADY_KMH = 5.0

    /** Index 0..15 into the 16-point compass (N, NNE, NE, …, NNW) for a bearing in degrees. */
    fun compassIndex(bearingDeg: Double): Int =
        ((bearingDeg % 360 + 360) % 360 / 22.5).roundToInt() % 16

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
}
