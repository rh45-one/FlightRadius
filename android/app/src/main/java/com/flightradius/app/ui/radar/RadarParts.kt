package com.flightradius.app.ui.radar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flightradius.app.R
import com.flightradius.app.data.api.ApiError
import com.flightradius.app.data.prefs.AppSettings
import com.flightradius.app.domain.AircraftObservation
import com.flightradius.app.domain.DistanceUnit
import com.flightradius.app.domain.Fleet
import com.flightradius.app.domain.OpenSkyStatus
import com.flightradius.app.domain.TrackedAircraft
import com.flightradius.app.domain.UserFix
import com.flightradius.app.location.LocationStatus
import com.flightradius.app.service.MonitoringState
import com.flightradius.app.service.MonitoringStatus
import com.flightradius.app.ui.components.BearingArrow
import com.flightradius.app.ui.components.IssueCard
import com.flightradius.app.ui.components.MiniRingGauge
import com.flightradius.app.ui.components.RadialGauge
import com.flightradius.app.ui.components.RadarIllustration
import com.flightradius.app.ui.components.ShimmerBox
import com.flightradius.app.ui.components.zoneColor
import com.flightradius.app.ui.format.Format
import com.flightradius.app.ui.theme.extended

// ---------- label helpers ----------

internal fun locationLabel(s: LocationStatus, fix: UserFix?, now: Long): String =
    when (s) {
        is LocationStatus.Fix -> {
            val acc = s.accuracyM?.let { " ±${it.toInt()} m" } ?: ""
            "GPS$acc · ${Format.age(now, fix?.timeMs ?: now - s.ageMs)}"
        }
        LocationStatus.Searching -> "Searching…"
        LocationStatus.Manual -> "Manual"
        LocationStatus.PermissionDenied -> "Permission needed"
        LocationStatus.ProviderDisabled -> "Location off"
        LocationStatus.PlayServicesUnavailable -> "Play services missing"
    }

internal fun locationColor(s: LocationStatus): Color = when (s) {
    is LocationStatus.Fix -> EmeraldGreen
    LocationStatus.Searching -> AmberYellow
    LocationStatus.Manual -> CyanInfo
    LocationStatus.PermissionDenied, LocationStatus.ProviderDisabled,
    LocationStatus.PlayServicesUnavailable -> RoseDanger
}

internal fun backendLabel(s: OpenSkyStatus, online: Boolean): String = when {
    !online -> "Offline"
    s == OpenSkyStatus.OK -> "OpenSky OK"
    s == OpenSkyStatus.RATE_LIMITED -> "Rate limited"
    s == OpenSkyStatus.UNAVAILABLE -> "OpenSky down"
    s == OpenSkyStatus.TIMEOUT -> "OpenSky timeout"
    s == OpenSkyStatus.BACKEND_UNREACHABLE -> "Backend unreachable"
    else -> "Backend unknown"
}

internal fun backendColor(s: OpenSkyStatus, online: Boolean): Color = when {
    !online -> AmberYellow
    s == OpenSkyStatus.OK -> EmeraldGreen
    s == OpenSkyStatus.RATE_LIMITED || s == OpenSkyStatus.TIMEOUT -> AmberYellow
    s == OpenSkyStatus.UNAVAILABLE ||
        s == OpenSkyStatus.BACKEND_UNREACHABLE -> RoseDanger
    else -> CyanInfo
}

internal fun monitoringLabel(s: MonitoringStatus): String = when (s) {
    MonitoringStatus.RUNNING -> "Monitoring on"
    MonitoringStatus.PAUSED -> "Paused"
    MonitoringStatus.STOPPED -> "Monitoring off"
    MonitoringStatus.STARTING -> "Starting…"
    MonitoringStatus.DEFERRED_DOZE -> "Doze-deferred"
    MonitoringStatus.OFFLINE -> "Offline"
    MonitoringStatus.WAITING_FOR_LOCATION -> "Waiting for location"
    MonitoringStatus.ERROR -> "Error"
}

internal fun monitoringColor(s: MonitoringStatus): Color = when (s) {
    MonitoringStatus.RUNNING -> EmeraldGreen
    MonitoringStatus.PAUSED -> AmberYellow
    MonitoringStatus.STOPPED -> SlateGray
    MonitoringStatus.STARTING, MonitoringStatus.WAITING_FOR_LOCATION -> CyanInfo
    MonitoringStatus.DEFERRED_DOZE, MonitoringStatus.OFFLINE -> AmberYellow
    MonitoringStatus.ERROR -> RoseDanger
}

// Semantic colors resolved without a composable context for labels (chips
// recolor via MaterialTheme.colorScheme.extended where themed values matter).
private val EmeraldGreen = Color(0xFF34D399)
private val AmberYellow = Color(0xFFFBBF24)
private val RoseDanger = Color(0xFFFB7185)
private val CyanInfo = Color(0xFF22D3EE)
private val SlateGray = Color(0xFF94A3B8)

// ---------- issue cards ----------

@Composable
internal fun IssueCards(
    state: MonitoringState,
    settings: AppSettings,
    locationStatus: LocationStatus,
    online: Boolean,
    viewModel: RadarViewModel,
    onOpenSettings: () -> Unit,
    onStartMonitoring: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (locationStatus == LocationStatus.PermissionDenied) {
            IssueCard(
                title = stringResource(R.string.issue_location_denied),
                body = stringResource(R.string.issue_location_denied_body),
                actionLabel = stringResource(R.string.action_settings),
                onAction = onOpenSettings
            )
        }
        if (viewModel.localNetworkGuard.isBlocked()) {
            IssueCard(
                title = stringResource(R.string.issue_lan),
                body = stringResource(R.string.issue_lan_body),
                actionLabel = stringResource(R.string.action_fix),
                onAction = onStartMonitoring // runs the permission chain
            )
        }
        if (!online) {
            IssueCard(
                title = stringResource(R.string.issue_offline),
                body = stringResource(R.string.issue_offline_body)
            )
        }
        if (settings.resumeOnBoot &&
            settings.locationMode == com.flightradius.app.data.prefs.LocationMode.GPS
        ) {
            IssueCard(
                title = stringResource(R.string.issue_background_location),
                body = stringResource(R.string.issue_background_location_body)
            )
        }
        state.lastError?.let { err ->
            if (state.status == MonitoringStatus.ERROR ||
                state.status == MonitoringStatus.STOPPED && state.consecutiveFailures > 0
            ) {
                IssueCard(
                    title = stringResource(R.string.issue_last_error),
                    body = err.message,
                    tone = MaterialTheme.colorScheme.extended.danger,
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = { viewModel.retryNow() }
                )
            }
        }
    }
}

// ---------- hero ----------

@Composable
internal fun HeroCard(
    obs: AircraftObservation,
    unit: DistanceUnit,
    sweeping: Boolean,
    nowMs: Long
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val animatedDist by animateFloatAsState(
                targetValue = Format.kmToUnit(obs.distanceKm, unit).toFloat(),
                animationSpec = tween(600),
                label = "heroDist"
            )
            val desc = stringResource(
                R.string.gauge_description,
                Format.distance(obs.distanceKm, unit),
                Format.distance(obs.effectiveRadiusKm, unit)
            )
            Box(contentAlignment = Alignment.Center) {
                RadialGauge(
                    distanceKm = obs.distanceKm,
                    radiusKm = obs.effectiveRadiusKm,
                    sweeping = sweeping,
                    description = desc,
                    size = 240.dp
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "%.1f".format(animatedDist),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                        color = zoneColor(obs.distanceKm, obs.effectiveRadiusKm)
                    )
                    Text(
                        Format.distanceUnitLabel(unit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        Format.callsign(obs),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(
                            R.string.radius_label,
                            Format.distance(obs.effectiveRadiusKm, unit)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BearingArrow(obs.bearingDeg,
                            MaterialTheme.colorScheme.extended.info, size = 18.dp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            Format.bearing(obs.bearingDeg),
                            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum")
                            )
                    }
                    Text(
                        stringResource(R.string.bearing_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val cp = Format.closingParts(obs, unit)
                    Text(
                        cp?.speed ?: "—",
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        cp?.direction ?: stringResource(R.string.closing_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = when (cp?.trend) {
                            Format.ClosingTrend.APPROACHING ->
                                MaterialTheme.colorScheme.extended.danger
                            Format.ClosingTrend.RECEDING ->
                                MaterialTheme.colorScheme.extended.success
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        })
                }
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        Format.altitude(obs.altitudeM, unit) ?: "—",
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                        
                        maxLines = 1)
                    Text(
                        Format.speed(obs.velocityMps, unit) ?: "—",
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---------- ranked card ----------

@Composable
internal fun AircraftCard(
    obs: AircraftObservation,
    unit: DistanceUnit,
    fleetColors: List<Int>,
    snoozed: Boolean,
    nowMs: Long,
    onClick: () -> Unit,
    onSnooze: () -> Unit,
    onDismissAlert: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val inside = obs.distanceKm <= obs.effectiveRadiusKm
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (inside) {
                MaterialTheme.colorScheme.extended.danger.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        )
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniRingGauge(obs.distanceKm, obs.effectiveRadiusKm)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Format.callsign(obs),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (obs.icao24 != null) {
                        Text(
                            "  ${obs.icao24}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    for (c in fleetColors.take(4)) {
                        Box(
                            Modifier
                                .padding(start = 4.dp)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Format.distance(obs.distanceKm, unit),
                        style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                        
                        fontWeight = FontWeight.Bold,
                        color = zoneColor(obs.distanceKm, obs.effectiveRadiusKm)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        Format.bearing(obs.bearingDeg),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    buildList {
                        Format.altitude(obs.altitudeM, unit)?.let { add(it) }
                        Format.speed(obs.velocityMps, unit)?.let { add(it) }
                        Format.heading(obs.headingDeg)?.let { add("hdg $it") }
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Format.closing(obs, unit)?.let { cl ->
                    Text(
                        cl,
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        
                        color = if (Format.closingIsSteady(obs))
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else if ((obs.closingSpeedKmh ?: 0.0) >= 0)
                            MaterialTheme.colorScheme.extended.danger
                        else MaterialTheme.colorScheme.extended.success
                    )
                }
                if (inside) {
                    Row {
                        TextButton(onClick = onSnooze) {
                            Text(stringResource(R.string.action_snooze_30))
                        }
                        TextButton(onClick = onDismissAlert) {
                            Text(stringResource(R.string.action_dismiss))
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (inside) {
                    Text(
                        stringResource(R.string.alert_inside),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.extended.danger,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (snoozed) {
                    Text(
                        stringResource(R.string.alert_snoozed),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.extended.warning
                    )
                }
                obs.lastContactSec?.let { lc ->
                    val ageSec = nowMs / 1000 - lc.toLong()
                    Text(
                        Format.age(nowMs, lc.toLong() * 1000),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (ageSec > 60) MaterialTheme.colorScheme.extended.warning
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
internal fun NoDataRow(t: TrackedAircraft) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            t.identifier,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            stringResource(R.string.radar_no_live_data),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------- empty / skeleton / debug ----------

@Composable
internal fun EmptyState(onAdd: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RadarIllustration(size = 160.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.radar_empty_title),
            style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.radar_empty_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        androidx.compose.material3.Button(onClick = onAdd) {
            Text(stringResource(R.string.radar_add_aircraft))
        }
    }
}

@Composable
internal fun SkeletonHero() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ShimmerBox(Modifier.size(240.dp), shape = MaterialTheme.shapes.extraLarge)
        Spacer(Modifier.height(8.dp))
        ShimmerBox(Modifier.size(160.dp, 20.dp))
    }
}

@Composable
internal fun SkeletonCard() {
    Row(Modifier.padding(vertical = 4.dp)) {
        ShimmerBox(Modifier.size(44.dp), shape = MaterialTheme.shapes.small)
        Spacer(Modifier.width(12.dp))
        Column {
            ShimmerBox(Modifier.size(140.dp, 16.dp))
            Spacer(Modifier.height(6.dp))
            ShimmerBox(Modifier.size(220.dp, 12.dp))
        }
    }
}

@Composable
internal fun DebugOverlay(
    state: MonitoringState,
    fix: UserFix?,
    now: Long,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.padding(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = MaterialTheme.shapes.small
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                "cycles ${state.cycleCount} · last ${state.lastLatencyMs ?: "—"} ms · " +
                    "next ${state.nextCycleAtMs?.let { Format.countdown(now, it) } ?: "—"}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "fails ${state.consecutiveFailures} · opensky ${state.openSkyStatus} · " +
                    "doze ${state.dozing} · wl ${state.wakeLockHeld}/hb ${state.heartbeatHeld} · " +
                    "hp ${state.highPriority}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "fix ${fix?.let { Format.age(now, it.timeMs) } ?: "—"}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// ---------- sheets ----------

@Composable
internal fun ChipDetail(
    which: String,
    state: MonitoringState,
    locationStatus: LocationStatus,
    fix: UserFix?,
    online: Boolean,
    settings: AppSettings,
    now: Long,
    onOpenSettings: () -> Unit,
    onStartMonitoring: () -> Unit
) {
    Column(Modifier.padding(24.dp)) {
        val (title, body) = when (which) {
            "location" -> stringResource(R.string.chip_location) to
                locationLabel(locationStatus, fix, now)
            "backend" -> stringResource(R.string.chip_backend) to
                backendLabel(state.openSkyStatus, online)
            else -> stringResource(R.string.chip_monitoring) to
                monitoringLabel(state.status)
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium)
        state.lastError?.let {
            Spacer(Modifier.height(6.dp))
            Text(it.message, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.extended.danger)
        }
        Spacer(Modifier.height(12.dp))
        when (which) {
            "location" -> TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.action_open_settings))
            }
            "monitoring" -> if (state.status == MonitoringStatus.STOPPED) {
                TextButton(onClick = onStartMonitoring) {
                    Text(stringResource(R.string.radar_start))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun AircraftDetail(
    obs: AircraftObservation,
    unit: DistanceUnit,
    nowMs: Long,
    fleets: List<Fleet>,
    onEdit: () -> Unit
) {
    Column(Modifier.padding(24.dp)) {
        Text(Format.callsign(obs), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        val lDistance = stringResource(R.string.detail_distance)
        val lRadius = stringResource(R.string.detail_radius)
        val lBearing = stringResource(R.string.detail_bearing)
        val lAltitude = stringResource(R.string.detail_altitude)
        val lSpeed = stringResource(R.string.detail_speed)
        val lHeading = stringResource(R.string.detail_heading)
        val lLastContact = stringResource(R.string.detail_last_contact)
        val lFleets = stringResource(R.string.detail_fleets)
        val rows = buildList {
            fun row(l: String, v: String, c: Color? = null) = add(Triple(l, v, c))
            row(lDistance, Format.distance(obs.distanceKm, unit))
            row(lRadius, Format.distance(obs.effectiveRadiusKm, unit))
            row(lBearing, Format.bearing(obs.bearingDeg))
            Format.altitude(obs.altitudeM, unit)?.let { row(lAltitude, it) }
            Format.speed(obs.velocityMps, unit)?.let { row(lSpeed, it) }
            Format.heading(obs.headingDeg)?.let { row(lHeading, it) }
            Format.closingParts(obs, unit)?.let { cp ->
                row(cp.direction, cp.speed,
                    when (cp.trend) {
                        Format.ClosingTrend.APPROACHING ->
                            MaterialTheme.colorScheme.extended.danger
                        Format.ClosingTrend.RECEDING ->
                            MaterialTheme.colorScheme.extended.success
                        Format.ClosingTrend.STEADY -> null
                    })
            }
            obs.lastContactSec?.let {
                row(lLastContact, Format.age(nowMs, it.toLong() * 1000))
            }
            if (fleets.isNotEmpty()) {
                row(lFleets, fleets.joinToString(", ") { it.name })
            }
        }
        for ((k, v, kc) in rows) {
            Row(Modifier.padding(vertical = 2.dp)) {
                Text(k, Modifier.weight(0.4f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = kc ?: MaterialTheme.colorScheme.onSurfaceVariant)
                Text(v, Modifier.weight(0.6f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
                    )
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onEdit) {
            Text(stringResource(R.string.action_edit_aircraft))
        }
        Spacer(Modifier.height(24.dp))
    }
}
