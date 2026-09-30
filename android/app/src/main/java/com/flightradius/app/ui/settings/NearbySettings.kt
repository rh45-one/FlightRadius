package com.flightradius.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flightradius.app.R
import com.flightradius.app.data.aircraftdb.DbState
import com.flightradius.app.data.prefs.AppSettings
import com.flightradius.app.domain.DistanceUnit
import com.flightradius.app.ui.components.GroupedDivider
import com.flightradius.app.ui.components.GroupedRow
import com.flightradius.app.ui.components.GroupedSection
import com.flightradius.app.ui.components.NavigationChevron
import com.flightradius.app.ui.components.SwitchRow
import com.flightradius.app.ui.format.Format
import com.flightradius.app.ui.theme.NumericFeatures
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun NearbyAirspaceSection(
    settings: AppSettings,
    dbState: DbState,
    onWatch: (Boolean) -> Unit,
    onRadius: (Double) -> Unit,
    onOpenRules: () -> Unit,
    onOpenDb: () -> Unit
) {
    val unit = settings.distanceUnit
    GroupedSection(header = stringResource(R.string.settings_nearby_header)) {
        SwitchRow(
            stringResource(R.string.settings_nearby_watch),
            stringResource(R.string.settings_nearby_watch_body),
            settings.airspaceWatch,
            onWatch
        )
        if (settings.airspaceWatch) {
            GroupedDivider()
            val unitMin = Format.kmToUnit(5.0, unit).toFloat()
            val unitMax = Format.kmToUnit(100.0, unit).toFloat()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.settings_nearby_area),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        Format.distance(settings.airspaceRadiusKm, unit),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFeatureSettings = NumericFeatures),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Slider(
                    value = Format.kmToUnit(settings.airspaceRadiusKm, unit).toFloat()
                        .coerceIn(unitMin, unitMax),
                    onValueChange = { v ->
                        val km = if (unit == DistanceUnit.MI) v.toDouble() / Format.KM_TO_MI
                        else v.toDouble()
                        onRadius(km.coerceIn(5.0, 100.0))
                    },
                    valueRange = unitMin..unitMax
                )
            }
            GroupedDivider()
            val active = settings.airspaceRules.count { it.enabled }
            GroupedRow(
                title = stringResource(R.string.settings_nearby_alerts),
                trailing = {
                    Text(
                        if (active == 0) stringResource(R.string.settings_nearby_alerts_off)
                        else pluralStringResource(R.plurals.settings_nearby_alerts_on, active, active),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    NavigationChevron()
                },
                onClick = onOpenRules
            )
        }
        GroupedDivider()
        GroupedRow(
            title = stringResource(R.string.settings_aircraft_db),
            trailing = {
                Text(
                    dbRowValue(dbState),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFeatureSettings = NumericFeatures),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                NavigationChevron()
            },
            onClick = onOpenDb
        )
    }
}

@Composable
private fun dbRowValue(state: DbState): String = when (state) {
    DbState.NotDownloaded -> stringResource(R.string.db_state_not_downloaded)
    is DbState.Downloading -> stringResource(R.string.db_state_percent, (state.progress * 100).toInt())
    is DbState.Ready -> dumpLabel(state)
    is DbState.Failed -> stringResource(R.string.db_state_failed)
}

/** "Aug 2025" from the source key (`…-2025-08.csv`), else the import date. */
private fun dumpLabel(ready: DbState.Ready): String {
    val m = Regex("(\\d{4})-(\\d{2})\\.csv").find(ready.sourceKey)
    val date = if (m != null) {
        val cal = java.util.Calendar.getInstance()
        cal.clear()
        cal.set(m.groupValues[1].toInt(), m.groupValues[2].toInt() - 1, 1)
        cal.time
    } else Date(ready.importedAtMs)
    return SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(date)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AircraftDbSheet(
    state: DbState,
    metered: () -> Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onCheck: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmMetered by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(false) }
    val startDownload = {
        if (metered()) confirmMetered = true else onDownload()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(
                stringResource(R.string.db_sheet_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                stringResource(R.string.db_sheet_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(Modifier.height(16.dp))
            when (state) {
                DbState.NotDownloaded -> PrimaryAction(stringResource(R.string.db_action_download), startDownload)
                is DbState.Failed -> {
                    Text(
                        stringResource(R.string.db_failed_summary, state.message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    PrimaryAction(stringResource(R.string.db_action_download), startDownload)
                }
                is DbState.Downloading -> {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        stringResource(R.string.db_state_percent, (state.progress * 100).toInt()),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFeatureSettings = NumericFeatures),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text(stringResource(R.string.db_action_cancel)) }
                }
                is DbState.Ready -> {
                    Text(
                        stringResource(
                            R.string.db_ready_summary, dumpLabel(state),
                            String.format(Locale.getDefault(), "%,d", state.rowCount)),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    if (state.updateAvailable) {
                        Text(
                            stringResource(R.string.db_update_available),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        PrimaryAction(stringResource(R.string.db_action_update), startDownload)
                    } else {
                        if (checked) {
                            Text(
                                stringResource(R.string.db_up_to_date),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        OutlinedButton(
                            onClick = { checked = true; onCheck() },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) { Text(stringResource(R.string.db_action_check)) }
                    }
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            stringResource(R.string.db_action_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmMetered) {
        AlertDialog(
            onDismissRequest = { confirmMetered = false },
            title = { Text(stringResource(R.string.db_metered_title)) },
            text = { Text(stringResource(R.string.db_metered_body)) },
            confirmButton = {
                TextButton(onClick = { confirmMetered = false; onDownload() }) {
                    Text(stringResource(R.string.db_metered_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmMetered = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun PrimaryAction(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    ) { Text(label, style = MaterialTheme.typography.titleMedium) }
}
