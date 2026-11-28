package com.pulldock.domain.discovery

import com.pulldock.domain.discovery.model.ProviderConfiguration
import kotlinx.coroutines.flow.Flow

/**
 * Torznab provider configuration contract (FR-029..FR-034). Implemented in `:data:discovery`
 * over DataStore. API keys are never persisted (FR-042); only non-secret fields are stored.
 */
interface TorznabConfigRepository {

    fun observe(): Flow<List<ProviderConfiguration>>

    suspend fun current(): List<ProviderConfiguration>

    suspend fun replace(configs: List<ProviderConfiguration>)
}