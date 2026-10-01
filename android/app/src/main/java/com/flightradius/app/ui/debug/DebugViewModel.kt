package com.flightradius.app.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightradius.app.BuildConfig
import com.flightradius.app.data.prefs.SettingsRepository
import com.flightradius.app.data.repo.AircraftRepository
import com.flightradius.app.domain.LocationSource
import com.flightradius.app.domain.MonitoringSnapshot
import com.flightradius.app.domain.UserFix
import com.flightradius.app.location.LocationRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.flightradius.app.service.MonitoringStateRepository
import com.flightradius.app.util.log.AppLog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val stateRepository: MonitoringStateRepository,
    private val settingsRepository: SettingsRepository,
    private val locationRepository: LocationRepository,
    private val aircraftRepository: AircraftRepository
) : ViewModel() {
    val monitoringState = stateRepository.state
    val demoActive = stateRepository.demoActive

    /** Publishes synthetic nearby traffic; cycles then re-publish it instead of calling OpenSky. */
    fun injectDemo(stale: Boolean = false) {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val now = System.currentTimeMillis()
            val fix = locationRepository.currentFix()
                ?: settings.manualLat?.let { lat ->
                    settings.manualLon?.let { lon ->
                        UserFix(lat, lon, null, now, LocationSource.MANUAL)
                    }
                }
                ?: UserFix(40.4168, -3.7038, null, now, LocationSource.MANUAL)
            val nearby = DemoTraffic.build(fix, settings.airspaceRadiusKm, settings.airspaceRules)
            val ranked = DemoTraffic.tracked(aircraftRepository.getAll(), settings.globalAlertRadiusKm, fix)
            val snap = MonitoringSnapshot(
                timeMs = now, fix = fix, ranked = ranked.first, noData = ranked.second,
                fleets = emptyList(), closest = ranked.first.firstOrNull(), nearby = nearby,
                airspaceRadiusKm = settings.airspaceRadiusKm, nearbyStale = stale
            )
            stateRepository.update { st ->
                st.copy(lastSnapshot = snap, lastSuccessAtMs = now, lastError = null)
            }
            stateRepository.setDemoActive(true)
        }
    }

    fun clearDemo() {
        if (!BuildConfig.DEBUG) return
        stateRepository.setDemoActive(false)
        stateRepository.update { st ->
            st.copy(lastSnapshot = st.lastSnapshot?.copy(nearby = emptyList()))
        }
    }
    val entries: StateFlow<List<com.flightradius.app.util.log.LogEntry>> =
        AppLog.entries

    fun clear() = AppLog.clear()
}
