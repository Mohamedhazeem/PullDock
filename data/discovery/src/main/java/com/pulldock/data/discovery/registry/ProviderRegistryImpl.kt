package com.pulldock.data.discovery.registry

import com.pulldock.data.discovery.config.TorznabConfigStore
import com.pulldock.data.discovery.provider.TorznabProvider
import com.pulldock.domain.discovery.ApiCredentialStore
import com.pulldock.domain.discovery.ProviderRegistry
import com.pulldock.domain.discovery.TorrentSearchProvider
import com.pulldock.domain.discovery.model.SearchCategory
import io.ktor.client.HttpClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.JvmSuppressWildcards

/**
 * Resolves enabled providers (FR-006). Built-in adapters are contributed via Hilt Set
 * multibinding; Torznab adapters are built from user configuration on demand. Adding a
 * provider requires only a new `@Inject` adapter — no UI/engine change (FR-005).
 */
@Singleton
class ProviderRegistryImpl @Inject constructor(
    private val builtInProviders: @JvmSuppressWildcards Set<TorrentSearchProvider>,
    private val configStore: TorznabConfigStore,
    private val credentials: ApiCredentialStore,
    private val httpClient: HttpClient,
) : ProviderRegistry {

    override suspend fun enabled(): List<TorrentSearchProvider> =
        builtInProviders.sortedBy { it.id } + torznabProviders()

    override suspend fun supporting(category: SearchCategory): List<TorrentSearchProvider> =
        enabled().filter { provider ->
            category == SearchCategory.ALL || provider.capabilities().categories.containsKey(category)
        }

    private suspend fun torznabProviders(): List<TorrentSearchProvider> =
        configStore.current()
            .filter { it.enabled && it.endpoint.isNotBlank() }
            .map { TorznabProvider(it, httpClient, credentials) }
}