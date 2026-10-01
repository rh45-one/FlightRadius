package com.flightradius.app.service

import android.content.Context
import androidx.core.content.ContextCompat
import com.flightradius.app.data.prefs.SettingsRepository
import com.flightradius.app.di.ApplicationScope
import com.flightradius.app.notifications.AlertNotifier
import com.flightradius.app.util.log.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Public API for starting/stopping monitoring. Start/stop dispatch intents
 * to [MonitoringService]; `monitoringDesired` persists across reboots.
 *
 * Pause/resume use a plain startService call and are no-ops when the
 * service isn't running. Snooze/dismiss act in-process on
 * [MonitoringStateRepository] — the service observes state changes — and
 * cancel the corresponding alert notification.
 */
@Singleton
class MonitoringController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val stateRepository: MonitoringStateRepository,
    private val notifier: AlertNotifier,
    @ApplicationScope private val scope: CoroutineScope
) {
    /**
     * Start monitoring. [fromUser] = explicit UI/notification action —
     * grants while-in-use audio (radar chirp) and requires a valid location
     * config (fails loudly otherwise).
     */
    fun start(fromUser: Boolean, appVisible: Boolean = false) {
        AppLog.i("MonitoringCtl", "start", "fromUser" to fromUser, "visible" to appVisible)
        scope.launch { settingsRepository.setMonitoringDesired(true) }
        ContextCompat.startForegroundService(
            context,
            MonitoringService.intent(context, MonitoringService.ACTION_START)
                .putExtra(MonitoringService.EXTRA_FROM_USER, fromUser)
                .putExtra(MonitoringService.EXTRA_APP_VISIBLE, appVisible)
        )
    }

    /**
     * Stops the service completely because the app left the foreground
     * ("Run in the background" is off). Unlike [stop] this keeps
     * `monitoringDesired`, so the app resumes when it returns. Uses a plain
     * startService: the process still holds the foreground service, which
     * exempts it from background-start limits.
     */
    fun stopForBackground() {
        if (stateRepository.state.value.status == MonitoringStatus.STOPPED) return
        AppLog.i("MonitoringCtl", "stop for background")
        runCatching {
            context.startService(
                MonitoringService.intent(context, MonitoringService.ACTION_STOP)
                    .putExtra(MonitoringService.EXTRA_KEEP_DESIRED, true))
        }.onFailure { AppLog.w("MonitoringCtl", "stopForBackground failed", throwable = it) }
    }

    fun stop() {
        AppLog.i("MonitoringCtl", "stop")
        scope.launch { settingsRepository.setMonitoringDesired(false) }
        if (stateRepository.state.value.status == MonitoringStatus.STOPPED) return
        dispatch(MonitoringService.ACTION_STOP)
    }

    fun pause() {
        if (stateRepository.state.value.status == MonitoringStatus.STOPPED) return
        AppLog.i("MonitoringCtl", "pause")
        context.startService(
            MonitoringService.intent(context, MonitoringService.ACTION_PAUSE))
    }

    fun resume() {
        if (stateRepository.state.value.status == MonitoringStatus.STOPPED) return
        AppLog.i("MonitoringCtl", "resume")
        context.startService(
            MonitoringService.intent(context, MonitoringService.ACTION_RESUME))
    }

    fun snooze(aircraftId: Long, minutes: Long) {
        AppLog.i("MonitoringCtl", "snooze",
            "aircraftId" to aircraftId, "min" to minutes)
        stateRepository.snooze(aircraftId, minutes, System.currentTimeMillis())
        notifier.cancelAlert(aircraftId)
    }

    fun dismissAlert() {
        AppLog.i("MonitoringCtl", "dismiss alert")
        stateRepository.activeAlert.value?.let {
            notifier.cancelAlert(it.observation.aircraftId)
        }
        stateRepository.dismissAlert()
    }

    private fun dispatch(action: String) {
        ContextCompat.startForegroundService(
            context, MonitoringService.intent(context, action))
    }
}
