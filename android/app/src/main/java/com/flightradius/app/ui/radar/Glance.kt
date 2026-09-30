package com.flightradius.app.ui.radar

import com.flightradius.app.data.opensky.CreditState
import com.flightradius.app.domain.AircraftObservation
import com.flightradius.app.domain.MonitoringSnapshot
import com.flightradius.app.domain.OpenSkyStatus
import com.flightradius.app.location.LocationStatus
import com.flightradius.app.service.MonitoringStatus

enum class Zone { INSIDE, NEAR, CLEAR }

fun zoneOf(distanceKm: Double, radiusKm: Double): Zone = when {
    distanceKm <= radiusKm -> Zone.INSIDE
    distanceKm <= radiusKm * 2 -> Zone.NEAR
    else -> Zone.CLEAR
}

sealed interface Glance {
    /** Nothing is tracked. */
    data object NoAircraft : Glance

    /** Aircraft are tracked but no snapshot has arrived yet. */
    data object Loading : Glance

    /** A snapshot exists but none of the tracked aircraft is reporting. */
    data class NoneAirborne(val notReporting: Int) : Glance

    data class Nearest(
        val obs: AircraftObservation,
        val zone: Zone,
        val alsoInside: Int,
        val stale: Boolean,
        val snapshotAgeMs: Long
    ) : Glance
}

private const val MIN_STALE_MS = 60_000L

fun glanceOf(
    trackedCount: Int,
    snapshot: MonitoringSnapshot?,
    nowMs: Long,
    intervalSec: Int
): Glance {
    if (trackedCount == 0) return Glance.NoAircraft
    if (snapshot == null) return Glance.Loading
    val nearest = snapshot.ranked.minByOrNull { it.distanceKm }
        ?: return Glance.NoneAirborne(snapshot.noData.size)
    val ageMs = (nowMs - snapshot.timeMs).coerceAtLeast(0L)
    val staleAfterMs = maxOf(MIN_STALE_MS, 2L * intervalSec * 1000L)
    return Glance.Nearest(
        obs = nearest,
        zone = zoneOf(nearest.distanceKm, nearest.effectiveRadiusKm),
        alsoInside = snapshot.ranked.count {
            it !== nearest && zoneOf(it.distanceKm, it.effectiveRadiusKm) == Zone.INSIDE
        },
        stale = ageMs > staleAfterMs,
        snapshotAgeMs = ageMs
    )
}

enum class Tone { LIVE, NEUTRAL, WARNING, PROBLEM }

enum class StatusAction { NONE, OPEN_SETTINGS, START_MONITORING, RETRY }

enum class StatusKind {
    LOCATION_OFF, LOCATION_PERMISSION, PLAY_SERVICES_MISSING,
    LAN_BLOCKED, OFFLINE_RESUME,
    AUTH_FAILED, OUT_OF_CREDITS, UNREACHABLE_OPENSKY, UNREACHABLE_BACKEND,
    ERROR, LIVE, PAUSED, STARTING, FINDING_LOCATION, WAITING_BATTERY,
    OFFLINE, STOPPED
}

/**
 * [creditsLeft] is set on LIVE only when the balance is below a quarter of
 * the daily quota. [updatedAtMs] is the last successful cycle (LIVE).
 */
data class StatusSummary(
    val tone: Tone,
    val kind: StatusKind,
    val action: StatusAction,
    val creditsLeft: Int? = null,
    val updatedAtMs: Long? = null
)

private const val LOW_CREDITS_FRACTION = 0.25f

@Suppress("UNUSED_PARAMETER")
fun statusSummary(
    status: MonitoringStatus,
    openSkyStatus: OpenSkyStatus,
    locationStatus: LocationStatus,
    online: Boolean,
    lanBlocked: Boolean,
    credits: CreditState,
    lastSuccessAtMs: Long?,
    nowMs: Long,
    backendMode: Boolean = false
): StatusSummary {
    fun s(tone: Tone, kind: StatusKind, action: StatusAction = StatusAction.NONE) =
        StatusSummary(tone, kind, action)

    when (locationStatus) {
        LocationStatus.ProviderDisabled ->
            return s(Tone.PROBLEM, StatusKind.LOCATION_OFF, StatusAction.OPEN_SETTINGS)
        LocationStatus.PermissionDenied ->
            return s(Tone.PROBLEM, StatusKind.LOCATION_PERMISSION, StatusAction.OPEN_SETTINGS)
        LocationStatus.PlayServicesUnavailable ->
            return s(Tone.PROBLEM, StatusKind.PLAY_SERVICES_MISSING, StatusAction.OPEN_SETTINGS)
        else -> Unit
    }
    if (lanBlocked) {
        return s(Tone.PROBLEM, StatusKind.LAN_BLOCKED, StatusAction.START_MONITORING)
    }
    if (!online) return s(Tone.WARNING, StatusKind.OFFLINE_RESUME)
    when (openSkyStatus) {
        OpenSkyStatus.AUTH_FAILED ->
            return s(Tone.PROBLEM, StatusKind.AUTH_FAILED, StatusAction.OPEN_SETTINGS)
        OpenSkyStatus.RATE_LIMITED -> return s(Tone.WARNING, StatusKind.OUT_OF_CREDITS)
        OpenSkyStatus.UNAVAILABLE, OpenSkyStatus.UNREACHABLE, OpenSkyStatus.TIMEOUT ->
            return s(
                Tone.WARNING,
                if (backendMode) StatusKind.UNREACHABLE_BACKEND else StatusKind.UNREACHABLE_OPENSKY
            )
        else -> Unit
    }
    return when (status) {
        MonitoringStatus.ERROR -> s(Tone.PROBLEM, StatusKind.ERROR, StatusAction.RETRY)
        MonitoringStatus.RUNNING -> {
            val remaining = credits.remaining
            val low = remaining != null &&
                remaining.toFloat() / credits.dailyQuota < LOW_CREDITS_FRACTION
            StatusSummary(
                Tone.LIVE, StatusKind.LIVE, StatusAction.NONE,
                creditsLeft = if (low) remaining else null,
                updatedAtMs = lastSuccessAtMs
            )
        }
        MonitoringStatus.PAUSED -> s(Tone.WARNING, StatusKind.PAUSED)
        MonitoringStatus.STARTING -> s(Tone.NEUTRAL, StatusKind.STARTING)
        MonitoringStatus.WAITING_FOR_LOCATION -> s(Tone.NEUTRAL, StatusKind.FINDING_LOCATION)
        MonitoringStatus.DEFERRED_DOZE -> s(Tone.WARNING, StatusKind.WAITING_BATTERY)
        MonitoringStatus.OFFLINE -> s(Tone.WARNING, StatusKind.OFFLINE)
        MonitoringStatus.STOPPED -> s(Tone.NEUTRAL, StatusKind.STOPPED)
    }
}
