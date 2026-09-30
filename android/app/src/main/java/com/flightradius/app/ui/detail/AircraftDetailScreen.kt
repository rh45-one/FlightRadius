package com.flightradius.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flightradius.app.R
import com.flightradius.app.domain.DistanceUnit
import com.flightradius.app.ui.components.BearingArrow
import com.flightradius.app.ui.components.GroupedDivider
import com.flightradius.app.ui.components.GroupedRow
import com.flightradius.app.ui.components.GroupedSection
import com.flightradius.app.ui.format.Format
import com.flightradius.app.ui.format.labelRes
import com.flightradius.app.ui.theme.CodeFeatures
import com.flightradius.app.ui.theme.InterDisplay
import com.flightradius.app.ui.theme.NumericFeatures
import com.flightradius.app.ui.theme.extended
import kotlinx.coroutines.delay

@Composable
fun AircraftDetailScreen(
    onBack: () -> Unit,
    onShowOnMap: () -> Unit,
    onEdit: (aircraftId: Long) -> Unit,
    viewModel: AircraftDetailViewModel = hiltViewModel()
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(1000); now = System.currentTimeMillis() }
    }
    AircraftDetailContent(
        ui = ui,
        unit = settings.distanceUnit,
        nowMs = now,
        onBack = onBack,
        onTrack = { viewModel.trackThis() },
        onShowOnMap = { viewModel.showOnMap(); onShowOnMap() },
        onEdit = { ui.data?.aircraftId?.let(onEdit) },
        onSnooze = { ui.data?.aircraftId?.let { viewModel.snooze(it) } }
    )
}

@Composable
internal fun AircraftDetailContent(
    ui: DetailUi,
    unit: DistanceUnit,
    nowMs: Long,
    onBack: () -> Unit,
    onTrack: () -> Unit,
    onShowOnMap: () -> Unit,
    onEdit: () -> Unit,
    onSnooze: () -> Unit
) {
    val d = ui.data
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
        if (d == null) {
            Text(
                stringResource(R.string.radar_loading),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
            return@Column
        }
        val tint = if (d.matchesRule || (d.radiusKm != null && d.distanceKm <= d.radiusKm))
            MaterialTheme.colorScheme.extended.danger else MaterialTheme.colorScheme.onSurface

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .aircraftShared(d.key, SharedPart.CONTAINER)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    d.name,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold, fontFeatureSettings = CodeFeatures),
                    color = tint,
                    maxLines = 1,
                    modifier = Modifier.aircraftShared(d.key, SharedPart.CALLSIGN)
                )
                d.cls?.let {
                    Text(
                        stringResource(it.labelRes()),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        Format.glanceNumber(d.distanceKm, unit),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold,
                            fontFeatureSettings = NumericFeatures),
                        color = tint,
                        maxLines = 1,
                        modifier = Modifier.aircraftShared(d.key, SharedPart.DISTANCE)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        Format.distanceUnitLabel(unit),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        BearingArrow(d.bearingDeg, tint, size = 20.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            Format.bearingShort(d.bearingDeg),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFeatureSettings = NumericFeatures)
                        )
                    }
                }
                if (!ui.reporting) {
                    Text(
                        stringResource(R.string.detail_not_reporting, widgetClock(d.lastSeenMs)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.extended.warning,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        val rows = buildList {
            fun row(l: String, v: String?) { if (v != null) add(l to v) }
            row(stringResource(R.string.detail_altitude), Format.altitude(d.altitudeM, unit))
            row(stringResource(R.string.detail_speed), Format.speed(d.velocityMps, unit))
            row(stringResource(R.string.detail_heading), Format.heading(d.headingDeg))
            row(stringResource(R.string.detail_vertical_rate), d.verticalRateMps?.let { verticalRate(it, unit) })
            row(stringResource(R.string.detail_closing_speed), Format.speedKmh(d.closingSpeedKmh, unit))
            row(stringResource(R.string.detail_radius), d.radiusKm?.let { Format.distance(it, unit) })
            row(stringResource(R.string.detail_registration), d.registration)
            row(stringResource(R.string.detail_model), d.model)
            row(stringResource(R.string.detail_operator), d.operator)
            row(stringResource(R.string.detail_icao24), d.icao24?.lowercase())
            row(
                stringResource(R.string.detail_last_contact),
                d.lastContactSec?.let { Format.age(nowMs, it.toLong() * 1000) }
            )
        }
        GroupedSection(header = null) {
            rows.forEachIndexed { i, (label, value) ->
                if (i > 0) GroupedDivider()
                GroupedRow(
                    title = label,
                    titleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    titleStyle = MaterialTheme.typography.bodyMedium,
                    trailing = {
                        Text(
                            value,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFeatureSettings = NumericFeatures),
                            textAlign = TextAlign.End
                        )
                    }
                )
            }
        }

        if (d.kind == DetailKind.NEARBY && d.icao24 != null) {
            Button(
                onClick = onTrack,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp)
            ) { Text(stringResource(R.string.detail_track_this), style = MaterialTheme.typography.titleMedium) }
        } else {
            OutlinedButton(
                onClick = onShowOnMap,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp)
            ) { Text(stringResource(R.string.detail_show_on_map)) }
        }
        if (d.kind == DetailKind.TRACKED) {
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit_aircraft)) }
                if (d.radiusKm != null && d.distanceKm <= d.radiusKm) {
                    TextButton(onClick = onSnooze) { Text(stringResource(R.string.action_snooze_30)) }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

private fun widgetClock(ms: Long): String =
    java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(ms))

private fun verticalRate(mps: Double, unit: DistanceUnit): String =
    if (unit == DistanceUnit.MI) {
        String.format(java.util.Locale.getDefault(), "%+d ft/min", Math.round(mps * 196.85).toInt())
    } else String.format(java.util.Locale.getDefault(), "%+.1f m/s", mps)
