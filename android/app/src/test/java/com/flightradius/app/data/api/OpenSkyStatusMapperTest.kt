package com.flightradius.app.data.api

import com.flightradius.app.domain.OpenSkyStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenSkyStatusMapperTest {

    private fun map(e: ApiError): OpenSkyStatus = openSkyStatusFor(e)

    @Test
    fun `OpenSkyUnavailable maps to UNAVAILABLE`() {
        assertEquals(OpenSkyStatus.UNAVAILABLE, map(ApiError.OpenSkyUnavailable("down")))
    }

    @Test
    fun `OpenSkyTimeout maps to TIMEOUT`() {
        assertEquals(OpenSkyStatus.TIMEOUT, map(ApiError.OpenSkyTimeout))
    }

    @Test
    fun `AuthFailed maps to AUTH_FAILED`() {
        assertEquals(OpenSkyStatus.AUTH_FAILED, map(ApiError.AuthFailed))
    }

    @Test
    fun `RateLimited maps to RATE_LIMITED`() {
        assertEquals(OpenSkyStatus.RATE_LIMITED, map(ApiError.RateLimited()))
    }

    @Test
    fun `client timeout maps to TIMEOUT`() {
        assertEquals(OpenSkyStatus.TIMEOUT, map(ApiError.Timeout("client timeout")))
    }

    @Test
    fun `Server 5xx maps to UNAVAILABLE`() {
        assertEquals(OpenSkyStatus.UNAVAILABLE, map(ApiError.Server(500, "err")))
        assertEquals(OpenSkyStatus.UNAVAILABLE, map(ApiError.Server(503, "err")))
    }

    @Test
    fun `Network maps to UNREACHABLE`() {
        assertEquals(
            OpenSkyStatus.UNREACHABLE,
            map(ApiError.Network("boom"))
        )
    }

    @Test
    fun `Tls maps to UNREACHABLE`() {
        assertEquals(
            OpenSkyStatus.UNREACHABLE,
            map(ApiError.Tls("cert"))
        )
    }

    @Test
    fun `LocalNetworkPermissionRequired maps to UNREACHABLE`() {
        assertEquals(
            OpenSkyStatus.UNREACHABLE,
            map(ApiError.LocalNetworkPermissionRequired)
        )
    }

    @Test
    fun `Http 4xx maps to UNREACHABLE`() {
        assertEquals(
            OpenSkyStatus.UNREACHABLE,
            map(ApiError.Http(404, "not found"))
        )
    }

    @Test
    fun `Malformed maps to UNREACHABLE`() {
        assertEquals(
            OpenSkyStatus.UNREACHABLE,
            map(ApiError.Malformed("bad json"))
        )
    }

    @Test
    fun `BadRequest maps to UNKNOWN`() {
        assertEquals(OpenSkyStatus.UNKNOWN, map(ApiError.BadRequest("bad")))
    }

    @Test
    fun `Unknown maps to UNKNOWN`() {
        assertEquals(OpenSkyStatus.UNKNOWN, map(ApiError.Unknown("?")))
    }
}
