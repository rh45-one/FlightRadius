package com.flightradius.app.data.source

import com.flightradius.app.data.api.ApiResult
import com.flightradius.app.domain.ComputeResultEntry
import com.flightradius.app.domain.Fleet
import com.flightradius.app.domain.TrackedAircraft
import com.flightradius.app.domain.UserFix

/**
 * Provider of live flight data for the monitoring loop and the UI. Two
 * implementations: [OpenSkyFlightSource] (direct) and [BackendFlightSource]
 * (self-hosted proxy); [SelectedFlightDataSource] routes to the one chosen
 * in settings. No implementation throws except for cancellation.
 */
interface FlightDataSource {

    /**
     * Positions of the tracked [aircraft] with distances from [fix]. Aircraft
     * without live data are simply absent from the result.
     */
    suspend fun fetchTracked(
        fix: UserFix,
        aircraft: List<TrackedAircraft>,
        fleets: List<Fleet>
    ): ApiResult<List<ComputeResultEntry>>

    /** Which of the normalized [callsigns] are currently broadcasting. */
    suspend fun liveCallsigns(callsigns: List<String>): ApiResult<Set<String>>

    /** Whether the transponder [icao24] is currently reporting. */
    suspend fun isIcao24Live(icao24: String): ApiResult<Boolean>

    /** OpenSky credits one regular monitoring cycle is expected to cost. */
    fun estimatedCreditsPerCycle(aircraft: List<TrackedAircraft>): Int
}
