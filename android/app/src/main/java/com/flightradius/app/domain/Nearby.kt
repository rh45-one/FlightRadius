package com.flightradius.app.domain

import kotlinx.serialization.Serializable

/** A non-tracked aircraft around the user, built from the airspace query. */
data class NearbyAircraft(
    val icao24: String,
    val callsign: String?,
    val cls: AircraftClass,
    val lat: Double,
    val lon: Double,
    val distanceKm: Double,
    val bearingDeg: Double,
    val altitudeM: Double? = null,
    val velocityMps: Double? = null,
    val trackDeg: Double? = null,
    val verticalRateMps: Double? = null,
    val lastContactSec: Double? = null,
    val registration: String? = null,
    val typecode: String? = null,
    val model: String? = null,
    val matchesRule: Boolean = false
) {
    /** Callsign, else registration, else the transponder address. */
    val displayName: String get() = callsign ?: registration ?: icao24
}

@Serializable
data class AirspaceRule(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val radiusKm: Double,
    val maxAltitudeM: Double? = null,
    /** Empty = any class. */
    val classes: Set<AircraftClass> = emptySet()
) {
    fun matches(distanceKm: Double, altitudeM: Double?, cls: AircraftClass): Boolean =
        distanceKm <= radiusKm &&
            (maxAltitudeM == null || (altitudeM != null && altitudeM <= maxAltitudeM)) &&
            (classes.isEmpty() || cls in classes)

    companion object {
        const val DEFAULT_ID = "low-and-close"

        val DEFAULTS: List<AirspaceRule> = listOf(
            AirspaceRule(
                id = DEFAULT_ID,
                name = "Low and close",
                enabled = false,
                radiusKm = 5.0,
                maxAltitudeM = 1_500.0,
                classes = emptySet()
            )
        )
    }
}

object NearbyBuilder {

    /**
     * Area snapshot -> nearby list. Drops aircraft on the ground, without a
     * position, already tracked, or beyond [radiusKm] (the bbox is a square).
     */
    fun build(
        fix: UserFix,
        states: List<StateVector>,
        trackedIcao24s: Set<String>,
        radiusKm: Double,
        meta: Map<String, AircraftMeta>,
        rules: List<AirspaceRule>
    ): List<NearbyAircraft> {
        val enabled = rules.filter { it.enabled }
        return states.mapNotNull { s ->
            if (s.onGround == true) return@mapNotNull null
            val lat = s.lat ?: return@mapNotNull null
            val lon = s.lon ?: return@mapNotNull null
            if (!lat.isFinite() || !lon.isFinite()) return@mapNotNull null
            if (s.icao24 in trackedIcao24s) return@mapNotNull null
            val distance = Geo.distanceKm(fix.lat, fix.lon, lat, lon)
            if (distance > radiusKm) return@mapNotNull null
            val m = meta[s.icao24]
            val cls = AircraftClassifier.classify(s.category, m)
            val altitude = s.altitudeM?.takeIf { it.isFinite() }
            NearbyAircraft(
                icao24 = s.icao24,
                callsign = s.callsign,
                cls = cls,
                lat = lat,
                lon = lon,
                distanceKm = distance,
                bearingDeg = Geo.initialBearingDeg(fix.lat, fix.lon, lat, lon),
                altitudeM = altitude,
                velocityMps = s.velocityMps,
                trackDeg = s.trackDeg,
                verticalRateMps = s.verticalRateMps,
                lastContactSec = s.lastContactSec?.toDouble(),
                registration = m?.registration,
                typecode = m?.typecode,
                model = m?.model,
                matchesRule = enabled.any { it.matches(distance, altitude, cls) }
            )
        }.sortedWith(compareBy({ it.distanceKm }, { it.icao24 }))
    }
}

/** Re-projects a previous nearby list onto a new fix (used when the area query failed). */
fun NearbyBuilder.refresh(
    fix: UserFix,
    previous: List<NearbyAircraft>,
    radiusKm: Double,
    rules: List<AirspaceRule>
): List<NearbyAircraft> {
    val enabled = rules.filter { it.enabled }
    return previous.mapNotNull { a ->
        val distance = Geo.distanceKm(fix.lat, fix.lon, a.lat, a.lon)
        if (distance > radiusKm) return@mapNotNull null
        a.copy(
            distanceKm = distance,
            bearingDeg = Geo.initialBearingDeg(fix.lat, fix.lon, a.lat, a.lon),
            matchesRule = enabled.any { it.matches(distance, a.altitudeM, a.cls) }
        )
    }.sortedWith(compareBy({ it.distanceKm }, { it.icao24 }))
}

data class AirspaceAlertConfig(
    val exitMarginFraction: Double = 0.15,
    val minExitMarginKm: Double = 0.5,
    val exitAltitudeMarginM: Double = 150.0,
    val cooldownMs: Long = 10 * 60_000L,
    val staleAfterMs: Long = 120_000L,
    val forgetAfterMs: Long = 10 * 60_000L
)

data class NearbyAlertState(
    val inside: Boolean = false,
    val lastAlertAtMs: Long? = null,
    val lastSeenMs: Long? = null
)

data class NearbyAlertEvent(val aircraft: NearbyAircraft, val rule: AirspaceRule)

data class NearbyEvaluation(
    val states: Map<String, NearbyAlertState>,
    /** Enter events this cycle, nearest first. */
    val alerts: List<NearbyAlertEvent>
)

/**
 * Pure enter/exit state machine for airspace rules, keyed by icao24.
 * Mirrors [ProximityAlertEngine]: enter when any enabled rule matches while
 * OUTSIDE; leave only once no rule matches even with hysteresis (distance >
 * radius + max(15 %, 0.5 km) or altitude > max + 150 m); per-aircraft
 * cooldown; global mute; stale/forget semantics for old or absent data.
 */
class AirspaceAlertEngine(private val config: AirspaceAlertConfig = AirspaceAlertConfig()) {

    fun evaluate(
        nowMs: Long,
        nearby: List<NearbyAircraft>,
        rules: List<AirspaceRule>,
        previous: Map<String, NearbyAlertState>,
        mutedUntilMs: Long
    ): NearbyEvaluation {
        val enabled = rules.filter { it.enabled }
        val states = HashMap<String, NearbyAlertState>(previous)
        val alerts = ArrayList<NearbyAlertEvent>()
        val seen = HashSet<String>()
        val muted = nowMs < mutedUntilMs

        for (a in nearby) {
            seen += a.icao24
            val prev = states[a.icao24] ?: NearbyAlertState()
            val stale = a.lastContactSec != null &&
                nowMs - a.lastContactSec * 1000.0 > config.staleAfterMs
            if (stale) {
                states[a.icao24] = forgetIfExpired(prev, nowMs)
                continue
            }

            val entering = enabled.firstOrNull { it.matches(a.distanceKm, a.altitudeM, a.cls) }
            val stillInside = enabled.any { holds(it, a) }

            var inside = prev.inside
            var lastAlert = prev.lastAlertAtMs
            when {
                !prev.inside && entering != null -> {
                    inside = true
                    val cooled = lastAlert == null || nowMs - lastAlert >= config.cooldownMs
                    if (!muted && cooled) {
                        alerts += NearbyAlertEvent(a, entering)
                        lastAlert = nowMs
                    }
                }
                prev.inside && !stillInside -> inside = false
            }
            states[a.icao24] = NearbyAlertState(inside, lastAlert, nowMs)
        }

        for ((id, prev) in previous) {
            if (id !in seen) states[id] = forgetIfExpired(prev, nowMs)
        }
        alerts.sortBy { it.aircraft.distanceKm }
        return NearbyEvaluation(states, alerts)
    }

    /** Hysteresis-extended match: still "inside" this rule. */
    private fun holds(rule: AirspaceRule, a: NearbyAircraft): Boolean {
        val exitKm = rule.radiusKm + maxOf(rule.radiusKm * config.exitMarginFraction, config.minExitMarginKm)
        if (a.distanceKm > exitKm) return false
        val max = rule.maxAltitudeM
        if (max != null) {
            val alt = a.altitudeM ?: return false
            if (alt > max + config.exitAltitudeMarginM) return false
        }
        return rule.classes.isEmpty() || a.cls in rule.classes
    }

    private fun forgetIfExpired(state: NearbyAlertState, nowMs: Long): NearbyAlertState {
        val lastSeen = state.lastSeenMs ?: return state
        return if (nowMs - lastSeen > config.forgetAfterMs) state.copy(inside = false) else state
    }
}
