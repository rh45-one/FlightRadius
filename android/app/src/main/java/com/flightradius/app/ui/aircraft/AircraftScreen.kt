package com.flightradius.app.ui.aircraft

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flightradius.app.R
import com.flightradius.app.data.prefs.AppSettings
import com.flightradius.app.domain.Fleet
import com.flightradius.app.domain.IdentifierType
import com.flightradius.app.domain.TrackedAircraft
import com.flightradius.app.service.MonitoringStatus
import com.flightradius.app.ui.components.FormBottomSheet
import com.flightradius.app.ui.components.GroupBadge
import com.flightradius.app.ui.components.GroupedDivider
import com.flightradius.app.ui.components.GroupedRow
import com.flightradius.app.ui.components.NavigationChevron
import com.flightradius.app.ui.components.ScreenTitle
import com.flightradius.app.ui.format.Format
import com.flightradius.app.ui.theme.CodeFeatures
import com.flightradius.app.ui.theme.NumericFeatures
import com.flightradius.app.ui.theme.extended
import kotlinx.coroutines.launch

private val FolderIcon: ImageVector = ImageVector.Builder(
    "Folder", 24.dp, 24.dp, 24f, 24f
).addPath(
    pathData = addPathNodes(
        "M10,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z"),
    fill = SolidColor(Color.Black)
).build()

private val NewFolderIcon: ImageVector = ImageVector.Builder(
    "NewFolder", 24.dp, 24.dp, 24f, 24f
).addPath(
    pathData = addPathNodes(
        "M20,6h-8l-2,-2H4c-1.11,0 -1.99,0.89 -1.99,2L2,18c0,1.11 0.89,2 2,2h16c1.11,0 2,-0.89 2,-2V8c0,-1.11 -0.89,-2 -2,-2zM18,14h-3v3h-2v-3h-3v-2h3V9h2v3h3v2z"),
    fill = SolidColor(Color.Black)
).build()

/**
 * Tracked aircraft, grouped into folders. [openAddOnLaunch] opens the add
 * sheet directly (radar empty-state CTA).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AircraftScreen(
    settings: AppSettings,
    openAddOnLaunch: Boolean = false,
    editId: Long? = null,
    viewModel: AircraftViewModel = hiltViewModel()
) {
    val allAircraft by viewModel.aircraft.collectAsStateWithLifecycle()
    val groups by viewModel.fleets.collectAsStateWithLifecycle()
    val collapsed by viewModel.collapsedGroupIds.collectAsStateWithLifecycle()
    val monitoring by viewModel.monitoring.collectAsStateWithLifecycle()
    val query by viewModel.search.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalContext.current.resources
    val removedMsg = stringResource(R.string.aircraft_removed)
    val undoMsg = stringResource(R.string.action_undo)

    var showAdd by remember { mutableStateOf(openAddOnLaunch) }
    var showBulk by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TrackedAircraft?>(null) }
    if (editId != null && editing == null) {
        editing = allAircraft.find { it.id == editId }
    }

    var selected by remember { mutableStateOf(setOf<Long>()) }
    var selecting by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editGroup by remember { mutableStateOf<Fleet?>(null) }
    var newGroupFor by remember { mutableStateOf<NewGroupPurpose?>(null) }
    var deleteGroup by remember { mutableStateOf<Fleet?>(null) }

    BackHandler(enabled = selecting) { selected = emptySet(); selecting = false }

    val items = remember(groups, allAircraft, query, collapsed) {
        AircraftFolders.build(groups, allAircraft, query, collapsed)
    }
    val snapshot = monitoring.lastSnapshot
    val live = monitoring.status == MonitoringStatus.RUNNING
    val dataFailed = flightDataFailed(monitoring)
    val detectedIds = snapshot?.ranked?.mapTo(HashSet()) { it.aircraftId } ?: emptySet()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            if (selecting) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selected = emptySet(); selecting = false }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.selection_close))
                    }
                    Text(
                        pluralStringResource(R.plurals.selection_count, selected.size, selected.size),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.testTag("selection-count"))
                }
            } else {
                ScreenTitle(
                    stringResource(R.string.aircraft_title),
                    actions = {
                        TextButton(onClick = { showBulk = true }) {
                            Text(stringResource(R.string.aircraft_bulk_add))
                        }
                        IconButton(onClick = { newGroupFor = NewGroupPurpose.Plain }) {
                            Icon(
                                NewFolderIcon,
                                contentDescription = stringResource(R.string.group_new_action))
                        }
                        IconButton(onClick = { showAdd = true }) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = stringResource(R.string.aircraft_add))
                        }
                    }
                )
            }
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
            if (items.isEmpty()) {
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
                LazyColumn(Modifier.testTag("aircraft-list")) {
                    items(
                        items,
                        key = {
                            when (it) {
                                is FolderItem.Header -> "g${it.group.id}"
                                is FolderItem.Member -> "a${it.aircraft.id}"
                                is FolderItem.EmptyGroup -> "e${it.groupId}"
                                is FolderItem.UngroupedHeader -> "ungrouped"
                            }
                        }
                    ) { item ->
                        val topRadius = 14.dp
                        val isCardTop = item is FolderItem.Header ||
                            item is FolderItem.UngroupedHeader ||
                            (item is FolderItem.Member && item.first)
                        val shape = RoundedCornerShape(
                            topStart = if (isCardTop) topRadius else 0.dp,
                            topEnd = if (isCardTop) topRadius else 0.dp,
                            bottomStart = if (item.last) topRadius else 0.dp,
                            bottomEnd = if (item.last) topRadius else 0.dp
                        )
                        val spacing = if (isCardTop && item !== items.first()) 12.dp else 0.dp
                        Box(Modifier.padding(top = spacing).clip(shape)) {
                            when (item) {
                                is FolderItem.Header -> {
                                    val g = item.group
                                    val nearest = snapshot?.fleets
                                        ?.find { it.fleet.id == g.id }?.closest
                                    GroupHeaderRow(
                                        group = g,
                                        count = item.count,
                                        expanded = item.expanded,
                                        nearest = nearest?.let {
                                            stringResource(
                                                R.string.group_nearest,
                                                Format.callsign(it),
                                                Format.distance(it.distanceKm, settings.distanceUnit))
                                        },
                                        radius = g.alertRadiusKm?.let {
                                            Format.radius(it, settings.distanceUnit)
                                        },
                                        selecting = selecting,
                                        onToggle = {
                                            if (viewModel.search.value.isBlank()) {
                                                viewModel.setCollapsed(g.id, item.expanded)
                                            }
                                        },
                                        onEdit = { editGroup = g },
                                        onSelectAll = { selected = selected + g.memberIds }
                                    )
                                }
                                is FolderItem.EmptyGroup -> {
                                    Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
                                        GroupedDivider()
                                        Text(
                                            stringResource(R.string.group_empty_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(
                                                start = 28.dp, end = 16.dp, top = 12.dp, bottom = 12.dp))
                                    }
                                }
                                is FolderItem.UngroupedHeader -> {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceContainer)
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            stringResource(R.string.group_ungrouped),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                is FolderItem.Member -> {
                                    val a = item.aircraft
                                    val inGroup = item.groupId != null
                                    val dismissState = rememberSwipeToDismissBoxState(
                                        confirmValueChange = { v ->
                                            !selecting && v == SwipeToDismissBoxValue.EndToStart
                                        }
                                    )
                                    SwipeToDismissBox(
                                        state = dismissState,
                                        gesturesEnabled = !selecting,
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
                                            if (!item.first) GroupedDivider()
                                            AircraftRow(
                                                a,
                                                settings,
                                                group = groups.find { it.id == item.groupId },
                                                presence = aircraftPresence(
                                                    detected = a.id in detectedIds,
                                                    live = live,
                                                    dataFailed = dataFailed
                                                ),
                                                indent = inGroup,
                                                selecting = selecting,
                                                checked = a.id in selected,
                                                onClick = {
                                                    if (selecting) {
                                                        selected = if (a.id in selected)
                                                            selected - a.id else selected + a.id
                                                    } else editing = a
                                                },
                                                onLongClick = {
                                                    selecting = true
                                                    selected = if (a.id in selected)
                                                        selected - a.id else selected + a.id
                                                }
                                            )
                                        }
                                    }
                                    if (dismissState.currentValue ==
                                        SwipeToDismissBoxValue.EndToStart
                                    ) {
                                        val groupId = item.groupId
                                        LaunchedEffect(a.id) {
                                            viewModel.remove(a)
                                            val res = snackbar.showSnackbar(
                                                removedMsg, undoMsg,
                                                withDismissAction = true
                                            )
                                            if (res == SnackbarResult.ActionPerformed) {
                                                viewModel.restore(a, groupId?.let { setOf(it) } ?: emptySet())
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(if (selecting) 140.dp else 24.dp)) }
                }
            }
        }

        if (selecting) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            ) {
                HorizontalDivider(
                    thickness = Dp.Hairline,
                    color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { showMove = true },
                        enabled = selected.isNotEmpty(),
                        modifier = Modifier.weight(1f).testTag("move-to-group")
                    ) {
                        Icon(
                            FolderIcon, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.group_move))
                    }
                    TextButton(
                        onClick = { confirmDelete = true },
                        enabled = selected.isNotEmpty(),
                        modifier = Modifier.testTag("delete-selected")
                    ) {
                        Text(
                            stringResource(R.string.aircraft_delete_selected),
                            color = if (selected.isNotEmpty()) MaterialTheme.colorScheme.extended.danger
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                    }
                }
            }
        }

        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (selecting) 76.dp else 0.dp)
        )
    }

    if (showAdd) {
        FormBottomSheet(onDismiss = { showAdd = false }) {
            AddAircraftSheet(
                viewModel = viewModel,
                settings = settings,
                onDone = { showAdd = false }
            )
        }
    }
    if (showBulk) {
        FormBottomSheet(onDismiss = { showBulk = false }) {
            BulkAddSheet(
                viewModel = viewModel,
                settings = settings,
                onDone = { showBulk = false }
            )
        }
    }
    editing?.let { a ->
        FormBottomSheet(onDismiss = { editing = null }) {
            EditAircraftSheet(
                aircraft = a,
                fleets = groups,
                settings = settings,
                viewModel = viewModel,
                onDone = { editing = null }
            )
        }
    }

    fun moveSelected(groupId: Long?) {
        val ids = selected
        viewModel.moveToGroup(ids, groupId) { move ->
            selected = emptySet()
            selecting = false
            scope.launch {
                val msg = if (move.targetName != null) {
                    resources.getQuantityString(
                        R.plurals.group_moved, move.count, move.count, move.targetName)
                } else {
                    resources.getQuantityString(
                        R.plurals.group_removed_from, move.count, move.count)
                }
                val res = snackbar.showSnackbar(msg, undoMsg, withDismissAction = true)
                if (res == SnackbarResult.ActionPerformed) viewModel.undoMove(move)
            }
        }
    }

    if (showMove) {
        FormBottomSheet(onDismiss = { showMove = false }) {
            MoveToGroupSheet(
                groups = groups,
                onPick = { showMove = false; moveSelected(it) },
                onNewGroup = { showMove = false; newGroupFor = NewGroupPurpose.MoveSelected }
            )
        }
    }

    newGroupFor?.let { purpose ->
        FormBottomSheet(onDismiss = { newGroupFor = null }) {
            GroupEditorSheet(
                group = null,
                existing = groups,
                settings = settings,
                onSave = { name, color, icon, radius ->
                    viewModel.saveGroup(null, name, color, icon, radius) { id ->
                        if (id != null) {
                            newGroupFor = null
                            if (purpose == NewGroupPurpose.MoveSelected) moveSelected(id)
                        }
                    }
                }
            )
        }
    }
    editGroup?.let { g ->
        FormBottomSheet(onDismiss = { editGroup = null }) {
            GroupEditorSheet(
                group = g,
                existing = groups,
                settings = settings,
                onSave = { name, color, icon, radius ->
                    viewModel.saveGroup(g.id, name, color, icon, radius) { id ->
                        if (id != null) editGroup = null
                    }
                },
                onDelete = { editGroup = null; deleteGroup = g }
            )
        }
    }
    deleteGroup?.let { g ->
        AlertDialog(
            onDismissRequest = { deleteGroup = null },
            title = { Text(stringResource(R.string.fleets_delete_title)) },
            text = { Text(stringResource(R.string.fleets_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGroup(g.id); deleteGroup = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteGroup = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
    if (confirmDelete) {
        val count = selected.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = {
                Text(pluralStringResource(R.plurals.aircraft_delete_selected_title, count, count))
            },
            text = { Text(stringResource(R.string.aircraft_delete_selected_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val removed = allAircraft.filter { it.id in selected }.map { a ->
                            a to groups.firstOrNull { a.id in it.memberIds }?.id
                        }
                        confirmDelete = false
                        selected = emptySet()
                        selecting = false
                        viewModel.removeAll(removed.map { it.first })
                        scope.launch {
                            val res = snackbar.showSnackbar(
                                resources.getQuantityString(
                                    R.plurals.aircraft_deleted_count, removed.size, removed.size),
                                undoMsg, withDismissAction = true)
                            if (res == SnackbarResult.ActionPerformed) viewModel.restoreAll(removed)
                        }
                    },
                    modifier = Modifier.testTag("confirm-delete")
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

private enum class NewGroupPurpose { Plain, MoveSelected }

/** Folder header: badge, name, count + nearest member, radius, edit and chevron. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupHeaderRow(
    group: Fleet,
    count: Int,
    expanded: Boolean,
    nearest: String?,
    radius: String?,
    selecting: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onSelectAll: () -> Unit
) {
    val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "chevron")
    val subtitle = buildList {
        add(pluralStringResource(R.plurals.group_aircraft_count, count, count))
        nearest?.let { add(it) }
    }.joinToString(" · ")
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(onClick = onToggle)
            .testTag("group-header-${group.id}")
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GroupBadge(group.icon, group.colorArgb, 32.dp)
        Column(Modifier.weight(1f)) {
            Text(group.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFeatureSettings = NumericFeatures),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
        if (radius != null) {
            Text(
                radius,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFeatureSettings = NumericFeatures),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        if (selecting) {
            TextButton(onClick = onSelectAll) {
                Text(stringResource(R.string.group_select_all))
            }
        } else {
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.fleets_edit_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp))
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(
                    if (expanded) R.string.fleets_collapse else R.string.fleets_expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .rotate(rotation)
            )
            Spacer(Modifier.size(8.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AircraftRow(
    a: TrackedAircraft,
    settings: AppSettings,
    group: Fleet?,
    presence: AircraftPresence,
    indent: Boolean,
    selecting: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val typeLabel = stringResource(
        if (a.type == IdentifierType.CALLSIGN) R.string.aircraft_callsign
        else R.string.aircraft_type_icao24)
    val radius = radiusInfo(a, group, settings.globalAlertRadiusKm)
    val radiusLabel = stringResource(
        R.string.aircraft_radius_label,
        Format.radius(radius.km, settings.distanceUnit),
        stringResource(
            when (radius.source) {
                RadiusSource.OWN -> R.string.override_label
                RadiusSource.GROUP -> R.string.radius_group_label
                RadiusSource.GLOBAL -> R.string.default_label
            }))
    val subtitle = listOf(typeLabel, radiusLabel).joinToString(" · ")
    GroupedRow(
        title = a.identifier,
        titleStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = CodeFeatures),
        subtitle = subtitle,
        subtitleStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = NumericFeatures),
        modifier = Modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("aircraft-row-${a.identifier}")
            .padding(start = if (indent) 12.dp else 0.dp),
        leading = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (selecting) Checkbox(checked = checked, onCheckedChange = null)
                PresenceDot(presence)
            }
        },
        trailing = if (selecting) null else {
            { NavigationChevron() }
        }
    )
}

@Composable
private fun PresenceDot(presence: AircraftPresence) {
    val color = when (presence) {
        AircraftPresence.ONLINE -> MaterialTheme.colorScheme.extended.success
        AircraftPresence.OFFLINE -> MaterialTheme.colorScheme.onSurfaceVariant
        AircraftPresence.ERROR -> MaterialTheme.colorScheme.extended.danger
    }
    val label = stringResource(
        when (presence) {
            AircraftPresence.ONLINE -> R.string.aircraft_status_online
            AircraftPresence.OFFLINE -> R.string.aircraft_status_offline
            AircraftPresence.ERROR -> R.string.aircraft_status_error
        }
    )
    val transition = rememberInfiniteTransition(label = "online")
    val animated by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )
    val breath = if (presence == AircraftPresence.ONLINE) animated else 1f
    Box(
        Modifier
            .size(10.dp)
            .graphicsLayer { alpha = breath }
            .clip(CircleShape)
            .background(color)
            .semantics { contentDescription = label }
            .testTag("presence-${presence.name}")
    )
}

/** Pick a destination: None, a group, or a brand-new one. */
@Composable
private fun MoveToGroupSheet(
    groups: List<Fleet>,
    onPick: (Long?) -> Unit,
    onNewGroup: () -> Unit
) {
    Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text(
            stringResource(R.string.group_move),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp))
        GroupedRow(
            title = stringResource(R.string.group_none),
            leading = { Spacer(Modifier.size(32.dp)) },
            modifier = Modifier.combinedClickableCompat { onPick(null) }.testTag("move-none")
        )
        for (g in groups.sortedBy { it.name.lowercase() }) {
            GroupedDivider()
            GroupedRow(
                title = g.name,
                leading = { GroupBadge(g.icon, g.colorArgb, 32.dp) },
                modifier = Modifier.combinedClickableCompat { onPick(g.id) }.testTag("move-to-${g.id}")
            )
        }
        GroupedDivider()
        GroupedRow(
            title = stringResource(R.string.group_new),
            titleColor = MaterialTheme.colorScheme.primary,
            leading = { Spacer(Modifier.size(32.dp)) },
            modifier = Modifier.combinedClickableCompat { onNewGroup() }.testTag("move-new")
        )
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick)
