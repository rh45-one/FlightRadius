package com.flightradius.app.ui.format

import com.flightradius.app.data.api.ApiError

/** User-facing, localised text for an [ApiError] (server/cause details win when present). */
fun ApiError.localized(): String = when (this) {
    is ApiError.Network -> causeMessage ?: Words.get(W.ERR_NETWORK)
    ApiError.Offline -> Words.get(W.ERR_OFFLINE)
    is ApiError.Timeout -> Words.get(W.ERR_TIMEOUT)
    is ApiError.Tls -> causeMessage ?: Words.get(W.ERR_TLS)
    is ApiError.BadRequest -> detail ?: Words.get(W.ERR_BAD_REQUEST)
    is ApiError.RateLimited -> Words.get(W.ERR_RATE_LIMITED)
    ApiError.AuthFailed -> Words.get(W.ERR_AUTH_FAILED)
    is ApiError.OpenSkyUnavailable -> detail ?: Words.get(W.ERR_OPENSKY_UNAVAILABLE)
    ApiError.OpenSkyTimeout -> Words.get(W.ERR_OPENSKY_TIMEOUT)
    is ApiError.Server -> detail ?: Words.get(W.ERR_SERVER, code)
    is ApiError.Http -> detail ?: Words.get(W.ERR_HTTP, code)
    is ApiError.Malformed -> detail ?: Words.get(W.ERR_MALFORMED)
    ApiError.LocalNetworkPermissionRequired -> Words.get(W.ERR_LAN_PERMISSION)
    is ApiError.Unknown -> causeMessage ?: Words.get(W.ERR_UNKNOWN)
}
