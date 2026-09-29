package com.flightradius.app.service

import com.flightradius.app.data.api.ApiError
import com.flightradius.app.data.api.ApiResult
import com.flightradius.app.data.api.LocalNetworkGuard
import com.flightradius.app.data.api.openSkyStatusFor
import com.flightradius.app.data.opensky.CreditTracker
import com.flightradius.app.data.prefs.AppSettings
import com.flightradius.app.data.prefs.RuntimeSettings
import com.flightradius.app.data.prefs.SettingsRepository
import com.flightradius.app.data.repo.AircraftRepository
import com.flightradius.app.data.repo.FleetRepository
import com.flightradius.app.data.source.SelectedFlightDataSource
import com.flightradius.app.domain.CreditPlanner
import com.flightradius.app.domain.Fleet
import com.flightradius.app.domain.MonitoringSnapshot
import com.flightradius.app.domain.OpenSkyStatus
import com.flightradius.app.domain.SnapshotBuilder
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
    private val time: TimeSource
) {
    companion object {
        private const val TAG = "CycleRunner"
    }

    /** Settings only after DataStore has emitted (never fallback defaults). */
    suspend fun awaitSettings(): AppSettings {
        runtimeSettings.awaitReady()
        return settingsRepository.settings.first()
    }

    suspend fun runCycle(trigger: String): CycleResult {
        val settings = awaitSettings()
        val aircraft = aircraftRepository.getAll()
        val fleets = fleetRepository.getAll()
        val result = execute(trigger, settings, aircraft, fleets)
        publishPlan(settings, aircraft)
        return result
    }

    private suspend fun execute(
        trigger: String,
        settings: AppSettings,
        aircraft: List<TrackedAircraft>,
        fleets: List<Fleet>
    ): CycleResult {
        if (aircraft.isEmpty()) {
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
        val result = source.fetchTracked(fix, aircraft, fleets)
        val latencyMs = time.nowMs() - t0

        return when (result) {
            is ApiResult.Success -> {
                val snapshot = SnapshotBuilder.build(
                    timeMs = time.nowMs(),
                    fix = fix,
                    results = result.data,
                    tracked = aircraft,
                    fleets = fleets,
                    globalRadiusKm = settings.globalAlertRadiusKm,
                    previous = stateRepository.state.value.lastSnapshot
                )
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
                CycleResult.Success(snapshot, latencyMs)
            }
            is ApiResult.Failure -> recordFailure(result.error, latencyMs)
        }
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
        val perCycle = source.estimatedCreditsPerCycle(aircraft)
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
