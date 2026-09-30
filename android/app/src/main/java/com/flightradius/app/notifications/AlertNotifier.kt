package com.flightradius.app.notifications

import com.flightradius.app.ui.format.displayName
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.PathParser
import androidx.core.graphics.drawable.IconCompat
import com.flightradius.app.MainActivity
import com.flightradius.app.domain.AlertEvent
import com.flightradius.app.domain.AlertText
import com.flightradius.app.domain.DistanceUnit
import com.flightradius.app.domain.NearbyAircraft
import com.flightradius.app.domain.NearbyAlertEvent
import com.flightradius.app.ui.format.AlertFormat
import com.flightradius.app.ui.format.PLANE_PATH_DATA
import com.flightradius.app.ui.format.W
import com.flightradius.app.ui.format.Words
import com.flightradius.app.ui.format.labelRes
import com.flightradius.app.R
import com.flightradius.app.service.AlertActionReceiver
import com.flightradius.app.service.MonitoringService
import com.flightradius.app.util.log.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * All app notifications: the persistent monitoring status notification plus
 * proximity alerts (channel variants per sound/vibration combination, since
 * those can't be changed after channel creation).
 */
@Singleton
class AlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AlertNotifier"
        const val CHANNEL_STATUS = "monitoring_status"
        const val STATUS_NOTIFICATION_ID = 1
        private const val RESUME_NOTIFICATION_ID = 2
        private const val FAILURE_NOTIFICATION_ID = 3
        private const val TIMEOUT_NOTIFICATION_ID = 4
        private const val ALERT_ID_BASE = 1000
        private val VIBRATION = longArrayOf(0, 400, 200, 400, 200, 800)

        /** alert channel id variant for the current sound/vibration flags. */
        fun nearbyChannelId(sound: Boolean, vibration: Boolean): String =
            "nearby_airspace_s${if (sound) 1 else 0}_v${if (vibration) 1 else 0}"

        private const val NEARBY_GROUP = "nearby_airspace"
        private const val NEARBY_SUMMARY_ID = 5
        private const val NEARBY_ID_BASE = 2_000_000

        fun alertChannelId(sound: Boolean, vibration: Boolean): String =
            "proximity_alerts_s${if (sound) 1 else 0}_v${if (vibration) 1 else 0}"
    }

    private val nm = NotificationManagerCompat.from(context)

    /** Last-ensured alert channel variant (kept by ensureAlertChannel). */
    @Volatile
    private var alertChannelId: String = alertChannelId(sound = true, vibration = true)

    @Volatile
    private var nearbyChannelId: String = nearbyChannelId(sound = true, vibration = true)

    fun ensureStatusChannel() {
        val ch = NotificationChannel(
            CHANNEL_STATUS, Words.get(W.CH_STATUS), NotificationManager.IMPORTANCE_LOW
        ).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        nm.createNotificationChannel(ch)
    }

    /** Create the channel variant matching current settings; delete the rest. */
    fun ensureAlertChannel(sound: Boolean, vibration: Boolean) {
        alertChannelId = alertChannelId(sound, vibration)
        syncChannelVariants({ s, v -> alertChannelId(s, v) }, Words.get(W.CH_ALERTS), sound, vibration)
    }

    /** Same as [ensureAlertChannel] for the nearby-airspace channel family. */
    fun ensureNearbyChannel(sound: Boolean, vibration: Boolean) {
        nearbyChannelId = nearbyChannelId(sound, vibration)
        syncChannelVariants({ s, v -> nearbyChannelId(s, v) }, Words.get(W.CH_NEARBY), sound, vibration)
    }

    private fun syncChannelVariants(
        idFor: (Boolean, Boolean) -> String,
        name: String,
        sound: Boolean,
        vibration: Boolean
    ) {
        for (s in booleanArrayOf(true, false)) {
            for (v in booleanArrayOf(true, false)) {
                val id = idFor(s, v)
                if (s == sound && v == vibration) {
                    val ch = NotificationChannel(
                        id, name, NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        if (sound) {
                            setSound(
                                RingtoneManager.getDefaultUri(
                                    RingtoneManager.TYPE_NOTIFICATION),
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                                    .build()
                            )
                        } else {
                            setSound(null, null)
                        }
                        if (vibration) {
                            enableVibration(true)
                            vibrationPattern = VIBRATION
                        } else {
                            enableVibration(false)
                        }
                        enableLights(true)
                        lightColor = 0xFFFF0000.toInt()
                        setBypassDnd(false)
                    }
                    nm.createNotificationChannel(ch)
                } else {
                    nm.deleteNotificationChannel(id)
                }
            }
        }
    }

    /** System channel-settings screen for the current alert channel. */
    fun openChannelSettingsIntent(sound: Boolean, vibration: Boolean): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(
                Settings.EXTRA_CHANNEL_ID, alertChannelId(sound, vibration))

    fun canPost(): Boolean =
        nm.areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED)

    fun postProximityAlert(event: AlertEvent, unit: DistanceUnit) {
        val obs = event.observation
        val title = AlertFormat.alertTitle(obs, unit)
        val body = AlertFormat.alertBody(obs, unit)
        val notifId = ALERT_ID_BASE + (obs.aircraftId % 100_000).toInt()

        val content = PendingIntent.getActivity(
            context, notifId,
            Intent(context, MainActivity::class.java)
                .putExtra("alert_aircraft_id", obs.aircraftId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snooze = PendingIntent.getBroadcast(
            context, notifId * 10 + 1,
            AlertActionReceiver.intent(context, AlertActionReceiver.ACTION_SNOOZE)
                .putExtra(AlertActionReceiver.EXTRA_AIRCRAFT_ID, obs.aircraftId)
                .putExtra(AlertActionReceiver.EXTRA_SNOOZE_MINUTES, 30L),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismiss = PendingIntent.getBroadcast(
            context, notifId * 10 + 2,
            AlertActionReceiver.intent(context, AlertActionReceiver.ACTION_DISMISS)
                .putExtra(AlertActionReceiver.EXTRA_AIRCRAFT_ID, obs.aircraftId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val n = NotificationCompat.Builder(context, alertChannelId).apply {
            setSmallIcon(R.drawable.ic_stat_radar)
            setContentTitle(title)
            setContentText(body)
            setStyle(NotificationCompat.BigTextStyle().bigText(body))
            setPriority(NotificationCompat.PRIORITY_HIGH)
            setCategory(NotificationCompat.CATEGORY_STATUS)
            setAutoCancel(true)
            setTimeoutAfter(10 * 60_000L)
            setContentIntent(content)
            addAction(0, Words.get(W.ACT_SNOOZE), snooze)
            addAction(0, Words.get(W.ACT_DISMISS), dismiss)
        }.build()

        if (!canPost()) {
            AppLog.w(TAG, "notifications disabled; in-app alert only",
                "aircraftId" to obs.aircraftId)
            return
        }
        try {
            nm.notify(notifId, n)
        } catch (e: SecurityException) {
            AppLog.w(TAG, "post alert denied", throwable = e)
        }
    }

    private fun nearbyId(icao24: String): Int =
        NEARBY_ID_BASE + (icao24.toIntOrNull(16) ?: icao24.hashCode().and(0xFFFFFF))

    private fun nearbyLine(a: NearbyAircraft, unit: DistanceUnit): String = buildList {
        add(context.getString(a.cls.labelRes()))
        add(AlertText.formatDistance(a.distanceKm, unit) + " " + Words.compass(a.bearingDeg))
        AlertText.formatAltitude(a.altitudeM, unit)?.let { add(it) }
    }.joinToString(" · ")

    /**
     * One notification per newly matched aircraft plus an InboxStyle summary
     * while more than one aircraft is active. [active] = every aircraft
     * currently inside a rule (for the summary lines).
     */
    fun postNearbyAlerts(
        events: List<NearbyAlertEvent>,
        active: List<NearbyAircraft>,
        unit: DistanceUnit
    ) {
        if (events.isEmpty()) return
        if (!canPost()) {
            AppLog.w(TAG, "notifications disabled; nearby alert skipped")
            return
        }
        val content = PendingIntent.getActivity(
            context, NEARBY_SUMMARY_ID,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val mute = PendingIntent.getBroadcast(
            context, NEARBY_SUMMARY_ID * 10 + 1,
            AlertActionReceiver.intent(context, AlertActionReceiver.ACTION_MUTE_NEARBY)
                .putExtra(AlertActionReceiver.EXTRA_SNOOZE_MINUTES, 60L),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            for (e in events) {
                val a = e.aircraft
                val line = nearbyLine(a, unit)
                val n = NotificationCompat.Builder(context, nearbyChannelId)
                    .setSmallIcon(R.drawable.ic_stat_radar)
                    .setContentTitle(a.displayName)
                    .setContentText(line)
                    .setSubText(e.rule.displayName())
                    .setStyle(NotificationCompat.BigTextStyle().bigText(line))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setGroup(NEARBY_GROUP)
                    .setAutoCancel(true)
                    .setTimeoutAfter(10 * 60_000L)
                    .setContentIntent(content)
                    .addAction(0, context.getString(R.string.nearby_mute_action), mute)
                    .build()
                nm.notify(nearbyId(a.icao24), n)
            }
            updateNearbySummary(active, unit, content, mute)
        } catch (e: SecurityException) {
            AppLog.w(TAG, "post nearby denied", throwable = e)
        }
    }

    private fun updateNearbySummary(
        active: List<NearbyAircraft>,
        unit: DistanceUnit,
        content: PendingIntent,
        mute: PendingIntent
    ) {
        if (active.size < 2) {
            nm.cancel(NEARBY_SUMMARY_ID)
            return
        }
        val inbox = NotificationCompat.InboxStyle()
        for (a in active.take(6)) inbox.addLine(a.displayName + " · " + nearbyLine(a, unit))
        val title = context.resources.getQuantityString(
            R.plurals.nearby_summary_title, active.size, active.size)
        val n = NotificationCompat.Builder(context, nearbyChannelId)
            .setSmallIcon(R.drawable.ic_stat_radar)
            .setContentTitle(title)
            .setContentText(active.first().displayName + " · " + nearbyLine(active.first(), unit))
            .setStyle(inbox)
            .setGroup(NEARBY_GROUP)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setAutoCancel(true)
            .setContentIntent(content)
            .addAction(0, context.getString(R.string.nearby_mute_action), mute)
            .build()
        nm.notify(NEARBY_SUMMARY_ID, n)
    }

    /** Clears notifications of aircraft that left every rule (and the summary if <2 remain). */
    fun syncNearbyNotifications(left: Collection<String>, active: List<NearbyAircraft>, unit: DistanceUnit) {
        for (id in left) nm.cancel(nearbyId(id))
        if (active.size < 2) nm.cancel(NEARBY_SUMMARY_ID)
        else if (left.isNotEmpty() && canPost()) {
            val content = PendingIntent.getActivity(
                context, NEARBY_SUMMARY_ID,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val mute = PendingIntent.getBroadcast(
                context, NEARBY_SUMMARY_ID * 10 + 1,
                AlertActionReceiver.intent(context, AlertActionReceiver.ACTION_MUTE_NEARBY)
                    .putExtra(AlertActionReceiver.EXTRA_SNOOZE_MINUTES, 60L),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            runCatching { updateNearbySummary(active, unit, content, mute) }
        }
    }

    fun cancelAllNearby(icao24s: Collection<String>) {
        for (id in icao24s) nm.cancel(nearbyId(id))
        nm.cancel(NEARBY_SUMMARY_ID)
    }

    /** "Tap to resume" fallback when a background start isn't allowed. */
    fun notifyResumeRequired() {
        val content = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .putExtra("resume_monitoring", true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        postAux(RESUME_NOTIFICATION_ID, Words.get(W.AUX_RESUME_TITLE),
            Words.get(W.AUX_RESUME_TEXT), content)
    }

    /** Clear failure reason when a user-requested start can't monitor. */
    fun notifyStartFailure(reason: String) {
        val content = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        postAux(FAILURE_NOTIFICATION_ID, Words.get(W.AUX_FAIL_TITLE), reason, content)
    }

    /** Android 15+ dataSync FGS hit its daily budget in manual mode. */
    fun notifyDataSyncTimeout() {
        val content = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        postAux(TIMEOUT_NOTIFICATION_ID, Words.get(W.AUX_TIMEOUT_TITLE),
            Words.get(W.AUX_TIMEOUT_TEXT),
            content)
    }

    /** Remove a posted proximity-alert notification for an aircraft. */
    fun cancelAlert(aircraftId: Long) {
        nm.cancel(ALERT_ID_BASE + (aircraftId % 100_000).toInt())
    }

    private fun postAux(id: Int, title: String, text: String, content: PendingIntent) {
        if (!canPost()) {
            AppLog.w(TAG, "notifications disabled; skipped aux", "title" to title)
            return
        }
        val n = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_radar)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(content)
            .build()
        try {
            nm.notify(id, n)
        } catch (e: SecurityException) {
            AppLog.w(TAG, "aux notification denied", throwable = e)
        }
    }

    /** Builder for the foreground status notification (caller sets texts). */
    fun statusNotification(title: String, text: String, paused: Boolean): Notification {
        ensureStatusChannel()
        val content = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .putExtra("resume_monitoring", true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_radar)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(
                NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(content)
        if (paused) {
            b.addAction(0, Words.get(W.ACT_RESUME), serviceAction(MonitoringService.ACTION_RESUME, 11))
        } else {
            b.addAction(0, Words.get(W.ACT_PAUSE), serviceAction(MonitoringService.ACTION_PAUSE, 12))
        }
        b.addAction(0, Words.get(W.ACT_STOP), serviceAction(MonitoringService.ACTION_STOP, 13))
        return b.build()
    }

    /**
     * The status notification while a tracked aircraft is inside its radius.
     * On Android 16+ it asks to be promoted to a Live Update (status-bar chip,
     * progress = how deep inside the radius the aircraft is); earlier versions
     * get the same content as a plain ongoing notification.
     */
    fun liveStatusNotification(content: LiveUpdateContent, paused: Boolean): Notification {
        ensureStatusChannel()
        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = liveText(content)
        val b = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_radar)
            .setContentTitle(content.title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(contentIntent)
        if (Build.VERSION.SDK_INT >= 36) {
            val danger = ContextCompat.getColor(context, R.color.live_update_danger)
            b.setStyle(
                NotificationCompat.ProgressStyle()
                    .setProgressSegments(
                        listOf(NotificationCompat.ProgressStyle.Segment(100).setColor(danger)))
                    .setProgress(content.progress)
                    .setProgressTrackerIcon(IconCompat.createWithBitmap(planeTracker(danger)))
            )
            b.setShortCriticalText(content.shortText)
            b.setRequestPromotedOngoing(true)
        }
        if (paused) {
            b.addAction(0, Words.get(W.ACT_RESUME), serviceAction(MonitoringService.ACTION_RESUME, 11))
        } else {
            b.addAction(0, Words.get(W.ACT_PAUSE), serviceAction(MonitoringService.ACTION_PAUSE, 12))
        }
        b.addAction(0, Words.get(W.ACT_STOP), serviceAction(MonitoringService.ACTION_STOP, 13))
        return b.build()
    }

    private fun liveText(c: LiveUpdateContent): String = buildString {
        append(context.getString(R.string.live_inside_radius, c.radiusText))
        when (c.trend) {
            com.flightradius.app.ui.format.Format.ClosingTrend.APPROACHING ->
                append(" · ").append(context.getString(R.string.radar_closing_lower))
            com.flightradius.app.ui.format.Format.ClosingTrend.RECEDING ->
                append(" · ").append(context.getString(R.string.radar_moving_away_lower))
            else -> Unit
        }
        if (c.moreInside > 0) {
            append(" · ").append(context.getString(R.string.live_more_inside, c.moreInside))
        }
    }

    private fun planeTracker(color: Int): Bitmap {
        val size = 64
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val path = PathParser.createPathFromPathData(PLANE_PATH_DATA)
        path.transform(Matrix().apply { setScale(size / 24f, size / 24f) })
        Canvas(bmp).drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        return bmp
    }

    private fun serviceAction(action: String, req: Int): PendingIntent =
        PendingIntent.getService(
            context, req,
            MonitoringService.intent(context, action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
