package com.flightradius.app.ui.radar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flightradius.app.R
import com.flightradius.app.domain.AircraftObservation
import com.flightradius.app.service.MonitoringStatus
import com.flightradius.app.ui.components.StatusChip
import com.flightradius.app.ui.components.rememberNow
import com.flightradius.app.ui.theme.extended

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(
    onStartMonitoring: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAircraft: (openAdd: Boolean) -> Unit,
    onEditAircraft: (Long) -> Unit,
    viewModel: RadarViewModel = hiltViewModel()
) {
    val state by viewModel.monitoringState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val credits by viewModel.credits.collectAsStateWithLifecycle()
    val aircraft by viewModel.aircraft.collectAsStateWithLifecycle()
    val fleets by viewModel.fleets.collectAsStateWithLifecycle()
    val locationStatus by viewModel.locationStatus.collectAsStateWithLifecycle()
    val fix by viewModel.fix.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val snoozes by viewModel.snoozes.collectAsStateWithLifecycle()
    val now by rememberNow()

    // Live preview loop while the screen is visible and service is stopped.
    DisposableEffect(Unit) {
        viewModel.onScreenVisible(true)
        onDispose { viewModel.onScreenVisible(false) }
    }

    var chipInfo by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<AircraftObservation?>(null) }

    val unit = settings.distanceUnit
    val snapshot = state.lastSnapshot

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Status chips
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    StatusChip(
                        label = locationLabel(locationStatus, fix, now),
                        color = locationColor(locationStatus),
                        onClick = { chipInfo = "location" }
                    )
                    StatusChip(
                        label = backendLabel(state.openSkyStatus, online, settings.dataSource),
                        color = backendColor(state.openSkyStatus, online),
                        onClick = { chipInfo = "backend" }
                    )
                    StatusChip(
                        label = monitoringLabel(state.status),
                        color = monitoringColor(state.status),
                        onClick = { chipInfo = "monitoring" }
                    )
                    creditsLabel(credits)?.let { label ->
                        StatusChip(
                            label = label,
                            color = creditsColor(credits),
                            onClick = { chipInfo = "credits" }
                        )
                    }
                }
            }

            // Issue cards
            item { IssueCards(state, settings, locationStatus, online, viewModel,
                onOpenSettings, onStartMonitoring) }

            // Controls
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when {
                        state.status == MonitoringStatus.STOPPED -> {
                            Button(
                                onClick = onStartMonitoring,
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.radar_start)) }
                        }
                        state.status == MonitoringStatus.PAUSED -> {
                            Button(
                                onClick = { viewModel.resume() },
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.radar_resume)) }
                            OutlinedButton(
                                onClick = { viewModel.stop() },
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.radar_stop)) }
                        }
                        else -> {
                            OutlinedButton(
                                onClick = { viewModel.pause() },
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.radar_pause)) }
                            OutlinedButton(
                                onClick = { viewModel.stop() },
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.radar_stop)) }
                        }
                    }
                }
            }

            // Preview banner when snapshot exists while service stopped
            if (state.status == MonitoringStatus.STOPPED && snapshot != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.extended.info.copy(alpha = 0.10f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            stringResource(R.string.radar_preview_banner),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.extended.info,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            when {
                aircraft.isEmpty() && snapshot == null -> {
                    item { EmptyState(onAdd = { onOpenAircraft(true) }) }
                }
                snapshot == null -> {
                    item { SkeletonHero() }
                    items(3) { SkeletonCard() }
                }
                else -> {
                    snapshot.closest?.let { obs ->
                        item {
                            HeroCard(
                                obs = obs,
                                unit = unit,
                                sweeping = state.status == MonitoringStatus.RUNNING,
                                nowMs = now
                            )
                        }
                    }
                    items(
                        snapshot.ranked,
                        key = { it.aircraftId }
                    ) { obs ->
                        AircraftCard(
                            obs = obs,
                            unit = unit,
                            fleetColors = fleets
                                .filter { obs.aircraftId in it.memberIds }
                                .map { it.colorArgb },
                            snoozed = snoozes[obs.aircraftId]
                                ?.let { it > now } == true,
                            nowMs = now,
                            onClick = { detail = obs },
                            onSnooze = { viewModel.snooze(obs.aircraftId, 30) },
                            onDismissAlert = { viewModel.dismissAlert() },
                            onEdit = { onEditAircraft(obs.aircraftId) },
                            modifier = Modifier.animateItem()
                        )
                    }
                    if (snapshot.noData.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.radar_not_reporting),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(snapshot.noData, key = { "nodata-${it.id}" }) { t ->
                            NoDataRow(t)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }

        if (settings.debugLogging) {
            DebugOverlay(state, fix, now, Modifier.align(Alignment.BottomCenter))
        }
    }

    // Status explanation sheet
    chipInfo?.let { which ->
        ModalBottomSheet(onDismissRequest = { chipInfo = null }) {
            ChipDetail(which, state, locationStatus, fix, online, settings,
                credits, now, onOpenSettings, onStartMonitoring)
        }
    }

    detail?.let { obs ->
        ModalBottomSheet(onDismissRequest = { detail = null }) {
            AircraftDetail(obs, unit, now,
                fleets.filter { obs.aircraftId in it.memberIds },
                onEdit = {
                    detail = null
                    onEditAircraft(obs.aircraftId)
                })
        }
    }
}
