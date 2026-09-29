package com.flightradius.app.data.source

import com.flightradius.app.data.api.ApiResult
import com.flightradius.app.data.opensky.CreditTracker
import com.flightradius.app.data.opensky.OpenSkyClient
import com.flightradius.app.domain.CallsignResolver
import com.flightradius.app.domain.ComputeResultEntry
import com.flightradius.app.domain.CreditPlanner
import com.flightradius.app.domain.Fleet
import com.flightradius.app.domain.Geo
import com.flightradius.app.domain.IdentifierType
import com.flightradius.app.domain.OpenSkyPricing
import com.flightradius.app.domain.StateVector
import com.flightradius.app.domain.StatesQuery
import com.flightradius.app.domain.TimeSource
import com.flightradius.app.domain.TrackedAircraft
import com.flightradius.app.domain.UserFix
import com.flightradius.app.util.log.AppLog

/**
 * Talks to OpenSky directly and computes distances on-device.
 *
 * Credit strategy per cycle:
 * - known transponders (ICAO24-tracked + resolved callsigns) → one
 *   icao24-filtered request (1 credit per 100 aircraft);
 * - unresolved callsigns → an occasional global snapshot (4 credits),
 *   rate-limited by [CallsignResolver]'s back-off and skipped when the
 *   balance can't afford it.
 */
class OpenSkyFlightSource(
    private val client: OpenSkyClient,
    private val credits: CreditTracker,
    private val resolver: CallsignResolver,
    private val time: TimeSource
) : FlightDataSource {

    override suspend fun fetchTracked(
        fix: UserFix,
        aircraft: List<TrackedAircraft>,
        fleets: List<Fleet>
    ): ApiResult<List<ComputeResultEntry>> {
        val callsigns = aircraft.identifiers(IdentifierType.CALLSIGN)
        val icaoTracked = aircraft.identifiers(IdentifierType.ICAO24)
        resolver.retainOnly(callsigns)
        val now = time.nowMs()
        val unresolved = resolver.unresolved(callsigns)

        val states = if (resolver.shouldSearch(now, unresolved) && canAfford(StatesQuery.Global)) {
            AppLog.d(TAG, "global search", "unresolved" to unresolved.size)
            when (val r = client.states(StatesQuery.Global)) {
                is ApiResult.Success -> r.data.states.also { resolver.learnFromGlobal(now, it, callsigns) }
                is ApiResult.Failure -> return r
            }
        } else {
            val ids = knownTransponders(callsigns, icaoTracked)
            if (ids.isEmpty()) return ApiResult.Success(emptyList())
            when (val r = client.states(StatesQuery.ByIcao24(ids))) {
                is ApiResult.Success -> r.data.states.also { resolver.reconcile(now, it) }
                is ApiResult.Failure -> return r
            }
        }

        val wantedIcao = knownTransponders(callsigns, icaoTracked).toSet()
        return ApiResult.Success(
            states.filter { it.icao24 in wantedIcao || it.callsign in callsigns }
                .mapNotNull { it.toResultEntry(fix) }
        )
    }

    override suspend fun liveCallsigns(callsigns: List<String>): ApiResult<Set<String>> {
        if (callsigns.isEmpty()) return ApiResult.Success(emptySet())
        val wanted = callsigns.toSet()
        return when (val r = client.states(StatesQuery.Global)) {
            is ApiResult.Success -> {
                // Validation doubles as resolution: newly added callsigns
                // can be polled cheaply from the very next cycle.
                resolver.learnFromGlobal(time.nowMs(), r.data.states, wanted)
                ApiResult.Success(r.data.states.mapNotNullTo(HashSet()) { s -> s.callsign?.takeIf { it in wanted } })
            }
            is ApiResult.Failure -> r
        }
    }

    override suspend fun isIcao24Live(icao24: String): ApiResult<Boolean> =
        when (val r = client.states(StatesQuery.ByIcao24(listOf(icao24)))) {
            is ApiResult.Success -> ApiResult.Success(r.data.states.any { it.icao24 == icao24.lowercase() })
            is ApiResult.Failure -> r
        }

    override fun estimatedCreditsPerCycle(aircraft: List<TrackedAircraft>): Int {
        if (aircraft.isEmpty()) return 0
        val ids = knownTransponders(
            aircraft.identifiers(IdentifierType.CALLSIGN),
            aircraft.identifiers(IdentifierType.ICAO24)
        )
        return maxOf(1, OpenSkyPricing.credits(StatesQuery.ByIcao24(ids)))
    }

    private fun knownTransponders(callsigns: Set<String>, icaoTracked: Set<String>): List<String> =
        (icaoTracked + callsigns.mapNotNull(resolver::icao24For)).distinct()

    private fun canAfford(query: StatesQuery): Boolean {
        val remaining = credits.state.value.remaining ?: return true
        return remaining >= OpenSkyPricing.credits(query) + CreditPlanner.RESERVE
    }

    private fun List<TrackedAircraft>.identifiers(type: IdentifierType): Set<String> =
        filter { it.type == type }.mapTo(LinkedHashSet()) { it.identifier }

    private fun StateVector.toResultEntry(fix: UserFix): ComputeResultEntry? {
        val lat = lat ?: return null
        val lon = lon ?: return null
        return ComputeResultEntry(
            callsign = callsign,
            icao24 = icao24,
            distanceKm = Geo.distanceKm(fix.lat, fix.lon, lat, lon),
            lat = lat,
            lon = lon,
            altitudeM = altitudeM,
            velocityMps = velocityMps,
            headingDeg = trackDeg,
            lastContactSec = lastContactSec?.toDouble()
        )
    }

    private companion object {
        const val TAG = "OpenSkySource"
    }
}
