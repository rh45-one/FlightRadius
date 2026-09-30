package com.flightradius.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightradius.app.data.prefs.AppSettings
import com.flightradius.app.data.prefs.SettingsRepository
import com.flightradius.app.domain.MonitoringSnapshot
import com.flightradius.app.service.MonitoringStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The map only renders the latest snapshot; it never triggers OpenSky requests. */
@HiltViewModel
class MapViewModel @Inject constructor(
    stateRepository: MonitoringStateRepository,
    settingsRepository: SettingsRepository,
    private val focus: MapFocusRepository
) : ViewModel() {
    val focusRequest: StateFlow<String?> = focus.request
    fun consumeFocus() = focus.consume()


    val snapshot: StateFlow<MonitoringSnapshot?> = stateRepository.state
        .map { it.lastSnapshot }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), stateRepository.state.value.lastSnapshot)

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
}
