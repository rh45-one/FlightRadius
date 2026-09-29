package com.flightradius.app.data.opensky

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** OpenSky endpoints; injectable so tests can point at a local server. */
data class OpenSkyEndpoints(
    /** REST root, e.g. `https://opensky-network.org/api/`. */
    val apiBase: HttpUrl,
    /** OAuth2 client-credentials token endpoint. */
    val tokenUrl: HttpUrl
) {
    val statesAll: HttpUrl get() = apiBase.newBuilder().addPathSegments("states/all").build()

    companion object {
        val Default = OpenSkyEndpoints(
            apiBase = "https://opensky-network.org/api/".toHttpUrl(),
            tokenUrl = ("https://auth.opensky-network.org/auth/realms/opensky-network/" +
                "protocol/openid-connect/token").toHttpUrl()
        )
    }
}

/**
 * An OpenSky API client (Account page → API clients). [toString] is
 * redacted so the pair can never leak into logs by accident.
 */
data class OpenSkyCredentials(val clientId: String, val clientSecret: String) {
    override fun toString(): String = "OpenSkyCredentials(clientId=***, clientSecret=***)"
}

/** Read access to the stored credentials (seam for tests). */
fun interface CredentialSource {
    suspend fun current(): OpenSkyCredentials?
}
