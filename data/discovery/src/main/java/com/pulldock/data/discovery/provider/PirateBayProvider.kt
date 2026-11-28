package com.pulldock.data.discovery.provider

import com.pulldock.data.discovery.http.BoundedResponse
import com.pulldock.data.discovery.http.HttpResult
import com.pulldock.data.discovery.mapper.MagnetCheck
import com.pulldock.data.discovery.provider.dto.ApibayDetail
import com.pulldock.data.discovery.provider.dto.ApibayFile
import com.pulldock.data.discovery.provider.dto.ApibayTorrent
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
import java.net.URLEncoder
import java.time.Instant
import javax.inject.Inject
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The Pirate Bay via apibay JSON (unofficial, untrusted). Enabled only when the user opts in.
 * Search `q.php`; details `t.php` + files `f.php`; magnet built from the provider info_hash.
 */
class PirateBayProvider @Inject constructor(
    private val client: HttpClient,
    private val credentials: ApiCredentialStore,
) : TorrentSearchProvider {

    override val id: String = "piratebay"
    override val displayName: String = "The Pirate Bay"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun capabilities(): ProviderCapabilities = ProviderCapabilities(
        providerId = id,
        searchTypes = setOf(SearchType.GENERAL, SearchType.MOVIE, SearchType.TV, SearchType.MUSIC, SearchType.BOOK),
        categories = CATEGORY_MAP,
        supportsPagination = true,
        requiresAuth = false,
    )

    override suspend fun search(query: TorrentSearchQuery, page: ProviderPage): ProviderPageResult {
        val cat = CATEGORY_MAP[query.filters.category] ?: "0"
        val url = "$BASE/q.php?q=${URLEncoder.encode(query.text, "UTF-8")}&cat=$cat"
        val body = when (val r = BoundedResponse.fetch(client, url)) {
            is HttpResult.Failure -> throw r.error
            is HttpResult.Ok -> r.body
        }
        val parsed = runCatching { json.decodeFromString(ListSerializer(ApibayTorrent.serializer()), body) }
            .getOrElse { throw DiscoveryError.Parse("apibay search") }
        val results = parsed.mapNotNull(::toResult)
        return ProviderPageResult(results, hasMore = false, nextPage = null)
    }

    override suspend fun details(nativeId: String): TorrentResultDetails? {
        val detail = fetchDetail(nativeId) ?: return null
        val files = fetchFiles(nativeId)
        return TorrentResultDetails(
            resultId = nativeId,
            providerId = id,
            providerName = displayName,
            title = detail.name ?: nativeId,
            description = detail.descr?.let(::stripHtml),
            category = categoryOf(detail.category),
            infoHash = detail.info_hash,
            sizeBytes = detail.size?.toLongOrNull(),
            seeders = detail.seeders,
            leechers = detail.leechers,
            publishedAt = detail.added?.toLongOrNull()?.let { runCatching { Instant.ofEpochSecond(it) }.getOrNull() },
            sourcePageUrl = "$BASE/description.php?id=$nativeId",
            rightsStatus = RightsStatus.UNKNOWN,
            licence = null,
            downloadDescriptor = magnetFor(detail.info_hash, detail.name),
            availableActions = setOf(ResultAction.ADD, ResultAction.OPEN_SOURCE, ResultAction.SHARE),
            files = files.map { TorrentFile(it.name ?: "", it.size?.toLongOrNull()) },
            images = detail.descr?.let(::extractImages).orEmpty(),
        )
    }

    override suspend fun downloadDescriptor(nativeId: String, preferredFileName: String?): DownloadDescriptor? {
        val detail = fetchDetail(nativeId) ?: return null
        return magnetFor(detail.info_hash, detail.name)
    }

    private suspend fun fetchDetail(id: String): ApibayDetail? {
        val body = when (val r = BoundedResponse.fetch(client, "$BASE/t.php?id=$id")) {
            is HttpResult.Failure -> throw r.error
            is HttpResult.Ok -> r.body
        }
        return runCatching { json.decodeFromString(ApibayDetail.serializer(), body) }.getOrNull()
    }

    private suspend fun fetchFiles(id: String): List<ApibayFile> {
        val body = when (val r = BoundedResponse.fetch(client, "$BASE/f.php?id=$id")) {
            is HttpResult.Failure -> return emptyList()
            is HttpResult.Ok -> r.body
        }
        return runCatching { json.decodeFromString(ListSerializer(ApibayFile.serializer()), body) }.getOrDefault(emptyList())
    }

    private fun toResult(dto: ApibayTorrent): TorrentSearchResult? {
        val title = dto.name?.takeIf { it.isNotBlank() } ?: return null
        val nativeId = dto.id ?: dto.info_hash ?: return null
        return TorrentSearchResult(
            resultId = nativeId,
            providerId = id,
            providerName = displayName,
            title = title,
            description = null,
            category = categoryOf(dto.category),
            originalProviderCategory = dto.category,
            infoHash = dto.info_hash,
            sizeBytes = dto.size?.toLongOrNull(),
            seeders = dto.seeders,
            leechers = dto.leechers,
            publishedAt = dto.added?.toLongOrNull()?.let { runCatching { Instant.ofEpochSecond(it) }.getOrNull() },
            providerVerificationClaim = dto.status?.equals("vip", true) == true,
            sourcePageUrl = "$BASE/description.php?id=$nativeId",
            resultSourceType = ResultSourceType.MAGNET,
            rightsStatus = RightsStatus.UNKNOWN,
            availableActions = setOf(ResultAction.ADD, ResultAction.OPEN_SOURCE, ResultAction.SHARE),
            contributingSources = listOf(ContributingSource(id, displayName, "$BASE/description.php?id=$nativeId", nativeId)),
        )
    }

    private fun magnetFor(infoHash: String?, name: String?): DownloadDescriptor? {
        if (infoHash.isNullOrBlank()) return null
        val magnet = "magnet:?xt=urn:btih:$infoHash" + (name?.let { "&dn=" + URLEncoder.encode(it, "UTF-8") } ?: "")
        return if (MagnetCheck.isValid(magnet)) DownloadDescriptor(DescriptorKind.MAGNET, magnet, null) else null
    }

    private fun categoryOf(cat: String?): SearchCategory = when (cat) {
        "100" -> SearchCategory.MUSIC
        "200" -> SearchCategory.MOVIES
        "300" -> SearchCategory.SOFTWARE
        "400" -> SearchCategory.GAMES
        "500" -> SearchCategory.ADULT
        "600" -> SearchCategory.EBOOKS
        else -> SearchCategory.OTHER
    }

    private fun stripHtml(raw: String): String = raw.replace(Regex("<[^>]+>"), "").trim()

    private fun extractImages(raw: String): List<String> =
        Regex("https?://[^\\s\"'<>]+?\\.(?:jpg|jpeg|png|webp)", RegexOption.IGNORE_CASE)
            .findAll(raw).map { it.value }.filter { it.startsWith("https://") }.take(4).toList()

    private companion object {
        const val BASE = "https://apibay.org"
        val CATEGORY_MAP: Map<SearchCategory, String> = mapOf(
            SearchCategory.MOVIES to "200",
            SearchCategory.TV_SERIES to "200",
            SearchCategory.MUSIC to "100",
            SearchCategory.SOFTWARE to "300",
            SearchCategory.GAMES to "400",
            SearchCategory.ADULT to "500",
            SearchCategory.EBOOKS to "600",
            SearchCategory.BOOKS to "600",
        )
    }
}