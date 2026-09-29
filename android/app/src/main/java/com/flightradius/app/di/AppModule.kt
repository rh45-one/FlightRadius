package com.flightradius.app.di

import android.content.Context
import androidx.room.Room
import com.flightradius.app.data.api.BackendUrl
import com.flightradius.app.data.api.BaseUrlInterceptor
import com.flightradius.app.data.api.FlightRadiusApi
import com.flightradius.app.data.api.IoErrorMapper
import com.flightradius.app.data.api.LocalNetworkGuard
import com.flightradius.app.data.db.AircraftDao
import com.flightradius.app.data.db.FlightRadiusDatabase
import com.flightradius.app.data.db.FleetDao
import com.flightradius.app.data.opensky.BackendCreditsInterceptor
import com.flightradius.app.data.opensky.CreditTracker
import com.flightradius.app.data.prefs.RuntimeSettings
import com.flightradius.app.domain.SystemTimeSource
import com.flightradius.app.domain.TimeSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    fun provideTimeSource(): TimeSource = SystemTimeSource

    /** Reclassifies IO failures on API 37 LAN backends (safeApiCall hook). */
    @Provides
    fun provideIoErrorMapper(
        guard: LocalNetworkGuard
    ): IoErrorMapper = guard.ioErrorMapper

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
    }
}

/** Plain client for OpenSky (timeouts + gated BASIC logging). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OpenSkyHttp

/** Client for the self-hosted backend (base-URL rewriting + credit header). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BackendHttp

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val PLACEHOLDER_BASE_URL = "http://localhost/"

    @Provides
    @Singleton
    @OpenSkyHttp
    fun provideOpenSkyOkHttpClient(runtimeSettings: RuntimeSettings): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization")
            redactHeader("Proxy-Authorization")
        }
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // Sets the logging level per request, then delegates to the
            // real logging interceptor (BASIC only, never BODY).
            .addInterceptor { chain ->
                logging.level = if (runtimeSettings.debugLogging) {
                    HttpLoggingInterceptor.Level.BASIC
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
                chain.proceed(chain.request())
            }
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    @BackendHttp
    fun provideBackendOkHttpClient(
        @OpenSkyHttp base: OkHttpClient,
        runtimeSettings: RuntimeSettings,
        credits: CreditTracker
    ): OkHttpClient = base.newBuilder()
        .apply {
            // Rewrite first so logging shows the real backend URL.
            interceptors().add(0, BaseUrlInterceptor { runtimeSettings.baseUrl })
            interceptors().add(1, BackendCreditsInterceptor(credits))
        }
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(@BackendHttp client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            // Requests are rewritten by BaseUrlInterceptor; this is only a
            // structural base so relative path segments resolve.
            .baseUrl(BackendUrl.normalize(PLACEHOLDER_BASE_URL)!!)
            .client(client)
            .addConverterFactory(
                json.asConverterFactory("application/json".toMediaType())
            )
            .build()

    @Provides
    @Singleton
    fun provideFlightRadiusApi(retrofit: Retrofit): FlightRadiusApi =
        retrofit.create(FlightRadiusApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FlightRadiusDatabase =
        Room.databaseBuilder(context, FlightRadiusDatabase::class.java, "flightradius.db")
            .build()

    @Provides
    fun provideAircraftDao(db: FlightRadiusDatabase): AircraftDao = db.aircraftDao()

    @Provides
    fun provideFleetDao(db: FlightRadiusDatabase): FleetDao = db.fleetDao()
}
