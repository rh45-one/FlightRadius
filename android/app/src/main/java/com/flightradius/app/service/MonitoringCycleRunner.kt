package com.flightradius.app.service

import com.flightradius.app.data.api.ApiError
import com.flightradius.app.data.api.ApiResult
import com.flightradius.app.data.api.LocalNetworkGuard
import com.flightradius.app.data.api.openSkyStatusFor
import com.flightradius.app.data.prefs.RuntimeSettings
import com.flightradius.app.data.prefs.SettingsRepository
import com.flightradius.app.data.repo.AircraftRepository
import com.flightradius.app.data.repo.FleetRepository
import com.flightradius.app.data.repo.FlightRadiusRepository
import com.flightradius.app.domain.MonitoringSnapshot
import com.flightradius.app.domain.OpenSkyStatus
import com.flightradius.app.domain.SnapshotBuilder
import com.flightradius.app.domain.TimeSource
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
 * local-network + connectivity pre-checks -> compute -> snapshot -> publish.
 * Reused by the service loop and by a UI one-shot refresh; alert evaluation
 * deliberately lives in the service so refreshes never alert.
 */
@Singleton
class MonitoringCycleRunner @Inject constructor(
    private val runtimeSettings: RuntimeSettings,
    private val settingsRepository: SettingsRepository,
    private val aircraftRepository: AircraftRepository,
    private val fleetRepository: FleetRepository,
    private val locationRepository: LocationRepository,
    private val repository: FlightRadiusRepository,
    private val connectivity: ConnectivityMonitor,
    private val localNetworkGuard: LocalNetworkGuard,
    private val stateRepository: MonitoringStateRepository,
    private val time: TimeSource
) {
    companion object {
        private const val TAG = "CycleRunner"
    }

    /** Settings only after DataStore has emitted (never fallback defaults). */
    suspend fun awaitSettings(): com.flightradius.app.data.prefs.AppSettings {
        runtimeSettings.awaitReady()
        return settingsRepository.settings.first()
    }

    suspend fun runCycle(trigger: String): CycleResult {
        // Never hit the fallback URL before DataStore has emitted.
        runtimeSettings.awaitReady()

        val settings = settingsRepository.settings.first()
        val aircraft = aircraftRepository.getAll()
        val fleets = fleetRepository.getAll()

        if (aircraft.isEmpty()) {
            AppLog.d(TAG, "cycle idle", "reason" to IdleReason.NO_AIRCRAFT,
                "trigger" to trigger)
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

        // API 37: fail fast instead of letting the TCP connect time out.
        if (localNetworkGuard.isBlocked()) {
            return CycleResult.Failure(ApiError.LocalNetworkPermissionRequired)
        }

        if (!connectivity.online.value) {
            return CycleResult.Offline
        }

        val t0 = time.nowMs()
        val result = repository.compute(fix, aircraft, fleets)
        val latencyMs = time.nowMs() - t0

        return when (result) {
            is ApiResult.Success -> {
                val snapshot = SnapshotBuilder.build(
                    timeMs = time.nowMs(),
                    fix = fix,
                    results = result.data.results,
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
            is ApiResult.Failure -> {
                AppLog.w(TAG, "compute failed",
                    "err" to result.error.message,
                    "retryable" to result.error.retryable)
                stateRepository.update {
                    it.copy(
                        lastError = result.error,
                        consecutiveFailures = it.consecutiveFailures + 1,
                        openSkyStatus = openSkyStatusFor(result.error),
                        cycleCount = it.cycleCount + 1,
                        lastLatencyMs = latencyMs
                    )
                }
                CycleResult.Failure(result.error)
            }
        }
    }
}
