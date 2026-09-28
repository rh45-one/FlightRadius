package com.flightradius.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flightradius.app.domain.DistanceUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "flightradius_settings"
)

enum class GpsAccuracy { HIGH, BALANCED, LOW_POWER }
enum class LocationMode { GPS, MANUAL }
enum class ThemeMode { SYSTEM, DARK, LIGHT }

/**
 * App settings snapshot. [backendBaseUrl] is null when unset (BuildConfig
 * default applies). OpenSky credentials are NEVER stored on device — they
 * are write-only and forwarded straight to the backend.
 */
data class AppSettings(
    val backendBaseUrl: String? = null,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    val monitoringIntervalSec: Int = 15,
    val gpsAccuracy: GpsAccuracy = GpsAccuracy.BALANCED,
    val locationMode: LocationMode = LocationMode.GPS,
    val manualLat: Double? = null,
    val manualLon: Double? = null,
    val globalAlertRadiusKm: Double = 25.0,
    val alertSound: Boolean = true,
    val alertVibration: Boolean = true,
    val radarMode: Boolean = false,
    val highPriorityMode: Boolean = false,
    val resumeOnBoot: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val debugLogging: Boolean = false,
    val inAppAlertBanner: Boolean = true,
    /** Internal flag: monitoring should be running (survives reboot). */
    val monitoringDesired: Boolean = false
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val BACKEND_BASE_URL = stringPreferencesKey("backend_base_url")
        val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
        val MONITORING_INTERVAL_SEC = intPreferencesKey("monitoring_interval_sec")
        val GPS_ACCURACY = stringPreferencesKey("gps_accuracy")
        val LOCATION_MODE = stringPreferencesKey("location_mode")
        val MANUAL_LAT = doublePreferencesKey("manual_lat")
        val MANUAL_LON = doublePreferencesKey("manual_lon")
        val GLOBAL_ALERT_RADIUS_KM = doublePreferencesKey("global_alert_radius_km")
        val ALERT_SOUND = booleanPreferencesKey("alert_sound")
        val ALERT_VIBRATION = booleanPreferencesKey("alert_vibration")
        val RADAR_MODE = booleanPreferencesKey("radar_mode")
        val HIGH_PRIORITY_MODE = booleanPreferencesKey("high_priority_mode")
        val RESUME_ON_BOOT = booleanPreferencesKey("resume_on_boot")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val DEBUG_LOGGING = booleanPreferencesKey("debug_logging")
        val IN_APP_ALERT_BANNER = booleanPreferencesKey("in_app_alert_banner")
        val MONITORING_DESIRED = booleanPreferencesKey("monitoring_desired")
    }

    /** Emits [AppSettings]; falls back to defaults on corrupted prefs. */
    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { prefs -> prefs.toAppSettings() }

    private fun Preferences.toAppSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            backendBaseUrl = this[Keys.BACKEND_BASE_URL]?.takeIf { it.isNotBlank() },
            distanceUnit = enumOr(this[Keys.DISTANCE_UNIT], defaults.distanceUnit),
            monitoringIntervalSec =
                (this[Keys.MONITORING_INTERVAL_SEC] ?: defaults.monitoringIntervalSec)
                    .coerceIn(10, 600),
            gpsAccuracy = enumOr(this[Keys.GPS_ACCURACY], defaults.gpsAccuracy),
            locationMode = enumOr(this[Keys.LOCATION_MODE], defaults.locationMode),
            manualLat = this[Keys.MANUAL_LAT]?.takeIf { it in -90.0..90.0 },
            manualLon = this[Keys.MANUAL_LON]?.takeIf { it in -180.0..180.0 },
            globalAlertRadiusKm =
                (this[Keys.GLOBAL_ALERT_RADIUS_KM] ?: defaults.globalAlertRadiusKm)
                    .coerceIn(0.5, 500.0),
            alertSound = this[Keys.ALERT_SOUND] ?: defaults.alertSound,
            alertVibration = this[Keys.ALERT_VIBRATION] ?: defaults.alertVibration,
            radarMode = this[Keys.RADAR_MODE] ?: defaults.radarMode,
            highPriorityMode = this[Keys.HIGH_PRIORITY_MODE] ?: defaults.highPriorityMode,
            resumeOnBoot = this[Keys.RESUME_ON_BOOT] ?: defaults.resumeOnBoot,
            themeMode = enumOr(this[Keys.THEME_MODE], defaults.themeMode),
            dynamicColor = this[Keys.DYNAMIC_COLOR] ?: defaults.dynamicColor,
            debugLogging = this[Keys.DEBUG_LOGGING] ?: defaults.debugLogging,
            inAppAlertBanner = this[Keys.IN_APP_ALERT_BANNER] ?: defaults.inAppAlertBanner,
            monitoringDesired = this[Keys.MONITORING_DESIRED] ?: defaults.monitoringDesired
        )
    }

    private inline fun <reified T : Enum<T>> enumOr(raw: String?, fallback: T): T =
        raw?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    suspend fun setBackendBaseUrl(value: String?) = edit {
        if (value.isNullOrBlank()) it.remove(Keys.BACKEND_BASE_URL)
        else it[Keys.BACKEND_BASE_URL] = value.trim()
    }

    suspend fun setDistanceUnit(value: DistanceUnit) = edit { it[Keys.DISTANCE_UNIT] = value.name }

    suspend fun setMonitoringIntervalSec(value: Int) =
        edit { it[Keys.MONITORING_INTERVAL_SEC] = value.coerceIn(10, 600) }

    suspend fun setGpsAccuracy(value: GpsAccuracy) = edit { it[Keys.GPS_ACCURACY] = value.name }

    suspend fun setLocationMode(value: LocationMode) = edit { it[Keys.LOCATION_MODE] = value.name }

    suspend fun setManualLocation(lat: Double?, lon: Double?) = edit {
        if (lat != null && lat in -90.0..90.0) it[Keys.MANUAL_LAT] = lat
        else it.remove(Keys.MANUAL_LAT)
        if (lon != null && lon in -180.0..180.0) it[Keys.MANUAL_LON] = lon
        else it.remove(Keys.MANUAL_LON)
    }

    suspend fun setGlobalAlertRadiusKm(value: Double) =
        edit { it[Keys.GLOBAL_ALERT_RADIUS_KM] = value.coerceIn(0.5, 500.0) }

    suspend fun setAlertSound(value: Boolean) = edit { it[Keys.ALERT_SOUND] = value }
    suspend fun setAlertVibration(value: Boolean) = edit { it[Keys.ALERT_VIBRATION] = value }
    suspend fun setRadarMode(value: Boolean) = edit { it[Keys.RADAR_MODE] = value }
    suspend fun setHighPriorityMode(value: Boolean) = edit { it[Keys.HIGH_PRIORITY_MODE] = value }
    suspend fun setResumeOnBoot(value: Boolean) = edit { it[Keys.RESUME_ON_BOOT] = value }
    suspend fun setThemeMode(value: ThemeMode) = edit { it[Keys.THEME_MODE] = value.name }
    suspend fun setDynamicColor(value: Boolean) = edit { it[Keys.DYNAMIC_COLOR] = value }
    suspend fun setDebugLogging(value: Boolean) = edit { it[Keys.DEBUG_LOGGING] = value }
    suspend fun setInAppAlertBanner(value: Boolean) = edit { it[Keys.IN_APP_ALERT_BANNER] = value }
    suspend fun setMonitoringDesired(value: Boolean) = edit { it[Keys.MONITORING_DESIRED] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(block)
    }
}
