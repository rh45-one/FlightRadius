package com.flightradius.app.data.api

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Rewrites every request to the CURRENT backend base URL (scheme, host,
 * port and path prefix) read at request time, so the Retrofit instance can
 * be built once against a placeholder base while the real backend URL is
 * user-configurable at runtime.
 *
 * Request paths are relative to the placeholder base (e.g. "/api/health");
 * they are appended after the configured base path ("/" or "/sub/").
 */
class BaseUrlInterceptor(private val baseUrlProvider: () -> HttpUrl) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val base = baseUrlProvider()
        val basePath = base.encodedPath.removeSuffix("/")
        val newPath = basePath + request.url.encodedPath
        val newUrl = request.url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .encodedPath(newPath)
            .build()
        return chain.proceed(request.newBuilder().url(newUrl).build())
    }
}
