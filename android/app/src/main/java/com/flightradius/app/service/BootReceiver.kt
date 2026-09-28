package com.flightradius.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.flightradius.app.data.prefs.LocationMode
import com.flightradius.app.data.prefs.SettingsRepository
import com.flightradius.app.location.LocationRepository
import com.flightradius.app.notifications.AlertNotifier
import com.flightradius.app.util.log.AppLog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * BOOT_COMPLETED / MY_PACKAGE_REPLACED resume.
 *
 * Only resumes when resumeOnBoot && monitoringDesired, and only when an FGS
 * of the needed type may be started from background:
 * - GPS mode requires ACCESS_BACKGROUND_LOCATION (location FGS from boot).
 * - Manual mode needs a dataSync FGS — forbidden from BOOT_COMPLETED on
 *   API 35+, allowed below.
 * Otherwise a "Tap to resume" notification is posted.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var locationRepository: LocationRepository
    @Inject lateinit var notifier: AlertNotifier

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val settings = withTimeoutOrNull(3_000) {
                    settingsRepository.settings.first()
                } ?: return@launch

                if (!settings.resumeOnBoot || !settings.monitoringDesired) return@launch
                AppLog.i("BootReceiver", "resume requested",
                    "mode" to settings.locationMode)

                val canStart = when (settings.locationMode) {
                    LocationMode.GPS ->
                        locationRepository.hasBackgroundLocationPermission()
                    LocationMode.MANUAL -> Build.VERSION.SDK_INT < 35
                }

                if (canStart) {
                    runCatching {
                        ContextCompat.startForegroundService(
                            context,
                            MonitoringService.intent(
                                context, MonitoringService.ACTION_START)
                                .putExtra(MonitoringService.EXTRA_FROM_USER, false)
                        )
                    }.onFailure { e ->
                        AppLog.w("BootReceiver", "start failed", throwable = e)
                        notifier.notifyResumeRequired()
                    }
                } else {
                    notifier.notifyResumeRequired()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
