package com.flightradius.app

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.flightradius.app.widget.FlightRadiusWidget
import com.flightradius.app.widget.FlightRadiusWidgetReceiver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetSmokeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun receiverIsRegisteredAsAppWidgetProvider() {
        val info = context.packageManager.getReceiverInfo(
            ComponentName(context, FlightRadiusWidgetReceiver::class.java),
            PackageManager.GET_META_DATA
        )
        assertNotNull(info.metaData?.get("android.appwidget.provider"))
        assertTrue(info.exported)
    }

    @Test
    fun glanceIdsCanBeQueriedWithoutCrash() = runBlocking {
        val ids = GlanceAppWidgetManager(context).getGlanceIds(FlightRadiusWidget::class.java)
        assertTrue(ids.size >= 0)
    }
}
