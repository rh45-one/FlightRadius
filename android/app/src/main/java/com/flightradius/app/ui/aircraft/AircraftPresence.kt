package com.flightradius.app.ui.aircraft

import com.flightradius.app.domain.OpenSkyStatus
import com.flightradius.app.service.MonitoringState
import com.flightradius.app.service.MonitoringStatus

/** What the Aircraft-tab status dot says about one tracked aircraft. */
enum class AircraftPresence {
    /** Latest successful cycle saw this aircraft. */
    ONLINE,
    /** Latest successful cycle did not see it, or monitoring is not running. */
    OFFLINE,
    /** A cycle failed, so this aircraft could not be confirmed. */
    ERROR
}

fun flightDataFailed(state: MonitoringState): Boolean {
    if (state.status == MonitoringStatus.STOPPED || state.status == MonitoringStatus.PAUSED) {
        return false
    }
    if (state.status == MonitoringStatus.ERROR || state.status == MonitoringStatus.OFFLINE) {
        return true
    }
    if (state.lastError != null) return true
    return when (state.openSkyStatus) {
        OpenSkyStatus.RATE_LIMITED,
        OpenSkyStatus.AUTH_FAILED,
        OpenSkyStatus.UNAVAILABLE,
        OpenSkyStatus.TIMEOUT,
        OpenSkyStatus.UNREACHABLE -> true
        OpenSkyStatus.UNKNOWN, OpenSkyStatus.OK -> false
    }
}

/**
 * [detected] is true when the aircraft is in the latest snapshot.
 * [live] is true only while monitoring is actually running, so a stale
 * snapshot left after Stop does not keep the dot breathing.
 */
fun aircraftPresence(detected: Boolean, live: Boolean, dataFailed: Boolean): AircraftPresence =
    when {
        dataFailed -> AircraftPresence.ERROR
        live && detected -> AircraftPresence.ONLINE
        else -> AircraftPresence.OFFLINE
    }
