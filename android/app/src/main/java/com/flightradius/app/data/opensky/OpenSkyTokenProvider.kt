package com.flightradius.app.data.opensky

import com.flightradius.app.data.api.ApiError
import com.flightradius.app.data.api.toApiError
import com.flightradius.app.data.net.await
import com.flightradius.app.domain.TimeSource
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/** How to authorize the next OpenSky request. */
sealed interface OpenSkyAuth {
    /** No API client stored: anonymous access (400 credits/day). */
    data object Anonymous : OpenSkyAuth

    data class Bearer(val token: String) : OpenSkyAuth {
        override fun toString() = "Bearer(***)"
    }

    data class Failed(val error: ApiError) : OpenSkyAuth
}

/**
 * OAuth2 client-credentials tokens for OpenSky, cached until shortly before
 * expiry and refreshed single-flight. A change of stored credentials
 * invalidates the cache automatically. Credentials the auth server rejected
 * are not retried on every cycle — only after they change or on an explicit
 * [authorization] call with `force = true` (the user pressing "Verify").
 */
class OpenSkyTokenProvider(
    private val http: OkHttpClient,
    private val endpoints: OpenSkyEndpoints,
    private val credentials: CredentialSource,
    private val json: Json,
    private val time: TimeSource
) {
    private data class CachedToken(
        val token: String,
        val expiresAtMs: Long,
        val issuedFor: OpenSkyCredentials
    )

    private val mutex = Mutex()
    @Volatile private var cached: CachedToken? = null
    @Volatile private var rejected: OpenSkyCredentials? = null

    suspend fun authorization(force: Boolean = false): OpenSkyAuth {
        val creds = credentials.current() ?: return OpenSkyAuth.Anonymous
        if (!force) {
            if (creds == rejected) return OpenSkyAuth.Failed(ApiError.AuthFailed)
            cached?.takeIf { it.isValidFor(creds) }?.let { return OpenSkyAuth.Bearer(it.token) }
        }
        return mutex.withLock {
            if (!force) {
                cached?.takeIf { it.isValidFor(creds) }?.let { return@withLock OpenSkyAuth.Bearer(it.token) }
            }
            fetchToken(creds).also { auth ->
                rejected = if ((auth as? OpenSkyAuth.Failed)?.error == ApiError.AuthFailed) creds else null
            }
        }
    }

    /** Drop the cached token (e.g. after a 401). */
    fun invalidate() {
        cached = null
    }

    private fun CachedToken.isValidFor(creds: OpenSkyCredentials) =
        issuedFor == creds && time.nowMs() < expiresAtMs

    private suspend fun fetchToken(creds: OpenSkyCredentials): OpenSkyAuth {
        val request = Request.Builder()
            .url(endpoints.tokenUrl)
            .post(
                FormBody.Builder()
                    .add("grant_type", "client_credentials")
                    .add("client_id", creds.clientId)
                    .add("client_secret", creds.clientSecret)
                    .build()
            )
            .build()
        return try {
            http.newCall(request).await().use { response ->
                when {
                    response.isSuccessful -> {
                        val body = json.decodeFromString(
                            TokenResponse.serializer(), response.body.string()
                        )
                        val token = body.access_token?.takeIf { it.isNotBlank() }
                            ?: return OpenSkyAuth.Failed(ApiError.Malformed())
                        val lifetimeSec = (body.expires_in ?: DEFAULT_LIFETIME_SEC)
                            .coerceAtLeast(REFRESH_MARGIN_SEC * 2)
                        cached = CachedToken(
                            token = token,
                            expiresAtMs = time.nowMs() + (lifetimeSec - REFRESH_MARGIN_SEC) * 1000,
                            issuedFor = creds
                        )
                        OpenSkyAuth.Bearer(token)
                    }
                    // invalid_client / unauthorized_client.
                    response.code in 400..401 -> OpenSkyAuth.Failed(ApiError.AuthFailed)
                    response.code >= 500 -> OpenSkyAuth.Failed(ApiError.OpenSkyUnavailable())
                    else -> OpenSkyAuth.Failed(ApiError.Http(response.code))
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            OpenSkyAuth.Failed(t.toApiError())
        }
    }

    @Serializable
    private data class TokenResponse(
        val access_token: String? = null,
        val expires_in: Long? = null
    )

    private companion object {
        const val DEFAULT_LIFETIME_SEC = 1_800L
        const val REFRESH_MARGIN_SEC = 60L
    }
}
