package com.flightradius.app.data.api

import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

val <T> ApiResult<T>.isSuccess: Boolean get() = this is ApiResult.Success
fun <T> ApiResult<T>.getOrNull(): T? = (this as? ApiResult.Success)?.data

sealed interface ApiError {
    val retryable: Boolean
    val message: String

    /** No connectivity / socket-level failure (IOException). */
    data class Network(val causeMessage: String? = null) : ApiError {
        override val retryable = true
        override val message = causeMessage ?: "Network error"
    }

    /** Same as Network but specifically "device appears offline". */
    data object Offline : ApiError {
        override val retryable = true
        override val message = "No internet connection"
    }

    data class Timeout(val causeMessage: String? = null) : ApiError {
        override val retryable = true
        override val message = "Request timed out"
    }

    data class Tls(val causeMessage: String? = null) : ApiError {
        override val retryable = false
        override val message =
            causeMessage ?: "Secure connection failed (certificate not trusted?)"
    }

    data class BadRequest(val detail: String? = null) : ApiError {
        override val retryable = false
        override val message = detail ?: "Invalid request"
    }

    data object RateLimited : ApiError {
        override val retryable = false
        override val message = "Rate limited — slow down"
    }

    /** 502 from our backend when OpenSky is unreachable/auth failed. */
    data class OpenSkyUnavailable(val detail: String? = null) : ApiError {
        override val retryable = true
        override val message = detail ?: "OpenSky unavailable"
    }

    /** 504 from our backend when OpenSky timed out. */
    data object OpenSkyTimeout : ApiError {
        override val retryable = true
        override val message = "OpenSky timed out"
    }

    /** Other 5xx. */
    data class Server(val code: Int, val detail: String? = null) : ApiError {
        override val retryable = true
        override val message = detail ?: "Server error ($code)"
    }

    /** Any other non-2xx. */
    data class Http(val code: Int, val detail: String? = null) : ApiError {
        override val retryable = false
        override val message = detail ?: "HTTP $code"
    }

    /** Body could not be parsed or was unexpectedly null. */
    data class Malformed(val detail: String? = null) : ApiError {
        override val retryable = false
        override val message = detail ?: "Malformed response"
    }

    /** API 37+: LAN backend unreachable without the Nearby devices grant. */
    data object LocalNetworkPermissionRequired : ApiError {
        override val retryable = false
        override val message =
            "Allow 'Nearby devices' access to reach your LAN backend"
    }

    data class Unknown(val causeMessage: String? = null) : ApiError {
        override val retryable = false
        override val message = causeMessage ?: "Unexpected error"
    }
}

val ApiResult<*>.retryable: Boolean
    get() = (this as? ApiResult.Failure)?.error?.retryable == true

/**
 * Runs a Retrofit call and wraps it in [ApiResult].
 * - CancellationException is rethrown, never swallowed.
 * - Error bodies are parsed leniently for `{error}` / `{status}`.
 * - [mapIOException] lets callers reclassify IO failures (e.g. API 37 LAN
 *   blocking -> LocalNetworkPermissionRequired); default = Network.
 */
suspend fun <T> safeApiCall(
    json: Json,
    mapIOException: (java.io.IOException) -> ApiError = { ApiError.Network(it.message) },
    block: suspend () -> Response<T>
): ApiResult<T> {
    val response = try {
        block()
    } catch (ce: CancellationException) {
        throw ce
    } catch (e: SocketTimeoutException) {
        return ApiResult.Failure(ApiError.Timeout(e.message))
    } catch (e: InterruptedIOException) {
        return ApiResult.Failure(ApiError.Timeout(e.message))
    } catch (e: SSLException) {
        return ApiResult.Failure(ApiError.Tls(e.message))
    } catch (e: java.io.IOException) {
        return ApiResult.Failure(mapIOException(e))
    } catch (e: SerializationException) {
        return ApiResult.Failure(ApiError.Malformed(e.message))
    } catch (e: IllegalArgumentException) {
        return ApiResult.Failure(ApiError.Malformed(e.message))
    } catch (t: Throwable) {
        return ApiResult.Failure(ApiError.Unknown(t.message))
    }

    if (response.isSuccessful) {
        val body = response.body()
        return if (body != null) {
            ApiResult.Success(body)
        } else {
            ApiResult.Failure(ApiError.Malformed("empty body for ${response.code()}"))
        }
    }

    val detail = response.errorBody()?.let { raw ->
        try {
            json.decodeFromString<ErrorBodyDto>(raw.string()).error
        } catch (_: Throwable) {
            null
        }
    }

    val error: ApiError = when (response.code()) {
        400 -> ApiError.BadRequest(detail)
        429 -> ApiError.RateLimited
        502 -> ApiError.OpenSkyUnavailable(detail ?: "OpenSky unavailable")
        504 -> ApiError.OpenSkyTimeout
        in 500..599 -> ApiError.Server(response.code(), detail)
        else -> ApiError.Http(response.code(), detail)
    }
    return ApiResult.Failure(error)
}
