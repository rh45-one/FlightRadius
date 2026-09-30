package com.flightradius.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.flightradius.app.notifications.AlertNotifier
import com.flightradius.app.util.log.AppLog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Handles proximity-alert notification actions (Snooze / Dismiss) in-process.
 * Non-exported; works whether or not the monitoring service is running —
 * state changes are applied directly to [MonitoringStateRepository], which
 * the service observes.
 */
@AndroidEntryPoint
class AlertActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlertActions"
        const val ACTION_SNOOZE = "com.flightradius.app.action.ALERT_SNOOZE"
        const val ACTION_DISMISS = "com.flightradius.app.action.ALERT_DISMISS"
        const val ACTION_MUTE_NEARBY = "com.flightradius.app.action.NEARBY_MUTE"
        const val EXTRA_AIRCRAFT_ID = "aircraft_id"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"

        fun intent(context: Context, action: String): Intent =
            Intent(context, AlertActionReceiver::class.java).setAction(action)
    }

    @Inject lateinit var stateRepository: MonitoringStateRepository
    @Inject lateinit var notifier: AlertNotifier

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_AIRCRAFT_ID, -1L)
        when (intent.action) {
            ACTION_SNOOZE -> {
                val minutes = intent.getLongExtra(EXTRA_SNOOZE_MINUTES, 30L)
                if (id >= 0) {
                    AppLog.i(TAG, "snooze", "aircraftId" to id, "min" to minutes)
                    stateRepository.snooze(id, minutes, System.currentTimeMillis())
                    notifier.cancelAlert(id)
                }
            }
            ACTION_MUTE_NEARBY -> {
                val minutes = intent.getLongExtra(EXTRA_SNOOZE_MINUTES, 60L)
                AppLog.i(TAG, "mute nearby", "min" to minutes)
                stateRepository.muteNearby(System.currentTimeMillis() + minutes * 60_000L)
            }
            ACTION_DISMISS -> {
                AppLog.i(TAG, "dismiss alert", "aircraftId" to id)
                stateRepository.dismissAlert()
                if (id >= 0) notifier.cancelAlert(id)
            }
        }
    }
}
