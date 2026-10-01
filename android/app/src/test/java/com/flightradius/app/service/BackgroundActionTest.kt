package com.flightradius.app.service

import com.flightradius.app.service.AppVisibility.STARTED
import com.flightradius.app.service.AppVisibility.STOPPED
import com.flightradius.app.service.BackgroundAction.NONE
import com.flightradius.app.service.BackgroundAction.START
import com.flightradius.app.service.BackgroundAction.STOP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundActionTest {

    private val running = MonitoringStatus.entries.filter { it != MonitoringStatus.STOPPED }

    @Test fun `background on never does anything`() {
        for (event in AppVisibility.entries) for (desired in listOf(true, false))
            for (status in MonitoringStatus.entries)
                assertEquals(NONE, backgroundAction(event, true, desired, status))
    }

    @Test fun `leaving with background off stops whatever is running`() {
        for (status in running) for (desired in listOf(true, false))
            assertEquals("$status", STOP, backgroundAction(STOPPED, false, desired, status))
    }

    @Test fun `leaving with nothing running does nothing`() {
        for (desired in listOf(true, false))
            assertEquals(NONE, backgroundAction(STOPPED, false, desired, MonitoringStatus.STOPPED))
    }

    @Test fun `returning restarts only when desired and stopped`() {
        assertEquals(START, backgroundAction(STARTED, false, true, MonitoringStatus.STOPPED))
        assertEquals(NONE, backgroundAction(STARTED, false, false, MonitoringStatus.STOPPED))
        for (status in running) for (desired in listOf(true, false))
            assertEquals("$status", NONE, backgroundAction(STARTED, false, desired, status))
    }

    @Test fun `a user stop is never undone by returning`() {
        assertEquals(NONE, backgroundAction(STARTED, false, false, MonitoringStatus.STOPPED))
    }

    @Test fun `boot resume needs all three settings`() {
        assertTrue(shouldResumeOnBoot(true, true, true))
        assertFalse(shouldResumeOnBoot(false, true, true))
        assertFalse(shouldResumeOnBoot(true, false, true))
        assertFalse(shouldResumeOnBoot(true, true, false))
        assertFalse(shouldResumeOnBoot(false, false, false))
    }
}
