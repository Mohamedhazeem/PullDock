package com.pulldock.data.discovery.provider

import com.pulldock.data.discovery.http.BoundedResponse
import com.pulldock.data.discovery.http.HttpResult
import com.pulldock.data.discovery.mapper.MagnetCheck
import com.pulldock.data.discovery.provider.dto.EztvResponse
import com.pulldock.data.discovery.provider.dto.EztvTorrent
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
import com.pulldock.domain.discovery.model.TorrentFile
import com.pulldock.domain.discovery.model.TorrentResultDetails
import com.pulldock.domain.discovery.model.TorrentSearchQuery
import com.pulldock.domain.discovery.model.TorrentSearchResult
import io.ktor.client.HttpClient
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.serialization.json.Json

/**
 * EZTV JSON adapter (TV). No keyword endpoint: search returns the recent listing filtered
 * client-side by title. A session cache maps native id → torrent so details can be built
 * (EZTV has no detail endpoint).
 */
class EztvProvider @Inject constructor(
    private val client: HttpClient,
    private val credentials: ApiCredentialStore,
) : TorrentSearchProvider {

    override val id: String = "eztv"
    override val displayName: String = "EZTV"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cache = ConcurrentHashMap<String, EztvTorrent>()

    override suspend fun capabilities(): ProviderCapabilities = ProviderCapabilities(
        providerId = id,
        searchTypes = setOf(SearchType.TV),
        categories = mapOf(SearchCategory.TV_SERIES to "tv"),
        supportsPagination = true,
        requiresAuth = false,
    )

    override suspend fun search(query: TorrentSearchQuery, page: ProviderPage): ProviderPageResult {
        val term = query.text.lowercase()
        val collected = mutableListOf<EztvTorrent>()
        var lastSize = 0
        // Widen the recent window: fetch up to 3 pages (3x100) since EZTV has no title search.
        for (p in page.number until page.number + RECENT_PAGES) {
            val parsed = fetchPage(p)
            lastSize = parsed.torrents.size
            collected += parsed.torrents
            if (lastSize < PAGE_SIZE) break
        }
        val seen = HashSet<String>()
        val results = collected
            .filter { (it.title ?: it.filename)?.lowercase()?.contains(term) == true }
            .mapNotNull(::toResult)
            .filter { seen.add(it.resultId) }
        val hasMore = lastSize == PAGE_SIZE
        return ProviderPageResult(results, hasMore, if (hasMore) page.copy(number = page.number + RECENT_PAGES) else null)
    }

    private suspend fun fetchPage(pageNumber: Int): EztvResponse {
        val url = "$BASE/api/get-torrents?limit=$PAGE_SIZE&page=$pageNumber"
        val body = when (val r = BoundedResponse.fetch(client, url)) {
            is HttpResult.Failure -> throw r.error
            is HttpResult.Ok -> r.body
        }
        return runCatching { json.decodeFromString(EztvResponse.serializer(), body) }
            .getOrElse { throw DiscoveryError.Parse("eztv search") }
    }

    override suspend fun details(nativeId: String): TorrentResultDetails? {
        val t = cache[nativeId] ?: return null
        val magnet = t.magnet_url
        return TorrentResultDetails(
            resultId = nativeId,
            providerId = id,
            providerName = displayName,
            title = t.title ?: t.filename ?: nativeId,
            description = null,
            category = SearchCategory.TV_SERIES,
            infoHash = t.hash,
            sizeBytes = t.size_bytes?.toLongOrNull(),
            seeders = t.seeds,
            leechers = t.peers,
            publishedAt = t.date_released_unix?.toLongOrNull()?.let { runCatching { java.time.Instant.ofEpochSecond(it) }.getOrNull() },
            sourcePageUrl = t.episode_url(),
            rightsStatus = RightsStatus.UNKNOWN,
            licence = null,
            downloadDescriptor = if (MagnetCheck.isValid(magnet)) DownloadDescriptor(DescriptorKind.MAGNET, magnet, null) else null,
            availableActions = setOf(ResultAction.ADD, ResultAction.SHARE),
            files = listOfNotNull(t.filename?.let { TorrentFile(it, t.size_bytes?.toLongOrNull()) }),
            images = listOfNotNull(t.small_screenshot, t.large_screenshot).filter { it.startsWith("https://") },
        )
    }

    override suspend fun downloadDescriptor(nativeId: String, preferredFileName: String?): DownloadDescriptor? {
        val magnet = cache[nativeId]?.magnet_url
        return if (MagnetCheck.isValid(magnet)) DownloadDescriptor(DescriptorKind.MAGNET, magnet, null) else null
    }

    private fun toResult(dto: EztvTorrent): TorrentSearchResult? {
        val title = dto.title ?: dto.filename ?: return null
        val nativeId = dto.hash ?: dto.id?.toString() ?: return null
        cache[nativeId] = dto
        return TorrentSearchResult(
            resultId = nativeId,
            providerId = id,
            providerName = displayName,
            title = title,
            description = null,
            category = SearchCategory.TV_SERIES,
            originalProviderCategory = "tv",
            infoHash = dto.hash,
            sizeBytes = dto.size_bytes?.toLongOrNull(),
            seeders = dto.seeds,
            leechers = dto.peers,
            publishedAt = dto.date_released_unix?.toLongOrNull()?.let { runCatching { java.time.Instant.ofEpochSecond(it) }.getOrNull() },
            providerVerificationClaim = null,
            sourcePageUrl = dto.episode_url(),
            resultSourceType = ResultSourceType.MAGNET,
            rightsStatus = RightsStatus.UNKNOWN,
            availableActions = setOf(ResultAction.ADD, ResultAction.OPEN_SOURCE, ResultAction.SHARE),
            contributingSources = listOf(ContributingSource(id, displayName, dto.episode_url(), nativeId)),
            thumbnailUrl = dto.small_screenshot,
        )
    }

    private fun EztvTorrent.episode_url(): String? = torrent_url

    private companion object {
        const val BASE = "https://eztv.re"
        const val PAGE_SIZE = 100
        const val RECENT_PAGES = 3
    }
}