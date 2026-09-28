package com.flightradius.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.flightradius.app.data.prefs.AppSettings
import com.flightradius.app.data.prefs.GpsAccuracy
import com.flightradius.app.data.prefs.LocationMode
import com.flightradius.app.data.prefs.SettingsRepository
import com.flightradius.app.di.ApplicationScope
import com.flightradius.app.domain.LocationSource
import com.flightradius.app.domain.TimeSource
import com.flightradius.app.domain.UserFix
import com.flightradius.app.util.log.AppLog
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LocationStatus {
    /** Manual mode with invalid/absent coords, or manual fix (fix still emitted). */
    data object Manual : LocationStatus
    data object PermissionDenied : LocationStatus
    data object PlayServicesUnavailable : LocationStatus
    data object ProviderDisabled : LocationStatus
    data object Searching : LocationStatus
    data class Fix(val ageMs: Long, val accuracyM: Double?) : LocationStatus
}

/**
 * Ref-counted location provider. The monitoring service and (later) a
 * foreground UI both `acquire(owner)`; GPS runs while at least one owner
 * holds it. Manual mode just emits the configured coordinates.
 */
@Singleton
class LocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
    private val time: TimeSource
) {
    companion object {
        private const val TAG = "LocationRepo"
        private const val SEED_MAX_AGE_MS = 2 * 60_000L
        private const val STALE_FIX_MS = 5 * 60_000L
    }

    private val _fix = MutableStateFlow<UserFix?>(null)
    val fix: StateFlow<UserFix?> = _fix.asStateFlow()

    private val _status = MutableStateFlow<LocationStatus>(LocationStatus.Searching)
    val status: StateFlow<LocationStatus> = _status.asStateFlow()

    /** All mutable state below is guarded by [lock]. */
    private val lock = Any()
    private val owners = mutableSetOf<String>()
    private var fused: FusedLocationProviderClient? = null
    private var settingsJob: Job? = null
    private var applied: GpsRequest? = null
    private var lastSettings: AppSettings? = null

    private data class GpsRequest(val mode: LocationMode, val intervalMs: Long, val accuracy: GpsAccuracy)

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            val newFix = UserFix(
                lat = loc.latitude,
                lon = loc.longitude,
                accuracyM = if (loc.hasAccuracy()) loc.accuracy.toDouble() else null,
                timeMs = time.nowMs(),
                source = LocationSource.GPS
            )
            _fix.value = newFix
            _status.value = LocationStatus.Fix(ageMs = 0, accuracyM = newFix.accuracyM)
            AppLog.d(TAG, "fix", "lat" to loc.latitude, "lon" to loc.longitude,
                "acc" to newFix.accuracyM)
        }
    }

    /** Start location (idempotent via owner set). Thread-safe. */
    fun acquire(owner: String) = synchronized(lock) {
        if (!owners.add(owner)) return
        AppLog.d(TAG, "acquire", "owner" to owner, "owners" to owners.size)
        if (owners.size == 1) startLocked()
    }

    fun release(owner: String) = synchronized(lock) {
        if (!owners.remove(owner)) return
        AppLog.d(TAG, "release", "owner" to owner, "owners" to owners.size)
        if (owners.isEmpty()) stopLocked()
    }

    /**
     * Re-applies the last settings when GPS is stuck in a recoverable error
     * state (permission granted later, provider re-enabled, Play services
     * restored). Called at the start of every monitoring cycle and by the UI
     * after a permission grant / on resume.
     */
    fun refreshIfNeeded() = synchronized(lock) {
        val s = lastSettings ?: return
        if (owners.isEmpty()) return
        if (s.locationMode != LocationMode.GPS) return
        when (_status.value) {
            LocationStatus.PermissionDenied,
            LocationStatus.ProviderDisabled,
            LocationStatus.PlayServicesUnavailable -> {
                AppLog.i(TAG, "refreshIfNeeded: retrying GPS start")
                applied = null
                applySettingsLocked(s)
            }
            else -> Unit
        }
    }

    /**
     * Latest usable fix. GPS fixes older than 5 min count as none (the status
     * flow still shows the stale FIX status so the UI can explain).
     */
    fun currentFix(): UserFix? {
        val f = _fix.value ?: return null
        if (f.source == LocationSource.GPS && time.nowMs() - f.timeMs > STALE_FIX_MS) return null
        return f
    }

    private fun startLocked() {
        settingsJob?.cancel()
        settingsJob = scope.launch {
            settingsRepository.settings.collect { s ->
                synchronized(lock) { applySettingsLocked(s) }
            }
        }
    }

    private fun stopLocked() {
        settingsJob?.cancel(); settingsJob = null
        fused?.removeLocationUpdates(callback)
        applied = null
        lastSettings = null
        _fix.value = null
        _status.value = LocationStatus.Searching
    }

    @SuppressLint("MissingPermission") // checked in hasPermission()
    private fun applySettingsLocked(s: AppSettings) {
        lastSettings = s
        val wanted = GpsRequest(s.locationMode, s.monitoringIntervalSec * 1000L, s.gpsAccuracy)
        // GPS: skip redundant restarts. Manual: always re-emit (cheap, and
        // coordinate changes must propagate).
        if (wanted == applied && s.locationMode == LocationMode.GPS) return
        applied = wanted
        AppLog.i(TAG, "applySettings",
            "mode" to s.locationMode, "intervalMs" to wanted.intervalMs,
            "acc" to s.gpsAccuracy)

        fused?.removeLocationUpdates(callback)

        when (s.locationMode) {
            LocationMode.MANUAL -> emitManual(s)
            LocationMode.GPS -> startGps(s)
        }
    }

    private fun emitManual(s: AppSettings) {
        val lat = s.manualLat
        val lon = s.manualLon
        if (lat != null && lon != null &&
            lat in -90.0..90.0 && lon in -180.0..180.0
        ) {
            _fix.value = UserFix(lat, lon, accuracyM = 0.0, timeMs = time.nowMs(),
                source = LocationSource.MANUAL)
        } else {
            _fix.value = null
        }
        _status.value = LocationStatus.Manual
    }

    @SuppressLint("MissingPermission") // checked in hasPermission()
    private fun startGps(s: AppSettings) {
        if (GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS
        ) {
            _status.value = LocationStatus.PlayServicesUnavailable
            _fix.value = null
            AppLog.w(TAG, "play services unavailable")
            return
        }
        if (!hasPermission()) {
            _status.value = LocationStatus.PermissionDenied
            _fix.value = null
            AppLog.w(TAG, "location permission not granted")
            return
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!LocationManagerCompat.isLocationEnabled(lm)) {
            _status.value = LocationStatus.ProviderDisabled
            _fix.value = null
            AppLog.w(TAG, "location provider disabled")
            return
        }
        try {
            val client = fused ?: LocationServices
                .getFusedLocationProviderClient(context).also { fused = it }
            val req = LocationRequest.Builder(
                priorityFor(s.gpsAccuracy),
                s.monitoringIntervalSec * 1000L
            )
                .setMinUpdateIntervalMillis(s.monitoringIntervalSec * 1000L / 2)
                .setWaitForAccurateLocation(false)
                .build()
            client.requestLocationUpdates(req, callback, Looper.getMainLooper())
            _status.value = LocationStatus.Searching

            // Seed with a fresh last fix, if any.
            client.lastLocation.addOnSuccessListener { loc ->
                if (loc != null &&
                    time.nowMs() - loc.time <= SEED_MAX_AGE_MS &&
                    _fix.value == null
                ) {
                    val seed = UserFix(loc.latitude, loc.longitude,
                        accuracyM = if (loc.hasAccuracy()) loc.accuracy.toDouble() else null,
                        timeMs = loc.time, source = LocationSource.GPS)
                    _fix.value = seed
                    _status.value = LocationStatus.Fix(
                        ageMs = time.nowMs() - loc.time, accuracyM = seed.accuracyM)
                }
            }
        } catch (e: SecurityException) {
            _status.value = LocationStatus.PermissionDenied
            _fix.value = null
            AppLog.w(TAG, "SecurityException", throwable = e)
        }
    }

    fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun playServicesAvailable(): Boolean =
        GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    fun hasBackgroundLocationPermission(): Boolean =
        Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun priorityFor(acc: GpsAccuracy): Int = when (acc) {
        GpsAccuracy.HIGH -> Priority.PRIORITY_HIGH_ACCURACY
        GpsAccuracy.BALANCED -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
        GpsAccuracy.LOW_POWER -> Priority.PRIORITY_LOW_POWER
    }
}
