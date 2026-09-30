package com.flightradius.app.ui.radar

import com.flightradius.app.ui.format.localized
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flightradius.app.R
import com.flightradius.app.data.opensky.CreditState
import com.flightradius.app.data.prefs.AppSettings
import com.flightradius.app.data.prefs.DataSource
import com.flightradius.app.domain.AircraftObservation
import com.flightradius.app.domain.DistanceUnit
import com.flightradius.app.domain.Fleet
import com.flightradius.app.domain.NearbyAircraft
import com.flightradius.app.domain.OpenSkyStatus
import com.flightradius.app.domain.TrackedAircraft
import com.flightradius.app.domain.UserFix
import com.flightradius.app.location.LocationStatus
import com.flightradius.app.service.MonitoringState
import com.flightradius.app.service.MonitoringStatus
import com.flightradius.app.ui.components.BearingArrow
import com.flightradius.app.ui.components.GroupedDivider
import com.flightradius.app.ui.components.GroupedRow
import com.flightradius.app.ui.components.GroupedSection
import com.flightradius.app.ui.components.NavigationChevron
import com.flightradius.app.ui.components.DialScope
import com.flightradius.app.ui.detail.SharedPart
import com.flightradius.app.ui.detail.aircraftShared
import com.flightradius.app.ui.components.ProximityDial
import com.flightradius.app.ui.format.Format
import com.flightradius.app.ui.format.W
import com.flightradius.app.ui.format.Words
import com.flightradius.app.ui.format.icon
import com.flightradius.app.ui.format.labelRes
import com.flightradius.app.ui.theme.CodeFeatures
import com.flightradius.app.ui.theme.InterDisplay
import com.flightradius.app.ui.theme.NumericFeatures
import com.flightradius.app.ui.theme.extended
import java.util.Locale

// ---------- label helpers ----------

internal fun locationLabel(s: LocationStatus, fix: UserFix?, now: Long): String =
    when (s) {
        is LocationStatus.Fix -> {
            val acc = s.accuracyM?.let { " ±${it.toInt()} m" } ?: ""
            Words.get(W.LOC_FIX, acc, Format.age(now, fix?.timeMs ?: now - s.ageMs))
        }
        LocationStatus.Searching -> Words.get(W.LOC_SEARCHING)
        LocationStatus.Manual -> Words.get(W.LOC_MANUAL)
        LocationStatus.PermissionDenied -> Words.get(W.LOC_PERMISSION)
        LocationStatus.ProviderDisabled -> Words.get(W.LOC_PROVIDER_OFF)
        LocationStatus.PlayServicesUnavailable -> Words.get(W.LOC_PLAY)
    }

internal fun backendLabel(s: OpenSkyStatus, online: Boolean, source: DataSource): String = when {
    !online -> Words.get(W.BACKEND_OFFLINE)
    s == OpenSkyStatus.OK -> Words.get(W.BACKEND_OK)
    s == OpenSkyStatus.RATE_LIMITED -> Words.get(W.BACKEND_NO_CREDITS)
    s == OpenSkyStatus.AUTH_FAILED -> Words.get(W.BACKEND_LOGIN_FAILED)
    s == OpenSkyStatus.UNAVAILABLE -> Words.get(W.BACKEND_DOWN)
    s == OpenSkyStatus.TIMEOUT -> Words.get(W.BACKEND_TIMEOUT)
    s == OpenSkyStatus.UNREACHABLE ->
        if (source == DataSource.DIRECT) Words.get(W.BACKEND_UNREACHABLE_OPENSKY)
        else Words.get(W.BACKEND_UNREACHABLE_BACKEND)
    else -> if (source == DataSource.DIRECT) Words.get(W.BACKEND_OPENSKY) else Words.get(W.BACKEND_BACKEND)
}

/** "3.4k credits" label; null until a balance has been observed. */
internal fun creditsLabel(c: CreditState): String? = c.remaining?.let {
    if (it >= 1000) Words.get(W.CREDITS_K, String.format(Locale.getDefault(), "%.1f", it / 1000.0))
    else Words.get(W.CREDITS_N, it)
}

internal fun monitoringLabel(s: MonitoringStatus): String = when (s) {
    MonitoringStatus.RUNNING -> Words.get(W.MON_ON)
    MonitoringStatus.PAUSED -> Words.get(W.MON_PAUSED)
    MonitoringStatus.STOPPED -> Words.get(W.MON_OFF)
    MonitoringStatus.STARTING -> Words.get(W.MON_STARTING)
    MonitoringStatus.DEFERRED_DOZE -> Words.get(W.MON_DOZE)
    MonitoringStatus.OFFLINE -> Words.get(W.BACKEND_OFFLINE)
    MonitoringStatus.WAITING_FOR_LOCATION -> Words.get(W.MON_WAITING_LOCATION)
    MonitoringStatus.ERROR -> Words.get(W.MON_ERROR)
}

@Composable
internal fun zoneColor(zone: Zone): Color = when (zone) {
    Zone.INSIDE -> MaterialTheme.colorScheme.extended.danger
    Zone.NEAR -> MaterialTheme.colorScheme.extended.warning
    Zone.CLEAR -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun toneColor(tone: Tone): Color = when (tone) {
    Tone.LIVE -> MaterialTheme.colorScheme.extended.success
    Tone.WARNING -> MaterialTheme.colorScheme.extended.warning
    Tone.PROBLEM -> MaterialTheme.colorScheme.extended.danger
    Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun statusText(s: StatusSummary, nowMs: Long): String = when (s.kind) {
    StatusKind.LOCATION_OFF -> stringResource(R.string.status_location_off)
    StatusKind.LOCATION_PERMISSION -> stringResource(R.string.status_location_permission)
    StatusKind.PLAY_SERVICES_MISSING -> stringResource(R.string.status_play_services)
    StatusKind.LAN_BLOCKED -> stringResource(R.string.status_lan)
    StatusKind.OFFLINE_RESUME -> stringResource(R.string.status_offline_resume)
    StatusKind.AUTH_FAILED -> stringResource(R.string.status_auth_failed)
    StatusKind.OUT_OF_CREDITS -> stringResource(R.string.status_out_of_credits)
    StatusKind.UNREACHABLE_OPENSKY -> stringResource(R.string.status_unreachable_opensky)
    StatusKind.UNREACHABLE_BACKEND -> stringResource(R.string.status_unreachable_backend)
    StatusKind.ERROR -> stringResource(R.string.status_error)
    StatusKind.LIVE -> {
        val base = s.updatedAtMs?.let {
            stringResource(R.string.status_live_updated, Format.age(nowMs, it))
        } ?: stringResource(R.string.status_live)
        s.creditsLeft?.let {
            base + stringResource(R.string.status_credits_left_suffix, it)
        } ?: base
    }
    StatusKind.PAUSED -> stringResource(R.string.status_paused)
    StatusKind.STARTING -> stringResource(R.string.status_starting)
    StatusKind.FINDING_LOCATION -> stringResource(R.string.status_finding_location)
    StatusKind.WAITING_BATTERY -> stringResource(R.string.status_waiting_battery)
    StatusKind.OFFLINE -> stringResource(R.string.status_offline)
    StatusKind.STOPPED -> stringResource(R.string.status_stopped)
}

// ---------- status line + sheet ----------

@Composable
internal fun StatusLine(
    summary: StatusSummary,
    nowMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(toneColor(summary.tone))
        )
        Spacer(Modifier.width(10.dp))
        Text(
            statusText(summary, nowMs),
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = NumericFeatures),
            modifier = Modifier.weight(1f)
        )
        NavigationChevron()
    }
}

@Composable
internal fun StatusSheetContent(
    summary: StatusSummary,
    state: MonitoringState,
    locationStatus: LocationStatus,
    fix: UserFix?,
    online: Boolean,
    settings: AppSettings,
    credits: CreditState,
    now: Long,
    onAction: () -> Unit
) {
    Column(Modifier.padding(horizontal = 24.dp)) {
        Text(
            stringResource(R.string.status_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        GroupedSection(
            header = null,
            footer = state.lastError?.localized()
        ) {
            StatusValueRow(
                stringResource(R.string.chip_location),
                locationLabel(locationStatus, fix, now)
            )
            GroupedDivider()
            StatusValueRow(
                stringResource(R.string.status_row_flight_data),
                flightDataValue(state.openSkyStatus, online, settings.dataSource)
            )
            GroupedDivider()
            StatusValueRow(
                stringResource(R.string.chip_monitoring),
                monitoringValue(state.status)
            )
            GroupedDivider()
            StatusValueRow(
                stringResource(R.string.settings_credits),
                creditsLabel(credits) ?: stringResource(R.string.status_credits_unknown)
            )
        }
        val actionLabel = when (summary.action) {
            StatusAction.NONE -> null
            StatusAction.OPEN_SETTINGS -> stringResource(R.string.action_open_settings)
            StatusAction.START_MONITORING -> stringResource(R.string.status_action_allow)
            StatusAction.RETRY -> stringResource(R.string.action_retry)
        }
        if (actionLabel != null) {
            Button(
                onClick = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .heightIn(min = 48.dp)
            ) { Text(actionLabel) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun monitoringValue(s: MonitoringStatus): String = stringResource(
    when (s) {
        MonitoringStatus.RUNNING -> R.string.status_value_on
        MonitoringStatus.PAUSED -> R.string.status_paused
        MonitoringStatus.STOPPED -> R.string.status_value_off
        MonitoringStatus.STARTING -> R.string.status_starting
        MonitoringStatus.DEFERRED_DOZE -> R.string.status_waiting_battery
        MonitoringStatus.OFFLINE -> R.string.status_offline
        MonitoringStatus.WAITING_FOR_LOCATION -> R.string.status_finding_location
        MonitoringStatus.ERROR -> R.string.status_error
    }
)

@Composable
private fun flightDataValue(
    openSky: OpenSkyStatus,
    online: Boolean,
    source: DataSource
): String = stringResource(
    when {
        !online -> R.string.status_offline
        openSky == OpenSkyStatus.RATE_LIMITED -> R.string.status_out_of_credits
        openSky == OpenSkyStatus.AUTH_FAILED -> R.string.status_auth_failed
        openSky == OpenSkyStatus.UNAVAILABLE || openSky == OpenSkyStatus.UNREACHABLE ||
            openSky == OpenSkyStatus.TIMEOUT ->
            if (source == DataSource.BACKEND) R.string.status_unreachable_backend
            else R.string.status_unreachable_opensky
        else -> R.string.status_value_connected
    }
)

@Composable
private fun StatusValueRow(title: String, value: String) {
    GroupedRow(
        title = title,
        trailing = {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFeatureSettings = NumericFeatures),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

// ---------- dial ----------

@Composable
internal fun GlanceDial(
    glance: Glance,
    unit: DistanceUnit,
    nowMs: Long,
    dialSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    scope: DialScope? = null,
    onHeroClick: (() -> Unit)? = null,
    monitoringIdle: Boolean = false
) {
    val heroModifier = if (onHeroClick != null) modifier.clickable(onClick = onHeroClick) else modifier
    when (glance) {
        is Glance.Nearest -> {
            val obs = glance.obs
            val color = if (glance.stale) MaterialTheme.colorScheme.onSurfaceVariant
            else zoneColor(glance.zone)
            ProximityDial(
                modifier = heroModifier,
                size = dialSize,
                markerBearingDeg = obs.bearingDeg,
                markerHeadingDeg = obs.headingDeg,
                markerColor = zoneColor(glance.zone),
                description = nearestDescription(glance, unit, nowMs, scope),
                scope = scope
            ) {
                NearestCenter(glance, unit, nowMs, color)
            }
        }
        is Glance.NearbyNearest -> {
            val a = glance.aircraft
            val color = if (glance.stale) MaterialTheme.colorScheme.onSurfaceVariant
            else if (a.matchesRule) MaterialTheme.colorScheme.extended.danger
            else MaterialTheme.colorScheme.onSurface
            ProximityDial(
                modifier = heroModifier,
                size = dialSize,
                markerBearingDeg = a.bearingDeg,
                markerHeadingDeg = a.trackDeg,
                markerColor = if (a.matchesRule) MaterialTheme.colorScheme.extended.danger
                else MaterialTheme.colorScheme.onSurface,
                description = nearbyDescription(glance, unit, nowMs, scope),
                scope = scope?.copy(excludeIcao24 = a.icao24)
            ) {
                NearbyCenter(glance, unit, nowMs, color)
            }
        }
        is Glance.NothingNearby -> ProximityDial(modifier, dialSize, scope = scope) {
            CenterMessage(
                stringResource(R.string.radar_nothing_nearby),
                glance.radiusKm?.let {
                    stringResource(R.string.radar_nothing_nearby_body, Format.distance(it, unit))
                } ?: ""
            )
        }
        Glance.NoAircraft -> ProximityDial(modifier, dialSize) {
            CenterMessage(
                stringResource(R.string.radar_empty_title),
                stringResource(R.string.radar_empty_body)
            )
        }
        Glance.Loading -> ProximityDial(modifier, dialSize) {
            Text(
                stringResource(if (monitoringIdle) R.string.radar_loading_idle else R.string.radar_loading),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        is Glance.NoneAirborne -> ProximityDial(modifier, dialSize, scope = scope) {
            Text(
                stringResource(R.string.radar_none_airborne),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            if (glance.notReporting > 0) {
                Text(
                    pluralStringResource(
                        R.plurals.radar_not_reporting_count,
                        glance.notReporting, glance.notReporting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun CenterMessage(title: String, body: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(4.dp))
    Text(
        body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun stateWord(glance: Glance.Nearest, nowMs: Long): String = when {
    glance.stale -> stringResource(
        R.string.radar_last_update, Format.age(nowMs, nowMs - glance.snapshotAgeMs))
    glance.zone == Zone.INSIDE -> stringResource(R.string.alert_inside)
    glance.zone == Zone.NEAR -> stringResource(R.string.radar_state_nearby)
    else -> stringResource(R.string.radar_state_nearest)
}

@Composable
private fun NearestCenter(
    glance: Glance.Nearest,
    unit: DistanceUnit,
    nowMs: Long,
    color: Color
) {
    val obs = glance.obs
    Text(
        stateWord(glance, nowMs),
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.SemiBold, fontFeatureSettings = NumericFeatures),
        color = color,
        maxLines = 1,
        textAlign = TextAlign.Center
    )
    val animatedKm by animateFloatAsState(
        targetValue = obs.distanceKm.toFloat(),
        animationSpec = tween(600),
        label = "glanceDistance"
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            Format.glanceNumber(animatedKm.toDouble(), unit),
            style = MaterialTheme.typography.displayLarge.copy(
                fontFamily = InterDisplay,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = NumericFeatures),
            color = color,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 32.sp, maxFontSize = 96.sp),
            modifier = Modifier
                .weight(1f, fill = false)
                .alignByBaseline()
                .aircraftShared("t:${obs.aircraftId}", SharedPart.DISTANCE)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            Format.distanceUnitLabel(unit),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alignByBaseline()
        )
    }
    Text(
        Format.callsign(obs),
        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = CodeFeatures),
        maxLines = 1,
        textAlign = TextAlign.Center,
        modifier = Modifier.aircraftShared("t:${obs.aircraftId}", SharedPart.CALLSIGN)
    )
    if (glance.alsoInside > 0) {
        Text(
            pluralStringResource(
                R.plurals.radar_also_inside, glance.alsoInside, glance.alsoInside),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.extended.danger,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun NearbyCenter(glance: Glance.NearbyNearest, unit: DistanceUnit, nowMs: Long, color: Color) {
    val a = glance.aircraft
    Text(
        if (glance.stale) stringResource(
            R.string.radar_last_update, Format.age(nowMs, nowMs - glance.snapshotAgeMs))
        else stringResource(R.string.radar_nearest_nearby),
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.SemiBold, fontFeatureSettings = NumericFeatures),
        color = color,
        maxLines = 1,
        textAlign = TextAlign.Center
    )
    val animatedKm by animateFloatAsState(
        targetValue = a.distanceKm.toFloat(),
        animationSpec = tween(600),
        label = "nearbyDistance"
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Text(
            Format.glanceNumber(animatedKm.toDouble(), unit),
            style = MaterialTheme.typography.displayLarge.copy(
                fontFamily = InterDisplay,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = NumericFeatures),
            color = color,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 32.sp, maxFontSize = 96.sp),
            modifier = Modifier
                .weight(1f, fill = false)
                .alignByBaseline()
                .aircraftShared("n:${a.icao24}", SharedPart.DISTANCE)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            Format.distanceUnitLabel(unit),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alignByBaseline()
        )
    }
    Text(
        a.displayName,
        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = CodeFeatures),
        maxLines = 1,
        textAlign = TextAlign.Center,
        modifier = Modifier.aircraftShared("n:${a.icao24}", SharedPart.CALLSIGN)
    )
    Text(
        stringResource(a.cls.labelRes()),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun scopeSummary(scope: DialScope?, excludeIcao24: String?): String? {
    val others = scope?.nearby?.filter { it.icao24 != excludeIcao24 } ?: return null
    if (others.isEmpty()) return null
    val matching = others.count { it.matchesRule }
    return pluralStringResource(R.plurals.radar_others_nearby, others.size, others.size) + ", " +
        pluralStringResource(R.plurals.radar_matching_alerts, matching, matching)
}

@Composable
private fun nearbyDescription(
    glance: Glance.NearbyNearest, unit: DistanceUnit, nowMs: Long, scope: DialScope?
): String {
    val a = glance.aircraft
    val compass = stringArrayResource(R.array.compass_points)
    val idx = Math.round(((a.bearingDeg % 360 + 360) % 360) / 22.5).toInt() % 16
    val unitWord = stringResource(
        if (unit == DistanceUnit.KM) R.string.unit_km_spoken else R.string.unit_mi_spoken)
    return stringResource(R.string.radar_nearest_nearby) + ". " +
        stringResource(
            R.string.radar_dial_description,
            a.displayName, Format.glanceNumber(a.distanceKm, unit), unitWord, compass[idx]
        ) + ". " + stringResource(a.cls.labelRes()) + "." +
        (scopeSummary(scope, a.icao24)?.let { " $it." } ?: "")
}

@Composable
private fun nearestDescription(
    glance: Glance.Nearest, unit: DistanceUnit, nowMs: Long, scope: DialScope?
): String {
    val obs = glance.obs
    val compass = stringArrayResource(R.array.compass_points)
    val idx = Math.round(((obs.bearingDeg % 360 + 360) % 360) / 22.5).toInt() % 16
    val trend = when (Format.closingParts(obs, unit)?.trend) {
        Format.ClosingTrend.APPROACHING -> stringResource(R.string.radar_closing)
        Format.ClosingTrend.RECEDING -> stringResource(R.string.radar_moving_away)
        Format.ClosingTrend.STEADY -> stringResource(R.string.radar_steady)
        null -> null
    }
    val unitWord = stringResource(
        if (unit == DistanceUnit.KM) R.string.unit_km_spoken else R.string.unit_mi_spoken)
    val also = if (glance.alsoInside > 0) {
        pluralStringResource(
            R.plurals.radar_also_inside, glance.alsoInside, glance.alsoInside)
    } else null
    return buildList {
        add(stateWord(glance, nowMs))
        add(
            stringResource(
                R.string.radar_dial_description,
                Format.callsign(obs),
                Format.glanceNumber(obs.distanceKm, unit),
                unitWord,
                compass[idx]
            )
        )
        trend?.let { add(it) }
        also?.let { add(it) }
        scopeSummary(scope, null)?.let { add(it) }
    }.joinToString(". ") + "."
}

// ---------- detail row ----------

@Composable
internal fun DetailRow(obs: AircraftObservation, unit: DistanceUnit, modifier: Modifier = Modifier) {
    val cp = Format.closingParts(obs, unit)
    val closingLabel = when (cp?.trend) {
        Format.ClosingTrend.RECEDING -> stringResource(R.string.radar_moving_away)
        Format.ClosingTrend.STEADY -> stringResource(R.string.radar_steady)
        else -> stringResource(R.string.radar_closing)
    }
    DetailRowContent(
        obs.bearingDeg, Format.altitude(obs.altitudeM, unit), closingLabel, cp?.speed, modifier)
}

@Composable
internal fun NearbyDetailRow(a: NearbyAircraft, unit: DistanceUnit, modifier: Modifier = Modifier) {
    DetailRowContent(
        a.bearingDeg, Format.altitude(a.altitudeM, unit),
        stringResource(R.string.detail_speed), Format.speed(a.velocityMps, unit), modifier)
}

@Composable
private fun DetailRowContent(
    bearingDeg: Double,
    altitude: String?,
    thirdLabel: String,
    thirdValue: String?,
    modifier: Modifier
) {
    val stacked = LocalDensity.current.fontScale >= 1.5f
    val directionLabel = stringResource(R.string.detail_direction)
    val altitudeLabel = stringResource(R.string.detail_altitude)
    val direction: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BearingArrow(bearingDeg, MaterialTheme.colorScheme.onSurface, size = 18.dp)
            Spacer(Modifier.width(6.dp))
            DetailValue(Format.bearingShort(bearingDeg))
        }
    }
    val altitudeValue: @Composable () -> Unit = { DetailValue(altitude ?: "—") }
    val third: @Composable () -> Unit = { DetailValue(thirdValue ?: "—") }

    if (stacked) {
        Column(modifier.fillMaxWidth()) {
            StackedDetail(directionLabel, direction)
            StackedDetail(altitudeLabel, altitudeValue)
            StackedDetail(thirdLabel, third)
        }
    } else {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            ColumnDetail(directionLabel, direction, Modifier.weight(1f))
            ColumnDetail(altitudeLabel, altitudeValue, Modifier.weight(1f))
            ColumnDetail(thirdLabel, third, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DetailValue(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = NumericFeatures),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1
    )
}

@Composable
private fun ColumnDetail(label: String, value: @Composable () -> Unit, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        value()
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun StackedDetail(label: String, value: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        value()
    }
}

// ---------- lists ----------

@Composable
internal fun AlsoTrackingSection(
    ranked: List<AircraftObservation>,
    unit: DistanceUnit,
    snoozes: Map<Long, Long>,
    nowMs: Long,
    onClick: (AircraftObservation) -> Unit
) {
    if (ranked.isEmpty()) return
    GroupedSection(header = stringResource(R.string.radar_also_tracking)) {
        ranked.forEachIndexed { i, obs ->
            if (i > 0) GroupedDivider()
            TrackingRow(
                obs = obs,
                unit = unit,
                snoozed = snoozes[obs.aircraftId]?.let { it > nowMs } == true,
                nowMs = nowMs,
                onClick = { onClick(obs) }
            )
        }
    }
}

@Composable
private fun TrackingRow(
    obs: AircraftObservation,
    unit: DistanceUnit,
    snoozed: Boolean,
    nowMs: Long,
    onClick: () -> Unit
) {
    val zone = zoneOf(obs.distanceKm, obs.effectiveRadiusKm)
    val color = zoneColor(zone)
    val inside = zone == Zone.INSIDE
    val ageSec = obs.lastContactSec?.let { nowMs / 1000 - it.toLong() }
    val subtitle = buildList {
        Format.altitude(obs.altitudeM, unit)?.let { add(it) }
        Format.speed(obs.velocityMps, unit)?.let { add(it) }
    }.joinToString(" · ").ifEmpty { null }
    GroupedRow(
        title = Format.callsign(obs),
        modifier = Modifier.aircraftShared("t:${obs.aircraftId}", SharedPart.CONTAINER),
        titleModifier = Modifier.aircraftShared("t:${obs.aircraftId}", SharedPart.CALLSIGN),
        titleStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = CodeFeatures),
        subtitle = subtitle,
        subtitleStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = NumericFeatures),
        leading = { BearingArrow(obs.bearingDeg, color, size = 20.dp) },
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Format.distance(obs.distanceKm, unit),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = NumericFeatures),
                    color = color,
                    modifier = Modifier.aircraftShared("t:${obs.aircraftId}", SharedPart.DISTANCE)
                )
                when {
                    snoozed -> Text(
                        stringResource(R.string.alert_snoozed),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    inside -> Text(
                        stringResource(R.string.alert_inside),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.extended.danger)
                    ageSec != null && ageSec > 60 -> Text(
                        Format.age(nowMs, obs.lastContactSec.toLong() * 1000),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.extended.warning)
                }
            }
        },
        onClick = onClick
    )
}

private const val NEARBY_MAX_ROWS = 8

@Composable
internal fun NearbySection(
    nearby: List<NearbyAircraft>,
    unit: DistanceUnit,
    stale: Boolean = false,
    onClick: (NearbyAircraft) -> Unit = {}
) {
    if (nearby.isEmpty()) return
    val shown = nearby.take(NEARBY_MAX_ROWS)
    val extra = nearby.size - shown.size
    GroupedSection(
        header = stringResource(
            if (stale) R.string.radar_nearby_stale else R.string.radar_nearby)
    ) {
        shown.forEachIndexed { i, a ->
            if (i > 0) GroupedDivider()
            NearbyRow(a, unit, onClick = { onClick(a) })
        }
        if (extra > 0) {
            GroupedDivider()
            GroupedRow(
                title = stringResource(R.string.radar_nearby_more, extra),
                titleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                titleStyle = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun NearbyRow(a: NearbyAircraft, unit: DistanceUnit, onClick: () -> Unit) {
    val tint = if (a.matchesRule) MaterialTheme.colorScheme.extended.danger
    else MaterialTheme.colorScheme.onSurface
    val subtitle = buildList {
        add(stringResource(a.cls.labelRes()))
        Format.altitude(a.altitudeM, unit)?.let { add(it) }
        Format.speed(a.velocityMps, unit)?.let { add(it) }
    }.joinToString(" · ")
    GroupedRow(
        title = a.displayName,
        modifier = Modifier.aircraftShared("n:${a.icao24}", SharedPart.CONTAINER),
        titleModifier = Modifier.aircraftShared("n:${a.icao24}", SharedPart.CALLSIGN),
        onClick = onClick,
        titleColor = tint,
        titleStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = CodeFeatures),
        subtitle = subtitle,
        subtitleStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = NumericFeatures),
        leading = {
            Icon(
                a.cls.icon(),
                contentDescription = stringResource(a.cls.labelRes()),
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Format.distance(a.distanceKm, unit),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = NumericFeatures),
                    color = tint,
                    modifier = Modifier.aircraftShared("n:${a.icao24}", SharedPart.DISTANCE)
                )
                Spacer(Modifier.width(8.dp))
                BearingArrow(a.bearingDeg, tint, size = 20.dp)
            }
        }
    )
}

@Composable
internal fun NotReportingSection(noData: List<TrackedAircraft>) {
    if (noData.isEmpty()) return
    GroupedSection(header = stringResource(R.string.radar_not_reporting)) {
        noData.forEachIndexed { i, t ->
            if (i > 0) GroupedDivider()
            GroupedRow(
                title = t.identifier,
                titleStyle = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = CodeFeatures),
                titleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                subtitle = stringResource(R.string.radar_no_live_data)
            )
        }
    }
}

// ---------- action bar ----------

@Composable
internal fun ActionBar(
    status: MonitoringStatus,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider(
            thickness = androidx.compose.ui.unit.Dp.Hairline,
            color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (status) {
                MonitoringStatus.STOPPED -> Button(
                    onClick = onStart,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                ) { Text(stringResource(R.string.radar_start), style = MaterialTheme.typography.titleMedium) }
                MonitoringStatus.PAUSED -> {
                    Button(
                        onClick = onResume,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                    ) { Text(stringResource(R.string.radar_resume), style = MaterialTheme.typography.titleMedium) }
                    TextButton(
                        onClick = onStop,
                        modifier = Modifier.heightIn(min = 56.dp)
                    ) { Text(stringResource(R.string.radar_stop)) }
                }
                else -> {
                    FilledTonalButton(
                        onClick = onPause,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                    ) { Text(stringResource(R.string.radar_pause), style = MaterialTheme.typography.titleMedium) }
                    TextButton(
                        onClick = onStop,
                        modifier = Modifier.heightIn(min = 56.dp)
                    ) { Text(stringResource(R.string.radar_stop)) }
                }
            }
        }
    }
}

// ---------- debug ----------

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

// ---------- detail sheet ----------

@Composable
internal fun AircraftDetail(
    obs: AircraftObservation,
    unit: DistanceUnit,
    nowMs: Long,
    fleets: List<Fleet>,
    onEdit: () -> Unit,
    onSnooze: () -> Unit
) {
    val inside = obs.distanceKm <= obs.effectiveRadiusKm
    Column(Modifier.padding(horizontal = 24.dp)) {
        Text(
            Format.callsign(obs),
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = CodeFeatures)
        )
        val rows = buildList {
            fun row(l: String, v: String, code: Boolean = false) = add(Triple(l, v, code))
            row(stringResource(R.string.detail_distance), Format.distance(obs.distanceKm, unit))
            row(stringResource(R.string.detail_radius), Format.distance(obs.effectiveRadiusKm, unit))
            row(stringResource(R.string.detail_bearing), Format.bearing(obs.bearingDeg))
            Format.altitude(obs.altitudeM, unit)?.let {
                row(stringResource(R.string.detail_altitude), it)
            }
            Format.speed(obs.velocityMps, unit)?.let {
                row(stringResource(R.string.detail_speed), it)
            }
            Format.heading(obs.headingDeg)?.let {
                row(stringResource(R.string.detail_heading), it)
            }
            Format.closingParts(obs, unit)?.let { cp ->
                row(
                    when (cp.trend) {
                        Format.ClosingTrend.APPROACHING -> stringResource(R.string.radar_closing)
                        Format.ClosingTrend.RECEDING -> stringResource(R.string.radar_moving_away)
                        Format.ClosingTrend.STEADY -> stringResource(R.string.radar_steady)
                    },
                    cp.speed
                )
            }
            obs.lastContactSec?.let {
                row(stringResource(R.string.detail_last_contact), Format.age(nowMs, it.toLong() * 1000))
            }
            if (fleets.isNotEmpty()) {
                row(stringResource(R.string.detail_fleets), fleets.joinToString(", ") { it.name })
            }
        }
        GroupedSection(header = null) {
            rows.forEachIndexed { i, (label, value, _) ->
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
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onEdit) {
                Text(stringResource(R.string.action_edit_aircraft))
            }
            if (inside) {
                TextButton(onClick = onSnooze) {
                    Text(stringResource(R.string.action_snooze_30))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
