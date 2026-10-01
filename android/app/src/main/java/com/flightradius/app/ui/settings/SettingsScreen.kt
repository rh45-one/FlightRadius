package com.flightradius.app.ui.settings

import com.flightradius.app.ui.util.startActivitySafely
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.flightradius.app.ui.components.groupedSegmentedColors
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flightradius.app.BuildConfig
import com.flightradius.app.R
import com.flightradius.app.data.prefs.DataSource
import com.flightradius.app.data.prefs.GpsAccuracy
import com.flightradius.app.data.prefs.LocationMode
import com.flightradius.app.data.prefs.ThemeMode
import com.flightradius.app.domain.DistanceUnit
import com.flightradius.app.location.LocationStatus
import com.flightradius.app.service.BatteryOptimization
import com.flightradius.app.ui.components.GroupedDivider
import com.flightradius.app.ui.components.GroupedRow
import com.flightradius.app.ui.components.GroupedSection
import com.flightradius.app.ui.components.GroupedTextField
import com.flightradius.app.ui.components.NavigationChevron
import com.flightradius.app.ui.components.ScreenTitle
import com.flightradius.app.ui.components.SwitchRow
import com.flightradius.app.ui.format.Format
import com.flightradius.app.ui.radar.locationLabel
import com.flightradius.app.ui.theme.NumericFeatures
import com.flightradius.app.ui.theme.extended
import java.util.Locale

private val INTERVAL_PRESETS = listOf(10, 15, 20, 30, 45, 60, 120, 300, 600)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onOpenDebug: () -> Unit,
    onOpenAirspaceRules: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val locationStatus by viewModel.locationStatus.collectAsStateWithLifecycle()
    val fix by viewModel.fix.collectAsStateWithLifecycle()
    val health by viewModel.health.collectAsStateWithLifecycle()
    val apiStatus by viewModel.apiStatus.collectAsStateWithLifecycle()
    val apiStatusError by viewModel.apiStatusError.collectAsStateWithLifecycle()
    val importResult by viewModel.importResult.collectAsStateWithLifecycle()
    val importError by viewModel.importError.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val now by com.flightradius.app.ui.components.rememberNow()
    val context = LocalContext.current

    var urlText by remember { mutableStateOf<String?>(null) }
    var latText by remember { mutableStateOf<String?>(null) }
    var lonText by remember { mutableStateOf<String?>(null) }
    val storedCredentials by viewModel.storedCredentials.collectAsStateWithLifecycle()
    val credentialCheck by viewModel.credentialCheck.collectAsStateWithLifecycle()
    val credits by viewModel.credits.collectAsStateWithLifecycle()
    val monitoring by viewModel.monitoring.collectAsStateWithLifecycle()
    val directMode = settings.dataSource == DataSource.DIRECT
    val dbState by viewModel.aircraftDb.state.collectAsStateWithLifecycle()
    val updateCheck by viewModel.manualUpdateCheck.collectAsStateWithLifecycle()
    var showDbSheet by remember { mutableStateOf(false) }

    LaunchedEffect(directMode) { if (!directMode) viewModel.refreshApiStatus() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        ScreenTitle(stringResource(R.string.nav_settings))

        // ---- Alerts ----
        GroupedSection(header = stringResource(R.string.settings_alerts)) {
            val unit = settings.distanceUnit
            val unitMax = Format.kmToUnit(200.0, unit).toFloat()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.settings_alert_radius),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f))
                    Text(
                        Format.distance(settings.globalAlertRadiusKm, unit),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFeatureSettings = NumericFeatures),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Slider(
                    value = Format.kmToUnit(settings.globalAlertRadiusKm, unit)
                        .toFloat().coerceIn(1f, unitMax),
                    onValueChange = { v ->
                        val km = if (unit == DistanceUnit.MI)
                            v.toDouble() / Format.KM_TO_MI else v.toDouble()
                        viewModel.setGlobalRadius(km)
                    },
                    valueRange = 1f..unitMax
                )
            }
            GroupedDivider()
            SwitchRow(
                stringResource(R.string.settings_alert_sound), null,
                settings.alertSound
            ) { viewModel.setAlertSound(it) }
            GroupedDivider()
            SwitchRow(
                stringResource(R.string.settings_alert_vibration), null,
                settings.alertVibration
            ) { viewModel.setAlertVibration(it) }
            GroupedDivider()
            SwitchRow(
                stringResource(R.string.settings_alert_sheet), null,
                settings.inAppAlertBanner
            ) { viewModel.setInAppAlert(it) }
            GroupedDivider()
            AccentRow(
                title = stringResource(R.string.settings_test_alert),
                onClick = { viewModel.testAlert() }
            )
            if (Build.VERSION.SDK_INT >= 36) {
                GroupedDivider()
                LiveUpdatesRow()
            }
            GroupedDivider()
            GroupedRow(
                title = stringResource(R.string.settings_notif_settings),
                trailing = { NavigationChevron() },
                onClick = {
                    context.startActivitySafely(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        fallbackToAppDetails = true)
                }
            )
        }

        // ---- Nearby airspace ----
        NearbyAirspaceSection(
            settings = settings,
            dbState = dbState,
            onWatch = { viewModel.setAirspaceWatch(it) },
            onRadius = { viewModel.setAirspaceRadius(it) },
            onOpenRules = onOpenAirspaceRules,
            onOpenDb = { showDbSheet = true }
        )

        // ---- Monitoring ----
        GroupedSection(
            header = stringResource(R.string.settings_monitoring),
            footer = if (settings.resumeOnBoot && settings.locationMode == LocationMode.GPS)
                stringResource(R.string.issue_background_location_body) else null
        ) {
            var intervalMenu by remember { mutableStateOf(false) }
            Box {
                GroupedRow(
                    title = stringResource(R.string.settings_refresh_every),
                    trailing = {
                        Text(
                            Format.duration(settings.monitoringIntervalSec.toLong()),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFeatureSettings = NumericFeatures),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        NavigationChevron()
                    },
                    onClick = { intervalMenu = true }
                )
                DropdownMenu(
                    expanded = intervalMenu,
                    onDismissRequest = { intervalMenu = false }
                ) {
                    for (p in INTERVAL_PRESETS) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    Format.duration(p.toLong()),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontFeatureSettings = NumericFeatures))
                            },
                            trailingIcon = {
                                if (settings.monitoringIntervalSec == p) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            onClick = {
                                viewModel.setInterval(p)
                                intervalMenu = false
                            }
                        )
                    }
                }
            }
            GroupedDivider()
            SwitchRow(
                stringResource(R.string.settings_keep_screen_on),
                stringResource(R.string.settings_keep_screen_on_body),
                settings.keepScreenOn
            ) { viewModel.setKeepScreenOn(it) }
            GroupedDivider()
            SwitchRow(
                stringResource(R.string.credits_adaptive),
                stringResource(R.string.credits_adaptive_body),
                settings.adaptiveCredits
            ) { viewModel.setAdaptiveCredits(it) }
            GroupedDivider()
            SwitchRow(
                stringResource(R.string.settings_high_priority),
                stringResource(R.string.settings_high_priority_body),
                settings.highPriorityMode
            ) { viewModel.setHighPriority(it) }
            if (settings.highPriorityMode && !BatteryOptimization.isIgnoring(context)) {
                GroupedDivider()
                AccentRow(
                    title = stringResource(R.string.action_disable_battery_opt),
                    onClick = {
                        context.startActivitySafely(
                            BatteryOptimization.requestIntent(context), fallbackToAppDetails = true)
                    }
                )
            }
            GroupedDivider()
            SwitchRow(
                stringResource(R.string.settings_resume_boot),
                stringResource(R.string.settings_resume_boot_body),
                settings.resumeOnBoot
            ) { viewModel.setResumeOnBoot(it) }
            GroupedDivider()
            SwitchRow(
                stringResource(R.string.settings_radar_chirp),
                stringResource(R.string.settings_radar_chirp_body),
                settings.radarMode
            ) { viewModel.setRadarMode(it) }
        }

        // ---- Appearance ----
        GroupedSection(header = stringResource(R.string.settings_appearance)) {
            AppearanceRow(settings.themeMode) { viewModel.setThemeMode(it) }
        }

        // ---- Location ----
        GroupedSection(header = stringResource(R.string.settings_location)) {
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                LocationMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = settings.locationMode == m,
                        onClick = { viewModel.setLocationMode(m) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = i, count = LocationMode.entries.size),
                        colors = groupedSegmentedColors()
                    ) {
                        Text(
                            stringResource(
                                if (m == LocationMode.GPS) R.string.location_mode_gps
                                else R.string.location_mode_manual))
                    }
                }
            }
            GroupedDivider()
            if (settings.locationMode == LocationMode.MANUAL) {
                val lat = latText ?: (settings.manualLat?.toString() ?: "")
                val lon = lonText ?: (settings.manualLon?.toString() ?: "")
                val latErr = if (lat.isBlank()) null
                    else SettingsValidation.latitudeError(lat)
                val lonErr = if (lon.isBlank()) null
                    else SettingsValidation.longitudeError(lon)
                Row(Modifier.padding(horizontal = 4.dp)) {
                    GroupedTextField(
                        value = lat,
                        onValueChange = { latText = it },
                        label = stringResource(R.string.settings_lat),
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = latErr != null
                    )
                    Spacer(Modifier.width(8.dp))
                    GroupedTextField(
                        value = lon,
                        onValueChange = { lonText = it },
                        label = stringResource(R.string.settings_lon),
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = lonErr != null
                    )
                }
                GroupedDivider()
                AccentRow(
                    title = stringResource(R.string.action_save),
                    enabled = latErr == null && lonErr == null &&
                        lat.isNotBlank() && lon.isNotBlank(),
                    onClick = {
                        viewModel.setManualLocation(lat.toDoubleOrNull(), lon.toDoubleOrNull())
                        latText = null; lonText = null
                    }
                )
                GroupedDivider()
                AccentRow(
                    title = stringResource(R.string.settings_use_gps_fix),
                    onClick = {
                        fix?.let {
                            latText = it.lat.toString()
                            lonText = it.lon.toString()
                        }
                    }
                )
            } else {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        stringResource(R.string.settings_gps_accuracy),
                        style = MaterialTheme.typography.bodyLarge)
                    FlowRow(
                        Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (a in GpsAccuracy.entries) {
                            FilterChipLike(
                                selected = settings.gpsAccuracy == a,
                                label = gpsAccuracyLabel(a)
                            ) { viewModel.setGpsAccuracy(a) }
                        }
                    }
                }
            }
            GroupedDivider()
            GroupedRow(
                title = stringResource(R.string.settings_location_status),
                trailing = {
                    Text(
                        // Never print class names: R8 obfuscates them in release.
                        fix?.takeIf { locationStatus is LocationStatus.Fix }?.let {
                            String.format(Locale.ROOT, "%s · %.4f, %.4f",
                                locationLabel(locationStatus, it, now), it.lat, it.lon)
                        } ?: locationLabel(locationStatus, fix, now),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFeatureSettings = NumericFeatures),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
            GroupedDivider()
            GroupedRow(
                title = stringResource(R.string.action_location_settings),
                trailing = { NavigationChevron() },
                onClick = {
                    context.startActivitySafely(
                        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), fallbackToAppDetails = true)
                }
            )
        }

        // ---- Units ----
        GroupedSection(header = stringResource(R.string.settings_units)) {
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                DistanceUnit.entries.forEachIndexed { i, u ->
                    SegmentedButton(
                        selected = settings.distanceUnit == u,
                        onClick = { viewModel.setUnit(u) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = i, count = DistanceUnit.entries.size),
                        colors = groupedSegmentedColors()
                    ) { Text(if (u == DistanceUnit.KM) "km" else "mi") }
                }
            }
        }

        // ---- Flight data + account + credits ----
        DataSourceSection(settings.dataSource, viewModel::setDataSource)
        if (directMode) {
            OpenSkyAccountSection(
                stored = storedCredentials,
                check = credentialCheck,
                onSave = viewModel::saveDirectCredentials,
                onVerify = viewModel::verifyDirectCredentials,
                onRemove = viewModel::removeDirectCredentials
            )
        }
        if (directMode || credits.remaining != null) {
            CreditsSection(
                credits = credits,
                monitoring = monitoring,
                userIntervalSec = settings.monitoringIntervalSec,
                nowMs = now
            )
        }

        // ---- Backend ----
        GroupedSection(
            header = stringResource(R.string.settings_backend),
            footer = if (directMode) stringResource(R.string.settings_backend_optional) else null
        ) {
            val usingDefault = settings.backendBaseUrl == null
            val url = urlText ?: settings.backendBaseUrl
                ?: BuildConfig.DEFAULT_BACKEND_URL
            val urlErr = SettingsValidation.urlError(url)
            GroupedTextField(
                value = url,
                onValueChange = { urlText = it },
                label = stringResource(R.string.settings_backend_url),
                isError = urlErr == FieldError.INVALID_URL,
                supportingText = {
                    if (urlErr == FieldError.INVALID_URL) {
                        Text(stringResource(R.string.error_invalid_url))
                    } else if (usingDefault && urlText == null) {
                        Text(stringResource(R.string.settings_build_default))
                    }
                }
            )
            GroupedDivider()
            AccentRow(
                title = stringResource(R.string.action_save),
                enabled = urlErr == null,
                onClick = {
                    // Storing the build default as an override adds
                    // nothing — clear the override instead.
                    viewModel.saveBackendUrl(
                        if (com.flightradius.app.data.api.BackendUrl.normalize(url) ==
                            com.flightradius.app.data.api.BackendUrl
                                .normalize(BuildConfig.DEFAULT_BACKEND_URL))
                            "" else url)
                    urlText = null
                }
            )
            GroupedDivider()
            AccentRow(
                title = stringResource(R.string.settings_test_connection),
                onClick = { viewModel.testConnection() }
            )
            when (val h = health) {
                HealthState.Checking -> {
                    GroupedDivider()
                    GroupedRow(
                        title = stringResource(R.string.checking),
                        titleStyle = MaterialTheme.typography.bodyMedium)
                }
                is HealthState.Ok -> {
                    GroupedDivider()
                    GroupedRow(
                        title = "status=${h.health.status} · opensky=${h.health.opensky_status} · " +
                            "uptime=${h.health.uptime?.toInt()}s",
                        titleColor = MaterialTheme.colorScheme.extended.success,
                        titleStyle = MaterialTheme.typography.bodyMedium)
                }
                is HealthState.Failed -> {
                    GroupedDivider()
                    GroupedRow(
                        title = h.message,
                        titleColor = MaterialTheme.colorScheme.extended.danger,
                        titleStyle = MaterialTheme.typography.bodyMedium)
                }
                HealthState.Idle -> Unit
            }
        }

        if (!directMode) {
            BackendCredentialsSection(
                apiStatus = apiStatus,
                apiStatusError = apiStatusError,
                onSend = viewModel::sendBackendCredentials,
                onClear = viewModel::clearBackendCredentials
            )
        }

        // ---- Import ----
        GroupedSection(header = stringResource(R.string.settings_import)) {
            AccentRow(
                title = if (importing) stringResource(R.string.checking)
                else stringResource(R.string.settings_import_button),
                enabled = !importing,
                onClick = { viewModel.importFromBackend() }
            )
            importResult?.let { r ->
                GroupedDivider()
                GroupedRow(
                    title = stringResource(
                        R.string.import_result,
                        r.aircraftAdded, r.aircraftSkipped,
                        r.fleetsAdded, r.membershipsAdded),
                    titleColor = MaterialTheme.colorScheme.extended.success,
                    titleStyle = MaterialTheme.typography.bodyMedium)
            }
            importError?.let {
                GroupedDivider()
                GroupedRow(
                    title = it,
                    titleColor = MaterialTheme.colorScheme.extended.danger,
                    titleStyle = MaterialTheme.typography.bodyMedium)
            }
        }

        // ---- About ----
        UpdatesSection(
            frequency = settings.updateFrequency,
            onFrequency = { viewModel.setUpdateFrequency(it) },
            state = updateCheck,
            onCheckNow = { viewModel.checkForUpdates() }
        )

        GroupedSection(header = stringResource(R.string.settings_about)) {
            GroupedRow(
                title = "FlightRadius ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                subtitle = stringResource(
                    R.string.settings_default_backend, BuildConfig.DEFAULT_BACKEND_URL)
            )
            GroupedDivider()
            GroupedRow(
                title = stringResource(R.string.settings_show_welcome),
                trailing = { NavigationChevron() },
                onClick = { viewModel.showWelcome() }
            )
        }

        // ---- Debug ----
        GroupedSection(header = stringResource(R.string.settings_debug)) {
            SwitchRow(
                stringResource(R.string.settings_debug_logging), null,
                settings.debugLogging
            ) { viewModel.setDebugLogging(it) }
            GroupedDivider()
            GroupedRow(
                title = stringResource(R.string.settings_open_debug),
                trailing = { NavigationChevron() },
                onClick = onOpenDebug
            )
        }

        Spacer(Modifier.height(32.dp))
    }

    if (showDbSheet) {
        AircraftDbSheet(
            state = dbState,
            metered = { viewModel.aircraftDb.isMetered() },
            onDownload = { viewModel.aircraftDb.download() },
            onCancel = { viewModel.aircraftDb.cancel() },
            onCheck = { viewModel.checkDbUpdate() },
            onDelete = { viewModel.aircraftDb.delete() },
            onDismiss = { showDbSheet = false }
        )
    }
}

@Composable
internal fun AppearanceRow(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val options = listOf(
        ThemeMode.SYSTEM to R.string.theme_system,
        ThemeMode.LIGHT to R.string.theme_light,
        ThemeMode.DARK to R.string.theme_dark
    )
    SingleChoiceSegmentedButtonRow(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        options.forEachIndexed { i, (mode, label) ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                modifier = Modifier.heightIn(min = 48.dp),
                colors = groupedSegmentedColors()
            ) {
                Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** API 36+: whether Live Updates may be promoted; opens the system toggle. */
@Composable
private fun LiveUpdatesRow() {
    val context = LocalContext.current
    fun allowed() = Build.VERSION.SDK_INT >= 36 &&
        (context.getSystemService(android.app.NotificationManager::class.java)
            ?.canPostPromotedNotifications() == true)
    var on by remember { mutableStateOf(allowed()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { on = allowed() }
    GroupedRow(
        title = stringResource(R.string.settings_live_updates),
        subtitle = stringResource(R.string.settings_live_updates_body),
        trailing = {
            Text(
                stringResource(if (on) R.string.settings_on else R.string.settings_off),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            NavigationChevron()
        },
        onClick = {
            if (Build.VERSION.SDK_INT >= 36) {
                context.startActivitySafely(
                    Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    fallbackToAppDetails = true)
            }
        }
    )
}

@Composable
private fun gpsAccuracyLabel(a: GpsAccuracy): String = stringResource(
    when (a) {
        GpsAccuracy.HIGH -> R.string.gps_accuracy_high
        GpsAccuracy.BALANCED -> R.string.gps_accuracy_balanced
        GpsAccuracy.LOW_POWER -> R.string.gps_accuracy_low_power
    }
)

@Composable
private fun FilterChipLike(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected, onClick = onClick,
        label = { Text(label) })
}
