package com.pulldock.domain.discovery

/** In-memory search-result cache contract (FR-062); implemented in `:data:discovery`. */
interface DiscoveryCache {
    fun clear()
}