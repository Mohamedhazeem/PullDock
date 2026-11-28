package com.pulldock.data.discovery.registry

import com.pulldock.data.discovery.config.ProviderToggleStore
import com.pulldock.data.discovery.config.TorznabConfigStore
import com.pulldock.data.discovery.provider.PirateBayProvider
import com.pulldock.data.discovery.provider.TorznabProvider
import com.pulldock.domain.discovery.ApiCredentialStore
import com.pulldock.domain.discovery.ProviderRegistry
import com.pulldock.domain.discovery.TorrentSearchProvider
import com.pulldock.domain.discovery.model.ProviderConfiguration
import com.pulldock.domain.discovery.model.SearchCategory
import com.pulldock.domain.discovery.model.SearchType
import io.ktor.client.HttpClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.JvmSuppressWildcards
import kotlinx.coroutines.flow.first

/**
 * Resolves enabled providers (FR-006). Built-ins via Hilt; Torznab = a built-in AniBT config
 * plus user configs; The Pirate Bay (apibay) is included only when the user enables it.
 */
@Singleton
class ProviderRegistryImpl @Inject constructor(
    private val builtInProviders: @JvmSuppressWildcards Set<TorrentSearchProvider>,
    private val configStore: TorznabConfigStore,
    private val toggleStore: ProviderToggleStore,
    private val credentials: ApiCredentialStore,
    private val pirateBay: PirateBayProvider,
    private val httpClient: HttpClient,
) : ProviderRegistry {

    override suspend fun enabled(): List<TorrentSearchProvider> {
        val base = builtInProviders.sortedBy { it.id }.toMutableList()
        if (toggleStore.tpbEnabled.first()) base += pirateBay
        return base + torznabProviders()
    }

    /**
     * Returns all enabled providers. Category is applied per-adapter (when mapped) and enforced
     * client-side by the repository, so categories without a provider mapping (e.g. Documentaries)
     * still query general providers instead of returning nothing.
     */
    override suspend fun supporting(category: SearchCategory): List<TorrentSearchProvider> =
        enabled().filter { provider ->
            category == SearchCategory.ALL ||
                runCatching {
                    val caps = provider.capabilities()
                    caps.categories.containsKey(category) || caps.searchTypes.contains(SearchType.GENERAL)
                }.getOrDefault(true)
        }

    private suspend fun torznabProviders(): List<TorrentSearchProvider> {
        val builtIn = listOf(ANIBT).map { TorznabProvider(it, httpClient, credentials) }
        val user = configStore.current().filter { it.enabled && it.endpoint.isNotBlank() }
            .map { TorznabProvider(it, httpClient, credentials) }
        return builtIn + user
    }

    private companion object {
        val ANIBT = ProviderConfiguration(
            providerId = "anibt",
            displayName = "AniBT",
            endpoint = "https://anibt.net/torznab",
            enabled = true,
            supportedCategories = mapOf(SearchCategory.ANIME to "5070", SearchCategory.MOVIES to "2000"),
        )
    }
}