package com.flightradius.app.domain

/**
 * Decides whether a new fix should be POSTed to /api/user/location.
 *
 * - never uploaded -> upload
 * - MANUAL fixes -> upload once per change (same lat/lon = no re-upload)
 * - GPS fixes -> at most once per 30s, or immediately when moved >= 100m
 *   from the last uploaded fix
 */
object UploadThrottle {

    const val MIN_INTERVAL_MS = 30_000L
    const val MIN_DISTANCE_M = 100.0

    fun shouldUpload(
        nowMs: Long,
        current: UserFix,
        lastUploaded: UserFix?,
        lastUploadedAtMs: Long?
    ): Boolean {
        if (lastUploaded == null || lastUploadedAtMs == null) return true
        if (current.source == LocationSource.MANUAL) {
            return current.lat != lastUploaded.lat || current.lon != lastUploaded.lon
        }
        if (nowMs - lastUploadedAtMs >= MIN_INTERVAL_MS) return true
        return Geo.distanceKm(
            lastUploaded.lat, lastUploaded.lon, current.lat, current.lon
        ) * 1000.0 >= MIN_DISTANCE_M
    }
}
