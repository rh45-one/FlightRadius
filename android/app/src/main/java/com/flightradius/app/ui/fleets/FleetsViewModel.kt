package com.flightradius.app.ui.fleets

import com.flightradius.app.ui.format.localized
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightradius.app.data.api.ApiError
import com.flightradius.app.data.repo.AircraftRepository
import com.flightradius.app.data.repo.FleetRepository
import com.flightradius.app.domain.Fleet
import com.flightradius.app.domain.TrackedAircraft
import com.flightradius.app.service.CycleResult
import com.flightradius.app.service.MonitoringCycleRunner
import com.flightradius.app.service.MonitoringStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class FleetsViewModel @Inject constructor(
    private val fleetRepository: FleetRepository,
    private val aircraftRepository: AircraftRepository,
    private val stateRepository: MonitoringStateRepository,
    private val cycleRunner: MonitoringCycleRunner
) : ViewModel() {

    val fleets: StateFlow<List<Fleet>> = fleetRepository.fleets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val aircraft: StateFlow<List<TrackedAircraft>> = aircraftRepository.aircraft
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Latest snapshot: fleet statuses + staleness for the Refresh affordance. */
    val snapshot = stateRepository.state

    /** Manual refresh state for when no fresh snapshot exists. */
    val refreshError = MutableStateFlow<String?>(null)
    val refreshing = MutableStateFlow(false)

    fun createOrUpdate(
        id: Long?,
        name: String,
        colorArgb: Int,
        radiusKm: Double?,
        memberIds: Set<Long>,
        onDone: (ok: Boolean, error: String?) -> Unit
    ) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) { onDone(false, "name"); return@launch }
            val dup = fleets.value.any { it.name.equals(trimmed, true) && it.id != id }
            if (dup) { onDone(false, "duplicate"); return@launch }

            if (id == null) {
                val newId = fleetRepository.getOrCreate(trimmed, colorArgb, radiusKm)
                if (newId == null) { onDone(false, "create"); return@launch }
                for (mid in memberIds) fleetRepository.addMember(newId, mid)
            } else {
                fleetRepository.update(
                    Fleet(id, trimmed, colorArgb, radiusKm,
                        memberIds = fleets.value.find { it.id == id }?.memberIds ?: emptySet())
                )
                val current = fleets.value.find { it.id == id }?.memberIds ?: emptySet()
                for (mid in memberIds - current) fleetRepository.addMember(id, mid)
                for (mid in current - memberIds) fleetRepository.removeMember(id, mid)
            }
            onDone(true, null)
        }
    }

    fun delete(fleet: Fleet) {
        viewModelScope.launch { fleetRepository.remove(fleet.id) }
    }

    /**
     * Explicit refresh: runs one regular cycle through the selected data
     * source; fleet statuses come from the resulting snapshot.
     */
    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            refreshError.value = null
            try {
                refreshError.value = when (val r = cycleRunner.runCycle("fleets-refresh")) {
                    is CycleResult.Success, is CycleResult.Idle -> null
                    CycleResult.Offline -> ApiError.Offline.localized()
                    is CycleResult.Failure -> r.error.localized()
                }
            } finally {
                refreshing.value = false
            }
        }
    }
}
