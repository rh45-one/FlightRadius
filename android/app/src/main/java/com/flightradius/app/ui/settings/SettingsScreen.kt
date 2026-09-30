package com.flightradius.app.ui.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.flightradius.app.ui.components.SectionHeader
import com.flightradius.app.ui.format.Format
import com.flightradius.app.ui.radar.locationLabel
import com.flightradius.app.ui.theme.extended
import java.util.Locale

private val INTERVAL_PRESETS = listOf(10, 15, 20, 30, 45, 60, 120, 300, 600)

@Composable
fun SettingsScreen(
    onOpenDebug: () -> Unit,
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

    LaunchedEffect(directMode) { if (!directMode) viewModel.refreshApiStatus() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Text(
            stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 8.dp)
        )

        // ---- Data source + account ----
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
                adaptive = settings.adaptiveCredits,
                nowMs = now,
                onAdaptiveChange = viewModel::setAdaptiveCredits
            )
        }

        // ---- Backend ----
        SectionHeader(stringResource(R.string.settings_backend))
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.padding(16.dp)) {
                if (directMode) {
                    Text(
                        stringResource(R.string.settings_backend_optional),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val usingDefault = settings.backendBaseUrl == null
                val url = urlText ?: settings.backendBaseUrl
                    ?: BuildConfig.DEFAULT_BACKEND_URL
                val urlErr = SettingsValidation.urlError(url)
                OutlinedTextField(
                    value = url,
                    onValueChange = { urlText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_backend_url)) },
                    isError = urlErr == FieldError.INVALID_URL,
                    supportingText = {
                        if (urlErr == FieldError.INVALID_URL) {
                            Text(stringResource(R.string.error_invalid_url))
                        } else if (usingDefault && urlText == null) {
                            Text(stringResource(R.string.settings_build_default))
                        }
                    },
                    singleLine = true
                )
                Row {
                    TextButton(
                        enabled = urlErr == null,
                        onClick = {
                            // Storing the build default as an override adds
                            // nothing — clear the override instead.
                            viewModel.saveBackendUrl(
                                if (com.flightradius.app.data.api.BackendUrl
                                        .normalize(url) ==
                                    com.flightradius.app.data.api.BackendUrl
                                        .normalize(BuildConfig.DEFAULT_BACKEND_URL))
                                    "" else url)
                            urlText = null
                        }
                    ) { Text(stringResource(R.string.action_save)) }
                    TextButton(onClick = { viewModel.testConnection() }) {
                        Text(stringResource(R.string.settings_test_connection))
                    }
                }
                when (val h = health) {
                    HealthState.Checking -> Text(
                        stringResource(R.string.checking),
                        style = MaterialTheme.typography.bodySmall)
                    is HealthState.Ok -> Text(
                        "status=${h.health.status} · opensky=${h.health.opensky_status} · " +
                            "uptime=${h.health.uptime?.toInt()}s",
                        color = MaterialTheme.colorScheme.extended.success,
                        style = MaterialTheme.typography.bodySmall)
                    is HealthState.Failed -> Text(
                        h.message,
                        color = MaterialTheme.colorScheme.extended.danger,
                        style = MaterialTheme.typography.bodySmall)
                    HealthState.Idle -> Unit
                }
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
        SectionHeader(stringResource(R.string.settings_import))
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.padding(16.dp)) {
                OutlinedButton(
                    enabled = !importing,
                    onClick = { viewModel.importFromBackend() }
                ) {
                    Text(
                        if (importing) stringResource(R.string.checking)
                        else stringResource(R.string.settings_import_button))
                }
                importResult?.let { r ->
                    Text(
                        stringResource(
                            R.string.import_result,
                            r.aircraftAdded, r.aircraftSkipped,
                            r.fleetsAdded, r.membershipsAdded),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.extended.success)
                }
                importError?.let {
                    Text(it, color = MaterialTheme.colorScheme.extended.danger,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // ---- Monitoring ----
        SectionHeader(stringResource(R.string.settings_monitoring))
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.settings_interval),
                    style = MaterialTheme.typography.bodyMedium)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (p in INTERVAL_PRESETS.take(5)) {
                        FilterChipLike(
                            selected = settings.monitoringIntervalSec == p,
                            label = "${p}s"
                        ) { viewModel.setInterval(p) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (p in INTERVAL_PRESETS.drop(5)) {
                        FilterChipLike(
                            selected = settings.monitoringIntervalSec == p,
                            label = "${p}s"
                        ) { viewModel.setInterval(p) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(
                        R.string.settings_radius,
                        Format.distance(settings.globalAlertRadiusKm,
                            settings.distanceUnit)),
                    style = MaterialTheme.typography.bodyMedium)
                val unitMax = Format.kmToUnit(200.0, settings.distanceUnit).toFloat()
                Slider(
                    value = Format.kmToUnit(settings.globalAlertRadiusKm,
                        settings.distanceUnit).toFloat().coerceIn(1f, unitMax),
                    onValueChange = { v ->
                        val km = if (settings.distanceUnit == DistanceUnit.MI)
                            v.toDouble() / Format.KM_TO_MI else v.toDouble()
                        viewModel.setGlobalRadius(km)
                    },
                    valueRange = 1f..unitMax
                )
                SwitchRow(
                    stringResource(R.string.settings_high_priority),
                    stringResource(R.string.settings_high_priority_body),
                    settings.highPriorityMode
                ) { viewModel.setHighPriority(it) }
                SwitchRow(
                    stringResource(R.string.settings_resume_boot),
                    stringResource(R.string.settings_resume_boot_body),
                    settings.resumeOnBoot
                ) { viewModel.setResumeOnBoot(it) }
                SwitchRow(
                    stringResource(R.string.settings_radar_chirp),
                    stringResource(R.string.settings_radar_chirp_body),
                    settings.radarMode
                ) { viewModel.setRadarMode(it) }
                if (settings.highPriorityMode &&
                    !BatteryOptimization.isIgnoring(context)
                ) {
                    TextButton(onClick = {
                        context.startActivity(BatteryOptimization.requestIntent(context))
                    }) {
                        Text(stringResource(R.string.action_disable_battery_opt))
                    }
                }
            }
        }

        // ---- Location ----
        SectionHeader(stringResource(R.string.settings_location))
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.padding(16.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    LocationMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = settings.locationMode == m,
                            onClick = { viewModel.setLocationMode(m) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = i, count = LocationMode.entries.size)
                        ) { Text(m.name) }
                    }
                }
                if (settings.locationMode == LocationMode.MANUAL) {
                    val lat = latText ?: (settings.manualLat?.toString() ?: "")
                    val lon = lonText ?: (settings.manualLon?.toString() ?: "")
                    val latErr = if (lat.isBlank()) null
                        else SettingsValidation.latitudeError(lat)
                    val lonErr = if (lon.isBlank()) null
                        else SettingsValidation.longitudeError(lon)
                    Row(Modifier.padding(top = 8.dp)) {
                        OutlinedTextField(
                            value = lat,
                            onValueChange = { latText = it },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.settings_lat)) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal),
                            isError = latErr != null,
                            singleLine = true
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = lon,
                            onValueChange = { lonText = it },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.settings_lon)) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal),
                            isError = lonErr != null,
                            singleLine = true
                        )
                    }
                    Row {
                        TextButton(
                            enabled = latErr == null && lonErr == null &&
                                lat.isNotBlank() && lon.isNotBlank(),
                            onClick = {
                                viewModel.setManualLocation(
                                    lat.toDoubleOrNull(), lon.toDoubleOrNull())
                                latText = null; lonText = null
                            }
                        ) { Text(stringResource(R.string.action_save)) }
                        TextButton(onClick = {
                            fix?.let {
                                latText = it.lat.toString()
                                lonText = it.lon.toString()
                            }
                        }) { Text(stringResource(R.string.settings_use_gps_fix)) }
                    }
                } else {
                    Text(
                        "GPS: ${settings.gpsAccuracy.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (a in GpsAccuracy.entries) {
                            FilterChipLike(
                                selected = settings.gpsAccuracy == a,
                                label = a.name.replace('_', ' ')
                            ) { viewModel.setGpsAccuracy(a) }
                        }
                    }
                }
                Text(
                    // Never print class names: R8 obfuscates them in release.
                    fix?.takeIf { locationStatus is LocationStatus.Fix }?.let {
                        String.format(Locale.ROOT, "%s · %.4f, %.4f",
                            locationLabel(locationStatus, it, now), it.lat, it.lon)
                    } ?: locationLabel(locationStatus, fix, now),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.padding(top = 8.dp))
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }) {
                    Text(stringResource(R.string.action_location_settings))
                }
            }
        }

        // ---- Alerts ----
        SectionHeader(stringResource(R.string.settings_alerts))
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.padding(16.dp)) {
                SwitchRow(
                    stringResource(R.string.settings_alert_sound), null,
                    settings.alertSound
                ) { viewModel.setAlertSound(it) }
                SwitchRow(
                    stringResource(R.string.settings_alert_vibration), null,
                    settings.alertVibration
                ) { viewModel.setAlertVibration(it) }
                SwitchRow(
                    stringResource(R.string.settings_alert_sheet), null,
                    settings.inAppAlertBanner
                ) { viewModel.setInAppAlert(it) }
                Row {
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE,
                                    context.packageName))
                    }) {
                        Text(stringResource(R.string.settings_notif_settings))
                    }
                    TextButton(onClick = { viewModel.testAlert() }) {
                        Text(stringResource(R.string.settings_test_alert))
                    }
                }
            }
        }

        // ---- Appearance ----
        SectionHeader(stringResource(R.string.settings_appearance))
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.padding(16.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = settings.themeMode == m,
                            onClick = { viewModel.setThemeMode(m) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = i, count = ThemeMode.entries.size)
                        ) {
                            Text(
                                when (m) {
                                    ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                    ThemeMode.DARK -> stringResource(R.string.theme_dark)
                                    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                }
                            )
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= 31) {
                    SwitchRow(
                        stringResource(R.string.settings_dynamic_color), null,
                        settings.dynamicColor
                    ) { viewModel.setDynamicColor(it) }
                }
                Row(
                    Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.settings_units),
                        Modifier.weight(1f))
                    SingleChoiceSegmentedButtonRow {
                        DistanceUnit.entries.forEachIndexed { i, u ->
                            SegmentedButton(
                                selected = settings.distanceUnit == u,
                                onClick = { viewModel.setUnit(u) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = i, count = DistanceUnit.entries.size)
                            ) { Text(if (u == DistanceUnit.KM) "km" else "mi") }
                        }
                    }
                }
            }
        }

        // ---- Debug ----
        SectionHeader(stringResource(R.string.settings_debug))
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.padding(16.dp)) {
                SwitchRow(
                    stringResource(R.string.settings_debug_logging), null,
                    settings.debugLogging
                ) { viewModel.setDebugLogging(it) }
                TextButton(onClick = onOpenDebug) {
                    Text(stringResource(R.string.settings_open_debug))
                }
            }
        }

        // ---- About ----
        SectionHeader(stringResource(R.string.settings_about))
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "FlightRadius ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Default backend: ${BuildConfig.DEFAULT_BACKEND_URL}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(80.dp))
    }

}

@Composable
internal fun SecretField(
    label: String,
    value: String,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false),
        singleLine = true
    )
}

@Composable
private fun FilterChipLike(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    androidx.compose.material3.FilterChip(
        selected = selected, onClick = onClick,
        label = { Text(label) })
}

@Composable
internal fun SwitchRow(
    title: String,
    body: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = androidx.compose.ui.semantics.Role.Switch,
                onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            body?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
