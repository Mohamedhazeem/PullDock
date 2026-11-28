package com.pulldock.domain.discovery

import com.pulldock.domain.discovery.model.SearchCategory

/**
 * Resolves the enabled providers, optionally filtered by category support (FR-006).
 * Implemented in `:data:discovery` over the configured provider set. `suspend` because the
 * Torznab provider set is read from configuration.
 */
interface ProviderRegistry {

    suspend fun enabled(): List<TorrentSearchProvider>

    suspend fun supporting(category: SearchCategory): List<TorrentSearchProvider>
}