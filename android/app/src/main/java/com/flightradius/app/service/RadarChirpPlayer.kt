package com.flightradius.app.service

import android.media.AudioManager
import android.media.ToneGenerator
import com.flightradius.app.domain.MonitoringSnapshot
import com.flightradius.app.domain.RadarCadence
import com.flightradius.app.domain.TimeSource
import com.flightradius.app.util.log.AppLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Radar chirp: a short beep at a cadence derived from the closest tracked
 * aircraft's distance/radius. Only usable while the service runs with
 * while-in-use audio capability (i.e. not started from boot — on Android 17
 * a background-started app can't play audio). Never throws.
 *
 * [update] is called after EVERY monitoring cycle (and with null on pause);
 * the beep loop re-checks staleness before each beep.
 */
@Singleton
class RadarChirpPlayer @Inject constructor(
    private val time: TimeSource
) {
    companion object {
        private const val TAG = "RadarChirp"
        private const val BEEP_MS = 60
        private const val TONE_VOLUME = 80
    }

    private var job: Job? = null
    private var tone: ToneGenerator? = null
    private var disabled = false

    @Volatile private var lastSnapshotMs = 0L
    @Volatile private var staleAfterMs = Long.MAX_VALUE

    /** Latest cadence target; null = silent. */
    private val interval = MutableStateFlow<Long?>(null)

    fun start(scope: CoroutineScope) {
        if (job != null || disabled) return
        try {
            tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME)
        } catch (e: RuntimeException) {
            disabled = true
            AppLog.w(TAG, "ToneGenerator unavailable; radar disabled", throwable = e)
            return
        }
        job = scope.launch {
            interval.collectLatest { iv ->
                if (iv == null) awaitCancellation()
                while (true) {
                    val current = interval.value ?: break
                    // Re-check staleness before every beep.
                    if (time.nowMs() - lastSnapshotMs > staleAfterMs) {
                        interval.value = null
                        break
                    }
                    tone?.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_MS)
                    delay(current)
                }
            }
        }
    }

    /**
     * Update cadence from the latest snapshot (null -> silent, e.g. pause).
     * [intervalMs] is the monitoring interval; a snapshot older than 3x it
     * is stale.
     */
    fun update(snapshot: MonitoringSnapshot?, intervalMs: Long) {
        if (disabled) return
        if (snapshot != null) lastSnapshotMs = snapshot.timeMs
        staleAfterMs = if (intervalMs > 0) 3 * intervalMs else Long.MAX_VALUE
        val closest = snapshot?.closest
        val stale = snapshot == null ||
            time.nowMs() - snapshot.timeMs > staleAfterMs
        this.interval.value = if (stale || closest == null) null
        else RadarCadence.intervalMs(closest.distanceKm, closest.effectiveRadiusKm)
    }

    fun stop() {
        job?.cancel(); job = null
        interval.value = null
        tone?.release(); tone = null
    }
}
