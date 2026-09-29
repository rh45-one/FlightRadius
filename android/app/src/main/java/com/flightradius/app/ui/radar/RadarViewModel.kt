package com.flightradius.app.ui.radar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightradius.app.data.api.LocalNetworkGuard
import com.flightradius.app.data.opensky.CreditState
import com.flightradius.app.data.opensky.CreditTracker
import com.flightradius.app.data.prefs.AppSettings
import com.flightradius.app.data.prefs.SettingsRepository
import com.flightradius.app.data.repo.AircraftRepository
import com.flightradius.app.data.repo.FleetRepository
import com.flightradius.app.domain.Fleet
import com.flightradius.app.domain.TrackedAircraft
import com.flightradius.app.domain.UserFix
import com.flightradius.app.location.LocationRepository
import com.flightradius.app.location.LocationStatus
import com.flightradius.app.service.ConnectivityMonitor
import com.flightradius.app.service.MonitoringController
import com.flightradius.app.service.MonitoringCycleRunner
import com.flightradius.app.service.MonitoringStateRepository
import com.flightradius.app.service.MonitoringStatus
import com.flightradius.app.util.log.AppLog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Radar/home state. Exposes monitoring state, settings, location and
 * tracked data as StateFlows.
 *
 * Live preview: while the radar screen is visible AND the service is
 * STOPPED, the VM runs its own cycle loop (owner "ui") so the screen shows
 * live-looking data. Preview cycles never raise alerts — alert evaluation
 * is service-only.
 */
@HiltViewModel
class RadarViewModel @Inject constructor(
    val stateRepository: MonitoringStateRepository,
    private val settingsRepository: SettingsRepository,
    private val aircraftRepository: AircraftRepository,
    private val fleetRepository: FleetRepository,
    private val locationRepository: LocationRepository,
    private val cycleRunner: MonitoringCycleRunner,
    val controller: MonitoringController,
    val connectivity: ConnectivityMonitor,
    creditTracker: CreditTracker,
    val localNetworkGuard: LocalNetworkGuard
) : ViewModel() {

    companion object {
        private const val TAG = "RadarVM"
        private const val UI_LOCATION_OWNER = "ui"
    }

    val monitoringState: StateFlow<com.flightradius.app.service.MonitoringState> =
        stateRepository.state

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val aircraft: StateFlow<List<TrackedAircraft>> = aircraftRepository.aircraft
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val fleets: StateFlow<List<Fleet>> = fleetRepository.fleets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val locationStatus: StateFlow<LocationStatus> = locationRepository.status
    val fix: StateFlow<UserFix?> = locationRepository.fix
    val online: StateFlow<Boolean> = connectivity.online
    val credits: StateFlow<CreditState> = creditTracker.state

    private var previewJob: Job? = null
    @Volatile private var screenVisible = false

    /** Called from the screen's lifecycle — starts/stops the preview loop. */
    fun onScreenVisible(visible: Boolean) {
        screenVisible = visible
        if (visible) startPreviewIfStopped() else stopPreview()
    }

    private fun startPreviewIfStopped() {
        if (previewJob != null) return
        previewJob = viewModelScope.launch {
            var acquired = false
            try {
                while (true) {
                    val status = stateRepository.state.value.status
                    val serviceActive = status != MonitoringStatus.STOPPED
                    if (!serviceActive && screenVisible) {
                        if (!acquired) {
                            locationRepository.acquire(UI_LOCATION_OWNER)
                            locationRepository.refreshIfNeeded()
                            acquired = true
                        }
                        cycleRunner.runCycle("ui-preview")
                        // Same credit-aware interval the service would use.
                        val intervalSec = stateRepository.state.value.plannedIntervalSec
                            ?: settingsRepository.settings.first().monitoringIntervalSec
                        delay(intervalSec * 1000L)
                    } else {
                        if (acquired) {
                            locationRepository.release(UI_LOCATION_OWNER)
                            acquired = false
                        }
                        delay(1000)
                    }
                }
            } finally {
                if (acquired) locationRepository.release(UI_LOCATION_OWNER)
            }
        }
    }

    private fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
        locationRepository.release(UI_LOCATION_OWNER)
    }

    /** One-shot refresh for the error card's Retry. */
    fun retryNow() {
        viewModelScope.launch {
            locationRepository.acquire(UI_LOCATION_OWNER)
            try {
                locationRepository.refreshIfNeeded()
                cycleRunner.runCycle("ui-retry")
            } finally {
                locationRepository.release(UI_LOCATION_OWNER)
            }
        }
    }

    fun refreshLocation() = locationRepository.refreshIfNeeded()

    fun pause() = controller.pause()
    fun resume() = controller.resume()
    fun stop() = controller.stop()
    fun snooze(aircraftId: Long, minutes: Long) = controller.snooze(aircraftId, minutes)
    fun dismissAlert() = controller.dismissAlert()

    /** aircraftId -> snoozed-until ms (for card badges). */
    val snoozes = stateRepository.snoozes

    override fun onCleared() {
        stopPreview()
        AppLog.d(TAG, "cleared")
    }
}
