package com.pulldock.data.discovery.provider

import com.pulldock.data.discovery.http.BoundedResponse
import com.pulldock.data.discovery.http.HttpResult
import com.pulldock.data.discovery.mapper.MagnetCheck
import com.pulldock.domain.discovery.ProviderPage
import com.pulldock.domain.discovery.ProviderPageResult
import com.pulldock.domain.discovery.TorrentSearchProvider
import com.pulldock.domain.discovery.error.DiscoveryError
import com.pulldock.domain.discovery.model.ContributingSource
import com.pulldock.domain.discovery.model.DownloadDescriptor
import com.pulldock.domain.discovery.model.DescriptorKind
import com.pulldock.domain.discovery.model.ProviderCapabilities
import com.pulldock.domain.discovery.model.ProviderConfiguration
import com.pulldock.domain.discovery.model.ResultAction
import com.pulldock.domain.discovery.model.ResultSourceType
import com.pulldock.domain.discovery.model.RightsStatus
import com.pulldock.domain.discovery.model.SearchCategory
import com.pulldock.domain.discovery.model.SearchType
import com.pulldock.domain.discovery.model.TorrentResultDetails
import com.pulldock.domain.discovery.model.TorrentSearchQuery
import com.pulldock.domain.discovery.model.TorrentSearchResult
import io.ktor.client.HttpClient
import java.io.StringReader
import java.net.URLEncoder
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Reusable Torznab adapter (FR-029..FR-034). Parses the capability document before use, reads
 * RSS/XML with the platform `XmlPullParser` (bounded, namespace-tolerant), and treats returned
 * magnet/download URLs as untrusted. HTTPS endpoints only (validated by the config UI).
 */
class TorznabProvider(
    private val config: ProviderConfiguration,
    private val client: HttpClient,
    private val credentials: com.pulldock.domain.discovery.ApiCredentialStore? = null,
) : TorrentSearchProvider {

    override val id: String = config.providerId
    override val displayName: String = config.displayName

    override suspend fun capabilities(): ProviderCapabilities {
        val body = fetch("$endpoint/api?t=caps")
        val caps = parseCapabilities(body)
        return ProviderCapabilities(
            providerId = id,
            searchTypes = caps.searchTypes,
            categories = caps.categories,
            supportsPagination = true,
            requiresAuth = config.apiKey != null,
        )
    }

    override suspend fun search(query: TorrentSearchQuery, page: ProviderPage): ProviderPageResult {
        val cat = config.supportedCategories[query.filters.category]
        val url = buildString {
            append(endpoint).append("/api?t=search&q=").append(URLEncoder.encode(query.text, "UTF-8"))
            if (cat != null) append("&cat=").append(cat)
            append("&offset=").append((page.number - 1) * PAGE_SIZE)
        }
        val body = fetch(url)
        val items = parseItems(body)
        val results = items.mapNotNull(::toResult)
        return ProviderPageResult(
            results = results,
            hasMore = items.size == PAGE_SIZE,
            nextPage = if (items.size == PAGE_SIZE) page.copy(number = page.number + 1) else null,
        )
    }

    override suspend fun details(nativeId: String): TorrentResultDetails? = null

    override suspend fun downloadDescriptor(nativeId: String, preferredFileName: String?): DownloadDescriptor? {
        // Torznab exposes the magnet in the search item; the descriptor is resolved from the
        // result's stored source. When a magnet is available it is validated before handoff.
        val magnet = nativeId
        return if (MagnetCheck.isValid(magnet)) {
            DownloadDescriptor(DescriptorKind.MAGNET, magnetUri = magnet, torrentFileRef = null)
        } else null
    }

    private val endpoint: String get() = config.endpoint.trimEnd('/')

    private suspend fun fetch(url: String): String {
        val key = credentials?.get(id) ?: config.apiKey
        val headers = key?.let { mapOf("X-Api-Key" to it) } ?: emptyMap()
        return when (val r = BoundedResponse.fetch(client, url, headers = headers)) {
            is HttpResult.Failure -> throw r.error
            is HttpResult.Ok -> r.body
        }
    }

    private fun toResult(item: TorznabItem): TorrentSearchResult? {
        val title = item.title?.takeIf { it.isNotBlank() } ?: return null
        val magnet = item.magnetUrl
        val nativeId = magnet ?: item.guid ?: item.link ?: title
        return TorrentSearchResult(
            resultId = nativeId,
            providerId = id,
            providerName = displayName,
            title = title,
            description = item.description,
            category = categoryOf(item.category),
            originalProviderCategory = item.category,
            infoHash = MagnetCheck.infoHash(magnet),
            sizeBytes = item.size,
            seeders = item.seeders,
            leechers = item.peers,
            publishedAt = item.pubDate?.let(::parseDate),
            providerVerificationClaim = null,
            sourcePageUrl = item.link,
            resultSourceType = if (magnet != null) ResultSourceType.MAGNET else ResultSourceType.TORRENT_FILE,
            rightsStatus = RightsStatus.UNKNOWN,
            availableActions = setOf(ResultAction.ADD, ResultAction.OPEN_SOURCE, ResultAction.SHARE),
            contributingSources = listOf(ContributingSource(id, displayName, item.link, nativeId)),
        )
    }

    private fun categoryOf(raw: String?): SearchCategory {
        val v = raw?.lowercase().orEmpty()
        return when {
            "movie" in v -> SearchCategory.MOVIES
            "tv" in v -> SearchCategory.TV_SERIES
            "anime" in v -> SearchCategory.ANIME
            "music" in v || "audio" in v -> SearchCategory.MUSIC
            "book" in v || "ebook" in v -> SearchCategory.BOOKS
            "software" in v -> SearchCategory.SOFTWARE
            "game" in v -> SearchCategory.GAMES
            else -> SearchCategory.OTHER
        }
    }

    private fun parseDate(raw: String): Instant? = runCatching {
        Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(raw))
    }.recoverCatching {
        Instant.parse(raw)
    }.getOrNull()

    private data class TorznabItem(
        val title: String?,
        val description: String?,
        val size: Long?,
        val pubDate: String?,
        val category: String?,
        val guid: String?,
        val link: String?,
        val magnetUrl: String?,
        val seeders: Int?,
        val peers: Int?,
    )

    private data class Caps(val searchTypes: Set<SearchType>, val categories: Map<SearchCategory, String>)

    /** Minimal capability parse: search entry points + category id/name pairs. */
    private fun parseCapabilities(xml: String): Caps {
        val parser = newParser(xml)
        val searchTypes = mutableSetOf<SearchType>()
        val categories = mutableMapOf<SearchCategory, String>()
        var inSearching = false
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "searching" -> inSearching = true
                    "search" -> if (inSearching && parser.getAttributeValue(null, "available") == "yes") {
                        val type = parser.getAttributeValue(null, "name")?.lowercase()
                        when (type) {
                            "movie" -> searchTypes += SearchType.MOVIE
                            "tv" -> searchTypes += SearchType.TV
                            "music" -> searchTypes += SearchType.MUSIC
                            "book" -> searchTypes += SearchType.BOOK
                            else -> searchTypes += SearchType.GENERAL
                        }
                    }
                    "category" -> {
                        val id = parser.getAttributeValue(null, "id")
                        val name = parser.getAttributeValue(null, "name")
                        if (id != null && name != null) {
                            val cat = categoryOf(name)
                            if (cat != SearchCategory.OTHER || name.contains("other", true)) categories[cat] = id
                        }
                    }
                }
            }
        }
        if (searchTypes.isEmpty()) searchTypes += SearchType.GENERAL
        return Caps(searchTypes, categories)
    }

    private fun parseItems(xml: String): List<TorznabItem> {
        val parser = newParser(xml)
        val items = mutableListOf<TorznabItem>()
        var item: MutableTorznab? = null
        var currentTag: String? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "item") {
                        item = MutableTorznab()
                    } else if (item != null) {
                        currentTag = parser.name
                        if (currentTag == "enclosure") {
                            item.magnetUrl = item.magnetUrl ?: parser.getAttributeValue(null, "url")
                        }
                        // torznab namespaced attributes (seeders/peers/magneturl) by local name
                        for (i in 0 until parser.attributeCount) {
                            val local = parser.getAttributeName(i)
                            val value = parser.getAttributeValue(i)
                            when (local) {
                                "magneturl" -> item.magnetUrl = value
                                "seeders" -> item.seeders = value.toIntOrNull()
                                "peers" -> item.peers = value.toIntOrNull()
                                "size" -> item.size = item.size ?: value.toLongOrNull()
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val it = item ?: continue
                    val text = parser.text
                    when (currentTag) {
                        "title" -> it.title = text
                        "description" -> it.description = text
                        "size" -> it.size = text.toLongOrNull() ?: it.size
                        "pubDate" -> it.pubDate = text
                        "category" -> it.category = text
                        "guid" -> it.guid = text
                        "link" -> it.link = text
                        "magneturl" -> it.magnetUrl = text
                        "seeders" -> it.seeders = text.toIntOrNull()
                        "peers" -> it.peers = text.toIntOrNull()
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "item") {
                    item?.let { items += it.toItem() }
                    item = null
                    currentTag = null
                }
            }
        }
        return items
    }

    private data class MutableTorznab(
        var title: String? = null,
        var description: String? = null,
        var size: Long? = null,
        var pubDate: String? = null,
        var category: String? = null,
        var guid: String? = null,
        var link: String? = null,
        var magnetUrl: String? = null,
        var seeders: Int? = null,
        var peers: Int? = null,
    ) {
        fun toItem() = TorznabItem(title, description, size, pubDate, category, guid, link, magnetUrl, seeders, peers)
    }

    private fun newParser(xml: String): XmlPullParser =
        XmlPullParserFactory.newInstance().apply { setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true) }
            .newPullParser().apply { setInput(StringReader(xml)) }

    private companion object {
        const val PAGE_SIZE = 50
    }
}