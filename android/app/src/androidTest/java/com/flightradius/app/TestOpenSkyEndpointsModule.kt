package com.flightradius.app

import com.flightradius.app.data.opensky.OpenSkyEndpoints
import com.flightradius.app.di.OpenSkyEndpointsModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Points OpenSky at [apiBase] (a MockWebServer set by the test before injection). */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [OpenSkyEndpointsModule::class])
object TestOpenSkyEndpointsModule {

    @Volatile
    var apiBase: HttpUrl = "http://127.0.0.1:1/api/".toHttpUrl()

    @Provides
    fun provideEndpoints(): OpenSkyEndpoints =
        OpenSkyEndpoints(apiBase, apiBase.resolve("token")!!)
}
