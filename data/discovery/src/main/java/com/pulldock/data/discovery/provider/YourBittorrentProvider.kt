package com.pulldock.data.discovery.provider

import com.pulldock.data.discovery.http.BoundedResponse
import com.pulldock.data.discovery.http.HttpResult
import com.pulldock.data.discovery.mapper.MagnetCheck
import com.pulldock.data.discovery.provider.dto.YbtResponse
import com.pulldock.data.discovery.provider.dto.YbtResult
import com.pulldock.domain.discovery.ApiCredentialStore
import com.pulldock.domain.discovery.ProviderPage
import com.pulldock.domain.discovery.ProviderPageResult
import com.pulldock.domain.discovery.TorrentSearchProvider
import com.pulldock.domain.discovery.error.DiscoveryError
import com.pulldock.domain.discovery.model.ContributingSource
import com.pulldock.domain.discovery.model.DownloadDescriptor
import com.pulldock.domain.discovery.model.ProviderCapabilities
import com.pulldock.domain.discovery.model.ResultAction
import com.pulldock.domain.discovery.model.ResultSourceType
import com.pulldock.domain.discovery.model.RightsStatus
import com.pulldock.domain.discovery.model.SearchCategory
import com.pulldock.domain.discovery.model.SearchType
import com.pulldock.domain.discovery.model.TorrentResultDetails
import com.pulldock.domain.discovery.model.TorrentSearchQuery
import com.pulldock.domain.discovery.model.TorrentSearchResult
import io.ktor.client.HttpClient
import java.net.URLEncoder
import java.time.Instant
import javax.inject.Inject
import kotlinx.serialization.json.Json

/** YourBittorrent JSON adapter (documented, no key). */
class YourBittorrentProvider @Inject constructor(
    private val client: HttpClient,
    private val credentials: ApiCredentialStore,
) : TorrentSearchProvider {

    override val id: String = "yourbittorrent"
    override val displayName: String = "YourBittorrent"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun capabilities(): ProviderCapabilities = ProviderCapabilities(
        providerId = id,
        searchTypes = setOf(SearchType.GENERAL, SearchType.MOVIE, SearchType.TV, SearchType.MUSIC, SearchType.BOOK),
        categories = CATEGORY_MAP,
        supportsPagination = true,
        requiresAuth = false,
    )

    override suspend fun search(query: TorrentSearchQuery, page: ProviderPage): ProviderPageResult {
        val category = CATEGORY_MAP[query.filters.category]
        val url = buildString {
            append("$BASE/api/search.json?q=").append(URLEncoder.encode(query.text, "UTF-8"))
            append("&limit=").append(PAGE_SIZE).append("&page=").append(page.number)
            if (category != null) append("&category=").append(category)
        }
        val body = when (val r = BoundedResponse.fetch(client, url)) {
            is HttpResult.Failure -> throw r.error
            is HttpResult.Ok -> r.body
        }
        val parsed = runCatching { json.decodeFromString(YbtResponse.serializer(), body) }
            .getOrElse { throw DiscoveryError.Parse("yourbittorrent search") }
        val results = parsed.results.mapNotNull(::toResult)
        return ProviderPageResult(results, results.size == PAGE_SIZE, if (results.size == PAGE_SIZE) page.copy(number = page.number + 1) else null)
    }

    override suspend fun details(nativeId: String): TorrentResultDetails? = null

    override suspend fun downloadDescriptor(nativeId: String, preferredFileName: String?): DownloadDescriptor? = null

    private fun toResult(dto: YbtResult): TorrentSearchResult? {
        val title = dto.name?.takeIf { it.isNotBlank() } ?: return null
        val nativeId = dto.id?.toString() ?: dto.infohash ?: return null
        val magnet = dto.magnet
        return TorrentSearchResult(
            resultId = nativeId,
            providerId = id,
            providerName = displayName,
            title = title,
            description = null,
            category = categoryOf(dto.category_id, dto.category),
            originalProviderCategory = dto.category,
            infoHash = dto.infohash,
            sizeBytes = dto.size_bytes,
            seeders = dto.seeds,
            leechers = dto.peers,
            publishedAt = dto.added?.let { runCatching { Instant.ofEpochSecond(it) }.getOrNull() },
            providerVerificationClaim = dto.verified,
            sourcePageUrl = dto.url,
            resultSourceType = if (magnet != null) ResultSourceType.MAGNET else ResultSourceType.TORRENT_FILE,
            rightsStatus = RightsStatus.UNKNOWN,
            availableActions = setOf(ResultAction.ADD, ResultAction.OPEN_SOURCE, ResultAction.SHARE),
            contributingSources = listOf(ContributingSource(id, displayName, dto.url, nativeId)),
        )
    }

    private fun categoryOf(id: Int?, name: String?): SearchCategory = when (id) {
        1 -> SearchCategory.MOVIES
        2 -> SearchCategory.MUSIC
        3 -> SearchCategory.TV_SERIES
        4 -> SearchCategory.GAMES
        5 -> SearchCategory.SOFTWARE
        6 -> SearchCategory.ANIME
        7 -> SearchCategory.ADULT
        8 -> SearchCategory.EBOOKS
        12 -> SearchCategory.AUDIOBOOKS
        else -> when (name?.lowercase()) {
            "movies" -> SearchCategory.MOVIES
            "television" -> SearchCategory.TV_SERIES
            "software" -> SearchCategory.SOFTWARE
            else -> SearchCategory.OTHER
        }
    }

    private companion object {
        const val BASE = "https://yourbittorrent.com"
        const val PAGE_SIZE = 20
        val CATEGORY_MAP: Map<SearchCategory, String> = mapOf(
            SearchCategory.MOVIES to "1",
            SearchCategory.MUSIC to "2",
            SearchCategory.TV_SERIES to "3",
            SearchCategory.GAMES to "4",
            SearchCategory.SOFTWARE to "5",
            SearchCategory.ANIME to "6",
            SearchCategory.ADULT to "7",
            SearchCategory.EBOOKS to "8",
            SearchCategory.OTHER to "10",
            SearchCategory.DOCUMENTS to "10",
            SearchCategory.AUDIOBOOKS to "12",
        )
    }
}