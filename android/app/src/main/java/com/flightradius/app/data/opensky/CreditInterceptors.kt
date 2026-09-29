package com.flightradius.app.data.opensky

import com.flightradius.app.data.prefs.SettingsRepository
import com.flightradius.app.data.secure.CredentialStore
import com.flightradius.app.data.secure.StoredCredentials
import com.flightradius.app.domain.CreditPlanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Feeds [CreditTracker] from backend responses: the backend forwards the
 * OpenSky balance in [HEADER] and passes 429s through with Retry-After.
 */
class BackendCreditsInterceptor(private val credits: CreditTracker) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val remaining = response.header(HEADER)?.trim()?.toIntOrNull()
        // The backend doesn't expose its auth mode; a balance above the
        // anonymous quota can only come from an authenticated bucket.
        val authenticated = (remaining ?: 0) > CreditPlanner.ANONYMOUS_DAILY
        when {
            response.code == 429 ->
                credits.onRateLimited(response.header("Retry-After")?.trim()?.toLongOrNull(), authenticated)
            remaining != null -> credits.onResponse(remaining, authenticated)
        }
        return response
    }

    companion object {
        const val HEADER = "X-OpenSky-Credits-Remaining"
    }
}

/**
 * Credit observations belong to one OpenSky bucket (an API client, or the
 * device IP when anonymous). Switching data source or credentials moves to
 * another bucket, so the stale observation is discarded.
 */
class CreditBucketWatcher(
    private val settings: SettingsRepository,
    private val credentialStore: CredentialStore,
    private val credits: CreditTracker
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(
                settings.settings.map { it.dataSource },
                credentialStore.stored.map { (it as? StoredCredentials.Present)?.credentials?.clientId }
            ) { source, clientId -> source to clientId }
                .distinctUntilChanged()
                .drop(1)
                .collect { credits.reset() }
        }
    }
}
