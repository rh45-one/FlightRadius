package com.flightradius.app.data.opensky

import com.flightradius.app.data.api.ApiError
import com.flightradius.app.data.api.ApiResult
import com.flightradius.app.data.api.toApiError
import com.flightradius.app.data.net.await
import com.flightradius.app.domain.OpenSkyPricing
import com.flightradius.app.domain.StateVector
import com.flightradius.app.domain.StatesQuery
import com.flightradius.app.domain.StatesSnapshot
import com.flightradius.app.util.log.AppLog
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Direct OpenSky `/states/all` access. Handles auth (anonymous or bearer
 * with one refresh on 401), icao24 chunking, credit bookkeeping and the
 * 429 back-off window. Never throws except for cancellation.
 */
class OpenSkyClient(
    private val http: OkHttpClient,
    private val endpoints: OpenSkyEndpoints,
    private val tokens: OpenSkyTokenProvider,
    private val credits: CreditTracker,
    private val json: Json
) {
    suspend fun states(query: StatesQuery, extended: Boolean = true): ApiResult<StatesSnapshot> {
        credits.blockedForSec()?.let { return ApiResult.Failure(ApiError.RateLimited(it)) }
        return when (query) {
            is StatesQuery.ByIcao24 -> fetchChunked(query.icao24s, extended)
            else -> fetch(buildUrl(query, extended))
        }
    }

    /** Verifies the stored API client by requesting a fresh token (free). */
    suspend fun verifyCredentials(): ApiResult<Boolean> = when (val auth = tokens.authorization(force = true)) {
        OpenSkyAuth.Anonymous -> ApiResult.Success(false)
        is OpenSkyAuth.Bearer -> ApiResult.Success(true)
        is OpenSkyAuth.Failed -> ApiResult.Failure(auth.error)
    }

    private suspend fun fetchChunked(icao24s: List<String>, extended: Boolean): ApiResult<StatesSnapshot> {
        val ids = icao24s.map { it.lowercase(Locale.ROOT) }.distinct()
        if (ids.isEmpty()) return ApiResult.Success(StatesSnapshot(timeSec = null, states = emptyList()))
        val merged = ArrayList<StateVector>()
        var timeSec: Long? = null
        for (chunk in ids.chunked(OpenSkyPricing.ICAO24_CHUNK_SIZE)) {
            when (val r = fetch(buildUrl(StatesQuery.ByIcao24(chunk), extended))) {
                is ApiResult.Success -> {
                    merged += r.data.states
                    timeSec = maxOf(timeSec ?: 0L, r.data.timeSec ?: 0L).takeIf { it > 0 }
                }
                is ApiResult.Failure -> return r
            }
        }
        return ApiResult.Success(StatesSnapshot(timeSec, merged))
    }

    internal fun buildUrl(query: StatesQuery, extended: Boolean): HttpUrl =
        endpoints.statesAll.newBuilder().apply {
            when (query) {
                StatesQuery.Global -> Unit
                is StatesQuery.ByIcao24 -> query.icao24s.forEach { addQueryParameter("icao24", it) }
                is StatesQuery.ByArea -> with(query.box) {
                    addQueryParameter("lamin", latMin.fmt())
                    addQueryParameter("lomin", lonMin.fmt())
                    addQueryParameter("lamax", latMax.fmt())
                    addQueryParameter("lomax", lonMax.fmt())
                }
            }
            if (extended) addQueryParameter("extended", "1")
        }.build()

    private suspend fun fetch(url: HttpUrl, allowReauth: Boolean = true): ApiResult<StatesSnapshot> {
        val auth = tokens.authorization()
        if (auth is OpenSkyAuth.Failed) return ApiResult.Failure(auth.error)
        val authenticated = auth is OpenSkyAuth.Bearer
        val request = Request.Builder().url(url).get().apply {
            if (auth is OpenSkyAuth.Bearer) header("Authorization", "Bearer ${auth.token}")
        }.build()

        return try {
            http.newCall(request).await().use { response ->
                val remaining = response.header(HEADER_REMAINING)?.trim()?.toIntOrNull()
                when {
                    response.isSuccessful -> {
                        credits.onResponse(remaining, authenticated)
                        ApiResult.Success(StateVectorParser.parse(json, response.body.string()))
                    }
                    response.code == 401 && authenticated && allowReauth -> {
                        tokens.invalidate()
                        null
                    }
                    response.code == 401 -> ApiResult.Failure(ApiError.AuthFailed)
                    response.code == 429 -> {
                        val retryAfter = response.header(HEADER_RETRY_AFTER)?.trim()?.toLongOrNull()
                        credits.onRateLimited(retryAfter, authenticated)
                        AppLog.w(TAG, "credits exhausted", "retryAfterSec" to retryAfter)
                        ApiResult.Failure(ApiError.RateLimited(retryAfter))
                    }
                    response.code == 400 -> ApiResult.Failure(ApiError.BadRequest())
                    response.code >= 500 -> ApiResult.Failure(ApiError.OpenSkyUnavailable())
                    else -> ApiResult.Failure(ApiError.Http(response.code))
                }
            } ?: fetch(url, allowReauth = false)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            ApiResult.Failure(t.toApiError())
        }
    }

    private fun Double.fmt(): String = String.format(Locale.ROOT, "%.4f", this)

    private companion object {
        const val TAG = "OpenSkyClient"
        const val HEADER_REMAINING = "X-Rate-Limit-Remaining"
        const val HEADER_RETRY_AFTER = "X-Rate-Limit-Retry-After-Seconds"
    }
}
