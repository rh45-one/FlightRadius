package com.flightradius.app.ui.settings

import com.flightradius.app.data.prefs.AppSettings
import com.flightradius.app.data.prefs.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeDefaultTest {
    @Test
    fun `default theme mode follows the system`() {
        assertEquals(ThemeMode.SYSTEM, AppSettings().themeMode)
    }
}
