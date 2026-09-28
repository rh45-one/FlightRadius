package com.flightradius.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        TrackedAircraftEntity::class,
        FleetEntity::class,
        FleetMemberEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class FlightRadiusDatabase : RoomDatabase() {
    abstract fun aircraftDao(): AircraftDao
    abstract fun fleetDao(): FleetDao
}
