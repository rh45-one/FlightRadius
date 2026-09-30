package com.flightradius.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightradius.app.data.prefs.AppSettings
import com.flightradius.app.data.prefs.SettingsRepository
import com.flightradius.app.domain.AirspaceRule
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AirspaceRulesViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private fun mutate(block: (List<AirspaceRule>) -> List<AirspaceRule>) {
        viewModelScope.launch {
            val current = settingsRepository.settings.first().airspaceRules
            settingsRepository.setAirspaceRules(block(current))
        }
    }

    fun setEnabled(id: String, enabled: Boolean) =
        mutate { list -> list.map { if (it.id == id) it.copy(enabled = enabled) else it } }

    /** Inserts or replaces by id. */
    fun save(rule: AirspaceRule) = mutate { list ->
        if (list.any { it.id == rule.id }) list.map { if (it.id == rule.id) rule else it }
        else list + rule
    }

    fun delete(id: String) = mutate { list -> list.filterNot { it.id == id } }

    fun newRule(name: String): AirspaceRule = AirspaceRule(
        id = UUID.randomUUID().toString(),
        name = name,
        enabled = true,
        radiusKm = 10.0,
        maxAltitudeM = null,
        classes = emptySet()
    )
}
