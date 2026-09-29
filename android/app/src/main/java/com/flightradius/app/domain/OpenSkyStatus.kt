package com.flightradius.app.domain

/**
 * Health of the flight-data path derived from the last fetch outcome.
 * [UNREACHABLE] means the configured source (OpenSky directly, or the
 * self-hosted backend) could not be reached or returned garbage.
 */
enum class OpenSkyStatus {
    UNKNOWN, OK, RATE_LIMITED, AUTH_FAILED, UNAVAILABLE, TIMEOUT, UNREACHABLE
}
