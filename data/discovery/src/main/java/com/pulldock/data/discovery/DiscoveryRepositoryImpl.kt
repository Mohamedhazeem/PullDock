package com.pulldock.data.discovery

import com.pulldock.core.common.dispatchers.AppDispatchers
import com.pulldock.data.discovery.cache.SearchResultCache
import com.pulldock.data.discovery.mapper.ResultMerger
import com.pulldock.domain.discovery.ProviderRegistry
import com.pulldock.domain.discovery.ProviderPage
import com.pulldock.domain.discovery.TorrentSearchProvider
import com.pulldock.domain.discovery.TorrentSearchRepository
import com.pulldock.domain.discovery.error.DiscoveryError
import com.pulldock.domain.discovery.model.DownloadDescriptor
import com.pulldock.domain.discovery.model.OverallSearchState
import com.pulldock.domain.discovery.model.ProviderState
import com.pulldock.domain.discovery.model.ProviderStatus
import com.pulldock.domain.discovery.model.SearchFilters
import com.pulldock.domain.discovery.model.SearchPageState
import com.pulldock.domain.discovery.model.TorrentResultDetails
import com.pulldock.domain.discovery.model.TorrentSearchQuery
import com.pulldock.domain.discovery.model.TorrentSearchResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Coordinates discovery (contracts/torrent-search-repository.md): fans a query out to enabled
 * providers concurrently, isolates per-provider failures, normalizes/dedupes/ranks, and caches
 * identical requests briefly. Cancellation of the caller cancels in-flight provider work; a
 * superseded search can never overwrite a newer one because the ViewModel drops the stale job.
 */
@Singleton
class DiscoveryRepositoryImpl @Inject constructor(
    private val registry: ProviderRegistry,
    private val cache: SearchResultCache,
    private val dispatchers: AppDispatchers,
) : TorrentSearchRepository {

    @Volatile
    private var lastState: SearchPageState? = null

    @Volatile
    private var lastPages: Map<String, ProviderPage> = emptyMap()

    override suspend fun search(
        query: TorrentSearchQuery,
        onProgress: (ProviderStatus, List<TorrentSearchResult>) -> Unit,
    ): SearchPageState = withContext(dispatchers.io) {
        if (query.text.isBlank()) {
            return@withContext SearchPageState(query = query.text, filters = query.filters, overall = OverallSearchState.EMPTY)
        }
        cache.get(query.text, query.filters.category)?.let { return@withContext it }
        execute(query, existing = emptyList(), pages = emptyMap(), onProgress = onProgress).also {
            cache.put(query.text, query.filters.category, it)
        }
    }

    override suspend fun loadMore(query: TorrentSearchQuery): SearchPageState = withContext(dispatchers.io) {
        val previous = lastState ?: return@withContext search(query)
        execute(query, existing = previous.results, pages = lastPages)
    }

    override suspend fun refresh(query: TorrentSearchQuery): SearchPageState = withContext(dispatchers.io) {
        cache.clear()
        execute(query, existing = emptyList(), pages = emptyMap())
    }

    override suspend fun getDetails(result: TorrentSearchResult): TorrentResultDetails = withContext(dispatchers.io) {
        val provider = registry.enabled().firstOrNull { it.id == result.providerId }
            ?: error("provider ${result.providerId} not available")
        provider.details(result.resultId)
            ?: TorrentResultDetails(
                resultId = result.resultId,
                providerId = result.providerId,
                providerName = result.providerName,
                title = result.title,
                description = result.description,
                category = result.category,
                infoHash = result.infoHash,
                sizeBytes = result.sizeBytes,
                seeders = result.seeders,
                leechers = result.leechers,
                publishedAt = result.publishedAt,
                sourcePageUrl = result.sourcePageUrl,
                rightsStatus = result.rightsStatus,
                licence = null,
                downloadDescriptor = null,
                availableActions = result.availableActions,
            )
    }

    override suspend fun resolveDownloadDescriptor(
        details: TorrentResultDetails,
        preferredFileName: String?,
    ): DownloadDescriptor? =
        withContext(dispatchers.io) {
            val provider = registry.enabled().firstOrNull { it.id == details.providerId } ?: return@withContext null
            provider.downloadDescriptor(details.resultId, preferredFileName)
        }

    private suspend fun execute(
        query: TorrentSearchQuery,
        existing: List<TorrentSearchResult>,
        pages: Map<String, ProviderPage>,
        onProgress: (ProviderStatus, List<TorrentSearchResult>) -> Unit = { _, _ -> },
    ): SearchPageState {
        val all = registry.supporting(query.filters.category)
        val providers = if (query.filters.providerIds.isEmpty()) all else all.filter { it.id in query.filters.providerIds }
        if (providers.isEmpty()) {
            return SearchPageState(query.text, query.filters, OverallSearchState.EMPTY, emptyList(), existing, emptyMap())
        }

        val statuses = mutableListOf<ProviderStatus>()
        val collected = existing.toMutableList()
        val hasMore = mutableMapOf<String, Boolean>()
        val nextPages = mutableMapOf<String, ProviderPage>()

        coroutineScope {
            val deferred = providers.map { provider ->
                val page = pages[provider.id] ?: ProviderPage(number = 1, cursor = null)
                async { provider to runProvider(provider, query, page) }
            }
            deferred.forEach { d ->
                val (provider, outcome) = d.await()
                statuses += outcome.status
                collected += outcome.results
                hasMore[provider.id] = outcome.hasMore
                outcome.nextPage?.let { nextPages[provider.id] = it }
                onProgress(outcome.status, collected.toList())
            }
        }

        val ranked = ResultMerger.combineAndRank(collected, query.filters.sort, query.text)
            .filter { matchesFilters(it, query.filters) }
        val state = SearchPageState(
            query = query.text,
            filters = query.filters,
            overall = computeOverall(statuses, ranked),
            perProvider = statuses,
            results = ranked,
            hasMoreByProvider = hasMore,
        )
        lastState = state
        lastPages = nextPages
        return state
    }

    private suspend fun runProvider(
        provider: TorrentSearchProvider,
        query: TorrentSearchQuery,
        page: ProviderPage,
    ): ProviderOutcome = try {
        val result = withTimeout(PER_PROVIDER_TIMEOUT_MS) { provider.search(query, page) }
        val status = if (result.results.isEmpty()) {
            ProviderStatus(provider.id, provider.displayName, ProviderState.EMPTY, 0)
        } else {
            ProviderStatus(provider.id, provider.displayName, ProviderState.SUCCESS, result.results.size)
        }
        ProviderOutcome(status, result.results, result.hasMore, result.nextPage)
    } catch (t: TimeoutCancellationException) {
        ProviderOutcome(ProviderStatus(provider.id, provider.displayName, ProviderState.TIMEOUT, error = DiscoveryError.Timeout(provider.id)), emptyList(), false, null)
    } catch (e: DiscoveryError) {
        val state = if (e is DiscoveryError.RateLimited) ProviderState.RATE_LIMITED else ProviderState.FAILED
        ProviderOutcome(ProviderStatus(provider.id, provider.displayName, state, error = e), emptyList(), false, null)
    } catch (e: Exception) {
        ProviderOutcome(
            ProviderStatus(provider.id, provider.displayName, ProviderState.FAILED, error = DiscoveryError.Provider(DiscoveryError.ProviderReason.SERVER_ERROR, "unexpected")),
            emptyList(), false, null,
        )
    }

    private data class ProviderOutcome(
        val status: ProviderStatus,
        val results: List<TorrentSearchResult>,
        val hasMore: Boolean,
        val nextPage: ProviderPage?,
    )

    private fun matchesFilters(result: TorrentSearchResult, filters: SearchFilters): Boolean {
        val size = result.sizeBytes
        val published = result.publishedAt
        val minSize = filters.minSizeBytes
        val maxSize = filters.maxSizeBytes
        val from = filters.dateFrom
        val to = filters.dateTo
        if (minSize != null && (size == null || size < minSize)) return false
        if (maxSize != null && (size == null || size > maxSize)) return false
        if (from != null && (published == null || published < from)) return false
        if (to != null && (published == null || published > to)) return false
        return true
    }

    private fun computeOverall(statuses: List<ProviderStatus>, results: List<TorrentSearchResult>): OverallSearchState {
        val successful = statuses.count { it.state == ProviderState.SUCCESS }
        val failed = statuses.count { it.state == ProviderState.FAILED || it.state == ProviderState.TIMEOUT || it.state == ProviderState.RATE_LIMITED }
        val allOffline = statuses.isNotEmpty() && statuses.all {
            (it.error as? DiscoveryError.Network)?.reason == DiscoveryError.NetworkReason.OFFLINE
        }
        return when {
            allOffline -> OverallSearchState.OFFLINE
            results.isNotEmpty() && failed > 0 -> OverallSearchState.PARTIAL
            results.isNotEmpty() -> OverallSearchState.RESULTS
            successful > 0 -> OverallSearchState.EMPTY
            failed > 0 -> OverallSearchState.ALL_FAILED
            else -> OverallSearchState.EMPTY
        }
    }

    private companion object {
        const val PER_PROVIDER_TIMEOUT_MS = 8_000L
    }
}