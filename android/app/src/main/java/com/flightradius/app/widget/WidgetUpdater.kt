package com.flightradius.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.flightradius.app.data.prefs.SettingsRepository
import com.flightradius.app.data.repo.AircraftRepository
import com.flightradius.app.di.ApplicationScope
import com.flightradius.app.service.MonitoringStateRepository
import com.flightradius.app.util.log.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Pushes widget updates from the app scope: only when the rendered content
 * actually changes, at most once per [MIN_INTERVAL_MS], and immediately when
 * monitoring starts or stops.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateRepository: MonitoringStateRepository,
    private val settingsRepository: SettingsRepository,
    private val aircraftRepository: AircraftRepository
) {
    companion object {
        const val MIN_INTERVAL_MS = 15_000L
        private const val TAG = "WidgetUpdater"
    }

    fun start(scope: CoroutineScope) {
        scope.launch {
            var last: WidgetContent? = null
            var lastPushAt = 0L
            combine(
                stateRepository.state, settingsRepository.settings, aircraftRepository.aircraft
            ) { state, settings, tracked ->
                WidgetContent.from(
                    state, tracked.size, settings.airspaceWatch, settings.distanceUnit,
                    state.plannedIntervalSec ?: settings.monitoringIntervalSec,
                    System.currentTimeMillis(), ::widgetTime
                )
            }.distinctUntilChanged().collectLatest { content ->
                val immediate = last == null || last?.monitoring != content.monitoring
                if (!immediate) {
                    val wait = lastPushAt + MIN_INTERVAL_MS - System.currentTimeMillis()
                    if (wait > 0) delay(wait)
                }
                last = content
                lastPushAt = System.currentTimeMillis()
                runCatching {
                    val widget = FlightRadiusWidget()
                    for (id in GlanceAppWidgetManager(context).getGlanceIds(FlightRadiusWidget::class.java)) {
                        updateAppWidgetState(context, id) { it[FlightRadiusWidget.RefreshKey] = System.nanoTime() }
                        widget.update(context, id)
                    }
                }
                    .onFailure { AppLog.w(TAG, "widget update failed", throwable = it) }
            }
        }
    }
}
