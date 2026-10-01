package com.flightradius.app.ui.aircraft

import com.flightradius.app.ui.format.localized
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flightradius.app.data.aircraftdb.AircraftMetaRepository
import com.flightradius.app.domain.AircraftMeta
import com.flightradius.app.data.api.ApiResult
import com.flightradius.app.data.repo.AircraftRepository
import com.flightradius.app.data.repo.FleetRepository
import com.flightradius.app.data.source.SelectedFlightDataSource
import com.flightradius.app.domain.Fleet
import com.flightradius.app.domain.IdentifierType
import com.flightradius.app.domain.Identifiers
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
    private val flightData: SelectedFlightDataSource,
    private val aircraftMeta: AircraftMetaRepository
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

    /** icao24 + stored metadata for an exact registration, when the database is ready. */
    suspend fun lookupRegistration(raw: String): Pair<String, AircraftMeta>? =
        aircraftMeta.findByRegistration(raw)

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

    /** Is this callsign broadcasting right now (live / no-data / error)? */
    suspend fun checkCallsign(raw: String): CheckResult {
        val callsign = Identifiers.normalize(raw, IdentifierType.CALLSIGN)
            ?: return CheckResult.NoData
        return when (val r = flightData.liveCallsigns(listOf(callsign))) {
            is ApiResult.Success -> if (callsign in r.data) CheckResult.Live else CheckResult.NoData
            is ApiResult.Failure -> CheckResult.NetworkError(r.error.localized())
        }
    }

    /** Is this transponder reporting right now? */
    suspend fun checkIcao24(raw: String): CheckResult {
        val icao24 = Identifiers.normalize(raw, IdentifierType.ICAO24)
            ?: return CheckResult.NoData
        return when (val r = flightData.isIcao24Live(icao24)) {
            is ApiResult.Success -> if (r.data) CheckResult.Live else CheckResult.NoData
            is ApiResult.Failure -> CheckResult.NetworkError(r.error.localized())
        }
    }

    /** One lookup for all bulk callsigns -> set of live callsigns (null on error). */
    suspend fun validateBulkCallsigns(callsigns: List<String>): Set<String>? {
        val normalized = callsigns.mapNotNull { Identifiers.normalize(it, IdentifierType.CALLSIGN) }
        return (flightData.liveCallsigns(normalized) as? ApiResult.Success)?.data
    }

    /** [radiusKm] null = follow the global default (also future changes to it). */
    fun addBulk(
        entries: List<BulkAddEntry>,
        radiusKm: Double?,
        onDone: (added: Int) -> Unit
    ) {
        viewModelScope.launch {
            var added = 0
            for (e in entries) {
                if (!e.selected || e.status != BulkAddStatus.NEW) continue
                val type = e.type ?: continue
                if (aircraftRepository.add(e.identifier, type, alertRadiusKm = radiusKm) != null) added++
            }
            onDone(added)
        }
    }
}
