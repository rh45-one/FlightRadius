package com.flightradius.app.data.source

import com.flightradius.app.data.api.ApiResult
import com.flightradius.app.data.prefs.DataSource
import com.flightradius.app.data.prefs.RuntimeSettings
import com.flightradius.app.domain.ComputeResultEntry
import com.flightradius.app.domain.Fleet
import com.flightradius.app.domain.TrackedAircraft
import com.flightradius.app.domain.UserFix
import javax.inject.Inject
import javax.inject.Singleton

/** Routes every call to the data source currently selected in settings. */
@Singleton
class SelectedFlightDataSource @Inject constructor(
    private val runtimeSettings: RuntimeSettings,
    private val direct: OpenSkyFlightSource,
    private val backend: BackendFlightSource
) : FlightDataSource {

    val kind: DataSource get() = runtimeSettings.dataSource

    private suspend fun current(): FlightDataSource {
        runtimeSettings.awaitReady()
        return select()
    }

    private fun select(): FlightDataSource = when (runtimeSettings.dataSource) {
        DataSource.DIRECT -> direct
        DataSource.BACKEND -> backend
    }

    override suspend fun fetchTracked(
        fix: UserFix,
        aircraft: List<TrackedAircraft>,
        fleets: List<Fleet>
    ): ApiResult<List<ComputeResultEntry>> = current().fetchTracked(fix, aircraft, fleets)

    override suspend fun liveCallsigns(callsigns: List<String>): ApiResult<Set<String>> =
        current().liveCallsigns(callsigns)

    override suspend fun isIcao24Live(icao24: String): ApiResult<Boolean> =
        current().isIcao24Live(icao24)

    override fun estimatedCreditsPerCycle(aircraft: List<TrackedAircraft>): Int =
        select().estimatedCreditsPerCycle(aircraft)
}
