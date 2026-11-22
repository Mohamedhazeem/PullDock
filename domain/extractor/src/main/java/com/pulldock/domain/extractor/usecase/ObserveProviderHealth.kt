package com.pulldock.domain.extractor.usecase

import com.pulldock.core.model.ProviderId
import com.pulldock.domain.extractor.MediaExtractor
import com.pulldock.domain.extractor.model.ProviderHealth
import javax.inject.Inject

/**
 * Per-provider health for Settings and error screens (T091, US8, FR-061). States are
 * honest: the bundled runtime pending device verification reports RUNTIME_INCOMPATIBLE,
 * and network errors are never confused with staleness (FR-061).
 */
class ObserveProviderHealth @Inject constructor(
    private val extractor: MediaExtractor,
) {
    fun all(): Map<ProviderId, ProviderHealth> =
        ProviderId.entries.associateWith { extractor.providerHealth(it) }

    fun runtimeInfo() = extractor.runtimeInfo()
}
