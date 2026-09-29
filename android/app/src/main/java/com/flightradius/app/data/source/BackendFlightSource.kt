package com.flightradius.app.data.source

import com.flightradius.app.data.api.ApiError
import com.flightradius.app.data.api.ApiResult
import com.flightradius.app.data.repo.FlightRadiusRepository
import com.flightradius.app.domain.ComputeResultEntry
import com.flightradius.app.domain.Fleet
import com.flightradius.app.domain.IdentifierType
import com.flightradius.app.domain.OpenSkyPricing
import com.flightradius.app.domain.StatesQuery
import com.flightradius.app.domain.TrackedAircraft
import com.flightradius.app.domain.UserFix
import javax.inject.Inject
import javax.inject.Singleton

/** Flight data via the self-hosted FlightRadius backend. */
@Singleton
class BackendFlightSource @Inject constructor(
    private val repository: FlightRadiusRepository
) : FlightDataSource {

    override suspend fun fetchTracked(
        fix: UserFix,
        aircraft: List<TrackedAircraft>,
        fleets: List<Fleet>
    ): ApiResult<List<ComputeResultEntry>> =
        when (val r = repository.compute(fix, aircraft, fleets)) {
            is ApiResult.Success -> ApiResult.Success(r.data.results)
            is ApiResult.Failure -> r
        }

    override suspend fun liveCallsigns(callsigns: List<String>): ApiResult<Set<String>> {
        if (callsigns.isEmpty()) return ApiResult.Success(emptySet())
        return when (val r = repository.validateCallsigns(callsigns)) {
            is ApiResult.Success -> ApiResult.Success(
                r.data.results.filter { it.status == "valid" }
                    .mapNotNullTo(HashSet()) { it.callsign?.trim()?.uppercase() }
            )
            is ApiResult.Failure -> r
        }
    }

    override suspend fun isIcao24Live(icao24: String): ApiResult<Boolean> =
        when (val r = repository.lookupIcao24(icao24)) {
            is ApiResult.Success -> ApiResult.Success(true)
            is ApiResult.Failure ->
                if ((r.error as? ApiError.Http)?.code == 404) ApiResult.Success(false) else r
        }

    /**
     * The backend resolves callsigns with a global snapshot (4 credits) and
     * icao24s with one filtered request (1 credit).
     */
    override fun estimatedCreditsPerCycle(aircraft: List<TrackedAircraft>): Int {
        val callsignCost = if (aircraft.any { it.type == IdentifierType.CALLSIGN }) {
            OpenSkyPricing.credits(StatesQuery.Global)
        } else 0
        val icaoCost = OpenSkyPricing.credits(
            StatesQuery.ByIcao24(aircraft.filter { it.type == IdentifierType.ICAO24 }.map { it.identifier })
        )
        return callsignCost + icaoCost
    }
}
