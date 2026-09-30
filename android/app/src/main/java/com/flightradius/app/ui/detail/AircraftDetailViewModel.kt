package com.flightradius.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightradius.app.data.aircraftdb.AircraftMetaRepository
import com.flightradius.app.data.prefs.AppSettings
import com.flightradius.app.data.prefs.SettingsRepository
import com.flightradius.app.data.repo.AircraftRepository
import com.flightradius.app.domain.AircraftMeta
import com.flightradius.app.domain.IdentifierType
import com.flightradius.app.service.MonitoringController
import com.flightradius.app.service.MonitoringStateRepository
import com.flightradius.app.ui.map.MapFocusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [data] holds the last known values; [reporting] is false when the aircraft left the snapshot. */
data class DetailUi(val data: DetailData?, val reporting: Boolean)

@HiltViewModel
class AircraftDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    stateRepository: MonitoringStateRepository,
    settingsRepository: SettingsRepository,
    private val aircraftRepository: AircraftRepository,
    private val metaRepository: AircraftMetaRepository,
    private val controller: MonitoringController,
    private val mapFocus: MapFocusRepository
) : ViewModel() {

    private val kind = DetailKind.fromRoute(savedState["kind"])
    private val id: String = savedState["id"] ?: ""

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _ui = MutableStateFlow(DetailUi(null, reporting = true))
    val ui: StateFlow<DetailUi> = _ui

    private var meta: AircraftMeta? = null
    private var metaLoadedFor: String? = null

    init {
        viewModelScope.launch {
            stateRepository.state.collect { st ->
                val snap = st.lastSnapshot ?: return@collect
                val probe = DetailLookup.find(kind, id, snap)
                val icao = probe?.icao24 ?: _ui.value.data?.icao24
                if (icao != null && metaLoadedFor != icao) {
                    metaLoadedFor = icao
                    meta = metaRepository.lookup(listOf(icao.lowercase()))[icao.lowercase()]
                }
                val found = DetailLookup.find(kind, id, snap, meta)
                _ui.value = if (found != null) DetailUi(found, reporting = true)
                else DetailUi(_ui.value.data, reporting = false)
            }
        }
    }

    /** Tracks this nearby aircraft by ICAO24; the next cycle resolves it as tracked. */
    fun trackThis() {
        val icao = _ui.value.data?.icao24 ?: return
        viewModelScope.launch { aircraftRepository.add(icao, IdentifierType.ICAO24) }
    }

    fun showOnMap() {
        _ui.value.data?.key?.let { mapFocus.focus(it) }
    }

    fun snooze(aircraftId: Long) = controller.snooze(aircraftId, 30)
}
