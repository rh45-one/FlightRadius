package com.flightradius.app.ui.format

import com.flightradius.app.domain.AircraftObservation
import com.flightradius.app.domain.AlertText
import com.flightradius.app.domain.DistanceUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/** Localised notification text for observations (words come from [Words]). */
object AlertFormat {

    /** e.g. "✈ IBE3174 within 25.0 km" */
    fun alertTitle(o: AircraftObservation, unit: DistanceUnit): String =
        Words.get(
            W.ALERT_TITLE, AlertText.displayName(o),
            AlertText.formatDistance(o.effectiveRadiusKm, unit))

    /**
     * e.g. "12.4 km · bearing 045° NE · closing 380 km/h · FL350" — segments are
     * omitted when data is absent.
     */
    fun alertBody(o: AircraftObservation, unit: DistanceUnit): String =
        buildList {
            add(AlertText.formatDistance(o.distanceKm, unit))
            add(
                Words.get(
                    W.ALERT_BEARING, "%03d°".format(o.bearingDeg.roundToInt() % 360),
                    Words.compass(o.bearingDeg)))
            o.closingSpeedKmh?.let {
                add(
                    if (abs(it) < AlertText.STEADY_KMH) Words.get(W.TREND_STEADY)
                    else Words.get(W.ALERT_CLOSING, AlertText.formatSpeed(it, unit)))
            }
            AlertText.formatAltitude(o.altitudeM, unit)?.let { add(it) }
        }.joinToString(" · ")

    /** "IBE3174 · 12.4 km · ▲ approaching" for the persistent notification. */
    fun statusLine(o: AircraftObservation, unit: DistanceUnit): String =
        buildList {
            add(AlertText.displayName(o))
            add(AlertText.formatDistance(o.distanceKm, unit))
            o.closingSpeedKmh?.let {
                add(when {
                    abs(it) < AlertText.STEADY_KMH -> Words.get(W.TREND_STEADY)
                    it >= 0 -> Words.get(W.ARROW_APPROACHING)
                    else -> Words.get(W.ARROW_RECEDING)
                })
            }
        }.joinToString(" · ")
}
