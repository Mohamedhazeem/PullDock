package com.pulldock.data.discovery.provider

import com.pulldock.data.discovery.http.BoundedResponse
import com.pulldock.data.discovery.http.HttpResult
import com.pulldock.data.discovery.mapper.MagnetCheck
import com.pulldock.data.discovery.provider.dto.BitSearchDetailResponse
import com.pulldock.data.discovery.provider.dto.BitSearchResultDto
import com.pulldock.data.discovery.provider.dto.BitSearchSearchResponse
import com.pulldock.domain.discovery.ApiCredentialStore
import com.pulldock.domain.discovery.ProviderPage
import com.pulldock.domain.discovery.ProviderPageResult
import com.pulldock.domain.discovery.TorrentSearchProvider
import com.pulldock.domain.discovery.error.DiscoveryError
import com.pulldock.domain.discovery.model.ContributingSource
import com.pulldock.domain.discovery.model.DownloadDescriptor
import com.pulldock.domain.discovery.model.DescriptorKind
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

/**
 * BitSearch adapter (FR-010..FR-019) for the documented REST schema. Detail is fetched only
 * on demand; the magnet is taken from the detail response and validated before handoff; the
 * optional `x-api-key` is session-only and never logged. No aggressive 401/403/429 retry.
 */
class BitSearchProvider @Inject constructor(
    private val client: HttpClient,
    private val credentials: ApiCredentialStore,
) : TorrentSearchProvider {

    override val id: String = "bitsearch"
    override val displayName: String = "BitSearch"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun headers(): Map<String, String> =
        credentials.get(id)?.let { mapOf("x-api-key" to it) } ?: emptyMap()

    override suspend fun capabilities(): ProviderCapabilities = ProviderCapabilities(
        providerId = id,
        searchTypes = setOf(SearchType.GENERAL),
        categories = CATEGORY_MAP,
        supportsPagination = true,
        requiresAuth = false,
    )

    override suspend fun search(query: TorrentSearchQuery, page: ProviderPage): ProviderPageResult {
        val category = CATEGORY_MAP[query.filters.category]
        val url = buildString {
            append("$BASE/search?q=").append(URLEncoder.encode(query.text, "UTF-8"))
            append("&page=").append(page.number)
            append("&limit=").append(PAGE_SIZE)
            if (category != null) append("&category=").append(category)
        }
        val body = when (val r = BoundedResponse.fetch(client, url, headers = headers())) {
            is HttpResult.Failure -> throw r.error
            is HttpResult.Ok -> r.body
        }
        val parsed = runCatching { json.decodeFromString(BitSearchSearchResponse.serializer(), body) }
            .getOrElse { throw DiscoveryError.Parse("bitsearch search") }
        val results = parsed.results.mapNotNull(::toResult)
        return ProviderPageResult(
            results = results,
            hasMore = results.size == PAGE_SIZE,
            nextPage = if (results.size == PAGE_SIZE) page.copy(number = page.number + 1) else null,
        )
    }

    override suspend fun details(nativeId: String): TorrentResultDetails? {
        val url = "$BASE/torrent/${URLEncoder.encode(nativeId, "UTF-8")}"
        val body = when (val r = BoundedResponse.fetch(client, url, headers = headers())) {
            is HttpResult.Failure -> throw r.error
            is HttpResult.Ok -> r.body
        }
        val dto = runCatching { json.decodeFromString(BitSearchDetailResponse.serializer(), body) }
            .getOrElse { throw DiscoveryError.Parse("bitsearch detail") }
        val t = dto.torrent ?: return null
        val magnet = t.magnetUri
        val descriptor = if (MagnetCheck.isValid(magnet)) {
            DownloadDescriptor(DescriptorKind.MAGNET, magnetUri = magnet, torrentFileRef = null)
        } else null
        return TorrentResultDetails(
            resultId = nativeId,
            providerId = id,
            providerName = displayName,
            title = t.title ?: nativeId,
            description = null,
            category = categoryOf(t.category),
            infoHash = t.infohash ?: MagnetCheck.infoHash(magnet),
            sizeBytes = t.size,
            seeders = t.swarm?.seeders,
            leechers = t.swarm?.leechers,
            publishedAt = t.createdAt?.let(::parseInstant),
            sourcePageUrl = "$BASE/torrent/$nativeId",
            rightsStatus = RightsStatus.UNKNOWN,
            licence = null,
            downloadDescriptor = descriptor,
            availableActions = actions(descriptor != null),
        )
    }

    override suspend fun downloadDescriptor(nativeId: String, preferredFileName: String?): DownloadDescriptor? =
        details(nativeId)?.downloadDescriptor

    private fun toResult(dto: BitSearchResultDto): TorrentSearchResult? {
        val title = dto.title?.takeIf { it.isNotBlank() } ?: return null
        val nativeId = dto.id ?: dto.infohash ?: return null
        return TorrentSearchResult(
            resultId = nativeId,
            providerId = id,
            providerName = displayName,
            title = title,
            description = null,
            category = categoryOf(dto.category),
            originalProviderCategory = dto.category,
            infoHash = dto.infohash,
            sizeBytes = dto.size,
            seeders = dto.seeders,
            leechers = dto.leechers,
            publishedAt = dto.createdAt?.let(::parseInstant),
            providerVerificationClaim = dto.verified,
            sourcePageUrl = "$BASE/torrent/$nativeId",
            resultSourceType = ResultSourceType.MAGNET,
            rightsStatus = RightsStatus.UNKNOWN,
            availableActions = actions(hasMagnet = false),
            contributingSources = listOf(ContributingSource(id, displayName, "$BASE/torrent/$nativeId", nativeId)),
        )
    }

    private fun actions(hasMagnet: Boolean): Set<ResultAction> = buildSet {
        add(ResultAction.ADD)
        add(ResultAction.OPEN_SOURCE)
        if (hasMagnet) add(ResultAction.COPY_INFOHASH)
        add(ResultAction.SHARE)
    }

    private fun categoryOf(raw: String?): SearchCategory = when (raw?.lowercase()?.trim()) {
        "movies", "movie" -> SearchCategory.MOVIES
        "tv", "tv shows", "tvseries" -> SearchCategory.TV_SERIES
        "anime" -> SearchCategory.ANIME
        "software", "softwares", "apps" -> SearchCategory.SOFTWARE
        "games", "game" -> SearchCategory.GAMES
        "music" -> SearchCategory.MUSIC
        "audiobook", "audiobooks" -> SearchCategory.AUDIOBOOKS
        "ebook", "ebooks", "course", "courses" -> SearchCategory.EBOOKS
        "xxx", "adult", "adult content" -> SearchCategory.ADULT
        "document", "documents" -> SearchCategory.DOCUMENTS
        else -> SearchCategory.OTHER
    }

    private fun parseInstant(raw: String): Instant? = runCatching { Instant.parse(raw) }.getOrNull()

    private companion object {
        const val BASE = "https://bitsearch.eu/api/v1"
        const val PAGE_SIZE = 20
        val CATEGORY_MAP: Map<SearchCategory, String> = mapOf(
            SearchCategory.OTHER to "1",
            SearchCategory.MOVIES to "2",
            SearchCategory.TV_SERIES to "3",
            SearchCategory.ANIME to "4",
            SearchCategory.SOFTWARE to "5",
            SearchCategory.GAMES to "6",
            SearchCategory.MUSIC to "7",
            SearchCategory.AUDIOBOOKS to "8",
            SearchCategory.EBOOKS to "9",
            SearchCategory.DOCUMENTS to "1",
            SearchCategory.ADULT to "10",
        )
    }
}