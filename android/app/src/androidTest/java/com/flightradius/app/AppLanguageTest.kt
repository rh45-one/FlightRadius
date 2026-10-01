package com.flightradius.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flightradius.app.domain.DistanceUnit
import com.flightradius.app.ui.format.Format
import com.flightradius.app.util.AppLanguage
import com.flightradius.app.util.AppLanguageOption
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the API 33+ (LocaleManager) path; the API 26-32 path needs an old device. */
@RunWith(AndroidJUnit4::class)
class AppLanguageTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun reset() {
        AppLanguage.set(context, AppLanguageOption.SYSTEM)
        waitFor { AppLanguage.current(context) == AppLanguageOption.SYSTEM }
    }

    private fun waitFor(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (!condition() && System.currentTimeMillis() < end) Thread.sleep(50)
    }

    private fun appString(id: Int) = context.applicationContext.resources.getString(id)

    @Test
    fun spanishSwitchesStringsAndNumbers() {
        AppLanguage.set(context, AppLanguageOption.SPANISH)
        waitFor { appString(R.string.nav_aircraft) == "Aeronaves" }
        assertEquals(AppLanguageOption.SPANISH, AppLanguage.current(context))
        assertEquals("Aeronaves", appString(R.string.nav_aircraft))
        assertEquals("3,0 km", Format.distance(3.0, DistanceUnit.KM))
    }

    @Test
    fun englishSwitchesBack() {
        AppLanguage.set(context, AppLanguageOption.SPANISH)
        waitFor { appString(R.string.nav_aircraft) == "Aeronaves" }
        AppLanguage.set(context, AppLanguageOption.ENGLISH)
        waitFor { appString(R.string.nav_aircraft) == "Aircraft" }
        assertEquals(AppLanguageOption.ENGLISH, AppLanguage.current(context))
        assertEquals("Aircraft", appString(R.string.nav_aircraft))
        assertEquals("3.0 km", Format.distance(3.0, DistanceUnit.KM))
    }

    @Test
    fun systemFollowsTheDeviceLocale() {
        AppLanguage.set(context, AppLanguageOption.SPANISH)
        waitFor { AppLanguage.current(context) == AppLanguageOption.SPANISH }
        AppLanguage.set(context, AppLanguageOption.SYSTEM)
        waitFor { AppLanguage.current(context) == AppLanguageOption.SYSTEM }
        assertEquals(AppLanguageOption.SYSTEM, AppLanguage.current(context))
        val device = context.getSystemService(android.app.LocaleManager::class.java)
            .systemLocales[0].language
        waitFor { appString(R.string.nav_aircraft) == (if (device == "es") "Aeronaves" else "Aircraft") }
        assertEquals(
            if (device == "es") "Aeronaves" else "Aircraft",
            appString(R.string.nav_aircraft))
    }
}
