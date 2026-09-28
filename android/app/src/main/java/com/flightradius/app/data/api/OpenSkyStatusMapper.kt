package com.flightradius.app.data.api

import com.flightradius.app.domain.OpenSkyStatus

/**
 * Maps a compute-call failure onto the coarse OpenSky/backend health shown
 * in the UI. Pure — unit-tested per branch.
 */
fun openSkyStatusFor(error: ApiError): OpenSkyStatus = when (error) {
    // 502/504 bodies from our backend = OpenSky itself is the problem.
    is ApiError.OpenSkyUnavailable -> OpenSkyStatus.UNAVAILABLE
    is ApiError.OpenSkyTimeout -> OpenSkyStatus.TIMEOUT
    is ApiError.RateLimited -> OpenSkyStatus.RATE_LIMITED
    // Client-side timeout — can't distinguish where it died.
    is ApiError.Timeout -> OpenSkyStatus.TIMEOUT
    is ApiError.Server -> OpenSkyStatus.UNAVAILABLE
    // We can't reach/parse our own backend.
    is ApiError.Network,
    is ApiError.Offline,
    is ApiError.Tls,
    is ApiError.LocalNetworkPermissionRequired,
    is ApiError.Http,
    is ApiError.Malformed -> OpenSkyStatus.BACKEND_UNREACHABLE
    is ApiError.BadRequest,
    is ApiError.Unknown -> OpenSkyStatus.UNKNOWN
}
