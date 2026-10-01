package com.flightradius.app.di

import com.flightradius.app.BuildConfig
import com.flightradius.app.data.update.CurrentVersion
import com.flightradius.app.data.update.UpdateChecker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {

    @Provides
    @Singleton
    fun provideCurrentVersion(): CurrentVersion = CurrentVersion(BuildConfig.VERSION_NAME)

    /** Plain client: no OpenSky interceptors, so no credentials can ever reach GitHub. */
    @Provides
    @Singleton
    fun provideUpdateChecker(current: CurrentVersion): UpdateChecker = UpdateChecker(
        client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build(),
        apiBase = "https://api.github.com",
        repo = BuildConfig.UPDATE_REPO,
        currentVersion = current.name
    )
}
