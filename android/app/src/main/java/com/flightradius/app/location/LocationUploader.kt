package com.flightradius.app.location

import com.flightradius.app.data.repo.FlightRadiusRepository
import com.flightradius.app.di.ApplicationScope
import com.flightradius.app.domain.TimeSource
import com.flightradius.app.domain.UploadThrottle
import com.flightradius.app.domain.UserFix
import com.flightradius.app.util.log.AppLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Uploads acquired fixes to /api/user/location — throttled by
 * [UploadThrottle] (≥30s apart unless moved ≥100m; manual once per change).
 * Best-effort: failures are logged at DEBUG only.
 */
@Singleton
class LocationUploader @Inject constructor(
    private val locationRepository: LocationRepository,
    private val repository: FlightRadiusRepository,
    @ApplicationScope private val scope: CoroutineScope,
    private val time: TimeSource
) {
    private var job: Job? = null
    private var lastUploaded: UserFix? = null
    private var lastUploadedAtMs: Long? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            locationRepository.fix.collect { fix ->
                if (fix != null &&
                    UploadThrottle.shouldUpload(
                        time.nowMs(), fix, lastUploaded, lastUploadedAtMs)
                ) {
                    lastUploaded = fix
                    lastUploadedAtMs = time.nowMs()
                    scope.launch {
                        val r = repository.postLocation(fix)
                        if (r is com.flightradius.app.data.api.ApiResult.Failure) {
                            AppLog.d("LocUpload", "postLocation failed",
                                "err" to r.error.message)
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        job?.cancel(); job = null
        lastUploaded = null
        lastUploadedAtMs = null
    }
}
