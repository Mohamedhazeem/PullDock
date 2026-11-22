package com.pulldock.data.extractor

import com.pulldock.core.model.ProviderId
import com.pulldock.domain.extractor.model.ProviderHealthState
import java.util.concurrent.ConcurrentHashMap

/**
 * Safe provider degradation and disablement (T129, FR-062): a failing provider is
 * disabled individually (recognition skips it) and can be re-enabled when health
 * recovers. Disablement never touches other providers (SC-007) and never attempts a
 * bypass for the disabled provider (FR-020).
 */
class ProviderDegradation {

    private val disabled = ConcurrentHashMap.newKeySet<ProviderId>()

    /** Disables one provider after validated failure (user-visible in Settings). */
    fun disable(providerId: ProviderId) {
        disabled.add(providerId)
    }

    /** Re-enables a provider (health recovered or the user chose to retry). */
    fun enable(providerId: ProviderId) {
        disabled.remove(providerId)
    }

    /** `false` when the provider is degraded; disabled providers are never used. */
    fun isUsable(providerId: ProviderId): Boolean = providerId !in disabled

    /** Snapshot of degraded providers for UI/health reporting. */
    fun snapshot(): Set<ProviderId> = disabled.toSet()

    /** Maps a degraded provider's health state. */
    fun healthState(providerId: ProviderId, base: ProviderHealthState): ProviderHealthState =
        if (providerId in disabled) ProviderHealthState.PROVIDER_UNSUPPORTED else base
}
