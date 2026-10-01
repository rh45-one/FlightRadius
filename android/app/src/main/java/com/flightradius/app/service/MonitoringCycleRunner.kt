package com.flightradius.app.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.flightradius.app.data.api.ApiError
import com.flightradius.app.data.api.ApiResult
import com.flightradius.app.data.api.LocalNetworkGuard
import com.flightradius.app.data.api.openSkyStatusFor
import com.flightradius.app.data.opensky.CreditTracker
import com.flightradius.app.BuildConfig
import com.flightradius.app.data.aircraftdb.AircraftMetaRepository
import com.flightradius.app.data.opensky.OpenSkyClient
import com.flightradius.app.data.prefs.AppSettings
import com.flightradius.app.data.prefs.RuntimeSettings
import com.flightradius.app.data.prefs.SettingsRepository
import com.flightradius.app.data.repo.AircraftRepository
import com.flightradius.app.data.repo.FleetRepository
import com.flightradius.app.data.source.SelectedFlightDataSource
import com.flightradius.app.domain.BoundingBox
import com.flightradius.app.domain.ComputeResultEntry
import com.flightradius.app.domain.CreditPlanner
import com.flightradius.app.domain.Fleet
import com.flightradius.app.domain.MonitoringSnapshot
import com.flightradius.app.domain.NearbyBuilder
import com.flightradius.app.domain.refresh
import com.flightradius.app.domain.OpenSkyPricing
import com.flightradius.app.domain.OpenSkyStatus
import com.flightradius.app.domain.SnapshotBuilder
import com.flightradius.app.domain.StateVector
import com.flightradius.app.domain.StatesQuery
import com.flightradius.app.domain.TimeSource
import com.flightradius.app.domain.TrackedAircraft
import com.flightradius.app.location.LocationRepository
import com.flightradius.app.util.log.AppLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

enum class IdleReason { NO_AIRCRAFT, WAITING_FOR_LOCATION }

sealed interface CycleResult {
    data class Success(val snapshot: MonitoringSnapshot, val latencyMs: Long) : CycleResult
    data class Idle(val reason: IdleReason) : CycleResult
    data object Offline : CycleResult
    data class Failure(val error: ApiError) : CycleResult
}

/**
 * One monitoring cycle: settings readiness -> tracked data -> fix ->
 * pre-checks -> fetch via the selected data source -> snapshot -> publish,
 * then plans the next interval from the credit balance. Reused by the
 * service loop and UI refreshes; alert evaluation deliberately lives in the
 * service so refreshes never alert.
 */
@Singleton
class MonitoringCycleRunner @Inject constructor(
    private val runtimeSettings: RuntimeSettings,
    private val settingsRepository: SettingsRepository,
    private val aircraftRepository: AircraftRepository,
    private val fleetRepository: FleetRepository,
    private val locationRepository: LocationRepository,
    private val source: SelectedFlightDataSource,
    private val credits: CreditTracker,
    private val connectivity: ConnectivityMonitor,
    private val localNetworkGuard: LocalNetworkGuard,
    private val stateRepository: MonitoringStateRepository,
    private val time: TimeSource,
    private val openSky: OpenSkyClient,
    private val aircraftMeta: AircraftMetaRepository
) {
    companion object {
        private const val TAG = "CycleRunner"
    }

    /** Settings only after DataStore has emitted (never fallback defaults). */
    suspend fun awaitSettings(): AppSettings {
        runtimeSettings.awaitReady()
        return settingsRepository.settings.first()
    }

    /**
     * Callers (service loop, Radar retry, Fleets refresh) may overlap; cycles run
     * one at a time so they never double-spend credits or interleave the
     * "previous snapshot" used for closing speed and heading.
     */
    private val cycleLock = Mutex()

    suspend fun runCycle(trigger: String): CycleResult = cycleLock.withLock {
        val settings = awaitSettings()
        val aircraft = aircraftRepository.getAll()
        val fleets = fleetRepository.getAll()
        val result = execute(trigger, settings, aircraft, fleets)
        publishPlan(settings, aircraft)
        result
    }

    /** Debug-only: re-publishes the injected demo snapshot instead of hitting the network. */
    private fun demoCycle(): CycleResult? {
        if (!BuildConfig.DEBUG || !stateRepository.demoActive.value) return null
        val snap = stateRepository.state.value.lastSnapshot ?: return null
        val refreshed = snap.copy(timeMs = time.nowMs())
        stateRepository.update {
            it.copy(lastSnapshot = refreshed, lastSuccessAtMs = time.nowMs(),
                openSkyStatus = OpenSkyStatus.OK, lastError = null,
                consecutiveFailures = 0, cycleCount = it.cycleCount + 1)
        }
        return CycleResult.Success(refreshed, 0)
    }

    private suspend fun execute(
        trigger: String,
        settings: AppSettings,
        aircraft: List<TrackedAircraft>,
        fleets: List<Fleet>
    ): CycleResult {
        demoCycle()?.let { return it }
        val watch = settings.airspaceWatch
        if (aircraft.isEmpty() && !watch) {
            AppLog.d(TAG, "cycle idle", "reason" to IdleReason.NO_AIRCRAFT, "trigger" to trigger)
            return CycleResult.Idle(IdleReason.NO_AIRCRAFT)
        }

        val fix = locationRepository.currentFix()
        if (fix == null) {
            AppLog.d(TAG, "cycle waiting for fix", "trigger" to trigger)
            return CycleResult.Idle(IdleReason.WAITING_FOR_LOCATION)
        }

        // Recover a location status that fixed itself (permission granted,
        // provider re-enabled, Play services restored).
        locationRepository.refreshIfNeeded()

        // API 37 backend mode: fail fast instead of letting the TCP connect time out.
        if (localNetworkGuard.isBlocked()) {
            return recordFailure(ApiError.LocalNetworkPermissionRequired, latencyMs = null)
        }

        if (!connectivity.online.value) {
            return CycleResult.Offline
        }

        val t0 = time.nowMs()
        val trackedResult: ApiResult<List<ComputeResultEntry>> =
            if (aircraft.isEmpty()) ApiResult.Success(emptyList())
            else source.fetchTracked(fix, aircraft, fleets)
        if (trackedResult is ApiResult.Failure) {
            return recordFailure(trackedResult.error, time.nowMs() - t0)
        }
        val results = (trackedResult as ApiResult.Success).data

        var airspaceStates: List<StateVector>? = null
        var airspaceFailed = false
        if (watch) {
            val box = BoundingBox.around(fix.lat, fix.lon, settings.airspaceRadiusKm)
            when (val area = openSky.states(StatesQuery.ByArea(box))) {
                is ApiResult.Success -> airspaceStates = area.data.states
                is ApiResult.Failure -> {
                    AppLog.w(TAG, "airspace fetch failed", "err" to area.error.message)
                    airspaceFailed = true
                    if (aircraft.isEmpty()) {
                        return recordFailure(area.error, time.nowMs() - t0)
                    }
                }
            }
        }
        val latencyMs = time.nowMs() - t0

        val base = SnapshotBuilder.build(
            timeMs = time.nowMs(),
            fix = fix,
            results = results,
            tracked = aircraft,
            fleets = fleets,
            globalRadiusKm = settings.globalAlertRadiusKm,
            previous = stateRepository.state.value.lastSnapshot
        )
        val snapshot = if (watch && airspaceFailed) {
            // Keep showing the last known traffic, marked as not updated.
            val carried = stateRepository.state.value.lastSnapshot?.nearby.orEmpty()
            base.copy(
                nearby = NearbyBuilder.refresh(
                    fix, carried, settings.airspaceRadiusKm, settings.airspaceRules),
                airspaceRadiusKm = settings.airspaceRadiusKm,
                nearbyStale = true
            )
        } else if (watch) {
            val states = airspaceStates.orEmpty()
            val trackedIds = base.ranked.mapNotNullTo(HashSet()) { it.icao24 }
            val meta = aircraftMeta.lookup(states.map { it.icao24 })
            base.copy(
                nearby = NearbyBuilder.build(
                    fix, states, trackedIds, settings.airspaceRadiusKm, meta,
                    settings.airspaceRules),
                airspaceRadiusKm = settings.airspaceRadiusKm
            )
        } else base
        stateRepository.update {
            it.copy(
                lastSnapshot = snapshot,
                lastError = null,
                lastSuccessAtMs = time.nowMs(),
                consecutiveFailures = 0,
                openSkyStatus = OpenSkyStatus.OK,
                cycleCount = it.cycleCount + 1,
                lastLatencyMs = latencyMs
            )
        }
        return CycleResult.Success(snapshot, latencyMs)
    }

    private fun recordFailure(error: ApiError, latencyMs: Long?): CycleResult.Failure {
        AppLog.w(TAG, "fetch failed", "err" to error.message, "retryable" to error.retryable)
        stateRepository.update {
            it.copy(
                lastError = error,
                consecutiveFailures = it.consecutiveFailures + 1,
                openSkyStatus = openSkyStatusFor(error),
                cycleCount = it.cycleCount + 1,
                lastLatencyMs = latencyMs ?: it.lastLatencyMs
            )
        }
        return CycleResult.Failure(error)
    }

    /** Chooses the next interval from the freshest credit balance. */
    private fun publishPlan(settings: AppSettings, aircraft: List<TrackedAircraft>) {
        val perCycle = OpenSkyPricing.creditsPerCycle(
            trackedCredits = if (aircraft.isEmpty()) 0 else source.estimatedCreditsPerCycle(aircraft),
            airspaceWatch = settings.airspaceWatch,
            airspaceRadiusKm = settings.airspaceRadiusKm
        )
        val interval = CreditPlanner.intervalSec(
            userIntervalSec = settings.monitoringIntervalSec,
            adaptive = settings.adaptiveCredits,
            remaining = credits.state.value.remaining,
            creditsPerCycle = perCycle,
            nowMs = time.nowMs()
        )
        stateRepository.update { it.copy(plannedIntervalSec = interval, creditsPerCycle = perCycle) }
    }
}
