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
interface FleetDao {

    @Transaction
    @Query("SELECT * FROM fleets ORDER BY name ASC")
    fun observeFleetsWithMembers(): Flow<List<FleetWithMembers>>

    @Transaction
    @Query("SELECT * FROM fleets ORDER BY name ASC")
    suspend fun getFleetsWithMembers(): List<FleetWithMembers>

    @Query("SELECT * FROM fleets WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): FleetEntity?

    @Query("SELECT * FROM fleets WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): FleetEntity?

    /** Returns row id, or -1 when the name already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: FleetEntity): Long

    @Update
    suspend fun update(entity: FleetEntity)

    @Query("DELETE FROM fleets WHERE id = :fleetId")
    suspend fun deleteById(fleetId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMember(member: FleetMemberEntity): Long

    @Query("DELETE FROM fleet_members WHERE fleetId = :fleetId AND aircraftId = :aircraftId")
    suspend fun removeMember(fleetId: Long, aircraftId: Long)

    @Query("DELETE FROM fleet_members WHERE fleetId = :fleetId")
    suspend fun clearMembers(fleetId: Long)
}
