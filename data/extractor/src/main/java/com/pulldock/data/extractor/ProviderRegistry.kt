package com.pulldock.data.extractor

import com.pulldock.core.model.ProviderId
import com.pulldock.domain.extractor.Provider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider registry (T084, FR-014/FR-062). Providers are independent: a resolve/
 * formats failure marks only that provider's health; others remain fully usable
 * (SC-007). Unknown URLs are UNSUPPORTED_PROVIDER — never a bypass attempt (FR-020).
 */
@Singleton
class ProviderRegistry @Inject constructor() {
    /** v1 provider set (FR-014); stateless instances, failures isolated per provider. */
    val all: List<Provider> = listOf(
        com.pulldock.data.extractor.provider.YouTubeProvider(),
        com.pulldock.data.extractor.provider.XProvider(),
        com.pulldock.data.extractor.provider.InstagramProvider(),
        com.pulldock.data.extractor.provider.FacebookProvider(),
        com.pulldock.data.extractor.provider.TikTokProvider(),
        com.pulldock.data.extractor.provider.RedditProvider(),
    )

    private val byId: Map<ProviderId, Provider> = all.associateBy { it.id }

    fun provider(id: ProviderId): Provider? = byId[id]

    /** First provider recognizing the URL, or null (unsupported). */
    fun recognize(url: String): Provider? = all.firstOrNull { it.recognizes(url) }

    /**
     * Marks one provider failed — implemented by the health store; isolation is the
     * registry's contract: this never touches other providers.
     */
    var onProviderFailure: ((ProviderId) -> Unit)? = null

    /** Wraps a provider call so only the failing provider's health is affected. */
    suspend fun <T> isolated(providerId: ProviderId, block: suspend (Provider) -> T): Result<T> {
        val provider = provider(providerId)
            ?: return Result.failure(IllegalArgumentException("unknown provider"))
        return try {
            Result.success(block(provider))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            onProviderFailure?.invoke(providerId)
            Result.failure(e)
        }
    }
}
