package com.flightradius.app

import android.content.Context
import androidx.room.Room
import com.flightradius.app.data.db.AircraftDao
import com.flightradius.app.data.db.FlightRadiusDatabase
import com.flightradius.app.data.db.FleetDao
import com.flightradius.app.di.DatabaseModule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/** In-memory Room database for instrumented tests. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object TestDatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FlightRadiusDatabase =
        Room.inMemoryDatabaseBuilder(context, FlightRadiusDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    @Provides
    fun provideAircraftDao(db: FlightRadiusDatabase): AircraftDao = db.aircraftDao()

    @Provides
    fun provideFleetDao(db: FlightRadiusDatabase): FleetDao = db.fleetDao()
}
