package com.flightradius.app.domain

/** OpenSky/backend health derived from the last compute outcome. */
enum class OpenSkyStatus {
    UNKNOWN, OK, RATE_LIMITED, UNAVAILABLE, TIMEOUT, BACKEND_UNREACHABLE
}
