package com.flightradius.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.currentState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.flightradius.app.MainActivity
import com.flightradius.app.R
import com.flightradius.app.data.prefs.AppSettings
import com.flightradius.app.data.prefs.SettingsRepository
import com.flightradius.app.data.repo.AircraftRepository
import com.flightradius.app.service.MonitoringStateRepository
import com.flightradius.app.ui.theme.Amber400
import com.flightradius.app.ui.theme.Amber800
import com.flightradius.app.ui.theme.Cyan400
import com.flightradius.app.ui.theme.Cyan700
import com.flightradius.app.ui.theme.DarkScheme
import com.flightradius.app.ui.theme.LightScheme
import com.flightradius.app.ui.theme.Rose400
import com.flightradius.app.ui.theme.Rose700
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun stateRepository(): MonitoringStateRepository
    fun settingsRepository(): SettingsRepository
    fun aircraftRepository(): AircraftRepository
}

private val SmallSize = DpSize(110.dp, 40.dp)
private val MediumSize = DpSize(250.dp, 110.dp)

/** Day/night widget colors from the same palettes as the app (system light/dark, not the in-app theme). */
private val WidgetColors = ColorProviders(light = LightScheme, dark = DarkScheme)

private val DangerColor = ColorProvider(day = Rose700, night = Rose400)
private val WarningColor = ColorProvider(day = Amber800, night = Amber400)
private val InfoColor = ColorProvider(day = Cyan700, night = Cyan400)

private fun HeroState.color() = when (this) {
    HeroState.INSIDE, HeroState.NEARBY_MATCH -> DangerColor
    HeroState.NEAR -> WarningColor
    HeroState.CLEAR, HeroState.NEARBY -> InfoColor
}

class FlightRadiusWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SmallSize, MediumSize))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = loadContent(context)
        provideContent {
            // An already-running Glance session recomposes on state changes but
            // does not re-enter provideGlance, so refresh the content here.
            val tick = currentState<Preferences>()[RefreshKey]
            var content by remember { mutableStateOf(initial) }
            LaunchedEffect(tick) {
                if (tick != null) content = loadContent(context)
            }
            GlanceTheme(colors = WidgetColors) { WidgetBody(content) }
        }
    }

    companion object {
        val RefreshKey = longPreferencesKey("refresh")

        /**
         * Rebuilt from the process singletons on every update. A freshly
         * started process has no snapshot and reports "Monitoring off"; the
         * previously rendered views stay on the launcher until then.
         */
        suspend fun loadContent(context: Context): WidgetContent {
            val entry = EntryPointAccessors.fromApplication(
                context.applicationContext, WidgetEntryPoint::class.java)
            val settings: AppSettings = entry.settingsRepository().settings.first()
            val state = entry.stateRepository().state.value
            val tracked = entry.aircraftRepository().getAll().size
            return WidgetContent.from(
                state, tracked, settings.airspaceWatch, settings.distanceUnit,
                state.plannedIntervalSec ?: settings.monitoringIntervalSec,
                System.currentTimeMillis(), ::widgetTime
            )
        }
    }
}

internal fun widgetTime(ms: Long): String =
    java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(ms))

@Composable
private fun WidgetBody(content: WidgetContent) {
    val size = LocalSize.current
    val context = LocalContext.current
    val medium = size.width >= 200.dp && size.height >= 90.dp
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(if (medium) 14.dp else 10.dp)
            .clickable(
                actionStartActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        if (medium) MediumContent(content) else SmallContent(content)
    }
}

@Composable
private fun label(resId: Int): String = LocalContext.current.getString(resId)

@Composable
private fun SmallContent(c: WidgetContent) {
    val hero = c.hero
    when {
        !c.monitoring -> Plain(label(R.string.widget_monitoring_off))
        hero == null -> Plain(
            label(if (c.empty == WidgetEmpty.NO_AIRCRAFT) R.string.widget_no_aircraft
            else R.string.widget_nothing_inside))
        else -> Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = GlanceModifier.size(10.dp).cornerRadius(5.dp)
                    .background(hero.state.color())
            ) {}
            Spacer(GlanceModifier.width(8.dp))
            Column {
                Text(
                    hero.distanceText,
                    style = TextStyle(
                        fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.onSurface),
                    maxLines = 1
                )
                Text(
                    hero.name,
                    style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun Plain(text: String) {
    Text(
        text,
        style = TextStyle(
            fontSize = 15.sp, fontWeight = FontWeight.Medium,
            color = GlanceTheme.colors.onSurface),
        maxLines = 2
    )
}

@Composable
private fun MediumContent(c: WidgetContent) {
    val hero = c.hero
    Column(modifier = GlanceModifier.fillMaxSize()) {
        if (hero == null) {
            Text(
                label(
                    when {
                        !c.monitoring -> R.string.widget_monitoring_off
                        c.empty == WidgetEmpty.NO_AIRCRAFT -> R.string.widget_no_aircraft
                        c.empty == WidgetEmpty.LOADING -> R.string.radar_loading
                        else -> R.string.widget_nothing_inside
                    }),
                style = TextStyle(
                    fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface)
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = GlanceModifier.size(10.dp).cornerRadius(5.dp)
                        .background(hero.state.color())
                ) {}
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    label(
                        when (hero.state) {
                            HeroState.INSIDE -> R.string.alert_inside
                            HeroState.NEAR -> R.string.radar_state_nearby
                            HeroState.CLEAR -> R.string.radar_state_nearest
                            HeroState.NEARBY, HeroState.NEARBY_MATCH -> R.string.radar_nearest_nearby
                        }),
                    style = TextStyle(
                        fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        color = hero.state.color())
                )
            }
            Text(
                hero.distanceText,
                style = TextStyle(
                    fontSize = 44.sp, fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface),
                maxLines = 1
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    hero.name,
                    style = TextStyle(
                        fontSize = 16.sp, fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.onSurface),
                    maxLines = 1
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    listOfNotNull(hero.directionText, hero.altitudeText).joinToString(" · "),
                    style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurfaceVariant),
                    maxLines = 1
                )
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        Text(
            when {
                !c.monitoring && hero == null -> ""
                !c.monitoring -> label(R.string.widget_monitoring_off)
                c.updated != null -> LocalContext.current.getString(R.string.widget_updated, c.updated)
                else -> ""
            },
            style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant)
        )
    }
}
