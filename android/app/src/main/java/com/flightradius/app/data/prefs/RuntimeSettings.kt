package com.flightradius.app.data.prefs

import com.flightradius.app.BuildConfig
import com.flightradius.app.data.api.BackendUrl
import com.flightradius.app.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl

/**
 * Hot-view of the settings values the network/log layers need at request
 * time (base URL, debug logging, data source). Collected once from DataStore so an
 * OkHttp interceptor never has to suspend. Callers that must not touch the
 * fallback URL before preferences are loaded should [awaitReady].
 */
@Singleton
class RuntimeSettings @Inject constructor(
    settingsRepository: SettingsRepository,
    @ApplicationScope scope: CoroutineScope
) {
    private val fallbackUrl: HttpUrl =
        BackendUrl.normalize(BuildConfig.DEFAULT_BACKEND_URL)
            ?: HttpUrl.Builder().scheme("http").host("localhost").build()

    private val _baseUrl = MutableStateFlow(fallbackUrl)
    private val _debugLogging = MutableStateFlow(BuildConfig.DEBUG)
    private val _dataSource = MutableStateFlow(DataSource.DIRECT)
    private val ready = CompletableDeferred<Unit>()

    val baseUrl: HttpUrl get() = _baseUrl.value
    val debugLogging: Boolean get() = _debugLogging.value
    val dataSource: DataSource get() = _dataSource.value

    /** Completes once the first DataStore emission has been applied. */
    suspend fun awaitReady() = ready.await()

    init {
        scope.launch {
            settingsRepository.settings.collect { settings ->
                _baseUrl.value = BackendUrl.normalize(settings.backendBaseUrl) ?: fallbackUrl
                _debugLogging.value = settings.debugLogging || BuildConfig.DEBUG
                _dataSource.value = settings.dataSource
                if (!ready.isCompleted) ready.complete(Unit)
            }
        }
    }
}
