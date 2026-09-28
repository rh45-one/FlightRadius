package com.flightradius.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightradius.app.data.api.ApiResult
import com.flightradius.app.data.api.ApiSettingsStatusDto
import com.flightradius.app.data.api.HealthResponseDto
import com.flightradius.app.data.prefs.AppSettings
import com.flightradius.app.data.prefs.GpsAccuracy
import com.flightradius.app.data.prefs.LocationMode
import com.flightradius.app.data.prefs.SettingsRepository
import com.flightradius.app.data.prefs.ThemeMode
import com.flightradius.app.data.repo.FlightRadiusRepository
import com.flightradius.app.data.repo.ImportReport
import com.flightradius.app.domain.AircraftObservation
import com.flightradius.app.domain.AlertEvent
import com.flightradius.app.domain.DistanceUnit
import com.flightradius.app.domain.UserFix
import com.flightradius.app.location.LocationRepository
import com.flightradius.app.location.LocationStatus
import com.flightradius.app.notifications.AlertNotifier
import com.flightradius.app.service.BatteryOptimization
import com.flightradius.app.service.ConnectivityMonitor
import com.flightradius.app.service.MonitoringStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HealthState {
    data object Idle : HealthState
    data object Checking : HealthState
    data class Ok(val health: HealthResponseDto) : HealthState
    data class Failed(val message: String) : HealthState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val repository: FlightRadiusRepository,
    private val locationRepository: LocationRepository,
    private val stateRepository: MonitoringStateRepository,
    private val notifier: AlertNotifier,
    val connectivity: ConnectivityMonitor
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val locationStatus: StateFlow<LocationStatus> = locationRepository.status
    val fix: StateFlow<UserFix?> = locationRepository.fix

    val health = MutableStateFlow<HealthState>(HealthState.Idle)
    val apiStatus = MutableStateFlow<ApiSettingsStatusDto?>(null)
    val apiStatusError = MutableStateFlow<String?>(null)
    val credentialsResult = MutableStateFlow<String?>(null)
    val importResult = MutableStateFlow<ImportReport?>(null)
    val importError = MutableStateFlow<String?>(null)
    val importing = MutableStateFlow(false)

    fun saveBackendUrl(raw: String) = viewModelScope.launch {
        settingsRepository.setBackendBaseUrl(raw.trim().ifBlank { null })
    }
    fun setUnit(unit: DistanceUnit) = viewModelScope.launch {
        settingsRepository.setDistanceUnit(unit)
    }
    fun setInterval(sec: Int) = viewModelScope.launch {
        settingsRepository.setMonitoringIntervalSec(sec)
    }
    fun setGpsAccuracy(v: GpsAccuracy) = viewModelScope.launch {
        settingsRepository.setGpsAccuracy(v)
    }
    fun setLocationMode(v: LocationMode) = viewModelScope.launch {
        settingsRepository.setLocationMode(v)
    }
    fun setManualLocation(lat: Double?, lon: Double?) = viewModelScope.launch {
        settingsRepository.setManualLocation(lat, lon)
    }
    fun setGlobalRadius(km: Double) = viewModelScope.launch {
        settingsRepository.setGlobalAlertRadiusKm(km)
    }
    fun setAlertSound(v: Boolean) = viewModelScope.launch {
        settingsRepository.setAlertSound(v)
    }
    fun setAlertVibration(v: Boolean) = viewModelScope.launch {
        settingsRepository.setAlertVibration(v)
    }
    fun setInAppAlert(v: Boolean) = viewModelScope.launch {
        settingsRepository.setInAppAlertBanner(v)
    }
    fun setRadarMode(v: Boolean) = viewModelScope.launch {
        settingsRepository.setRadarMode(v)
    }
    fun setHighPriority(v: Boolean) = viewModelScope.launch {
        settingsRepository.setHighPriorityMode(v)
    }
    fun setResumeOnBoot(v: Boolean) = viewModelScope.launch {
        settingsRepository.setResumeOnBoot(v)
    }
    fun setThemeMode(v: ThemeMode) = viewModelScope.launch {
        settingsRepository.setThemeMode(v)
    }
    fun setDynamicColor(v: Boolean) = viewModelScope.launch {
        settingsRepository.setDynamicColor(v)
    }
    fun setDebugLogging(v: Boolean) = viewModelScope.launch {
        settingsRepository.setDebugLogging(v)
    }

    fun refreshApiStatus() = viewModelScope.launch {
        apiStatusError.value = null
        when (val r = repository.getApiSettingsStatus()) {
            is ApiResult.Success -> apiStatus.value = r.data
            is ApiResult.Failure -> apiStatusError.value = r.error.message
        }
    }

    fun testConnection() = viewModelScope.launch {
        health.value = HealthState.Checking
        health.value = when (val r = repository.health()) {
            is ApiResult.Success -> HealthState.Ok(r.data)
            is ApiResult.Failure -> HealthState.Failed(r.error.message)
        }
    }

    /** Send credentials to the backend; values never stored locally. */
    fun sendCredentials(
        clientId: String, clientSecret: String,
        username: String, password: String,
        onDone: (ok: Boolean, msg: String?) -> Unit
    ) = viewModelScope.launch {
        val r = repository.updateCredentials(
            clientId = clientId.ifBlank { null },
            clientSecret = clientSecret.ifBlank { null },
            username = username.ifBlank { null },
            password = password.ifBlank { null }
        )
        when (r) {
            is ApiResult.Success -> {
                onDone(true, null)
                refreshApiStatus()
            }
            is ApiResult.Failure -> onDone(false, r.error.message)
        }
    }

    fun clearCredentials(onDone: (ok: Boolean, msg: String?) -> Unit) =
        viewModelScope.launch {
            val r = repository.updateCredentials("", "", "", "")
            when (r) {
                is ApiResult.Success -> {
                    onDone(true, null)
                    refreshApiStatus()
                }
                is ApiResult.Failure -> onDone(false, r.error.message)
            }
        }

    fun importFromBackend() = viewModelScope.launch {
        importing.value = true
        importError.value = null
        when (val r = repository.importFromBackend()) {
            is ApiResult.Success -> importResult.value = r.data
            is ApiResult.Failure -> importError.value = r.error.message
        }
        importing.value = false
    }

    /**
     * Posts a sample proximity notification and shows the in-app sheet with
     * synthetic data (aircraftId -1 never collides with real state).
     */
    fun testAlert() = viewModelScope.launch {
        val s = settings.value
        val event = AlertEvent(
            AircraftObservation(
                aircraftId = -1L,
                callsign = "TEST123",
                icao24 = "abc123",
                distanceKm = s.globalAlertRadiusKm * 0.5,
                lat = 0.0,
                lon = 0.0,
                altitudeM = 10_668.0,
                velocityMps = 240.0,
                headingDeg = 225.0,
                lastContactSec = System.currentTimeMillis() / 1000.0,
                bearingDeg = 45.0,
                closingSpeedKmh = 420.0,
                effectiveRadiusKm = s.globalAlertRadiusKm
            )
        )
        notifier.ensureAlertChannel(s.alertSound, s.alertVibration)
        notifier.postProximityAlert(event, s.distanceUnit)
        stateRepository.emitAlert(event)
    }

    fun refreshLocation() = locationRepository.refreshIfNeeded()

    fun hasLocationPermission() = locationRepository.hasPermission()
    fun hasBackgroundLocation() = locationRepository.hasBackgroundLocationPermission()
    fun batteryOptIgnored(context: Context) = BatteryOptimization.isIgnoring(context)
}
