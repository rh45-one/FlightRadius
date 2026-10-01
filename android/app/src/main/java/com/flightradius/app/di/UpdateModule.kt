package com.flightradius.app.di

import com.flightradius.app.BuildConfig
import com.flightradius.app.data.update.CurrentVersion
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.flightradius.app.data.update.AndroidPackageInstallGateway
import com.flightradius.app.data.update.AndroidUpdateNotifier
import com.flightradius.app.data.update.UpdateChecker
import com.flightradius.app.data.update.UpdateConfig
import com.flightradius.app.data.update.UpdateInstaller
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
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
        apiBase = UpdateConfig.apiBase,
        repo = BuildConfig.UPDATE_REPO,
        currentVersion = current.name
    )

    @Provides
    @Singleton
    fun provideUpdateInstaller(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope
    ): UpdateInstaller = UpdateInstaller(
        client = UpdateInstaller.downloadClient(UpdateConfig.relaxAssetHost),
        gateway = AndroidPackageInstallGateway(context),
        notifier = AndroidUpdateNotifier(context),
        cacheDir = context.cacheDir,
        isMetered = {
            context.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered
        },
        isAppVisible = {
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        },
        startConfirmation = { intent ->
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
        repo = BuildConfig.UPDATE_REPO,
        relaxHosts = UpdateConfig.relaxAssetHost,
        scope = scope
    )
}
