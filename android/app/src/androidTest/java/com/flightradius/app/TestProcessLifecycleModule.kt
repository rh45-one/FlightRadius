package com.flightradius.app

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.flightradius.app.di.ProcessLifecycle
import com.flightradius.app.di.ProcessLifecycleModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/** A hand-driven stand-in for the process lifecycle (one per test component). */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ProcessLifecycleModule::class])
object TestProcessLifecycleModule {

    @Provides
    @Singleton
    @ProcessLifecycle
    fun provideProcessLifecycle(): Lifecycle {
        val owner = object : LifecycleOwner {
            lateinit var registry: LifecycleRegistry
            override val lifecycle: Lifecycle get() = registry
        }
        owner.registry = LifecycleRegistry.createUnsafe(owner)
        owner.registry.currentState = Lifecycle.State.CREATED
        return owner.registry
    }
}
