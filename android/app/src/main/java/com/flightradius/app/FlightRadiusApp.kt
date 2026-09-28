package com.flightradius.app

import android.app.Application
import com.flightradius.app.data.prefs.RuntimeSettings
import com.flightradius.app.util.log.AppLog
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FlightRadiusApp : Application() {

    @Inject
    lateinit var runtimeSettings: RuntimeSettings

    override fun onCreate() {
        super.onCreate()
        // Wire AppLog's debug gate to the live settings value (debug builds
        // always log; release builds honor the debugLogging switch).
        AppLog.debugEnabled = { BuildConfig.DEBUG || runtimeSettings.debugLogging }
    }
}
