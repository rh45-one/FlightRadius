package com.flightradius.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.flightradius.app.data.api.ApiError
import com.flightradius.app.data.prefs.AppSettings
import com.flightradius.app.data.prefs.LocationMode
import com.flightradius.app.data.prefs.SettingsRepository
import com.flightradius.app.domain.AircraftAlertState
import com.flightradius.app.domain.AirspaceAlertEngine
import com.flightradius.app.domain.NearbyAlertState
import com.flightradius.app.domain.AlertText
import com.flightradius.app.domain.MonitoringSnapshot
import com.flightradius.app.domain.NextDelayPolicy
import com.flightradius.app.domain.ProximityAlertEngine
import com.flightradius.app.location.LocationRepository
import com.flightradius.app.location.LocationStatus
import com.flightradius.app.location.LocationUploader
import com.flightradius.app.notifications.AlertNotifier
import com.flightradius.app.util.log.AppLog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Foreground monitoring engine. Owns the polling loop, wake-locks, doze
 * handling, alert evaluation and all notifications. Started/stopped through
 * [MonitoringController].
 */
@AndroidEntryPoint
class MonitoringService : Service() {

    companion object {
        private const val TAG = "MonitoringSvc"
        private const val WAKELOCK_TAG = "FlightRadius:cycle"
        private const val HP_WAKELOCK_TAG = "FlightRadius:highpriority"
        private const val HEARTBEAT_WAKELOCK_TAG = "FlightRadius:heartbeat"
        private const val TICK_WAKELOCK_TAG = "FlightRadius:tick"
        private const val CYCLE_WAKELOCK_MS = 90_000L
        private const val HP_WAKELOCK_MS = 30 * 60_000L
        private const val TICK_WAKELOCK_MS = 10_000L
        private const val HEARTBEAT_GRACE_MS = 30_000L
        private const val LOCATION_OWNER = "service"

        const val ACTION_START = "com.flightradius.app.action.START"
        const val ACTION_STOP = "com.flightradius.app.action.STOP"
        const val ACTION_PAUSE = "com.flightradius.app.action.PAUSE"
        const val ACTION_RESUME = "com.flightradius.app.action.RESUME"
        /** Alarm backstop / location wake -> run a cycle if due. */
        const val ACTION_TICK = "com.flightradius.app.action.TICK"

        const val EXTRA_FROM_USER = "from_user"

        fun intent(context: Context, action: String): Intent =
            Intent(context, MonitoringService::class.java).setAction(action)
    }

    @Inject lateinit var stateRepository: MonitoringStateRepository
    @Inject lateinit var cycleRunner: MonitoringCycleRunner
    @Inject lateinit var locationRepository: LocationRepository
    @Inject lateinit var locationUploader: LocationUploader
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var connectivity: ConnectivityMonitor
    @Inject lateinit var notifier: AlertNotifier
    @Inject lateinit var radar: RadarChirpPlayer

    private val handler = CoroutineExceptionHandler { _, t ->
        AppLog.e(TAG, "service coroutine failed", throwable = t)
    }
    private val serviceScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default + handler)

    private val alertEngine = ProximityAlertEngine()
    private var alertStates: Map<Long, AircraftAlertState> = emptyMap()
    private val nearbyEngine = AirspaceAlertEngine()
    private var nearbyStates: Map<String, NearbyAlertState> = emptyMap()

    @Volatile private var running = false
    @Volatile private var foregroundStarted = false
    @Volatile private var paused = false
    @Volatile private var startedFromBackground = true
    /** Explicit ACTION_START intent vs null-intent system restart. */
    @Volatile private var explicitStart = false

    /** Conflated wake-up signal: location ticks, connectivity, alarm, doze. */
    private val tickChannel = Channel<Unit>(Channel.CONFLATED)
    private val resumeChannel = Channel<Unit>(Channel.CONFLATED)

    private var wakeLock: PowerManager.WakeLock? = null
    private var highPriorityWakeLock: PowerManager.WakeLock? = null
    private var heartbeatWakeLock: PowerManager.WakeLock? = null
    private var tickWakeLock: PowerManager.WakeLock? = null
    private var loopJob: kotlinx.coroutines.Job? = null
    private var lastRenderedStatus: String? = null
    @Volatile private var currentSettings: AppSettings = AppSettings()

    private val pm by lazy { getSystemService(POWER_SERVICE) as PowerManager }
    private val alarmManager by lazy {
        getSystemService(ALARM_SERVICE) as AlarmManager
    }

    private val dozeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val dozing = pm.isDeviceIdleMode
            stateRepository.update { it.copy(dozing = dozing) }
            if (!dozing) tickChannel.trySend(Unit)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifier.ensureStatusChannel()
        ContextCompat.registerReceiver(
            this, dozeReceiver,
            IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        // Wake ticks: connectivity regained.
        serviceScope.launch {
            var wasOnline = connectivity.online.value
            connectivity.online.collect { online ->
                if (online && !wasOnline) tickChannel.trySend(Unit)
                wasOnline = online
            }
        }
        // Wake ticks: fused-location callbacks (arrive ~at the interval and
        // wake the CPU); tick only when the next cycle is due.
        serviceScope.launch {
            locationRepository.fix.collect {
                val next = stateRepository.state.value.nextCycleAtMs
                if (running && next != null &&
                    System.currentTimeMillis() >= next - 1_000
                ) {
                    // Keep the CPU up until the loop takes the cycle wakelock.
                    val wl = tickWakeLock ?: pm.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK, TICK_WAKELOCK_TAG
                    ).also { tickWakeLock = it }
                    wl.acquire(TICK_WAKELOCK_MS)
                    tickChannel.trySend(Unit)
                }
            }
        }
        // Settings-driven side effects (alert channel variant, doze flag).
        serviceScope.launch {
            settingsRepository.settings.collect { s ->
                currentSettings = s
                notifier.ensureAlertChannel(s.alertSound, s.alertVibration)
                notifier.ensureNearbyChannel(s.alertSound, s.alertVibration)
                stateRepository.update {
                    it.copy(highPriority = s.highPriorityMode)
                }
                // Radar chirp follows the setting live; WIU audio only when
                // the service wasn't started from boot.
                if (s.radarMode && running && !paused && !startedFromBackground) {
                    radar.start(serviceScope)
                } else {
                    radar.stop()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        if (!running) {
            // Only ACTION_START (explicit or null-intent restart) may bring
            // the service up. Other actions on a stopped service are dropped
            // — the system must not restart us for them.
            if (action != ACTION_START) {
                stopSelf(startId)
                return START_NOT_STICKY
            }
            explicitStart = intent != null
            val fromUser = intent?.getBooleanExtra(EXTRA_FROM_USER, false) ?: false
            startedFromBackground = !fromUser
            if (!tryGoForeground(fromUser)) return START_NOT_STICKY
            foregroundStarted = true
            running = true
            paused = false
            stateRepository.update {
                it.copy(
                    status = MonitoringStatus.STARTING,
                    startedFromBackground = startedFromBackground
                )
            }
            loopJob = serviceScope.launch { monitoringLoop() }
            return START_STICKY
        }

        when (action) {
            // Already running: explicit start is idempotent.
            ACTION_START -> refreshStatusNotification()
            ACTION_TICK -> tickChannel.trySend(Unit)
            ACTION_PAUSE -> {
                if (running && !paused) {
                    paused = true
                    releaseHeartbeat()
                    locationRepository.release(LOCATION_OWNER)
                    locationUploader.stop()
                    radar.update(null, currentSettings.monitoringIntervalSec * 1000L)
                    stateRepository.update {
                        it.copy(status = MonitoringStatus.PAUSED)
                    }
                    refreshStatusNotification()
                }
            }
            ACTION_RESUME -> {
                if (paused) {
                    paused = false
                    locationRepository.acquire(LOCATION_OWNER)
                    locationUploader.start()
                    stateRepository.update {
                        it.copy(status = MonitoringStatus.RUNNING)
                    }
                    resumeChannel.trySend(Unit)
                    refreshStatusNotification()
                }
            }
            ACTION_STOP -> internalStop()
        }
        return START_STICKY
    }

    /** One-shot startForeground; returns false when Android refuses. */
    private fun tryGoForeground(fromUser: Boolean): Boolean {
        val type = if (
            Build.VERSION.SDK_INT >= 29 && locationRepository.hasPermission()
        ) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else if (Build.VERSION.SDK_INT >= 29) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
        }
        return try {
            ServiceCompat.startForeground(
                this,
                AlertNotifier.STATUS_NOTIFICATION_ID,
                notifier.statusNotification("Starting…", "FlightRadius", paused = false),
                type
            )
            true
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException / SecurityException /
            // IllegalStateException — never crash, surface instead.
            AppLog.e(TAG, "startForeground failed", throwable = e)
            stateRepository.update {
                it.copy(status = MonitoringStatus.STOPPED)
            }
            if (fromUser) {
                notifier.notifyStartFailure(
                    "Android refused to start background monitoring: ${e.message}")
            } else {
                notifier.notifyResumeRequired()
            }
            false
        }
    }

    /**
     * Validate the location configuration before entering the loop.
     * fromUser=true  -> fail loudly + stop.
     * fromUser=false -> stay STOPPED, explain, offer tap-to-resume.
     */
    private suspend fun validateLocationOrStop(
        settings: AppSettings,
        fromUser: Boolean
    ): Boolean {
        val problem: String? = when (settings.locationMode) {
            LocationMode.MANUAL ->
                if (settings.manualLat == null || settings.manualLon == null)
                    "Set a manual location first" else null
            LocationMode.GPS -> when {
                !locationRepository.hasPermission() ->
                    "Grant location access to monitor aircraft"
                !locationRepository.playServicesAvailable() ->
                    "Google Play services unavailable — switch to manual location"
                else -> null
            }
        }
        if (problem == null) return true
        AppLog.w(TAG, "location invalid at start", "problem" to problem,
            "fromUser" to fromUser)
        if (fromUser) {
            notifier.notifyStartFailure(problem)
            settingsRepository.setMonitoringDesired(false)
        } else {
            notifier.notifyResumeRequired()
        }
        internalStop(clearDesired = fromUser)
        return false
    }

    private suspend fun monitoringLoop() {
        // Wait for settings before the first request (no fallback URL race).
        val settings = cycleRunner.awaitSettings()
        // System restart (null intent): only resume when monitoringDesired.
        // Explicit starts skip this — the desired-flag write may still be in
        // flight when the service reads settings.
        if (!explicitStart && !settings.monitoringDesired) {
            internalStop()
            return
        }
        if (!validateLocationOrStop(settings, !startedFromBackground)) return

        locationRepository.acquire(LOCATION_OWNER)
        locationUploader.start()
        // The settings collector (re)starts the radar player on changes;
        // do the initial start here since the first emission may already
        // have been consumed before `running` flipped.
        if (settings.radarMode && !startedFromBackground) {
            radar.start(serviceScope)
        } else if (settings.radarMode) {
            AppLog.i(TAG, "radar mode on but no WIU audio (boot start)")
        }

        while (true) {
            // Pause gate.
            while (paused) {
                releaseHeartbeat()
                stateRepository.update { it.copy(status = MonitoringStatus.PAUSED) }
                refreshStatusNotification()
                radar.update(null, currentSettings.monitoringIntervalSec * 1000L)
                resumeChannel.receive()
            }
            val s = settingsRepository.settings.first()
            val dozing = pm.isDeviceIdleMode
            stateRepository.update { it.copy(dozing = dozing) }

            // Doze gate (skipped in high-priority mode). Wakes on any tick
            // (doze exit, location callback, alarm) or the poll timeout.
            while (pm.isDeviceIdleMode && !s.highPriorityMode) {
                releaseHeartbeat()
                stateRepository.update {
                    it.copy(status = MonitoringStatus.DEFERRED_DOZE)
                }
                refreshStatusNotification()
                withTimeoutOrNull(60_000L) { tickChannel.receive() }
            }

            // Re-acquire the high-priority wakelock each cycle so it can
            // never leak past its 30-min timeout.
            if (s.highPriorityMode) {
                if (highPriorityWakeLock == null) {
                    highPriorityWakeLock = pm.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK, HP_WAKELOCK_TAG
                    )
                }
                highPriorityWakeLock?.acquire(HP_WAKELOCK_MS)
            } else {
                highPriorityWakeLock?.let { if (it.isHeld) it.release() }
                highPriorityWakeLock = null
            }

            val cycleWl = wakeLock ?: pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG
            ).also { wakeLock = it }
            cycleWl.acquire(CYCLE_WAKELOCK_MS)
            stateRepository.update { it.copy(wakeLockHeld = true) }
            val result: CycleResult = try {
                runCatching { cycleRunner.runCycle("loop") }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrElse { CycleResult.Failure(ApiError.Unknown(it.message)) }
            } finally {
                if (cycleWl.isHeld) cycleWl.release()
                stateRepository.update { it.copy(wakeLockHeld = false) }
            }

            when (result) {
                is CycleResult.Success -> {
                    evaluateAlerts(result.snapshot, s)
                    evaluateNearby(result.snapshot, s)
                    stateRepository.update {
                        it.copy(status = MonitoringStatus.RUNNING)
                    }
                }
                is CycleResult.Idle -> when (result.reason) {
                    IdleReason.NO_AIRCRAFT ->
                        stateRepository.update {
                            it.copy(status = MonitoringStatus.RUNNING)
                        }
                    IdleReason.WAITING_FOR_LOCATION ->
                        stateRepository.update {
                            it.copy(status = MonitoringStatus.WAITING_FOR_LOCATION)
                        }
                }
                CycleResult.Offline ->
                    stateRepository.update {
                        it.copy(status = MonitoringStatus.OFFLINE)
                    }
                is CycleResult.Failure ->
                    stateRepository.update {
                        it.copy(status = MonitoringStatus.ERROR)
                    }
            }

            val failure = (result as? CycleResult.Failure)?.error
            val lastIntervalMs = (stateRepository.state.value.plannedIntervalSec
                ?: s.monitoringIntervalSec) * 1000L
            val delayMs = NextDelayPolicy.delayMs(
                success = result is CycleResult.Success,
                retryable = failure?.retryable ?: true,
                intervalMs = lastIntervalMs,
                consecutiveFailures = stateRepository.state.value.consecutiveFailures,
                retryAfterMs = (failure as? ApiError.RateLimited)?.retryAfterSec?.times(1000)
            )
            // Radar cadence follows the freshest snapshot on EVERY cycle;
            // the player silences itself when the snapshot goes stale.
            radar.update(stateRepository.state.value.lastSnapshot, lastIntervalMs)

            val nextAt = System.currentTimeMillis() + delayMs
            stateRepository.update { it.copy(nextCycleAtMs = nextAt) }
            refreshStatusNotification(delayMs)
            if (s.highPriorityMode) scheduleBackstopAlarm(nextAt)

            // Coroutine timers stop in CPU suspend. When no location
            // callback will wake us (manual mode, or GPS without a current
            // fix), hold a heartbeat wakelock across the wait so the next
            // cycle actually fires with the screen off. High-priority mode
            // already holds a continuous partial wakelock.
            val needsHeartbeat = !s.highPriorityMode &&
                (s.locationMode == LocationMode.MANUAL ||
                    locationRepository.status.value !is LocationStatus.Fix)
            if (needsHeartbeat) {
                val hb = heartbeatWakeLock ?: pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, HEARTBEAT_WAKELOCK_TAG
                ).also { heartbeatWakeLock = it }
                hb.acquire(delayMs + HEARTBEAT_GRACE_MS)
                stateRepository.update { it.copy(heartbeatHeld = true) }
            }
            try {
                withTimeoutOrNull(delayMs) { tickChannel.receive() }
            } finally {
                if (needsHeartbeat) releaseHeartbeat()
            }
        }
    }

    private fun releaseHeartbeat() {
        heartbeatWakeLock?.let { if (it.isHeld) it.release() }
        if (stateRepository.state.value.heartbeatHeld) {
            stateRepository.update { it.copy(heartbeatHeld = false) }
        }
    }

    private fun evaluateAlerts(
        snapshot: MonitoringSnapshot,
        settings: AppSettings
    ) {
        val now = System.currentTimeMillis()
        stateRepository.pruneSnoozes(now)
        val eval = alertEngine.evaluate(
            now, snapshot.ranked, alertStates, stateRepository.snoozes.value)
        // Drop states for aircraft no longer tracked.
        val trackedIds = (snapshot.ranked.map { it.aircraftId } +
            snapshot.noData.map { it.id }).toSet()
        alertStates = eval.states.filterKeys { it in trackedIds }
        for (event in eval.alerts) {
            notifier.postProximityAlert(event, settings.distanceUnit)
            stateRepository.emitAlert(event)
        }
    }

    private fun evaluateNearby(snapshot: MonitoringSnapshot, settings: AppSettings) {
        val wasInside = nearbyStates.filterValues { it.inside }.keys
        if (!settings.airspaceWatch || snapshot.airspaceRadiusKm == null) {
            if (nearbyStates.isNotEmpty()) {
                notifier.cancelAllNearby(wasInside)
                nearbyStates = emptyMap()
            }
            return
        }
        val now = System.currentTimeMillis()
        val eval = nearbyEngine.evaluate(
            now, snapshot.nearby, settings.airspaceRules, nearbyStates,
            stateRepository.nearbyMutedUntilMs.value)
        nearbyStates = eval.states.filterValues {
            it.inside || it.lastSeenMs?.let { seen -> now - seen < 30 * 60_000L } == true
        }
        val nowInside = nearbyStates.filterValues { it.inside }.keys
        val active = snapshot.nearby.filter { it.icao24 in nowInside }
        notifier.postNearbyAlerts(eval.alerts, active, settings.distanceUnit)
        val left = wasInside - nowInside
        if (left.isNotEmpty()) notifier.syncNearbyNotifications(left, active, settings.distanceUnit)
    }

    private fun scheduleBackstopAlarm(atMs: Long) {
        val pi = PendingIntent.getService(
            this, 0,
            intent(this, ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = SystemClock.elapsedRealtime() +
            (atMs - System.currentTimeMillis())
        runCatching {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
        }.onFailure { AppLog.w(TAG, "alarm schedule failed", throwable = it) }
    }

    private fun cancelBackstopAlarm() {
        val pi = PendingIntent.getService(
            this, 0,
            intent(this, ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pi)
    }

    /** Build + post the persistent notification only when text changed. */
    private fun refreshStatusNotification(retryInMs: Long? = null) {
        val st = stateRepository.state.value
        val snap = st.lastSnapshot
        val tracked = (snap?.ranked?.size ?: 0) + (snap?.noData?.size ?: 0)

        val title = when (st.status) {
            MonitoringStatus.PAUSED -> "Monitoring paused"
            MonitoringStatus.WAITING_FOR_LOCATION -> "Waiting for location"
            MonitoringStatus.OFFLINE ->
                "Offline — retrying in ${((retryInMs ?: 0) / 1000).coerceAtLeast(1)}s"
            MonitoringStatus.DEFERRED_DOZE -> "Deferred (battery saver)"
            MonitoringStatus.ERROR -> "Monitoring error"
            MonitoringStatus.STARTING -> "Starting…"
            MonitoringStatus.RUNNING -> "Monitoring $tracked aircraft"
            MonitoringStatus.STOPPED -> "Monitoring stopped"
        }
        val text = buildString {
            val closest = snap?.closest
            when {
                st.status == MonitoringStatus.ERROR ->
                    append(st.lastError?.message ?: "Unknown error")
                closest != null ->
                    append("Closest: ")
                        .append(AlertText.statusLine(closest, currentSettings.distanceUnit))
                tracked == 0 -> append("No tracked aircraft")
                else -> append("No aircraft in range")
            }
        }
        val rendered = "$title|$text"
        if (rendered != lastRenderedStatus) {
            lastRenderedStatus = rendered
            runCatching {
                NotificationManagerCompat.from(this)
                    .notify(
                        AlertNotifier.STATUS_NOTIFICATION_ID,
                        notifier.statusNotification(
                            title, text, paused = st.status == MonitoringStatus.PAUSED)
                    )
            }.onFailure { AppLog.w(TAG, "status notify failed", throwable = it) }
        }
    }

    private fun internalStop(clearDesired: Boolean = true) {
        AppLog.i(TAG, "stopping")
        running = false
        paused = false
        loopJob?.cancel()
        cancelBackstopAlarm()
        locationUploader.stop()
        locationRepository.release(LOCATION_OWNER)
        radar.stop()
        wakeLock?.let { if (it.isHeld) it.release() }
        highPriorityWakeLock?.let { if (it.isHeld) it.release() }
        heartbeatWakeLock?.let { if (it.isHeld) it.release() }
        tickWakeLock?.let { if (it.isHeld) it.release() }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
        stateRepository.update {
            it.copy(
                status = MonitoringStatus.STOPPED,
                nextCycleAtMs = null,
                wakeLockHeld = false,
                heartbeatHeld = false
            )
        }
        if (clearDesired) {
            serviceScope.launch { settingsRepository.setMonitoringDesired(false) }
        }
        stopSelf()
    }

    /** API 35+: dataSync FGS hit its daily timeout. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        AppLog.w(TAG, "onTimeout", "fgsType" to fgsType)
        if (fgsType == ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC &&
            locationRepository.hasPermission()
        ) {
            // GPS mode is available — continue as a location FGS.
            runCatching {
                ServiceCompat.startForeground(
                    this, AlertNotifier.STATUS_NOTIFICATION_ID,
                    notifier.statusNotification("Monitoring", "FlightRadius", false),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            }.onFailure {
                notifier.notifyDataSyncTimeout()
                internalStop(clearDesired = false)
            }
        } else {
            notifier.notifyDataSyncTimeout()
            internalStop(clearDesired = false)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Radar-app behavior: keep monitoring when the task is swiped away.
        AppLog.d(TAG, "task removed; continuing")
    }

    override fun onDestroy() {
        running = false
        cancelBackstopAlarm()
        loopJob?.cancel()
        locationUploader.stop()
        locationRepository.release(LOCATION_OWNER)
        radar.stop()
        runCatching { unregisterReceiver(dozeReceiver) }
        wakeLock?.let { if (it.isHeld) it.release() }
        highPriorityWakeLock?.let { if (it.isHeld) it.release() }
        heartbeatWakeLock?.let { if (it.isHeld) it.release() }
        tickWakeLock?.let { if (it.isHeld) it.release() }
        stateRepository.update { it.copy(status = MonitoringStatus.STOPPED) }
        serviceScope.cancel()
        super.onDestroy()
    }
}
