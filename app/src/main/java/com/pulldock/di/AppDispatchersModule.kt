package com.pulldock.di

import com.pulldock.core.common.dispatchers.AppDispatchers
import com.pulldock.core.common.dispatchers.DefaultAppDispatchers
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the dispatcher contract. The default implementation lives in the pure-Kotlin
 * `core:common` module, whose `@Inject` constructors are not scanned by Hilt's
 * aggregation — the binding is declared here in the composition root.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppDispatchersModule {

    @Binds
    @Singleton
    abstract fun bindAppDispatchers(impl: DefaultAppDispatchers): AppDispatchers
}
