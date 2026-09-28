package com.flightradius.app.ui.aircraft

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightradius.app.data.api.ApiResult
import com.flightradius.app.data.api.ValidateCallsignsResponseDto
import com.flightradius.app.data.repo.AircraftRepository
import com.flightradius.app.data.repo.FleetRepository
import com.flightradius.app.data.repo.FlightRadiusRepository
import com.flightradius.app.domain.Fleet
import com.flightradius.app.domain.IdentifierType
import com.flightradius.app.domain.TrackedAircraft
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backend validation outcome for a single identifier (UI-facing). */
sealed interface CheckResult {
    data object Idle : CheckResult
    data object Checking : CheckResult
    data object Live : CheckResult
    data object NoData : CheckResult
    data class NetworkError(val message: String) : CheckResult
}

@HiltViewModel
class AircraftViewModel @Inject constructor(
    private val aircraftRepository: AircraftRepository,
    private val fleetRepository: FleetRepository,
    private val repository: FlightRadiusRepository
) : ViewModel() {

    val search = MutableStateFlow("")

    val aircraft: StateFlow<List<TrackedAircraft>> = aircraftRepository.aircraft
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val fleets: StateFlow<List<Fleet>> = fleetRepository.fleets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Sorted + search-filtered list for the screen. */
    val filtered: StateFlow<List<TrackedAircraft>> =
        combine(aircraft, search) { list, q ->
            val query = q.trim().uppercase()
            list.filter {
                query.isEmpty() || it.identifier.contains(query) ||
                    it.notes?.contains(query, ignoreCase = true) == true
            }.sortedBy { it.identifier }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun existingIdentifiers(): Set<String> =
        aircraft.value.map { it.identifier }.toSet()

    fun add(
        identifier: String,
        type: IdentifierType,
        notes: String?,
        radiusKm: Double?,
        onDone: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val id = aircraftRepository.add(
                identifier, type,
                notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                alertRadiusKm = radiusKm
            )
            onDone(id != null)
        }
    }

    fun update(aircraft: TrackedAircraft) {
        viewModelScope.launch { aircraftRepository.update(aircraft) }
    }

    /** Deletes [aircraft]; onUndo re-inserts the identical row. */
    fun remove(aircraft: TrackedAircraft, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            aircraftRepository.remove(aircraft.id)
            onDeleted()
        }
    }

    fun restore(aircraft: TrackedAircraft, fleetIds: Set<Long>) {
        viewModelScope.launch {
            val id = aircraftRepository.add(
                aircraft.identifier, aircraft.type,
                notes = aircraft.notes,
                alertRadiusKm = aircraft.alertRadiusKm,
                createdAt = aircraft.createdAt
            )
            if (id != null) {
                for (fid in fleetIds) fleetRepository.addMember(fid, id)
            }
        }
    }

    fun setFleetMembership(aircraftId: Long, fleetIds: Set<Long>) {
        viewModelScope.launch {
            val current = fleets.value
                .filter { aircraftId in it.memberIds }.map { it.id }.toSet()
            for (fid in fleetIds - current) fleetRepository.addMember(fid, aircraftId)
            for (fid in current - fleetIds) fleetRepository.removeMember(fid, aircraftId)
        }
    }

    /** Check a callsign against the backend (live / no-data / error). */
    suspend fun checkCallsign(callsign: String): CheckResult =
        when (val r = repository.validateCallsigns(listOf(callsign))) {
            is ApiResult.Success -> {
                val v: ValidateCallsignsResponseDto = r.data
                val live = v.results.any {
                    it.callsign.equals(callsign, ignoreCase = true) &&
                        it.status == "valid"
                }
                if (live) CheckResult.Live else CheckResult.NoData
            }
            is ApiResult.Failure -> CheckResult.NetworkError(r.error.message)
        }

    /** Check an icao24 against the backend. */
    suspend fun checkIcao24(icao24: String): CheckResult =
        when (val r = repository.lookupIcao24(icao24)) {
            is ApiResult.Success -> CheckResult.Live
            is ApiResult.Failure -> {
                if (r.error is com.flightradius.app.data.api.ApiError.Http ||
                    r.error is com.flightradius.app.data.api.ApiError.BadRequest
                ) CheckResult.NoData
                else CheckResult.NetworkError(r.error.message)
            }
        }

    /** One validate-call for all bulk callsigns -> set of live callsigns. */
    suspend fun validateBulkCallsigns(callsigns: List<String>): Set<String>? =
        when (val r = repository.validateCallsigns(callsigns)) {
            is ApiResult.Success ->
                r.data.results.filter { it.status == "valid" }
                    .mapNotNull { it.callsign?.uppercase() }.toSet()
            is ApiResult.Failure -> null
        }

    fun addBulk(
        entries: List<BulkAddEntry>,
        onDone: (added: Int) -> Unit
    ) {
        viewModelScope.launch {
            var added = 0
            for (e in entries) {
                if (!e.selected || e.status != BulkAddStatus.NEW) continue
                val type = e.type ?: continue
                if (aircraftRepository.add(e.identifier, type) != null) added++
            }
            onDone(added)
        }
    }
}
