package com.flightradius.app.ui.aircraft

import com.flightradius.app.data.api.ApiError
import com.flightradius.app.domain.OpenSkyStatus
import com.flightradius.app.service.MonitoringState
import com.flightradius.app.service.MonitoringStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AircraftPresenceTest {

    @Test
    fun `running and detected is online`() {
        assertEquals(
            AircraftPresence.ONLINE,
            aircraftPresence(detected = true, live = true, dataFailed = false)
        )
    }

    @Test
    fun `running and not detected is offline`() {
        assertEquals(
            AircraftPresence.OFFLINE,
            aircraftPresence(detected = false, live = true, dataFailed = false)
        )
    }

    @Test
    fun `stopped snapshot does not stay online`() {
        assertEquals(
            AircraftPresence.OFFLINE,
            aircraftPresence(detected = true, live = false, dataFailed = false)
        )
    }

    @Test
    fun `a failed cycle is an error even if last seen`() {
        assertEquals(
            AircraftPresence.ERROR,
            aircraftPresence(detected = true, live = true, dataFailed = true)
        )
    }

    @Test
    fun `stopped or paused is not a data failure`() {
        assertFalse(flightDataFailed(MonitoringState(status = MonitoringStatus.STOPPED, lastError = ApiError.Network())))
        assertFalse(flightDataFailed(MonitoringState(status = MonitoringStatus.PAUSED, openSkyStatus = OpenSkyStatus.TIMEOUT)))
    }

    @Test
    fun `rate limit and fetch errors fail the cycle`() {
        assertTrue(flightDataFailed(MonitoringState(status = MonitoringStatus.RUNNING, openSkyStatus = OpenSkyStatus.RATE_LIMITED)))
        assertTrue(flightDataFailed(MonitoringState(status = MonitoringStatus.RUNNING, lastError = ApiError.Network())))
        assertTrue(flightDataFailed(MonitoringState(status = MonitoringStatus.OFFLINE)))
        assertFalse(flightDataFailed(MonitoringState(status = MonitoringStatus.RUNNING, openSkyStatus = OpenSkyStatus.OK)))
    }
}
