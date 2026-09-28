package com.flightradius.app.data.repo

import com.flightradius.app.data.db.AircraftDao
import com.flightradius.app.data.db.TrackedAircraftEntity
import com.flightradius.app.domain.IdentifierType
import com.flightradius.app.domain.Identifiers
import com.flightradius.app.domain.TrackedAircraft
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private fun TrackedAircraftEntity.toDomain() = TrackedAircraft(
    id = id,
    identifier = identifier,
    type = type,
    notes = notes,
    alertRadiusKm = alertRadiusKm,
    createdAt = createdAt
)

@Singleton
class AircraftRepository @Inject constructor(
    private val aircraftDao: AircraftDao
) {
    val aircraft: Flow<List<TrackedAircraft>> =
        aircraftDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getAll(): List<TrackedAircraft> =
        aircraftDao.getAll().map { it.toDomain() }

    /**
     * Adds a tracked aircraft after normalization/validation.
     * Returns the new id, or null when invalid or already tracked.
     */
    suspend fun add(
        rawIdentifier: String,
        type: IdentifierType,
        notes: String? = null,
        alertRadiusKm: Double? = null,
        createdAt: Long = System.currentTimeMillis()
    ): Long? {
        val normalized = Identifiers.normalize(rawIdentifier, type) ?: return null
        if (aircraftDao.findIdByIdentifier(normalized) != null) return null
        val rowId = aircraftDao.insertIgnore(
            TrackedAircraftEntity(
                identifier = normalized,
                type = type,
                notes = notes,
                alertRadiusKm = alertRadiusKm,
                createdAt = createdAt
            )
        )
        return rowId.takeIf { it > 0 }
    }

    suspend fun update(aircraft: TrackedAircraft) {
        aircraftDao.update(
            TrackedAircraftEntity(
                id = aircraft.id,
                identifier = aircraft.identifier,
                type = aircraft.type,
                notes = aircraft.notes,
                alertRadiusKm = aircraft.alertRadiusKm,
                createdAt = aircraft.createdAt
            )
        )
    }

    suspend fun remove(id: Long) = aircraftDao.deleteById(id)
}
