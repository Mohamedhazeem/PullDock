package com.pulldock.domain.discovery

import kotlinx.coroutines.flow.Flow

/**
 * Provider enablement toggles (FR-016). Only non-secret flags are stored (DataStore). The
 * Pirate Bay (apibay) provider is default-off and enabled here; API keys are never stored.
 */
interface ProviderToggleRepository {
    val tpbEnabled: Flow<Boolean>
    suspend fun setTpbEnabled(enabled: Boolean)
}