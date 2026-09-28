package com.flightradius.app.data.api

import android.content.Context
import com.flightradius.app.data.prefs.RuntimeSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Reclassifies IOException -> ApiError (API 37 LAN blocking). */
fun interface IoErrorMapper {
    fun map(e: java.io.IOException): ApiError
}

/** Decides whether the API 37 local-network restriction currently applies. */
@Singleton
class LocalNetworkGuard @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtimeSettings: RuntimeSettings
) {
    /** True when the resolved backend URL is LAN and the grant is missing. */
    fun isBlocked(): Boolean =
        LocalNetwork.isRequired(context, runtimeSettings.baseUrl)

    /** IOException mapper for safeApiCall: reclassify IO failures on LAN backends. */
    val ioErrorMapper = IoErrorMapper { e ->
        if (isBlocked()) ApiError.LocalNetworkPermissionRequired
        else ApiError.Network(e.message)
    }
}
