package com.flightradius.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flightradius.app.BuildConfig
import com.flightradius.app.data.update.AvailableUpdate
import com.flightradius.app.data.update.UpdateUrls
import com.flightradius.app.domain.AirspaceRule
import com.flightradius.app.domain.UpdateFrequency
import com.flightradius.app.domain.DistanceUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "flightradius_settings"
)

enum class GpsAccuracy { HIGH, BALANCED, LOW_POWER }
enum class LocationMode { GPS, MANUAL }
enum class ThemeMode { SYSTEM, DARK, LIGHT }

private val rulesJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** What was imported from the OpenSky aircraft database. */
data class AircraftDbMeta(
    val sourceKey: String,
    val etag: String?,
    val importedAtMs: Long,
    val rowCount: Int,
    val classifiedCount: Int,
    val lastCheckMs: Long
)

/** Where flight data comes from. */
enum class DataSource {
    /** The app queries OpenSky itself (default; works anywhere). */
    DIRECT,

    /** A self-hosted FlightRadius backend proxies OpenSky. */
    BACKEND
}

/**
 * App settings snapshot. [backendBaseUrl] is null when unset (BuildConfig
 * default applies). OpenSky credentials are not part of settings: they live
 * encrypted in CredentialStore.
 */
data class AppSettings(
    val dataSource: DataSource = DataSource.DIRECT,
    /** Stretch the OpenSky credit balance until the daily refill. */
    val adaptiveCredits: Boolean = true,
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
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val updateFrequency: UpdateFrequency = UpdateFrequency.DAILY,
    val lastUpdateCheckAtMs: Long = 0L,
    val availableUpdate: AvailableUpdate? = null,
    val dynamicColor: Boolean = false,
    val debugLogging: Boolean = false,
    val inAppAlertBanner: Boolean = true,
    val keepScreenOn: Boolean = true,
    val onboardingDone: Boolean = false,
    /** Watch every aircraft around the user (off by default). */
    val airspaceWatch: Boolean = false,
    val airspaceRadiusKm: Double = 25.0,
    val airspaceRules: List<AirspaceRule> = AirspaceRule.DEFAULTS,
    /** Internal flag: monitoring should be running (survives reboot). */
    val monitoringDesired: Boolean = false,
    /** Ids of groups folded in the Aircraft tab (default: all expanded). */
    val collapsedGroupIds: Set<Long> = emptySet(),
    /** false = stop monitoring when the app leaves the foreground; restart on return. */
    val backgroundMonitoring: Boolean = true
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val DATA_SOURCE = stringPreferencesKey("data_source")
        val ADAPTIVE_CREDITS = booleanPreferencesKey("adaptive_credits")
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
        val BACKGROUND_MONITORING = booleanPreferencesKey("background_monitoring")
        val COLLAPSED_GROUPS = stringSetPreferencesKey("collapsed_groups")
        val UPDATE_FREQUENCY = stringPreferencesKey("update_frequency")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check_ms")
        val UPDATE_VERSION = stringPreferencesKey("update_available_version")
        val UPDATE_URL = stringPreferencesKey("update_available_url")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val DEBUG_LOGGING = booleanPreferencesKey("debug_logging")
        val IN_APP_ALERT_BANNER = booleanPreferencesKey("in_app_alert_banner")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val AIRSPACE_WATCH = booleanPreferencesKey("airspace_watch")
        val AIRSPACE_RADIUS_KM = doublePreferencesKey("airspace_radius_km")
        val AIRSPACE_RULES = stringPreferencesKey("airspace_rules_json")
        val DB_SOURCE_KEY = stringPreferencesKey("aircraft_db_source_key")
        val DB_ETAG = stringPreferencesKey("aircraft_db_etag")
        val DB_IMPORTED_AT = longPreferencesKey("aircraft_db_imported_at")
        val DB_ROWS = intPreferencesKey("aircraft_db_rows")
        val DB_CLASSIFIED = intPreferencesKey("aircraft_db_classified")
        val DB_LAST_CHECK = longPreferencesKey("aircraft_db_last_check")
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
            dataSource = enumOr(this[Keys.DATA_SOURCE], defaults.dataSource),
            adaptiveCredits = this[Keys.ADAPTIVE_CREDITS] ?: defaults.adaptiveCredits,
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
            updateFrequency = enumOr(this[Keys.UPDATE_FREQUENCY], defaults.updateFrequency),
            lastUpdateCheckAtMs = this[Keys.LAST_UPDATE_CHECK] ?: 0L,
            availableUpdate = this[Keys.UPDATE_VERSION]?.let { v ->
                this[Keys.UPDATE_URL]?.let {
                    AvailableUpdate(v, UpdateUrls.safe(it, BuildConfig.UPDATE_REPO))
                }
            },
            dynamicColor = this[Keys.DYNAMIC_COLOR] ?: defaults.dynamicColor,
            debugLogging = this[Keys.DEBUG_LOGGING] ?: defaults.debugLogging,
            inAppAlertBanner = this[Keys.IN_APP_ALERT_BANNER] ?: defaults.inAppAlertBanner,
            keepScreenOn = this[Keys.KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
            onboardingDone = this[Keys.ONBOARDING_DONE] ?: defaults.onboardingDone,
            airspaceWatch = this[Keys.AIRSPACE_WATCH] ?: defaults.airspaceWatch,
            airspaceRadiusKm =
                (this[Keys.AIRSPACE_RADIUS_KM] ?: defaults.airspaceRadiusKm).coerceIn(5.0, 100.0),
            airspaceRules = decodeRules(this[Keys.AIRSPACE_RULES]),
            monitoringDesired = this[Keys.MONITORING_DESIRED] ?: defaults.monitoringDesired,
            collapsedGroupIds = this[Keys.COLLAPSED_GROUPS].orEmpty()
                .mapNotNull { it.toLongOrNull() }.toSet(),
            backgroundMonitoring = this[Keys.BACKGROUND_MONITORING] ?: defaults.backgroundMonitoring
        )
    }

    private fun decodeRules(raw: String?): List<AirspaceRule> =
        raw?.let { runCatching { rulesJson.decodeFromString<List<AirspaceRule>>(it) }.getOrNull() }
            ?: AirspaceRule.DEFAULTS

    /** Aircraft database import metadata (null until a database was imported). */
    val aircraftDbMeta: Flow<AircraftDbMeta?> = context.settingsDataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { prefs ->
            val rows = prefs[Keys.DB_ROWS]
            val importedAt = prefs[Keys.DB_IMPORTED_AT]
            if (rows == null || importedAt == null) null
            else AircraftDbMeta(
                sourceKey = prefs[Keys.DB_SOURCE_KEY].orEmpty(),
                etag = prefs[Keys.DB_ETAG],
                importedAtMs = importedAt,
                rowCount = rows,
                classifiedCount = prefs[Keys.DB_CLASSIFIED] ?: 0,
                lastCheckMs = prefs[Keys.DB_LAST_CHECK] ?: 0L
            )
        }

    suspend fun setAircraftDbMeta(meta: AircraftDbMeta?) = edit {
        if (meta == null) {
            it.remove(Keys.DB_SOURCE_KEY); it.remove(Keys.DB_ETAG); it.remove(Keys.DB_IMPORTED_AT)
            it.remove(Keys.DB_ROWS); it.remove(Keys.DB_CLASSIFIED); it.remove(Keys.DB_LAST_CHECK)
        } else {
            it[Keys.DB_SOURCE_KEY] = meta.sourceKey
            if (meta.etag != null) it[Keys.DB_ETAG] = meta.etag else it.remove(Keys.DB_ETAG)
            it[Keys.DB_IMPORTED_AT] = meta.importedAtMs
            it[Keys.DB_ROWS] = meta.rowCount
            it[Keys.DB_CLASSIFIED] = meta.classifiedCount
            it[Keys.DB_LAST_CHECK] = meta.lastCheckMs
        }
    }

    suspend fun setAircraftDbLastCheck(ms: Long) = edit { it[Keys.DB_LAST_CHECK] = ms }

    suspend fun setAirspaceWatch(value: Boolean) = edit { it[Keys.AIRSPACE_WATCH] = value }
    suspend fun setAirspaceRadiusKm(value: Double) =
        edit { it[Keys.AIRSPACE_RADIUS_KM] = value.coerceIn(5.0, 100.0) }
    suspend fun setAirspaceRules(rules: List<AirspaceRule>) =
        edit { it[Keys.AIRSPACE_RULES] = rulesJson.encodeToString(rules) }

    private inline fun <reified T : Enum<T>> enumOr(raw: String?, fallback: T): T =
        raw?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    suspend fun setDataSource(value: DataSource) = edit { it[Keys.DATA_SOURCE] = value.name }
    suspend fun setAdaptiveCredits(value: Boolean) = edit { it[Keys.ADAPTIVE_CREDITS] = value }

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
    suspend fun setBackgroundMonitoring(value: Boolean) =
        edit { it[Keys.BACKGROUND_MONITORING] = value }
    suspend fun setGroupCollapsed(groupId: Long, collapsed: Boolean) = edit {
        val current = it[Keys.COLLAPSED_GROUPS].orEmpty()
        val id = groupId.toString()
        it[Keys.COLLAPSED_GROUPS] = if (collapsed) current + id else current - id
    }
    suspend fun setUpdateFrequency(value: UpdateFrequency) =
        edit { it[Keys.UPDATE_FREQUENCY] = value.name }
    suspend fun setLastUpdateCheck(ms: Long) = edit { it[Keys.LAST_UPDATE_CHECK] = ms }
    suspend fun setAvailableUpdate(update: AvailableUpdate?) = edit {
        if (update == null) {
            it.remove(Keys.UPDATE_VERSION); it.remove(Keys.UPDATE_URL)
        } else {
            it[Keys.UPDATE_VERSION] = update.version; it[Keys.UPDATE_URL] = update.url
        }
    }
    suspend fun setDynamicColor(value: Boolean) = edit { it[Keys.DYNAMIC_COLOR] = value }
    suspend fun setDebugLogging(value: Boolean) = edit { it[Keys.DEBUG_LOGGING] = value }
    suspend fun setInAppAlertBanner(value: Boolean) = edit { it[Keys.IN_APP_ALERT_BANNER] = value }
    suspend fun setKeepScreenOn(value: Boolean) = edit { it[Keys.KEEP_SCREEN_ON] = value }
    suspend fun setOnboardingDone(value: Boolean = true) = edit { it[Keys.ONBOARDING_DONE] = value }
    suspend fun setMonitoringDesired(value: Boolean) = edit { it[Keys.MONITORING_DESIRED] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(block)
    }
}
