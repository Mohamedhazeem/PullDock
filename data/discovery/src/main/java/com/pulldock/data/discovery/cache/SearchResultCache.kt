package com.pulldock.data.discovery.cache

import com.pulldock.domain.discovery.DiscoveryCache
import com.pulldock.domain.discovery.model.SearchCategory
import com.pulldock.domain.discovery.model.SearchPageState
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Short-lived in-memory TTL cache for identical search requests (FR-062). Keyed by normalized
 * query + category; provider results are kept inside the cached page. Auth/rate-limit failures
 * are never stored as valid results (callers only cache successful/partial pages). Stale
 * entries are dropped on read and never presented as fresh.
 */
@Singleton
class SearchResultCache @Inject constructor() : DiscoveryCache {

    private data class Entry(val state: SearchPageState, val storedAtMillis: Long)

    private val entries = ConcurrentHashMap<String, Entry>()

    fun key(query: String, category: SearchCategory): String =
        "${query.trim().lowercase()}|${category.name}"

    fun get(query: String, category: SearchCategory): SearchPageState? {
        val entry = entries[key(query, category)] ?: return null
        if (System.currentTimeMillis() - entry.storedAtMillis > ttlMillis) {
            entries.remove(key(query, category))
            return null
        }
        return entry.state
    }

    fun put(query: String, category: SearchCategory, state: SearchPageState) {
        entries[key(query, category)] = Entry(state, System.currentTimeMillis())
    }

    override fun clear() = entries.clear()

    private companion object {
        const val ttlMillis = 3 * 60 * 1000L
    }
}