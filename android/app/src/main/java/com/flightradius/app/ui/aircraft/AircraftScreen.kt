package com.flightradius.app.ui.aircraft

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flightradius.app.R
import com.flightradius.app.data.prefs.AppSettings
import com.flightradius.app.domain.Fleet
import com.flightradius.app.domain.IdentifierType
import com.flightradius.app.domain.TrackedAircraft
import com.flightradius.app.domain.effectiveRadiusKm
import com.flightradius.app.ui.format.Format
import com.flightradius.app.ui.theme.extended
import kotlinx.coroutines.launch

/**
 * Tracked-aircraft list + add/bulk-add/edit sheets. [openAddOnLaunch] opens
 * the add sheet directly (radar empty-state CTA).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AircraftScreen(
    settings: AppSettings,
    openAddOnLaunch: Boolean = false,
    editId: Long? = null,
    viewModel: AircraftViewModel = hiltViewModel()
) {
    val aircraft by viewModel.filtered.collectAsStateWithLifecycle()
    val fleets by viewModel.fleets.collectAsStateWithLifecycle()
    val query by viewModel.search.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val removedMsg = stringResource(R.string.aircraft_removed)
    val undoMsg = stringResource(R.string.action_undo)

    var showAdd by remember { mutableStateOf(openAddOnLaunch) }
    var showBulk by remember { mutableStateOf(false) }
    var editing by remember {
        mutableStateOf<TrackedAircraft?>(null)
    }
    if (editId != null && editing == null) {
        editing = viewModel.aircraft.value.find { it.id == editId }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Text(
                stringResource(R.string.aircraft_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 16.dp)
            )
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.search.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                placeholder = { Text(stringResource(R.string.aircraft_search)) },
                singleLine = true
            )
            Row(
                Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { showBulk = true }) {
                    Text(stringResource(R.string.aircraft_bulk_add))
                }
            }
            if (aircraft.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.aircraft_empty_title),
                        style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.aircraft_empty_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(aircraft, key = { it.id }) { a ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { v ->
                                v == SwipeToDismissBoxValue.EndToStart
                            }
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(
                                            MaterialTheme.colorScheme.extended.danger
                                                .copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Text(
                                        stringResource(R.string.action_delete),
                                        color = MaterialTheme.colorScheme.extended.danger,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            },
                            enableDismissFromStartToEnd = false
                        ) {
                            AircraftRow(
                                a,
                                settings,
                                fleets = fleets
                                    .filter { a.id in it.memberIds },
                                globalRadius = settings.globalAlertRadiusKm,
                                onClick = { editing = a }
                            )
                        }
                        if (dismissState.currentValue ==
                            SwipeToDismissBoxValue.EndToStart
                        ) {
                            val fleetIds = fleets
                                .filter { a.id in it.memberIds }
                                .map { it.id }.toSet()
                            androidx.compose.runtime.LaunchedEffect(a.id) {
                                viewModel.remove(a)
                                val res = snackbar.showSnackbar(
                                    removedMsg, undoMsg,
                                    withDismissAction = true
                                )
                                if (res == SnackbarResult.ActionPerformed) {
                                    viewModel.restore(a, fleetIds)
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            icon = {
                Icon(Icons.Filled.Add,
                    contentDescription = stringResource(R.string.aircraft_add))
            },
            text = { Text(stringResource(R.string.aircraft_add)) }
        )

        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showAdd) {
        ModalBottomSheet(onDismissRequest = { showAdd = false }) {
            AddAircraftSheet(
                viewModel = viewModel,
                settings = settings,
                onDone = { showAdd = false }
            )
        }
    }
    if (showBulk) {
        ModalBottomSheet(onDismissRequest = { showBulk = false }) {
            BulkAddSheet(
                viewModel = viewModel,
                onDone = { showBulk = false }
            )
        }
    }
    editing?.let { a ->
        ModalBottomSheet(onDismissRequest = { editing = null }) {
            EditAircraftSheet(
                aircraft = a,
                fleets = fleets,
                settings = settings,
                viewModel = viewModel,
                onDone = { editing = null }
            )
        }
    }
}

@Composable
private fun AircraftRow(
    a: TrackedAircraft,
    settings: AppSettings,
    fleets: List<Fleet>,
    globalRadius: Double,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        a.identifier,
                        style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            a.type.name,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(
                                horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (fleets.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (f in fleets.take(3)) {
                            Surface(
                                color = Color(f.colorArgb).copy(alpha = 0.20f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    f.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(f.colorArgb),
                                    maxLines = 1,
                                    modifier = Modifier.padding(
                                        horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
                val radiusLabel = stringResource(
                    R.string.aircraft_radius_label,
                    Format.distance(
                        a.alertRadiusKm ?: globalRadius,
                        settings.distanceUnit),
                    if (a.alertRadiusKm != null)
                        stringResource(R.string.override_label)
                    else
                        stringResource(R.string.default_label))
                Text(
                    radiusLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                a.notes?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
