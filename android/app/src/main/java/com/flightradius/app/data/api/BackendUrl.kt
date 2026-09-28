package com.flightradius.app.data.api

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Validation/normalization for a user-configured backend base URL. */
object BackendUrl {

    /**
     * Parses [raw] as an http(s) URL and returns a normalized [HttpUrl]
     * guaranteed to end in a trailing slash (so Retrofit relative paths
     * resolve correctly, including under path prefixes like `/sub/`).
     * Returns null for missing/blank input, non-http(s) schemes, or
     * unparseable input. Query/fragment are dropped.
     */
    fun normalize(raw: String?): HttpUrl? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        var url = trimmed.toHttpUrlOrNull() ?: return null
        if (url.scheme != "http" && url.scheme != "https") return null
        if (!url.encodedPath.endsWith("/")) {
            url = url.newBuilder().encodedPath(url.encodedPath + "/").build()
        }
        return url
    }

    fun normalizeOrNull(raw: String?): String? = normalize(raw)?.toString()
}
