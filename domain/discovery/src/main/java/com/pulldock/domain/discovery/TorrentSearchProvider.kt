package com.pulldock.domain.discovery

import com.pulldock.domain.discovery.model.DownloadDescriptor
import com.pulldock.domain.discovery.model.ProviderCapabilities
import com.pulldock.domain.discovery.model.TorrentResultDetails
import com.pulldock.domain.discovery.model.TorrentSearchQuery
import com.pulldock.domain.discovery.model.TorrentSearchResult

/** One page request cursor (provider-defined). */
data class ProviderPage(val number: Int, val cursor: String?)

/** One page of normalized results from a provider. */
data class ProviderPageResult(
    val results: List<TorrentSearchResult>,
    val hasMore: Boolean,
    val nextPage: ProviderPage?,
)

/**
 * Provider adapter contract (contracts/torrent-search-provider.md). Each adapter
 * (`BitSearchProvider`, `InternetArchiveProvider`, `TorznabProvider`) implements it
 * independently and owns its DTOs, auth, pagination, and mapping (FR-005, NFR-001).
 */
interface TorrentSearchProvider {

    val id: String
    val displayName: String

    /** Categories/search types this provider supports (from caps or fixed per adapter). */
    suspend fun capabilities(): ProviderCapabilities

    /** One page of normalized results for the query; bounded and cancellable. */
    suspend fun search(query: TorrentSearchQuery, page: ProviderPage): ProviderPageResult

    /** Details for one native result id; null when unavailable. */
    suspend fun details(nativeId: String): TorrentResultDetails?

    /** Optional validated download descriptor (magnet or temp `.torrent` file ref). */
    suspend fun downloadDescriptor(nativeId: String, preferredFileName: String? = null): DownloadDescriptor?
}