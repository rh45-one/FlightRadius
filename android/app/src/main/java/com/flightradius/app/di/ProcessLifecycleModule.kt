package com.flightradius.app.di

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier

/** The lifecycle of the whole app process (not of one activity). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ProcessLifecycle

/** Separate module so instrumented tests can drive the lifecycle by hand. */
@Module
@InstallIn(SingletonComponent::class)
object ProcessLifecycleModule {
    @Provides
    @ProcessLifecycle
    fun provideProcessLifecycle(): Lifecycle = ProcessLifecycleOwner.get().lifecycle
}
