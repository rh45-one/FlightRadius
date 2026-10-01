package com.flightradius.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AircraftDao {

    @Query("SELECT * FROM tracked_aircraft ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<TrackedAircraftEntity>>

    @Query("SELECT * FROM tracked_aircraft ORDER BY createdAt ASC")
    suspend fun getAll(): List<TrackedAircraftEntity>

    /** Returns row id, or -1 when the identifier already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: TrackedAircraftEntity): Long

    @Query("SELECT id FROM tracked_aircraft WHERE identifier = :identifier LIMIT 1")
    suspend fun findIdByIdentifier(identifier: String): Long?

    @Query("SELECT * FROM tracked_aircraft WHERE identifier = :identifier LIMIT 1")
    suspend fun findByIdentifier(identifier: String): TrackedAircraftEntity?

    @Update
    suspend fun update(entity: TrackedAircraftEntity)

    @Query("DELETE FROM tracked_aircraft WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
abstract class FleetDao {

    @Transaction
    @Query("SELECT * FROM fleets ORDER BY name ASC")
    abstract fun observeFleetsWithMembers(): Flow<List<FleetWithMembers>>

    @Transaction
    @Query("SELECT * FROM fleets ORDER BY name ASC")
    abstract suspend fun getFleetsWithMembers(): List<FleetWithMembers>

    @Query("SELECT * FROM fleets WHERE name = :name LIMIT 1")
    abstract suspend fun findByName(name: String): FleetEntity?

    @Query("SELECT * FROM fleets WHERE id = :id LIMIT 1")
    abstract suspend fun findById(id: Long): FleetEntity?

    /** Returns row id, or -1 when the name already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIgnore(entity: FleetEntity): Long

    @Update
    abstract suspend fun update(entity: FleetEntity)

    @Query("DELETE FROM fleets WHERE id = :fleetId")
    abstract suspend fun deleteById(fleetId: Long)

    /** Ignored when the aircraft is already in a group (unique per aircraft). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun addMember(member: FleetMemberEntity): Long

    @Query("DELETE FROM fleet_members WHERE fleetId = :fleetId AND aircraftId = :aircraftId")
    abstract suspend fun removeMember(fleetId: Long, aircraftId: Long)

    @Query("DELETE FROM fleet_members WHERE fleetId = :fleetId")
    abstract suspend fun clearMembers(fleetId: Long)

    @Query("DELETE FROM fleet_members WHERE aircraftId IN (:aircraftIds)")
    abstract suspend fun clearMembershipsOf(aircraftIds: List<Long>)

    @Query("SELECT COUNT(*) FROM fleet_members WHERE aircraftId = :aircraftId")
    abstract suspend fun membershipCount(aircraftId: Long): Int

    /**
     * Sets each aircraft's group (null = ungrouped) in one transaction: the old
     * membership is removed before the new one is inserted, so the one-group
     * constraint is never violated.
     */
    @Transaction
    open suspend fun applyAssignments(assignments: Map<Long, Long?>) {
        assignments.keys.chunked(500).forEach { clearMembershipsOf(it) }
        for ((aircraftId, fleetId) in assignments) {
            if (fleetId != null) {
                addMember(FleetMemberEntity(fleetId = fleetId, aircraftId = aircraftId))
            }
        }
    }
}
