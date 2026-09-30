package com.flightradius.app.ui.aircraft

import com.flightradius.app.ui.format.W
import com.flightradius.app.ui.format.Words
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
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
import com.flightradius.app.ui.components.groupedSegmentedColors
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.flightradius.app.R
import com.flightradius.app.data.prefs.AppSettings
import com.flightradius.app.domain.AircraftMeta
import com.flightradius.app.domain.Fleet
import com.flightradius.app.domain.IdentifierType
import com.flightradius.app.domain.TrackedAircraft
import com.flightradius.app.ui.format.Format
import com.flightradius.app.ui.theme.CodeFeatures
import com.flightradius.app.ui.theme.extended
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun AddAircraftSheet(
    viewModel: AircraftViewModel,
    settings: AppSettings,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var identifier by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(IdentifierType.CALLSIGN) }
    var notes by remember { mutableStateOf("") }
    var radiusText by remember { mutableStateOf("") }
    var check by remember { mutableStateOf<CheckResult>(CheckResult.Idle) }
    var error by remember { mutableStateOf<String?>(null) }

    val validation = AircraftForms.validate(identifier, type, viewModel.existingIdentifiers())
    val suggestion = AircraftForms.suggestType(identifier)
    val canAdd = validation == AddValidation.VALID &&
        radiusText.isBlank() || (radiusText.toDoubleOrNull() != null)

    Column(
        Modifier
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            stringResource(R.string.aircraft_add_title),
            style = MaterialTheme.typography.titleLarge)

        SingleChoiceSegmentedButtonRow(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            IdentifierType.entries.forEachIndexed { i, t ->
                SegmentedButton(
                    selected = type == t,
                    onClick = { type = t; check = CheckResult.Idle },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = i, count = IdentifierType.entries.size),
                    colors = groupedSegmentedColors()
                ) { Text(t.label()) }
            }
        }

        var regMatch by remember { mutableStateOf<Pair<String, AircraftMeta>?>(null) }
        LaunchedEffect(identifier) {
            regMatch = null
            if (identifier.length >= 3) {
                delay(250)
                regMatch = viewModel.lookupRegistration(identifier)
            }
        }
        regMatch?.let { (icao, meta) ->
            TextButton(onClick = {
                type = IdentifierType.ICAO24
                identifier = icao
                check = CheckResult.Idle
            }) {
                Text(
                    stringResource(
                        R.string.aircraft_track_by_icao24,
                        listOfNotNull(icao, meta.registration, meta.typecode ?: meta.model)
                            .joinToString(" · ")))
            }
        }

        if (suggestion == IdentifierType.ICAO24 && type == IdentifierType.CALLSIGN) {
            TextButton(onClick = { type = IdentifierType.ICAO24 }) {
                Text(stringResource(R.string.aircraft_suggest_icao24))
            }
        }

        OutlinedTextField(
            value = identifier,
            onValueChange = {
                identifier = if (type == IdentifierType.CALLSIGN) it.uppercase() else it
                check = CheckResult.Idle
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(if (type == IdentifierType.CALLSIGN)
                    stringResource(R.string.aircraft_callsign)
                else stringResource(R.string.aircraft_icao24))
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters),
            isError = validation == AddValidation.INVALID_FORMAT ||
                validation == AddValidation.DUPLICATE,
            supportingText = {
                when (validation) {
                    AddValidation.INVALID_FORMAT ->
                        Text(stringResource(R.string.aircraft_invalid_format))
                    AddValidation.DUPLICATE ->
                        Text(stringResource(R.string.aircraft_duplicate))
                    else -> Unit
                }
            },
            singleLine = true
        )

        // Optional backend check (non-blocking).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            TextButton(
                enabled = validation == AddValidation.VALID &&
                    check != CheckResult.Checking,
                onClick = {
                    scope.launch {
                        check = CheckResult.Checking
                        check = if (type == IdentifierType.CALLSIGN) {
                            viewModel.checkCallsign(identifier)
                        } else {
                            viewModel.checkIcao24(identifier)
                        }
                    }
                }
            ) { Text(stringResource(R.string.aircraft_check)) }
            when (check) {
                CheckResult.Checking -> Text(
                    stringResource(R.string.checking),
                    style = MaterialTheme.typography.bodySmall)
                CheckResult.Live -> Text(
                    stringResource(R.string.aircraft_live_now),
                    color = MaterialTheme.colorScheme.extended.success,
                    style = MaterialTheme.typography.bodySmall)
                CheckResult.NoData -> Text(
                    stringResource(R.string.aircraft_no_data),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
                is CheckResult.NetworkError -> Text(
                    (check as CheckResult.NetworkError).message,
                    color = MaterialTheme.colorScheme.extended.danger,
                    style = MaterialTheme.typography.bodySmall)
                CheckResult.Idle -> Unit
            }
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.aircraft_notes)) },
            singleLine = true
        )

        OutlinedTextField(
            value = radiusText,
            onValueChange = { radiusText = it },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(stringResource(
                    R.string.aircraft_radius_override,
                    Format.distanceUnitLabel(settings.distanceUnit)))
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = radiusText.isNotBlank() && radiusText.toDoubleOrNull() == null,
            supportingText = {
                if (radiusText.isNotBlank() && radiusText.toDoubleOrNull() == null) {
                    Text(stringResource(R.string.error_not_a_number))
                }
            },
            singleLine = true
        )

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.extended.danger,
                style = MaterialTheme.typography.bodySmall)
        }

        Row(Modifier.padding(top = 8.dp)) {
            Spacer(Modifier.weight(1f))
            Button(
                enabled = validation == AddValidation.VALID &&
                    (radiusText.isBlank() || radiusText.toDoubleOrNull() != null),
                onClick = {
                    // Radius input is in the display unit; store as km.
                    val radiusKm = radiusText.toDoubleOrNull()?.let { v ->
                        if (settings.distanceUnit == com.flightradius.app.domain.DistanceUnit.MI) {
                            v / Format.KM_TO_MI
                        } else v
                    }
                    viewModel.add(identifier, type, notes, radiusKm) { ok ->
                        if (ok) onDone()
                        else error = Words.get(W.ERR_ADD)
                    }
                }
            ) { Text(stringResource(R.string.action_add)) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun BulkAddSheet(
    viewModel: AircraftViewModel,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<BulkAddEntry>>(emptyList()) }
    var parsed by remember { mutableStateOf(false) }
    var liveCallsigns by remember { mutableStateOf<Set<String>?>(null) }
    var validating by remember { mutableStateOf(false) }

    Column(
        Modifier
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            stringResource(R.string.aircraft_bulk_title),
            style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; parsed = false; liveCallsigns = null },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .heightIn(min = 120.dp),
            label = { Text(stringResource(R.string.aircraft_bulk_hint)) }
        )
        Text(
            stringResource(R.string.aircraft_bulk_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        Row(Modifier.padding(vertical = 8.dp)) {
            OutlinedButton(
                enabled = text.isNotBlank(),
                onClick = {
                    entries = AircraftForms.parseBulk(
                        text, viewModel.existingIdentifiers())
                    parsed = true
                    scope.launch {
                        validating = true
                        liveCallsigns = viewModel.validateBulkCallsigns(
                            entries.filter {
                                it.type == IdentifierType.CALLSIGN &&
                                    it.status == BulkAddStatus.NEW
                            }.map { it.identifier })
                        validating = false
                    }
                }
            ) { Text(stringResource(R.string.aircraft_parse)) }
        }

        if (parsed) {
            if (entries.isEmpty()) {
                Text(
                    stringResource(R.string.aircraft_bulk_none),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(entries) { e ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = e.selected,
                                enabled = e.status == BulkAddStatus.NEW,
                                onCheckedChange = { checked ->
                                    entries = entries.map {
                                        if (it.identifier == e.identifier)
                                            it.copy(selected = checked)
                                        else it
                                    }
                                }
                            )
                            Text(
                                e.identifier,
                                Modifier.padding(end = 8.dp),
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFeatureSettings = CodeFeatures))
                            if (e.ambiguous) {
                                FilterChip(
                                    selected = e.type == IdentifierType.ICAO24,
                                    onClick = {
                                        val newType =
                                            if (e.type == IdentifierType.ICAO24)
                                                IdentifierType.CALLSIGN
                                            else IdentifierType.ICAO24
                                        entries = entries.map {
                                            if (it.identifier == e.identifier)
                                                AircraftForms.retype(
                                                    it, newType,
                                                    viewModel
                                                        .existingIdentifiers())
                                            else it
                                        }
                                        if (newType == IdentifierType.CALLSIGN) {
                                            scope.launch {
                                                validating = true
                                                val live = viewModel
                                                    .validateBulkCallsigns(
                                                        listOf(
                                                            entries.first {
                                                                it.identifier ==
                                                                    e.identifier
                                                            }.identifier))
                                                liveCallsigns =
                                                    (liveCallsigns
                                                        ?: emptySet()) +
                                                        (live ?: emptySet())
                                                validating = false
                                            }
                                        }
                                    },
                                    label = {
                                        Text(
                                            if (e.type == IdentifierType.ICAO24)
                                                stringResource(R.string.aircraft_type_icao24)
                                            else stringResource(R.string.aircraft_callsign),
                                            style = MaterialTheme.typography
                                                .labelSmall)
                                    },
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                            Text(
                                when {
                                    e.status == BulkAddStatus.INVALID ->
                                        stringResource(R.string.bulk_invalid)
                                    e.status == BulkAddStatus.ALREADY_TRACKED ->
                                        stringResource(R.string.bulk_tracked)
                                    e.type == IdentifierType.ICAO24 ->
                                        stringResource(R.string.bulk_not_validated)
                                    validating ->
                                        stringResource(R.string.checking)
                                    liveCallsigns != null ->
                                        if (e.identifier in liveCallsigns!!)
                                            stringResource(R.string.aircraft_live_now)
                                        else stringResource(R.string.aircraft_no_data)
                                    else -> e.type?.label() ?: ""
                                },
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                color = when (e.status) {
                                    BulkAddStatus.INVALID -> MaterialTheme.colorScheme.extended.danger
                                    BulkAddStatus.ALREADY_TRACKED ->
                                        MaterialTheme.colorScheme.extended.warning
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
                val selected = entries.count {
                    it.selected && it.status == BulkAddStatus.NEW
                }
                Row(Modifier.padding(top = 8.dp)) {
                    Spacer(Modifier.weight(1f))
                    Button(
                        enabled = selected > 0,
                        onClick = {
                            viewModel.addBulk(entries) { onDone() }
                        }
                    ) {
                        Text(stringResource(R.string.bulk_add_n, selected))
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun EditAircraftSheet(
    aircraft: TrackedAircraft,
    fleets: List<Fleet>,
    settings: AppSettings,
    viewModel: AircraftViewModel,
    onDone: () -> Unit
) {
    var notes by remember { mutableStateOf(aircraft.notes ?: "") }
    var useOverride by remember { mutableStateOf(aircraft.alertRadiusKm != null) }
    var radiusKm by remember {
        mutableStateOf(aircraft.alertRadiusKm ?: settings.globalAlertRadiusKm)
    }
    var memberIds by remember {
        mutableStateOf(
            fleets.filter { aircraft.id in it.memberIds }.map { it.id }.toSet()
        )
    }
    val unit = settings.distanceUnit
    val maxKm = 200.0
    val unitMax = Format.kmToUnit(maxKm, unit).toFloat()

    Column(
        Modifier
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            aircraft.identifier,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFeatureSettings = CodeFeatures))
        Text(aircraft.type.label(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            label = { Text(stringResource(R.string.aircraft_notes)) }
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text(
                stringResource(R.string.aircraft_use_default_radius,
                    Format.distance(settings.globalAlertRadiusKm, unit)),
                Modifier.weight(1f))
            Switch(checked = !useOverride, onCheckedChange = { useOverride = !it })
        }
        if (useOverride) {
            Text(
                stringResource(R.string.aircraft_radius_label,
                    Format.distance(radiusKm, unit),
                    stringResource(R.string.override_label)),
                style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = Format.kmToUnit(radiusKm, unit).toFloat()
                    .coerceIn(1f, unitMax),
                onValueChange = { v ->
                    radiusKm = if (unit == com.flightradius.app.domain.DistanceUnit.MI) {
                        v.toDouble() / Format.KM_TO_MI
                    } else v.toDouble()
                },
                valueRange = 1f..unitMax
            )
        }

        if (fleets.isNotEmpty()) {
            Text(
                stringResource(R.string.aircraft_fleets),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (f in fleets) {
                    FilterChip(
                        selected = f.id in memberIds,
                        onClick = {
                            memberIds = if (f.id in memberIds)
                                memberIds - f.id else memberIds + f.id
                        },
                        label = { Text(f.name) }
                    )
                }
            }
        }

        Row(Modifier.padding(top = 12.dp)) {
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                viewModel.update(
                    aircraft.copy(
                        notes = notes.trim().ifBlank { null },
                        alertRadiusKm = if (useOverride) radiusKm else null
                    )
                )
                viewModel.setFleetMembership(aircraft.id, memberIds)
                onDone()
            }) { Text(stringResource(R.string.action_save)) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Localised name of an identifier type ("Indicativo" / "ICAO24"). */
@Composable
private fun IdentifierType.label(): String = stringResource(
    if (this == IdentifierType.CALLSIGN) R.string.aircraft_callsign else R.string.aircraft_type_icao24)
