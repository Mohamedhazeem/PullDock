package com.pulldock.domain.discovery

import com.pulldock.domain.discovery.model.DownloadDescriptor
import com.pulldock.domain.discovery.model.ProviderStatus
import com.pulldock.domain.discovery.model.SearchPageState
import com.pulldock.domain.discovery.model.TorrentResultDetails
import com.pulldock.domain.discovery.model.TorrentSearchQuery
import com.pulldock.domain.discovery.model.TorrentSearchResult

/**
 * Discovery repository contract (contracts/torrent-search-repository.md). Implemented by
 * `DiscoveryRepositoryImpl` in `:data:discovery`. All functions are `suspend`, run off the
 * main thread via injected `AppDispatchers`, and are cancellable.
 */
interface TorrentSearchRepository {

    /** Executes a new search across enabled providers concurrently; cancels any prior search. */
    suspend fun search(
        query: TorrentSearchQuery,
        onProgress: (ProviderStatus, List<TorrentSearchResult>) -> Unit = { _, _ -> },
    ): SearchPageState

    /** Loads the next page for providers that have more data; preserves existing results. */
    suspend fun loadMore(query: TorrentSearchQuery): SearchPageState

    /** Re-runs the current query (refresh); stale results are not presented as fresh. */
    suspend fun refresh(query: TorrentSearchQuery): SearchPageState

    /** Fetches details for one result on demand (never for a whole page). */
    suspend fun getDetails(result: TorrentSearchResult): TorrentResultDetails

    /** Returns a validated download descriptor, or null when none is usable. */
    suspend fun resolveDownloadDescriptor(
        details: TorrentResultDetails,
        preferredFileName: String? = null,
    ): DownloadDescriptor?
}