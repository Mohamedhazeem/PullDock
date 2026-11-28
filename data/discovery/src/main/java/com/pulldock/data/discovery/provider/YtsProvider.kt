package com.pulldock.data.discovery.provider

import com.pulldock.data.discovery.http.BoundedResponse
import com.pulldock.data.discovery.http.HttpResult
import com.pulldock.data.discovery.mapper.MagnetCheck
import com.pulldock.data.discovery.provider.dto.YtsMovie
import com.pulldock.data.discovery.provider.dto.YtsResponse
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
import javax.inject.Inject
import kotlinx.serialization.json.Json

/** YTS official API v2 adapter (movies, no key). Magnet is built from the provider hash. */
class YtsProvider @Inject constructor(
    private val client: HttpClient,
    private val credentials: ApiCredentialStore,
) : TorrentSearchProvider {

    override val id: String = "yts"
    override val displayName: String = "YTS"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun capabilities(): ProviderCapabilities = ProviderCapabilities(
        providerId = id,
        searchTypes = setOf(SearchType.MOVIE),
        categories = mapOf(SearchCategory.MOVIES to "movies"),
        supportsPagination = true,
        requiresAuth = false,
    )

    override suspend fun search(query: TorrentSearchQuery, page: ProviderPage): ProviderPageResult {
        val url = "$BASE/list_movies.json?query_term=${URLEncoder.encode(query.text, "UTF-8")}" +
            "&limit=$PAGE_SIZE&page=${page.number}&sort_by=seeds&order_by=desc"
        val body = when (val r = BoundedResponse.fetch(client, url)) {
            is HttpResult.Failure -> throw r.error
            is HttpResult.Ok -> r.body
        }
        val parsed = runCatching { json.decodeFromString(YtsResponse.serializer(), body) }
            .getOrElse { throw DiscoveryError.Parse("yts search: ${it.message}") }
        val movies = parsed.data?.movies.orEmpty()
        val results = movies.mapNotNull(::toResult)
        val count = parsed.data?.movie_count ?: 0
        return ProviderPageResult(results, page.number * PAGE_SIZE < count, if (page.number * PAGE_SIZE < count) page.copy(number = page.number + 1) else null)
    }

    override suspend fun details(nativeId: String): TorrentResultDetails? = null

    override suspend fun downloadDescriptor(nativeId: String, preferredFileName: String?): DownloadDescriptor? =
        magnetFor(nativeId, null)

    private fun toResult(m: YtsMovie): TorrentSearchResult? {
        val title = m.title?.takeIf { it.isNotBlank() } ?: return null
        val hash = m.hash ?: m.torrents.orEmpty().firstOrNull()?.hash ?: return null
        val nativeId = m.id?.toString() ?: hash
        val magnet = magnetFor(hash, title)?.magnetUri
        return TorrentSearchResult(
            resultId = nativeId,
            providerId = id,
            providerName = displayName,
            title = title,
            description = null,
            category = SearchCategory.MOVIES,
            originalProviderCategory = "movies",
            infoHash = hash,
            sizeBytes = m.size,
            seeders = m.seeds ?: m.torrents.orEmpty().firstOrNull()?.seeds,
            leechers = m.peers ?: m.torrents.orEmpty().firstOrNull()?.peers,
            publishedAt = m.date_added?.let(::parseInstant),
            providerVerificationClaim = null,
            sourcePageUrl = "https://yts.mx/movies/$nativeId",
            resultSourceType = ResultSourceType.MAGNET,
            rightsStatus = RightsStatus.UNKNOWN,
            availableActions = setOf(ResultAction.ADD, ResultAction.OPEN_SOURCE, ResultAction.SHARE),
            contributingSources = listOf(ContributingSource(id, displayName, "https://yts.mx/movies/$nativeId", nativeId)),
            thumbnailUrl = m.cover,
        )
    }

    private fun magnetFor(hash: String, title: String?): DownloadDescriptor? {
        val magnet = "magnet:?xt=urn:btih:$hash" + (title?.let { "&dn=" + URLEncoder.encode(it, "UTF-8") } ?: "") + TRACKERS
        return if (MagnetCheck.isValid(magnet)) DownloadDescriptor(DescriptorKind.MAGNET, magnet, null) else null
    }

    private fun parseInstant(raw: String): java.time.Instant? =
        runCatching { java.time.Instant.parse(raw.replace(" ", "T")) }.getOrNull()

    private companion object {
        const val BASE = "https://movies-api.accel.li/api/v2"
        const val PAGE_SIZE = 20
        const val TRACKERS = "&tr=udp://tracker.opentrackr.org:1337/announce&tr=udp://open.stealth.si:80/announce"
    }
}