package com.flightradius.app

import android.app.Application
import com.flightradius.app.data.aircraftdb.AircraftDatabaseManager
import com.flightradius.app.data.opensky.CreditBucketWatcher
import com.flightradius.app.data.prefs.RuntimeSettings
import com.flightradius.app.di.ApplicationScope
import com.flightradius.app.util.log.AppLog
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

@HiltAndroidApp
class FlightRadiusApp : Application() {

    @Inject
    lateinit var runtimeSettings: RuntimeSettings

    @Inject
    lateinit var creditBucketWatcher: CreditBucketWatcher

    @Inject
    lateinit var aircraftDatabase: AircraftDatabaseManager

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Wire AppLog's debug gate to the live settings value (debug builds
        // always log; release builds honor the debugLogging switch).
        AppLog.debugEnabled = { BuildConfig.DEBUG || runtimeSettings.debugLogging }
        org.maplibre.android.MapLibre.getInstance(this)
        creditBucketWatcher.start(appScope)
        aircraftDatabase.maybeAutoUpdate()
    }
}
