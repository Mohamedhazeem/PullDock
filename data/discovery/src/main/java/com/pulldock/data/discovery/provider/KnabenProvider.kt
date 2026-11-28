package com.pulldock.data.discovery.provider

import com.pulldock.data.discovery.http.BoundedResponse
import com.pulldock.data.discovery.http.HttpResult
import com.pulldock.data.discovery.mapper.MagnetCheck
import com.pulldock.data.discovery.provider.dto.KnabenHit
import com.pulldock.data.discovery.provider.dto.KnabenResponse
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
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

/**
 * Knaben Database v2 adapter (multi-source, no key). Enforces the published ~1 req / 2 s limit.
 * Adult is excluded (no `&xxx`); the client category filter applies on top.
 */
class KnabenProvider @Inject constructor(
    private val client: HttpClient,
    private val credentials: ApiCredentialStore,
) : TorrentSearchProvider {

    override val id: String = "knaben"
    override val displayName: String = "Knaben"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val lastRequest = AtomicLong(0L)

    override suspend fun capabilities(): ProviderCapabilities = ProviderCapabilities(
        providerId = id,
        searchTypes = setOf(SearchType.GENERAL, SearchType.MOVIE, SearchType.TV, SearchType.MUSIC, SearchType.BOOK),
        categories = SearchCategory.entries.associateWith { "knaben" },
        supportsPagination = true,
        requiresAuth = false,
    )

    override suspend fun search(query: TorrentSearchQuery, page: ProviderPage): ProviderPageResult {
        throttle()
        val url = "$BASE/v2/search?q=${URLEncoder.encode(query.text, "UTF-8")}" +
            "&s=$PAGE_SIZE&f=${(page.number - 1) * PAGE_SIZE}&o=seeders&d=desc"
        val body = when (val r = BoundedResponse.fetch(client, url)) {
            is HttpResult.Failure -> throw r.error
            is HttpResult.Ok -> r.body
        }
        val parsed = runCatching { json.decodeFromString(KnabenResponse.serializer(), body) }
            .getOrElse { throw DiscoveryError.Parse("knaben search") }
        val results = parsed.hits.mapNotNull(::toResult)
        return ProviderPageResult(results, results.size == PAGE_SIZE, if (results.size == PAGE_SIZE) page.copy(number = page.number + 1) else null)
    }

    override suspend fun details(nativeId: String): TorrentResultDetails? = null

    override suspend fun downloadDescriptor(nativeId: String, preferredFileName: String?): DownloadDescriptor? {
        val magnet = nativeId
        return if (MagnetCheck.isValid(magnet)) DownloadDescriptor(DescriptorKind.MAGNET, magnet, null) else null
    }

    private suspend fun throttle() {
        val now = System.currentTimeMillis()
        val wait = MIN_INTERVAL_MS - (now - lastRequest.get())
        if (wait > 0) delay(wait)
        lastRequest.set(System.currentTimeMillis())
    }

    private fun toResult(hit: KnabenHit): TorrentSearchResult? {
        val title = hit.name?.takeIf { it.isNotBlank() } ?: return null
        val nativeId = hit.magnetUrl ?: hit.hash ?: title
        return TorrentSearchResult(
            resultId = nativeId,
            providerId = id,
            providerName = displayName,
            title = title,
            description = null,
            category = SearchCategory.OTHER,
            originalProviderCategory = hit.tracker,
            infoHash = hit.hash,
            sizeBytes = hit.bytes,
            seeders = hit.seeders,
            leechers = hit.peers,
            publishedAt = null,
            providerVerificationClaim = hit.verified,
            sourcePageUrl = null,
            resultSourceType = if (hit.magnetUrl != null) ResultSourceType.MAGNET else ResultSourceType.TORRENT_FILE,
            rightsStatus = RightsStatus.UNKNOWN,
            availableActions = setOf(ResultAction.ADD, ResultAction.SHARE),
            contributingSources = listOf(ContributingSource(id, displayName, null, nativeId)),
        )
    }

    private companion object {
        const val BASE = "https://api.knaben.org"
        const val PAGE_SIZE = 100
        const val MIN_INTERVAL_MS = 2_000L
    }
}