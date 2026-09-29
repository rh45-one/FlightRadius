package com.flightradius.app.domain

import kotlin.math.min

/**
 * Maps tracked callsigns to transponder addresses so they can be polled with
 * 1-credit icao24 queries instead of 4-credit global snapshots.
 *
 * OpenSky can't filter by callsign, so learning a mapping needs a global
 * snapshot. Searches for still-unresolved callsigns back off exponentially;
 * a callsign that has never been searched for is looked up immediately.
 * Mappings are dropped when the airframe starts broadcasting a different
 * callsign (new flight) or stops reporting for [Config.forgetMissingMs].
 *
 * Thread-safe; time is passed in explicitly so behaviour is deterministic.
 */
class CallsignResolver(private val config: Config = Config()) {

    data class Config(
        val retryInitialMs: Long = 10 * 60_000L,
        val retryMaxMs: Long = 60 * 60_000L,
        val forgetMissingMs: Long = 30 * 60_000L
    )

    private data class Mapping(val icao24: String, val lastSeenMs: Long)

    private val mappings = HashMap<String, Mapping>()
    private val searched = HashSet<String>()
    private var failedSearches = 0
    private var nextSearchAtMs = 0L

    @Synchronized
    fun icao24For(callsign: String): String? = mappings[callsign]?.icao24

    @Synchronized
    fun unresolved(callsigns: Collection<String>): Set<String> =
        callsigns.filterTo(LinkedHashSet()) { it !in mappings }

    /** Whether a global search for [unresolved] is due now. */
    @Synchronized
    fun shouldSearch(nowMs: Long, unresolved: Set<String>): Boolean =
        unresolved.isNotEmpty() && (unresolved.any { it !in searched } || nowMs >= nextSearchAtMs)

    /**
     * Learns from a global snapshot searched for [wanted] callsigns and
     * schedules the next search if some are still missing.
     */
    @Synchronized
    fun learnFromGlobal(nowMs: Long, states: List<StateVector>, wanted: Collection<String>) {
        val wantedSet = wanted.toSet()
        for (state in states) {
            val callsign = state.callsign ?: continue
            if (callsign in wantedSet) mappings[callsign] = Mapping(state.icao24, nowMs)
        }
        searched += wantedSet
        val stillMissing = wantedSet.any { it !in mappings }
        if (stillMissing) {
            failedSearches++
            val backoff = config.retryInitialMs shl min(failedSearches - 1, 20)
            nextSearchAtMs = nowMs + min(backoff, config.retryMaxMs)
        } else {
            failedSearches = 0
            nextSearchAtMs = 0L
        }
    }

    /**
     * Checks mappings against an icao24 poll: refreshes the ones still
     * flying under their callsign and drops stale or reassigned ones. A
     * dropped callsign counts as never searched, so it is looked up again on
     * the next cycle (likely a new flight on another airframe).
     */
    @Synchronized
    fun reconcile(nowMs: Long, states: List<StateVector>) {
        val byIcao = states.associateBy { it.icao24 }
        val iterator = mappings.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val state = byIcao[entry.value.icao24]
            val reassigned = state?.callsign != null && state.callsign != entry.key
            val vanished = state == null && nowMs - entry.value.lastSeenMs > config.forgetMissingMs
            when {
                reassigned || vanished -> {
                    iterator.remove()
                    searched.remove(entry.key)
                }
                state != null -> entry.setValue(entry.value.copy(lastSeenMs = nowMs))
            }
        }
    }

    /** Forget callsigns the user no longer tracks. */
    @Synchronized
    fun retainOnly(tracked: Set<String>) {
        mappings.keys.retainAll(tracked)
        searched.retainAll(tracked)
    }
}
