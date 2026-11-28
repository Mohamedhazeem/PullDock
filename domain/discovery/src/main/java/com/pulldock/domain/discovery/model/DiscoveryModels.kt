package com.pulldock.domain.discovery.model

import com.pulldock.domain.discovery.error.DiscoveryError
import java.time.Instant

/** Application-level category, independent of provider-specific ids (FR-035). */
enum class SearchCategory {
    ALL, MOVIES, TV_SERIES, DOCUMENTARIES, ANIME, BOOKS, EBOOKS, AUDIOBOOKS,
    MUSIC, SOFTWARE, GAMES, DOCUMENTS, OTHER, ADULT,
}

/** How a result's torrent is obtained (FR-050). */
enum class ResultSourceType { MAGNET, TORRENT_FILE }

/** Rights metadata status; describes available metadata, not a legal guarantee (FR-051). */
enum class RightsStatus { CONFIRMED, UNKNOWN, RESTRICTED, DISPUTED }

/** Actions available for a result. */
enum class ResultAction { ADD, OPEN_SOURCE, COPY_INFOHASH, SHARE }

/** Result sort orders (FR-045). */
enum class SortOrder { RELEVANCE, SEEDERS, NEWEST, LARGEST, SMALLEST }

/** Provider search capabilities. */
enum class SearchType { GENERAL, MOVIE, TV, MUSIC, BOOK }

/** One provider's contribution to a (possibly merged) result (FR-043). */
data class ContributingSource(
    val providerId: String,
    val providerName: String,
    val sourcePageUrl: String?,
    val nativeId: String?,
)

/**
 * A normalized, provider-attributed result (FR-049). Every optional field is nullable;
 * missing metadata is never fabricated (FR-052).
 */
data class TorrentSearchResult(
    val resultId: String,
    val providerId: String,
    val providerName: String,
    val title: String,
    val description: String?,
    val category: SearchCategory,
    val originalProviderCategory: String?,
    val infoHash: String?,
    val sizeBytes: Long?,
    val seeders: Int?,
    val leechers: Int?,
    val publishedAt: Instant?,
    val providerVerificationClaim: Boolean?,
    val sourcePageUrl: String?,
    val resultSourceType: ResultSourceType,
    val rightsStatus: RightsStatus,
    val availableActions: Set<ResultAction>,
    val contributingSources: List<ContributingSource>,
    /** Optional poster/thumbnail URL when the provider supplies one (never fabricated). */
    val thumbnailUrl: String? = null,
)

/** Detail for one result, fetched on demand (FR-053). */
data class TorrentResultDetails(
    val resultId: String,
    val providerId: String,
    val providerName: String,
    val title: String,
    val description: String?,
    val category: SearchCategory,
    val infoHash: String?,
    val sizeBytes: Long?,
    val seeders: Int?,
    val leechers: Int?,
    val publishedAt: Instant?,
    val sourcePageUrl: String?,
    val rightsStatus: RightsStatus,
    val licence: String?,
    val downloadDescriptor: DownloadDescriptor?,
    val availableActions: Set<ResultAction>,
    /** Archive items with more than one `.torrent` file list them here for user choice. */
    val availableTorrentFiles: List<String> = emptyList(),
    /** Optional poster/thumbnail URL when the provider supplies one (never fabricated). */
    val thumbnailUrl: String? = null,
)

enum class DescriptorKind { MAGNET, TORRENT_FILE }

/** A validated download descriptor used to hand a result into the existing Add flow. */
data class DownloadDescriptor(
    val kind: DescriptorKind,
    val magnetUri: String?,
    val torrentFileRef: String?,
    val requiresUserChoice: Boolean = false,
)

/** Search filters (FR-048). */
data class SearchFilters(
    val category: SearchCategory = SearchCategory.ALL,
    val providerIds: Set<String> = emptySet(),
    val minSeeders: Int? = null,
    val minSizeBytes: Long? = null,
    val maxSizeBytes: Long? = null,
    val dateFrom: Instant? = null,
    val dateTo: Instant? = null,
    val sort: SortOrder = SortOrder.RELEVANCE,
    /** Which filters the provider actually applied upstream (empty = client-side only). */
    val appliedUpstream: Set<String> = emptySet(),
)

/** What a provider supports (FR-006). */
data class ProviderCapabilities(
    val providerId: String,
    val searchTypes: Set<SearchType>,
    val categories: Map<SearchCategory, String>,
    val supportsPagination: Boolean,
    val requiresAuth: Boolean,
)

enum class ProviderState { SUCCESS, EMPTY, FAILED, RATE_LIMITED, DISABLED, TIMEOUT }

/** Per-provider status for a search (FR-040). */
data class ProviderStatus(
    val providerId: String,
    val providerName: String,
    val state: ProviderState,
    val resultCount: Int? = null,
    val error: DiscoveryError? = null,
    val lastCapabilityCheck: Instant? = null,
)

enum class OverallSearchState {
    IDLE, LOADING, RESULTS, PARTIAL, EMPTY, ALL_FAILED, OFFLINE,
    LOADING_MORE, REFRESHING, CANCELLED,
}

/** The overall search state plus per-provider results/status (FR-040). */
data class SearchPageState(
    val query: String = "",
    val filters: SearchFilters = SearchFilters(),
    val overall: OverallSearchState = OverallSearchState.IDLE,
    val perProvider: List<ProviderStatus> = emptyList(),
    val results: List<TorrentSearchResult> = emptyList(),
    val hasMoreByProvider: Map<String, Boolean> = emptyMap(),
)

/** A local, user-deletable history entry (FR-061). */
data class SearchHistoryEntry(
    val query: String,
    val category: SearchCategory?,
    val usedAt: Instant,
)

/**
 * User-configured Torznab provider (FR-029..FR-034). [apiKey] is session-only in memory
 * and MUST never be persisted (FR-042).
 */
data class ProviderConfiguration(
    val providerId: String,
    val displayName: String,
    val endpoint: String,
    val enabled: Boolean,
    val lastCapabilityCheck: Instant? = null,
    val supportedCategories: Map<SearchCategory, String> = emptyMap(),
    val apiKey: String? = null,
)

/** A search request. */
data class TorrentSearchQuery(
    val text: String,
    val filters: SearchFilters,
)