package com.flightradius.app.ui.fleets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flightradius.app.R
import com.flightradius.app.data.prefs.AppSettings
import com.flightradius.app.data.repo.FLEET_COLOR_PALETTE
import com.flightradius.app.domain.Fleet
import com.flightradius.app.ui.format.Format
import com.flightradius.app.ui.theme.extended
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FleetsScreen(
    settings: AppSettings,
    viewModel: FleetsViewModel = hiltViewModel()
) {
    val fleets by viewModel.fleets.collectAsStateWithLifecycle()
    val aircraft by viewModel.aircraft.collectAsStateWithLifecycle()
    val state by viewModel.snapshot.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val refreshError by viewModel.refreshError.collectAsStateWithLifecycle()
    val manual by viewModel.manualOutcomes.collectAsStateWithLifecycle()
    val manualAt by viewModel.manualRefreshAtMs.collectAsStateWithLifecycle()
    val now by com.flightradius.app.ui.components.rememberNow()

    var editFleet by remember { mutableStateOf<Fleet?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var deleteFleet by remember { mutableStateOf<Fleet?>(null) }
    var expanded by remember { mutableStateOf(setOf<Long>()) }

    val snapshot = state.lastSnapshot
    val snapshotStale = snapshot == null ||
        now - snapshot.timeMs > 2 * settings.monitoringIntervalSec * 1000L

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.fleets_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (snapshotStale && fleets.isNotEmpty()) {
                item {
                    com.flightradius.app.ui.components.IssueCard(
                        title = stringResource(R.string.fleets_stale_title),
                        body = stringResource(R.string.fleets_stale_body),
                        actionLabel = if (refreshing)
                            stringResource(R.string.checking)
                        else stringResource(R.string.action_refresh),
                        onAction = { if (!refreshing) viewModel.refresh() }
                    )
                }
            }
            refreshError?.let { err ->
                item {
                    Text(err, color = MaterialTheme.colorScheme.extended.danger,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            manualAt?.let {
                item {
                    Text(
                        stringResource(R.string.fleets_manual_refresh,
                            Format.age(now, it)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            items(fleets, key = { it.id }) { fleet ->
                val status = snapshot?.fleets?.find { it.fleet.id == fleet.id }
                val manualOutcome = if (snapshotStale) {
                    manual?.find { it.groupName.equals(fleet.name, true) }
                } else null
                FleetCard(
                    fleet = fleet,
                    status = status,
                    manualOutcome = manualOutcome,
                    aircraft = aircraft,
                    unit = settings.distanceUnit,
                    globalRadiusKm = settings.globalAlertRadiusKm,
                    snapshotMs = snapshot?.timeMs,
                    stale = snapshotStale,
                    nowMs = now,
                    expanded = fleet.id in expanded,
                    onToggle = {
                        expanded = if (fleet.id in expanded) expanded - fleet.id
                        else expanded + fleet.id
                    },
                    onEdit = { editFleet = fleet },
                    onDelete = { deleteFleet = fleet }
                )
            }

            if (fleets.isEmpty()) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(R.string.fleets_empty_title),
                            style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.fleets_empty_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }

        ExtendedFloatingActionButton(
            onClick = { showCreate = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) { Text(stringResource(R.string.fleets_add)) }
    }

    if (showCreate) {
        ModalBottomSheet(onDismissRequest = { showCreate = false }) {
            FleetEditSheet(
                fleet = null,
                aircraft = aircraft,
                settings = settings,
                onSave = { name, color, radius, members ->
                    viewModel.createOrUpdate(null, name, color, radius, members) { ok, _ ->
                        if (ok) showCreate = false
                    }
                }
            )
        }
    }
    editFleet?.let { fleet ->
        ModalBottomSheet(onDismissRequest = { editFleet = null }) {
            FleetEditSheet(
                fleet = fleet,
                aircraft = aircraft,
                settings = settings,
                onSave = { name, color, radius, members ->
                    viewModel.createOrUpdate(
                        fleet.id, name, color, radius, members) { ok, _ ->
                        if (ok) editFleet = null
                    }
                }
            )
        }
    }
    deleteFleet?.let { fleet ->
        AlertDialog(
            onDismissRequest = { deleteFleet = null },
            title = { Text(stringResource(R.string.fleets_delete_title)) },
            text = { Text(stringResource(R.string.fleets_delete_body, fleet.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(fleet); deleteFleet = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteFleet = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun FleetCard(
    fleet: Fleet,
    status: com.flightradius.app.domain.FleetStatus?,
    manualOutcome: com.flightradius.app.domain.GroupOutcome?,
    aircraft: List<com.flightradius.app.domain.TrackedAircraft>,
    unit: com.flightradius.app.domain.DistanceUnit,
    globalRadiusKm: Double,
    snapshotMs: Long?,
    stale: Boolean,
    nowMs: Long,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color(fleet.colorArgb))
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    fleet.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = onEdit) {
                    Text(stringResource(R.string.action_edit))
                }
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.action_delete))
                }
            }
            val lMembers = stringResource(
                R.string.fleets_member_count, fleet.memberIds.size)
            // Effective radius: fleet override else the global default.
            val lRadius = fleet.alertRadiusKm?.let {
                stringResource(R.string.fleets_radius, Format.distance(it, unit))
            } ?: stringResource(R.string.fleets_radius_default,
                Format.distance(globalRadiusKm, unit))
            // Closest member stays visible when stale, marked with its age.
            val staleSuffix =
                if (stale && snapshotMs != null)
                    " · " + stringResource(
                        R.string.fleets_stale_ago, Format.age(nowMs, snapshotMs))
                else ""
            val lClosest = status?.closest?.let { c ->
                stringResource(R.string.fleets_closest,
                    Format.callsign(c), Format.distance(c.distanceKm, unit)) +
                    staleSuffix
            } ?: manualOutcome?.closest?.let { c ->
                stringResource(R.string.fleets_closest,
                    c.callsign ?: c.icao24 ?: "?",
                    Format.distance(c.distanceKm ?: 0.0, unit))
            }
            Text(
                buildList {
                    add(lMembers)
                    lRadius?.let { add(it) }
                    lClosest?.let { add(it) }
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onToggle) {
                Text(
                    if (expanded) stringResource(R.string.fleets_collapse)
                    else stringResource(R.string.fleets_expand))
            }
            if (expanded) {
                val members = fleet.memberIds.mapNotNull { id ->
                    aircraft.find { it.id == id }
                }
                val ranked = status?.membersRanked.orEmpty()
                if (members.isEmpty()) {
                    Text(
                        stringResource(R.string.fleets_no_members),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    for (m in members) {
                        val obs = ranked.find { it.aircraftId == m.id }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)) {
                            Text(m.identifier, Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium)
                            Text(
                                obs?.let { Format.distance(it.distanceKm, unit) }
                                    ?: stringResource(R.string.fleets_missing),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FleetEditSheet(
    fleet: Fleet?,
    aircraft: List<com.flightradius.app.domain.TrackedAircraft>,
    settings: AppSettings,
    onSave: (name: String, color: Int, radiusKm: Double?, members: Set<Long>) -> Unit
) {
    var name by remember { mutableStateOf(fleet?.name ?: "") }
    var color by remember {
        mutableStateOf(fleet?.colorArgb ?: FLEET_COLOR_PALETTE[0])
    }
    var useRadius by remember { mutableStateOf(fleet?.alertRadiusKm != null) }
    var radiusKm by remember {
        mutableStateOf(fleet?.alertRadiusKm ?: settings.globalAlertRadiusKm)
    }
    var members by remember { mutableStateOf(fleet?.memberIds ?: emptySet()) }
    val unit = settings.distanceUnit
    val unitMax = Format.kmToUnit(200.0, unit).toFloat()

    Column(
        Modifier
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            stringResource(
                if (fleet == null) R.string.fleets_add_title
                else R.string.fleets_edit_title),
            style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            label = { Text(stringResource(R.string.fleets_name)) },
            singleLine = true
        )

        Text(
            stringResource(R.string.fleets_color),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (c in FLEET_COLOR_PALETTE) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { color = c },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                    )
                    if (c == color) {
                        Box(
                            Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.85f))
                        )
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text(
                stringResource(R.string.fleets_radius_override),
                Modifier.weight(1f))
            Switch(checked = useRadius, onCheckedChange = { useRadius = it })
        }
        if (useRadius) {
            Text(
                Format.distance(radiusKm, unit),
                style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = Format.kmToUnit(radiusKm, unit).toFloat()
                    .coerceIn(1f, unitMax),
                onValueChange = { v ->
                    radiusKm = if (unit == com.flightradius.app.domain.DistanceUnit.MI)
                        v.toDouble() / Format.KM_TO_MI else v.toDouble()
                },
                valueRange = 1f..unitMax
            )
        }

        Text(
            stringResource(R.string.fleets_members),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 12.dp))
        for (a in aircraft) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = a.id in members,
                    onCheckedChange = { checked ->
                        members = if (checked) members + a.id else members - a.id
                    }
                )
                Text(a.identifier)
            }
        }

        Row(Modifier.padding(top = 12.dp)) {
            Spacer(Modifier.weight(1f))
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(name.trim(), color, if (useRadius) radiusKm else null, members)
                }
            ) { Text(stringResource(R.string.action_save)) }
        }
        Spacer(Modifier.height(24.dp))
    }
}
