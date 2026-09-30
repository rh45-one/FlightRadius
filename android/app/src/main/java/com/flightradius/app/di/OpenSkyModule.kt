package com.flightradius.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.flightradius.app.data.opensky.CreditBucketWatcher
import com.flightradius.app.data.opensky.CreditTracker
import com.flightradius.app.data.opensky.OpenSkyClient
import com.flightradius.app.data.opensky.OpenSkyEndpoints
import com.flightradius.app.data.opensky.OpenSkyTokenProvider
import com.flightradius.app.data.prefs.SettingsRepository
import com.flightradius.app.data.secure.CredentialStore
import com.flightradius.app.data.secure.KeystoreSecretCipher
import com.flightradius.app.data.source.OpenSkyFlightSource
import com.flightradius.app.domain.CallsignResolver
import com.flightradius.app.domain.TimeSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/** DataStore holding encrypted secrets; excluded from backups. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SecurePrefs

/** File name referenced by res/xml backup exclusion rules. */
const val SECURE_PREFS_NAME = "flightradius_secure"

/** Separate so instrumented tests can point OpenSky at a local server. */
@Module
@InstallIn(SingletonComponent::class)
object OpenSkyEndpointsModule {

    @Provides
    fun provideEndpoints(): OpenSkyEndpoints = OpenSkyEndpoints.Default
}

@Module
@InstallIn(SingletonComponent::class)
object OpenSkyModule {

    @Provides
    @Singleton
    @SecurePrefs
    fun provideSecurePrefs(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
        context.preferencesDataStoreFile(SECURE_PREFS_NAME)
    }

    @Provides
    @Singleton
    fun provideCredentialStore(@SecurePrefs prefs: DataStore<Preferences>): CredentialStore =
        CredentialStore(prefs, KeystoreSecretCipher())

    @Provides
    @Singleton
    fun provideTokenProvider(
        @OpenSkyHttp http: OkHttpClient,
        endpoints: OpenSkyEndpoints,
        credentialStore: CredentialStore,
        json: Json,
        time: TimeSource
    ): OpenSkyTokenProvider = OpenSkyTokenProvider(http, endpoints, credentialStore, json, time)

    @Provides
    @Singleton
    fun provideOpenSkyClient(
        @OpenSkyHttp http: OkHttpClient,
        endpoints: OpenSkyEndpoints,
        tokens: OpenSkyTokenProvider,
        credits: CreditTracker,
        json: Json
    ): OpenSkyClient = OpenSkyClient(http, endpoints, tokens, credits, json)

    @Provides
    @Singleton
    fun provideOpenSkyFlightSource(
        client: OpenSkyClient,
        credits: CreditTracker,
        time: TimeSource
    ): OpenSkyFlightSource = OpenSkyFlightSource(client, credits, CallsignResolver(), time)

    @Provides
    @Singleton
    fun provideCreditBucketWatcher(
        settings: SettingsRepository,
        credentialStore: CredentialStore,
        credits: CreditTracker
    ): CreditBucketWatcher = CreditBucketWatcher(settings, credentialStore, credits)
}
