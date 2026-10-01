package com.flightradius.app.service

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.flightradius.app.data.prefs.SettingsRepository
import com.flightradius.app.di.ApplicationScope
import com.flightradius.app.di.ProcessLifecycle
import com.flightradius.app.util.log.AppLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class AppVisibility { STOPPED, STARTED }

enum class BackgroundAction { NONE, STOP, START }

/**
 * What to do when the whole app leaves/enters the foreground.
 * Only matters while "Run in the background" is off: monitoring is stopped
 * (keeping `monitoringDesired`) on STOPPED and restarted on STARTED.
 */
fun backgroundAction(
    event: AppVisibility,
    backgroundMonitoring: Boolean,
    monitoringDesired: Boolean,
    status: MonitoringStatus
): BackgroundAction {
    if (backgroundMonitoring) return BackgroundAction.NONE
    return when (event) {
        AppVisibility.STOPPED ->
            if (status != MonitoringStatus.STOPPED) BackgroundAction.STOP else BackgroundAction.NONE
        AppVisibility.STARTED ->
            if (monitoringDesired && status == MonitoringStatus.STOPPED) BackgroundAction.START
            else BackgroundAction.NONE
    }
}

/** Applies [backgroundAction] to the process lifecycle. Started from the Application. */
@Singleton
class BackgroundPolicy @Inject constructor(
    private val controller: MonitoringController,
    private val settingsRepository: SettingsRepository,
    private val stateRepository: MonitoringStateRepository,
    @ApplicationScope private val scope: CoroutineScope,
    @ProcessLifecycle private val lifecycle: Lifecycle
) {
    private var started = false

    /** Main thread. Idempotent. */
    fun start() {
        if (started) return
        started = true
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = handle(AppVisibility.STARTED)
            override fun onStop(owner: LifecycleOwner) = handle(AppVisibility.STOPPED)
        })
    }

    private fun handle(event: AppVisibility) {
        scope.launch {
            val settings = settingsRepository.settings.first()
            val action = backgroundAction(
                event, settings.backgroundMonitoring, settings.monitoringDesired,
                stateRepository.state.value.status)
            if (action != BackgroundAction.NONE) AppLog.i("BackgroundPolicy", "$event -> $action")
            when (action) {
                BackgroundAction.STOP -> controller.stopForBackground()
                // The app is visible now, so the FGS start is allowed and the
                // radar chirp (while-in-use audio) may play.
                BackgroundAction.START -> controller.start(fromUser = false, appVisible = true)
                BackgroundAction.NONE -> Unit
            }
        }
    }
}
