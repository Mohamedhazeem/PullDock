package com.pulldock.domain.discovery

import com.pulldock.domain.discovery.model.SearchCategory
import com.pulldock.domain.discovery.model.SearchHistoryEntry
import kotlinx.coroutines.flow.Flow

/**
 * Local search-history contract (FR-061). Implemented in `:data:discovery` over DataStore.
 * History is never transmitted off-device and API keys are never stored here.
 */
interface SearchHistoryRepository {

    val enabled: Flow<Boolean>

    fun observe(): Flow<List<SearchHistoryEntry>>

    suspend fun add(query: String, category: SearchCategory?)

    suspend fun delete(entry: SearchHistoryEntry)

    suspend fun clear()

    suspend fun setEnabled(value: Boolean)
}