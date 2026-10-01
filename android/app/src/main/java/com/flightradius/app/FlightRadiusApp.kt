package com.flightradius.app

import com.flightradius.app.ui.format.Words
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
    lateinit var updateManager: com.flightradius.app.data.update.UpdateManager

    @Inject
    lateinit var widgetUpdater: com.flightradius.app.widget.WidgetUpdater

    @Inject
    lateinit var updateInstaller: com.flightradius.app.data.update.UpdateInstaller

    @Inject
    lateinit var backgroundPolicy: com.flightradius.app.service.BackgroundPolicy

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(com.flightradius.app.util.AppLanguage.wrap(base))
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // A language change (Settings picker or Android's per-app page).
        Words.resources = resources
    }

    override fun onCreate() {
        super.onCreate()
        // Wire AppLog's debug gate to the live settings value (debug builds
        // always log; release builds honor the debugLogging switch).
        AppLog.debugEnabled = { BuildConfig.DEBUG || runtimeSettings.debugLogging }
        Words.resources = resources
        org.maplibre.android.MapLibre.getInstance(this)
        creditBucketWatcher.start(appScope)
        aircraftDatabase.maybeAutoUpdate()
        widgetUpdater.start(appScope)
        updateManager.checkOnLaunch()
        backgroundPolicy.start()
        updateInstaller.clearOldFiles()
    }
}
