package com.flightradius.app.data.aircraftdb

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction

/** Row in the live table. Columns must mirror [AircraftMetaStagingEntity]. */
@Entity(tableName = "aircraft_meta", indices = [Index("registration")])
data class AircraftMetaEntity(
    @PrimaryKey val icao24: String,
    val cls: Int,
    val typecode: String?,
    val registration: String?,
    val model: String?,
    val operator: String?
)

@Entity(tableName = "aircraft_meta_staging")
data class AircraftMetaStagingEntity(
    @PrimaryKey val icao24: String,
    val cls: Int,
    val typecode: String?,
    val registration: String?,
    val model: String?,
    val operator: String?
)

@Dao
abstract class AircraftMetaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertStaging(rows: List<AircraftMetaStagingEntity>)

    @Query("DELETE FROM aircraft_meta_staging")
    abstract suspend fun clearStaging()

    @Query("DELETE FROM aircraft_meta")
    abstract suspend fun clearMain()

    @Query(
        "INSERT INTO aircraft_meta SELECT icao24, cls, typecode, registration, model, operator " +
            "FROM aircraft_meta_staging"
    )
    abstract suspend fun copyStaging()

    /** Atomically replaces the live table with the staged rows. */
    @Transaction
    open suspend fun swap() {
        clearMain()
        copyStaging()
        clearStaging()
    }

    @Query("SELECT * FROM aircraft_meta WHERE icao24 IN (:ids)")
    abstract suspend fun findByIds(ids: List<String>): List<AircraftMetaEntity>

    @Query("SELECT * FROM aircraft_meta WHERE registration = :registration LIMIT 1")
    abstract suspend fun findByRegistration(registration: String): AircraftMetaEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM aircraft_meta LIMIT 1)")
    abstract suspend fun hasRows(): Boolean

    @Query("SELECT COUNT(*) FROM aircraft_meta")
    abstract suspend fun count(): Int
}

@Database(
    entities = [AircraftMetaEntity::class, AircraftMetaStagingEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AircraftMetaDatabase : RoomDatabase() {
    abstract fun dao(): AircraftMetaDao
}
