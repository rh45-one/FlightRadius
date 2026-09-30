package com.flightradius.app.widget

import com.flightradius.app.domain.DistanceUnit
import com.flightradius.app.service.MonitoringState
import com.flightradius.app.service.MonitoringStatus
import com.flightradius.app.ui.format.Format
import com.flightradius.app.ui.radar.Glance
import com.flightradius.app.ui.radar.Zone
import com.flightradius.app.ui.radar.glanceOf

enum class HeroState { INSIDE, NEAR, CLEAR, NEARBY, NEARBY_MATCH }

enum class WidgetEmpty { NO_AIRCRAFT, LOADING, NOTHING_INSIDE }

data class WidgetHero(
    val state: HeroState,
    /** "3.0 km" */
    val distanceText: String,
    val name: String,
    /** "NE 040°" */
    val directionText: String,
    /** "400 m" / "FL320", null when unknown. */
    val altitudeText: String?
)

/**
 * Everything the home-screen widget renders. A small value class so the
 * updater can skip redundant pushes by equality. [updated] is an absolute
 * time ("14:32"): widgets don't tick, so relative ages would lie.
 */
data class WidgetContent(
    val monitoring: Boolean,
    val hero: WidgetHero?,
    val empty: WidgetEmpty?,
    val updated: String?
) {
    companion object {
        /** What a freshly started process knows: nothing, monitoring off. */
        val Off = WidgetContent(monitoring = false, hero = null, empty = WidgetEmpty.NO_AIRCRAFT, updated = null)

        /** Same [glanceOf] logic as the Radar dial (tracked nearest, else nearest nearby). */
        fun from(
            state: MonitoringState,
            trackedCount: Int,
            airspaceWatch: Boolean,
            unit: DistanceUnit,
            intervalSec: Int,
            nowMs: Long,
            formatTime: (Long) -> String
        ): WidgetContent {
            val monitoring = state.status != MonitoringStatus.STOPPED
            val snapshot = state.lastSnapshot
            val glance = glanceOf(trackedCount, snapshot, nowMs, intervalSec, airspaceWatch)
            val updated = snapshot?.let { formatTime(it.timeMs) }
            fun dist(km: Double) = "${Format.glanceNumber(km, unit)} ${Format.distanceUnitLabel(unit)}"
            return when (glance) {
                is Glance.Nearest -> WidgetContent(
                    monitoring,
                    WidgetHero(
                        state = when (glance.zone) {
                            Zone.INSIDE -> HeroState.INSIDE
                            Zone.NEAR -> HeroState.NEAR
                            Zone.CLEAR -> HeroState.CLEAR
                        },
                        distanceText = dist(glance.obs.distanceKm),
                        name = Format.callsign(glance.obs),
                        directionText = Format.bearingShort(glance.obs.bearingDeg),
                        altitudeText = Format.altitude(glance.obs.altitudeM, unit)
                    ),
                    null, updated
                )
                is Glance.NearbyNearest -> WidgetContent(
                    monitoring,
                    WidgetHero(
                        state = if (glance.aircraft.matchesRule) HeroState.NEARBY_MATCH else HeroState.NEARBY,
                        distanceText = dist(glance.aircraft.distanceKm),
                        name = glance.aircraft.displayName,
                        directionText = Format.bearingShort(glance.aircraft.bearingDeg),
                        altitudeText = Format.altitude(glance.aircraft.altitudeM, unit)
                    ),
                    null, updated
                )
                Glance.NoAircraft -> WidgetContent(monitoring, null, WidgetEmpty.NO_AIRCRAFT, updated)
                Glance.Loading -> WidgetContent(monitoring, null, WidgetEmpty.LOADING, updated)
                is Glance.NoneAirborne, is Glance.NothingNearby ->
                    WidgetContent(monitoring, null, WidgetEmpty.NOTHING_INSIDE, updated)
            }
        }
    }
}
