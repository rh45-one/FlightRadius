package com.flightradius.app.domain

data class AlertConfig(
    /** Fraction of radius used for the exit margin (hysteresis). */
    val exitMarginFraction: Double = 0.15,
    /** Minimum exit margin in km regardless of radius size. */
    val minExitMarginKm: Double = 0.5,
    /** Minimum ms between emitted alerts for the same aircraft. */
    val minRealertIntervalMs: Long = 5 * 60_000L,
    /** Observation older than this (by lastContact vs now) is stale. */
    val staleAfterMs: Long = 120_000L,
    /** No fresh data for this long -> reset zone to OUTSIDE. */
    val forgetAfterMs: Long = 10 * 60_000L
)

enum class AlertZone { INSIDE, OUTSIDE }

/**
 * Per-aircraft alert state.
 * [lastSeenMs] = last time a fresh observation was seen (evaluation time).
 */
data class AircraftAlertState(
    val zone: AlertZone = AlertZone.OUTSIDE,
    val lastAlertAtMs: Long? = null,
    val lastSeenMs: Long? = null
)

/** Emitted when an aircraft enters its radius. Carries the observation. */
data class AlertEvent(val observation: AircraftObservation)

data class AlertEvaluation(
    val states: Map<Long, AircraftAlertState>,
    /** Enter-events this cycle, sorted by distance ascending. */
    val alerts: List<AlertEvent>
)

/**
 * Pure proximity state machine. Call once per monitoring cycle.
 *
 * - Enter: distance <= radius while OUTSIDE -> INSIDE (+ AlertEvent unless
 *   snoozed or inside minRealertInterval since the last emitted alert; zone
 *   still becomes INSIDE, lastAlertAt is only touched when an alert fires).
 * - Exit (hysteresis): distance > radius + max(radius*fraction, minMargin)
 *   while INSIDE -> OUTSIDE. Between radius and exit-threshold the zone is
 *   unchanged, so jitter at the boundary never re-alerts.
 * - Stale observation (lastContact older than staleAfterMs vs now) or absent
 *   aircraft: state unchanged, except after forgetAfterMs since lastSeen the
 *   zone resets to OUTSIDE (lastAlertAt preserved).
 */
class ProximityAlertEngine(private val config: AlertConfig = AlertConfig()) {

    fun evaluate(
        nowMs: Long,
        observations: List<AircraftObservation>,
        previous: Map<Long, AircraftAlertState>,
        snoozes: Map<Long, Long>
    ): AlertEvaluation {
        val states = HashMap<Long, AircraftAlertState>(previous)
        val alerts = ArrayList<AlertEvent>()
        val observedIds = HashSet<Long>()

        for (obs in observations) {
            observedIds += obs.aircraftId
            val prev = states[obs.aircraftId] ?: AircraftAlertState()

            val stale = obs.lastContactSec != null &&
                nowMs - obs.lastContactSec * 1000.0 > config.staleAfterMs

            if (stale) {
                states[obs.aircraftId] = forgetIfExpired(prev, nowMs)
                continue
            }

            val radius = obs.effectiveRadiusKm
            val exitThreshold = radius +
                maxOf(radius * config.exitMarginFraction, config.minExitMarginKm)

            var zone = prev.zone
            var lastAlertAt = prev.lastAlertAtMs

            when {
                prev.zone == AlertZone.OUTSIDE && obs.distanceKm <= radius -> {
                    zone = AlertZone.INSIDE
                    val snoozed = snoozes[obs.aircraftId]?.let { nowMs < it } == true
                    val cooled = lastAlertAt == null ||
                        nowMs - lastAlertAt >= config.minRealertIntervalMs
                    if (!snoozed && cooled) {
                        alerts += AlertEvent(obs)
                        lastAlertAt = nowMs
                    }
                }
                prev.zone == AlertZone.INSIDE && obs.distanceKm > exitThreshold -> {
                    zone = AlertZone.OUTSIDE
                }
            }

            states[obs.aircraftId] = AircraftAlertState(
                zone = zone,
                lastAlertAtMs = lastAlertAt,
                lastSeenMs = nowMs
            )
        }

        // Aircraft previously tracked but absent this cycle: keep state,
        // only forgetting once forgetAfterMs has elapsed since last seen.
        for ((id, prev) in previous) {
            if (id !in observedIds) {
                states[id] = forgetIfExpired(prev, nowMs)
            }
        }

        alerts.sortBy { it.observation.distanceKm }
        return AlertEvaluation(states = states, alerts = alerts)
    }

    private fun forgetIfExpired(state: AircraftAlertState, nowMs: Long): AircraftAlertState {
        val lastSeen = state.lastSeenMs ?: return state
        return if (nowMs - lastSeen > config.forgetAfterMs) {
            state.copy(zone = AlertZone.OUTSIDE)
        } else {
            state
        }
    }
}
