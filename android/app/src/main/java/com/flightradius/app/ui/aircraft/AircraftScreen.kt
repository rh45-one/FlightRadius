package com.flightradius.app.ui.aircraft

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flightradius.app.R
import com.flightradius.app.data.prefs.AppSettings
import com.flightradius.app.domain.Fleet
import com.flightradius.app.domain.IdentifierType
import com.flightradius.app.domain.TrackedAircraft
import com.flightradius.app.ui.components.GroupedDivider
import com.flightradius.app.ui.components.GroupedRow
import com.flightradius.app.ui.components.NavigationChevron
import com.flightradius.app.ui.components.ScreenTitle
import com.flightradius.app.ui.format.Format
import com.flightradius.app.ui.theme.CodeFeatures
import com.flightradius.app.ui.theme.NumericFeatures
import com.flightradius.app.ui.theme.extended

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
            ScreenTitle(
                stringResource(R.string.aircraft_title),
                actions = {
                    TextButton(onClick = { showBulk = true }) {
                        Text(stringResource(R.string.aircraft_bulk_add))
                    }
                    IconButton(onClick = { showAdd = true }) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.aircraft_add))
                    }
                }
            )
            TextField(
                value = query,
                onValueChange = { viewModel.search.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                placeholder = { Text(stringResource(R.string.aircraft_search)) },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.search.value = "" }) {
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.aircraft_clear_search))
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            if (aircraft.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.aircraft_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center)
                    Text(
                        stringResource(R.string.aircraft_empty_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { showAdd = true }) {
                        Text(stringResource(R.string.aircraft_add))
                    }
                }
            } else {
                LazyColumn {
                    itemsIndexed(aircraft, key = { _, a -> a.id }) { index, a ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { v ->
                                v == SwipeToDismissBoxValue.EndToStart
                            }
                        )
                        val r = 14.dp
                        val shape = RoundedCornerShape(
                            topStart = if (index == 0) r else 0.dp,
                            topEnd = if (index == 0) r else 0.dp,
                            bottomStart = if (index == aircraft.lastIndex) r else 0.dp,
                            bottomEnd = if (index == aircraft.lastIndex) r else 0.dp
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            modifier = Modifier.clip(shape),
                            backgroundContent = {
                                if (dismissState.dismissDirection !=
                                    SwipeToDismissBoxValue.Settled
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.extended.danger),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Text(
                                            stringResource(R.string.action_delete),
                                            color = MaterialTheme.colorScheme.surface,
                                            style = MaterialTheme.typography.labelLarge,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                }
                            },
                            enableDismissFromStartToEnd = false
                        ) {
                            Column(
                                Modifier.background(
                                    MaterialTheme.colorScheme.surfaceContainer)
                            ) {
                                if (index > 0) GroupedDivider()
                                AircraftRow(
                                    a,
                                    settings,
                                    fleets = fleets
                                        .filter { a.id in it.memberIds },
                                    globalRadius = settings.globalAlertRadiusKm,
                                    onClick = { editing = a }
                                )
                            }
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
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }

        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showAdd) {
        ModalBottomSheet(
            onDismissRequest = { showAdd = false },
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            AddAircraftSheet(
                viewModel = viewModel,
                settings = settings,
                onDone = { showAdd = false }
            )
        }
    }
    if (showBulk) {
        ModalBottomSheet(
            onDismissRequest = { showBulk = false },
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            BulkAddSheet(
                viewModel = viewModel,
                onDone = { showBulk = false }
            )
        }
    }
    editing?.let { a ->
        ModalBottomSheet(
            onDismissRequest = { editing = null },
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
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
    val typeLabel = stringResource(
        if (a.type == IdentifierType.CALLSIGN) R.string.aircraft_callsign
        else R.string.aircraft_type_icao24)
    val radiusLabel = stringResource(
        R.string.aircraft_radius_label,
        Format.distance(a.alertRadiusKm ?: globalRadius, settings.distanceUnit),
        if (a.alertRadiusKm != null) stringResource(R.string.override_label)
        else stringResource(R.string.default_label))
    val subtitle = buildList {
        add(typeLabel)
        add(radiusLabel)
        if (fleets.isNotEmpty()) add(fleets.joinToString(", ") { it.name })
    }.joinToString(" · ")
    GroupedRow(
        title = a.identifier,
        titleStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = CodeFeatures),
        subtitle = subtitle,
        subtitleStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = NumericFeatures),
        leading = fleets.firstOrNull()?.let { f ->
            {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(f.colorArgb))
                )
            }
        },
        trailing = { NavigationChevron() },
        onClick = onClick
    )
}
