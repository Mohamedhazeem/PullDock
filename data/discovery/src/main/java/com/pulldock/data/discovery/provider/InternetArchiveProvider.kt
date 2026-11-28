package com.pulldock.data.discovery.provider

import com.pulldock.data.discovery.http.BoundedResponse
import com.pulldock.data.discovery.http.HttpResult
import com.pulldock.data.discovery.provider.dto.ArchiveDocDto
import com.pulldock.data.discovery.provider.dto.ArchiveMetadataResponse
import com.pulldock.data.discovery.provider.dto.ArchiveSearchResponse
import com.pulldock.data.discovery.provider.dto.asText
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
import javax.inject.Inject
import kotlinx.serialization.json.Json

/**
 * Internet Archive adapter (FR-020..FR-028). Official Advanced Search restricted to
 * `format:"Archive BitTorrent"`; the real `.torrent` is located from item metadata `files`
 * (never fabricated); the download URL follows documented conventions over https; licence →
 * rights (absent = UNKNOWN). Multiple torrent files require a user choice.
 */
class InternetArchiveProvider @Inject constructor(
    private val client: HttpClient,
    private val tempTorrent: TemporaryTorrentFile? = null,
) : TorrentSearchProvider {

    override val id: String = "archive"
    override val displayName: String = "Internet Archive"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun capabilities(): ProviderCapabilities = ProviderCapabilities(
        providerId = id,
        searchTypes = setOf(SearchType.GENERAL, SearchType.MOVIE, SearchType.MUSIC, SearchType.BOOK),
        categories = ARCHIVE_CATEGORIES,
        supportsPagination = true,
        requiresAuth = false,
    )

    override suspend fun search(query: TorrentSearchQuery, page: ProviderPage): ProviderPageResult {
        val catTerm = when (query.filters.category) {
            SearchCategory.MOVIES -> "mediatype:movies"
            SearchCategory.MUSIC -> "mediatype:audio"
            SearchCategory.BOOKS, SearchCategory.EBOOKS -> "mediatype:texts"
            SearchCategory.SOFTWARE -> "mediatype:software"
            else -> null
        }
        val q = "${URLEncoder.encode(query.text, "UTF-8")} AND format:\"Archive BitTorrent\"" +
            (catTerm?.let { " AND $it" } ?: "")
        val url = buildString {
            append("$ADVANCED_SEARCH?q=").append(q)
            append("&fl=").append(FL)
            append("&rows=").append(PAGE_SIZE)
            append("&page=").append(page.number)
            append("&output=json")
        }
        val body = when (val r = BoundedResponse.fetch(client, url)) {
            is HttpResult.Failure -> throw r.error
            is HttpResult.Ok -> r.body
        }
        val parsed = runCatching { json.decodeFromString(ArchiveSearchResponse.serializer(), body) }
            .getOrElse { throw DiscoveryError.Parse("archive search") }
        val docs = parsed.response?.docs.orEmpty()
        val results = docs.mapNotNull(::toResult)
        return ProviderPageResult(
            results = results,
            hasMore = page.number * PAGE_SIZE < (parsed.response?.numFound ?: 0),
            nextPage = if (page.number * PAGE_SIZE < (parsed.response?.numFound ?: 0)) page.copy(number = page.number + 1) else null,
        )
    }

    override suspend fun details(nativeId: String): TorrentResultDetails? {
        val meta = fetchMetadata(nativeId)
        val torrents = meta.files.filter { it.isTorrent() }
        return TorrentResultDetails(
            resultId = nativeId,
            providerId = id,
            providerName = displayName,
            title = meta.metadata?.title.asText() ?: nativeId,
            description = meta.metadata?.description.asText(),
            category = SearchCategory.OTHER,
            infoHash = null,
            sizeBytes = null,
            seeders = null,
            leechers = null,
            publishedAt = null,
            sourcePageUrl = "$ITEM_BASE/$nativeId",
            rightsStatus = if (meta.metadata?.licenseurl != null) RightsStatus.CONFIRMED else RightsStatus.UNKNOWN,
            licence = meta.metadata?.licenseurl,
            downloadDescriptor = null,
            availableActions = if (torrents.isEmpty()) emptySet() else setOf(ResultAction.ADD, ResultAction.OPEN_SOURCE, ResultAction.SHARE),
            availableTorrentFiles = torrents.mapNotNull { it.name },
            files = meta.files.map { TorrentFile(it.name ?: "", it.size) },
        )
    }

    override suspend fun downloadDescriptor(nativeId: String, preferredFileName: String?): DownloadDescriptor? {
        val meta = fetchMetadata(nativeId)
        val torrents = meta.files.filter { it.isTorrent() }
        val chosen = when {
            preferredFileName != null -> torrents.firstOrNull { it.name == preferredFileName }
            torrents.size == 1 -> torrents.first()
            else -> null
        }
        if (chosen == null && torrents.size > 1) {
            return DownloadDescriptor(DescriptorKind.TORRENT_FILE, null, null, requiresUserChoice = true)
        }
        val file = chosen ?: return null
        val temp = tempTorrent ?: return null
        val url = downloadUrl(nativeId, file.name ?: return null)
        return when (val result = temp.download(url)) {
            is TempTorrentResult.Failure -> throw result.error
            is TempTorrentResult.Ok -> DownloadDescriptor(
                kind = DescriptorKind.TORRENT_FILE,
                magnetUri = null,
                torrentFileRef = result.file.toURI().toString(),
                requiresUserChoice = false,
            )
        }
    }

    private suspend fun fetchMetadata(identifier: String): ArchiveMetadataResponse {
        val body = when (val r = BoundedResponse.fetch(client, "$METADATA_BASE/$identifier")) {
            is HttpResult.Failure -> throw r.error
            is HttpResult.Ok -> r.body
        }
        return runCatching { json.decodeFromString(ArchiveMetadataResponse.serializer(), body) }
            .getOrElse { throw DiscoveryError.Parse("archive metadata") }
    }

    private fun toResult(doc: ArchiveDocDto): TorrentSearchResult? {
        val identifier = doc.identifier?.takeIf { it.isNotBlank() } ?: return null
        return TorrentSearchResult(
            resultId = identifier,
            providerId = id,
            providerName = displayName,
            title = doc.title.asText()?.takeIf { it.isNotBlank() } ?: identifier,
            description = doc.description.asText(),
            category = categoryOf(doc.mediatype),
            originalProviderCategory = doc.mediatype,
            infoHash = null,
            sizeBytes = null,
            seeders = null,
            leechers = null,
            publishedAt = null,
            providerVerificationClaim = null,
            sourcePageUrl = "$ITEM_BASE/$identifier",
            resultSourceType = ResultSourceType.TORRENT_FILE,
            rightsStatus = if (doc.licenseurl != null) RightsStatus.CONFIRMED else RightsStatus.UNKNOWN,
            availableActions = setOf(ResultAction.ADD, ResultAction.OPEN_SOURCE, ResultAction.SHARE),
            contributingSources = listOf(ContributingSource(id, displayName, "$ITEM_BASE/$identifier", identifier)),
        )
    }

    private fun categoryOf(mediatype: String?): SearchCategory = when (mediatype?.lowercase()) {
        "movies" -> SearchCategory.MOVIES
        "audio" -> SearchCategory.MUSIC
        "texts" -> SearchCategory.BOOKS
        "software" -> SearchCategory.SOFTWARE
        else -> SearchCategory.OTHER
    }

    private fun downloadUrl(identifier: String, filename: String): String =
        "$DOWNLOAD_BASE/$identifier/${URLEncoder.encode(filename, "UTF-8")}"

    private fun com.pulldock.data.discovery.provider.dto.ArchiveFileDto.isTorrent(): Boolean {
        val name = name ?: return false
        val format = format?.lowercase().orEmpty()
        return name.endsWith(".torrent", ignoreCase = true) || format.contains("bittrorrent") || format.contains("torrent")
    }

    private companion object {
        const val ADVANCED_SEARCH = "https://archive.org/advancedsearch.php"
        const val METADATA_BASE = "https://archive.org/metadata"
        const val ITEM_BASE = "https://archive.org/details"
        const val DOWNLOAD_BASE = "https://archive.org/download"
        const val PAGE_SIZE = 20
        const val FL = "identifier,title,creator,description,date,mediatype,subject,licenseurl,publicdate"
        val ARCHIVE_CATEGORIES: Map<SearchCategory, String> = mapOf(
            SearchCategory.MOVIES to "movies",
            SearchCategory.MUSIC to "audio",
            SearchCategory.BOOKS to "texts",
            SearchCategory.SOFTWARE to "software",
        )
    }
}