package com.flightradius.app.service

import com.flightradius.app.data.api.ApiError
import com.flightradius.app.domain.AircraftObservation
import com.flightradius.app.domain.AlertEvent
import com.flightradius.app.domain.MonitoringSnapshot
import com.flightradius.app.domain.OpenSkyStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update

enum class MonitoringStatus {
    STOPPED, STARTING, RUNNING, PAUSED, DEFERRED_DOZE, OFFLINE,
    WAITING_FOR_LOCATION, ERROR
}

data class MonitoringState(
    val status: MonitoringStatus = MonitoringStatus.STOPPED,
    val lastSnapshot: MonitoringSnapshot? = null,
    val lastError: ApiError? = null,
    val lastSuccessAtMs: Long? = null,
    val consecutiveFailures: Int = 0,
    val nextCycleAtMs: Long? = null,
    val openSkyStatus: OpenSkyStatus = OpenSkyStatus.UNKNOWN,
    val cycleCount: Int = 0,
    val lastLatencyMs: Long? = null,
    val wakeLockHeld: Boolean = false,
    /** Between-cycle heartbeat wakelock (manual mode / no GPS fixes). */
    val heartbeatHeld: Boolean = false,
    val dozing: Boolean = false,
    val highPriority: Boolean = false,
    /** false when the service was started from boot (no audio allowed). */
    val startedFromBackground: Boolean = false
)

/**
 * Single source of truth for monitoring state — observed by the UI without
 * binding to the service. Alert events and snoozes also live here.
 */
@Singleton
class MonitoringStateRepository @Inject constructor() {

    private val _state = MutableStateFlow(MonitoringState())
    val state: StateFlow<MonitoringState> = _state

    /** In-app alert stream (notification sheet). */
    private val _alertEvents = MutableSharedFlow<AlertEvent>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val alertEvents = _alertEvents.asSharedFlow()

    /** Latest un-dismissed alert so the in-app sheet survives process/UI nav. */
    private val _activeAlert = MutableStateFlow<AlertEvent?>(null)
    val activeAlert: StateFlow<AlertEvent?> = _activeAlert

    /** aircraftId -> snoozed-until epoch ms (in-memory only). */
    private val _snoozes = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val snoozes: StateFlow<Map<Long, Long>> = _snoozes

    fun update(transform: (MonitoringState) -> MonitoringState) = _state.update(transform)

    fun set(snapshot: MonitoringState) {
        _state.value = snapshot
    }

    fun emitAlert(event: AlertEvent) {
        _activeAlert.value = event
        _alertEvents.tryEmit(event)
    }

    fun dismissAlert() {
        _activeAlert.value = null
    }

    /** Snooze alerts for [aircraftId] until nowMs + minutes. */
    fun snooze(aircraftId: Long, minutes: Long, nowMs: Long) {
        _snoozes.update { it + (aircraftId to nowMs + minutes * 60_000L) }
        if (_activeAlert.value?.observation?.aircraftId == aircraftId) {
            _activeAlert.value = null
        }
    }

    /** Drop expired snoozes (called opportunistically from the cycle). */
    fun pruneSnoozes(nowMs: Long) {
        _snoozes.update { s -> s.filterValues { until -> until > nowMs } }
    }
}
