package com.pulldock.data.discovery

import com.pulldock.data.discovery.ApiCredentialStoreImpl
import com.pulldock.data.discovery.cache.SearchResultCache
import com.pulldock.data.discovery.config.TorznabConfigStore
import com.pulldock.data.discovery.history.SearchHistoryStore
import com.pulldock.data.discovery.provider.BitSearchProvider
import com.pulldock.data.discovery.provider.InternetArchiveProvider
import com.pulldock.data.discovery.registry.ProviderRegistryImpl
import com.pulldock.domain.discovery.ApiCredentialStore
import com.pulldock.domain.discovery.DiscoveryCache
import com.pulldock.domain.discovery.ProviderRegistry
import com.pulldock.domain.discovery.SearchHistoryRepository
import com.pulldock.domain.discovery.TorrentSearchProvider
import com.pulldock.domain.discovery.TorrentSearchRepository
import com.pulldock.domain.discovery.TorznabConfigRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton
import kotlin.jvm.JvmSuppressWildcards

/** Hilt wiring for the discovery data layer (follows repository conventions). */
@Module
@InstallIn(SingletonComponent::class)
abstract class DiscoveryModule {

    @Binds
    @Singleton
    abstract fun bindRepository(impl: DiscoveryRepositoryImpl): TorrentSearchRepository

    @Binds
    @Singleton
    abstract fun bindRegistry(impl: ProviderRegistryImpl): ProviderRegistry

    @Binds
    @Singleton
    abstract fun bindHistory(impl: SearchHistoryStore): SearchHistoryRepository

    @Binds
    @Singleton
    abstract fun bindCache(impl: SearchResultCache): DiscoveryCache

    @Binds
    @Singleton
    abstract fun bindTorznabConfig(impl: TorznabConfigStore): TorznabConfigRepository

    @Binds
    @Singleton
    abstract fun bindCredentials(impl: ApiCredentialStoreImpl): ApiCredentialStore

    companion object {
        @Provides
        @Singleton
        fun provideHttpClient(provider: com.pulldock.data.discovery.http.HttpClientProvider): HttpClient =
            provider.create()

        /** Built-in providers; adding one means adding it here (Torznab is config-driven). */
        @Provides
        @Singleton
        @JvmSuppressWildcards
        fun provideBuiltInProviders(
            bitSearch: BitSearchProvider,
            archive: InternetArchiveProvider,
        ): Set<TorrentSearchProvider> = setOf(bitSearch, archive)
    }
}