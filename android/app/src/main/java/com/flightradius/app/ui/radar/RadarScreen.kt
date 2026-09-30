package com.flightradius.app.ui.radar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flightradius.app.R
import com.flightradius.app.data.prefs.DataSource
import com.flightradius.app.domain.AircraftObservation
import com.flightradius.app.service.MonitoringStatus
import com.flightradius.app.ui.components.ScreenTitle
import com.flightradius.app.ui.components.rememberNow

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

    val keepOn = settings.keepScreenOn && state.status != MonitoringStatus.STOPPED
    val view = LocalView.current
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    var showStatus by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<AircraftObservation?>(null) }

    val unit = settings.distanceUnit
    val snapshot = state.lastSnapshot
    val intervalSec = state.plannedIntervalSec ?: settings.monitoringIntervalSec
    val glance = glanceOf(aircraft.size, snapshot, now, intervalSec)
    val summary = statusSummary(
        status = state.status,
        openSkyStatus = state.openSkyStatus,
        locationStatus = locationStatus,
        online = online,
        lanBlocked = viewModel.localNetworkGuard.isBlocked(),
        credits = credits,
        lastSuccessAtMs = state.lastSuccessAtMs,
        nowMs = now,
        backendMode = settings.dataSource == DataSource.BACKEND
    )
    val hero = (glance as? Glance.Nearest)?.obs
    val showBar = glance != Glance.NoAircraft

    val lists: @Composable ColumnScope.() -> Unit = {
        if (glance == Glance.NoAircraft) {
            Button(
                onClick = { onOpenAircraft(true) },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 16.dp)
                    .heightIn(min = 48.dp)
            ) { Text(stringResource(R.string.radar_add_aircraft)) }
        }
        if (snapshot != null) {
            AlsoTrackingSection(
                ranked = snapshot.ranked.filter { it.aircraftId != hero?.aircraftId },
                unit = unit,
                snoozes = snoozes,
                nowMs = now,
                onClick = { detail = it }
            )
            NotReportingSection(snapshot.noData)
            if (state.status == MonitoringStatus.STOPPED) {
                Text(
                    stringResource(R.string.radar_preview_footnote),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    val actionBar: @Composable () -> Unit = {
        if (showBar) {
            ActionBar(
                status = state.status,
                onStart = onStartMonitoring,
                onPause = { viewModel.pause() },
                onResume = { viewModel.resume() },
                onStop = { viewModel.stop() }
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            if (landscape) {
                val dialSize: Dp = minOf(maxHeight - 32.dp, 340.dp, maxWidth * 0.5f - 16.dp)
                    .coerceAtLeast(160.dp)
                Row(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        GlanceDial(glance, unit, now, dialSize)
                    }
                    Column(Modifier.weight(1f)) {
                        Column(
                            Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp)
                        ) {
                            Spacer(Modifier.height(8.dp))
                            StatusLine(summary, now, onClick = { showStatus = true })
                            if (hero != null) {
                                DetailRow(hero, unit, Modifier.padding(top = 8.dp))
                            }
                            lists()
                        }
                        actionBar()
                    }
                }
            } else {
                val dialSize: Dp = minOf(maxWidth - 32.dp, 340.dp)
                Column(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                    ) {
                        ScreenTitle(stringResource(R.string.app_name))
                        StatusLine(summary, now, onClick = { showStatus = true })
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            GlanceDial(glance, unit, now, dialSize)
                        }
                        if (hero != null) {
                            DetailRow(hero, unit, Modifier.padding(top = 16.dp))
                        }
                        lists()
                    }
                    actionBar()
                }
            }
        }

        if (settings.debugLogging) {
            DebugOverlay(state, fix, now, Modifier.align(Alignment.BottomCenter))
        }
    }

    if (showStatus) {
        ModalBottomSheet(
            onDismissRequest = { showStatus = false },
            containerColor = MaterialTheme.colorScheme.background,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            StatusSheetContent(
                summary = summary,
                state = state,
                locationStatus = locationStatus,
                fix = fix,
                online = online,
                settings = settings,
                credits = credits,
                now = now,
                onAction = {
                    showStatus = false
                    when (summary.action) {
                        StatusAction.OPEN_SETTINGS -> onOpenSettings()
                        StatusAction.START_MONITORING -> onStartMonitoring()
                        StatusAction.RETRY -> viewModel.retryNow()
                        StatusAction.NONE -> Unit
                    }
                }
            )
        }
    }

    detail?.let { obs ->
        ModalBottomSheet(
            onDismissRequest = { detail = null },
            containerColor = MaterialTheme.colorScheme.background,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            AircraftDetail(
                obs, unit, now,
                fleets.filter { obs.aircraftId in it.memberIds },
                onEdit = {
                    detail = null
                    onEditAircraft(obs.aircraftId)
                },
                onSnooze = {
                    viewModel.snooze(obs.aircraftId, 30)
                    detail = null
                }
            )
        }
    }
}
