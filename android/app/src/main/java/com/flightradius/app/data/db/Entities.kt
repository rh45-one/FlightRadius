package com.flightradius.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter
import com.flightradius.app.domain.IdentifierType

@Entity(
    tableName = "tracked_aircraft",
    indices = [Index(value = ["identifier"], unique = true)]
)
data class TrackedAircraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Normalized identifier (uppercase callsign / lowercase icao24). */
    val identifier: String,
    val type: IdentifierType,
    val notes: String? = null,
    /** REAL; NULL = inherit fleet/global radius. */
    val alertRadiusKm: Double? = null,
    val createdAt: Long
)

@Entity(
    tableName = "fleets",
    indices = [Index(value = ["name"], unique = true)]
)
data class FleetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val alertRadiusKm: Double? = null,
    val createdAt: Long,
    /** [com.flightradius.app.domain.GroupIcon] name. */
    @ColumnInfo(defaultValue = "'PLANE'") val iconKey: String = "PLANE"
)

@Entity(
    tableName = "fleet_members",
    primaryKeys = ["fleetId", "aircraftId"],
    foreignKeys = [
        ForeignKey(
            entity = FleetEntity::class,
            parentColumns = ["id"],
            childColumns = ["fleetId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TrackedAircraftEntity::class,
            parentColumns = ["id"],
            childColumns = ["aircraftId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    // One group per aircraft.
    indices = [Index(value = ["aircraftId"], unique = true)]
)
data class FleetMemberEntity(
    val fleetId: Long,
    val aircraftId: Long
)

data class FleetWithMembers(
    @Embedded val fleet: FleetEntity,
    @Relation(
        entity = FleetMemberEntity::class,
        parentColumn = "id",
        entityColumn = "fleetId",
        projection = ["aircraftId"]
    )
    val memberIds: List<Long>
)

class Converters {
    @TypeConverter
    fun identifierTypeToString(value: IdentifierType): String = value.name

    @TypeConverter
    fun stringToIdentifierType(value: String): IdentifierType =
        runCatching { IdentifierType.valueOf(value) }.getOrDefault(IdentifierType.CALLSIGN)
}
