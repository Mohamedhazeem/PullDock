package com.pulldock.data.discovery

import com.pulldock.core.common.dispatchers.AppDispatchers
import com.pulldock.data.discovery.cache.SearchResultCache
import com.pulldock.domain.discovery.ProviderRegistry
import com.pulldock.domain.discovery.ProviderPage
import com.pulldock.domain.discovery.ProviderPageResult
import com.pulldock.domain.discovery.TorrentSearchProvider
import com.pulldock.domain.discovery.error.DiscoveryError
import com.pulldock.domain.discovery.model.ContributingSource
import com.pulldock.domain.discovery.model.OverallSearchState
import com.pulldock.domain.discovery.model.ProviderCapabilities
import com.pulldock.domain.discovery.model.ProviderState
import com.pulldock.domain.discovery.model.ResultSourceType
import com.pulldock.domain.discovery.model.RightsStatus
import com.pulldock.domain.discovery.model.SearchCategory
import com.pulldock.domain.discovery.model.SearchType
import com.pulldock.domain.discovery.model.SearchFilters
import com.pulldock.domain.discovery.model.TorrentSearchQuery
import com.pulldock.domain.discovery.model.TorrentSearchResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoveryRepositoryImplTest {

    private val dispatchers = object : AppDispatchers {
        override val io: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
        override val default: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
        override val main: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
    }

    private fun result(id: String, provider: String) = TorrentSearchResult(
        resultId = id, providerId = provider, providerName = provider, title = "T$id",
        description = null, category = SearchCategory.MOVIES, originalProviderCategory = null,
        infoHash = id, sizeBytes = null, seeders = 1, leechers = null, publishedAt = null,
        providerVerificationClaim = null, sourcePageUrl = null, resultSourceType = ResultSourceType.MAGNET,
        rightsStatus = RightsStatus.UNKNOWN, availableActions = emptySet(),
        contributingSources = listOf(ContributingSource(provider, provider, null, id)),
    )

    private class FakeProvider(
        override val id: String,
        private val produce: suspend () -> ProviderPageResult,
    ) : TorrentSearchProvider {
        override val displayName: String = id
        override suspend fun capabilities(): ProviderCapabilities =
            ProviderCapabilities(id, setOf(SearchType.GENERAL), emptyMap(), supportsPagination = false, requiresAuth = false)
        override suspend fun search(query: TorrentSearchQuery, page: ProviderPage): ProviderPageResult = produce()
        override suspend fun details(nativeId: String) = null
        override suspend fun downloadDescriptor(nativeId: String, preferredFileName: String?) = null
    }

    private class FakeRegistry(private val providers: List<TorrentSearchProvider>) : ProviderRegistry {
        override suspend fun enabled() = providers
        override suspend fun supporting(category: SearchCategory) = providers
    }

    private fun repo(providers: List<TorrentSearchProvider>): DiscoveryRepositoryImpl =
        DiscoveryRepositoryImpl(FakeRegistry(providers), SearchResultCache(), dispatchers)

    private val query = TorrentSearchQuery("q", SearchFilters())

    @Test
    fun `one provider fails but others succeed yields PARTIAL`() = runTest {
        val good = FakeProvider("good") { ProviderPageResult(listOf(result("1", "good"), result("2", "good")), false, null) }
        val bad = FakeProvider("bad") { throw DiscoveryError.RateLimited() }
        val state = repo(listOf(good, bad)).search(query)
        assertEquals(OverallSearchState.PARTIAL, state.overall)
        assertEquals(2, state.results.size)
        assertEquals(ProviderState.RATE_LIMITED, state.perProvider.first { it.providerId == "bad" }.state)
    }

    @Test
    fun `all providers fail yields ALL_FAILED`() = runTest {
        val bad1 = FakeProvider("a") { throw DiscoveryError.Timeout("a") }
        val bad2 = FakeProvider("b") { throw DiscoveryError.Provider(DiscoveryError.ProviderReason.SERVER_ERROR) }
        val state = repo(listOf(bad1, bad2)).search(query)
        assertEquals(OverallSearchState.ALL_FAILED, state.overall)
        assertEquals(0, state.results.size)
    }

    @Test
    fun `all providers empty yields EMPTY`() = runTest {
        val empty = FakeProvider("a") { ProviderPageResult(emptyList(), false, null) }
        val state = repo(listOf(empty)).search(query)
        assertEquals(OverallSearchState.EMPTY, state.overall)
    }

    private inner class PagedProvider(override val id: String) : TorrentSearchProvider {
        override val displayName = id
        override suspend fun capabilities() = ProviderCapabilities(id, setOf(SearchType.GENERAL), emptyMap(), true, false)
        override suspend fun search(query: TorrentSearchQuery, page: ProviderPage): ProviderPageResult =
            when (page.number) {
                1 -> ProviderPageResult(listOf(result("1", id), result("2", id)), hasMore = true, nextPage = ProviderPage(2, null))
                else -> ProviderPageResult(listOf(result("3", id)), hasMore = false, nextPage = null)
            }
        override suspend fun details(nativeId: String) = null
        override suspend fun downloadDescriptor(nativeId: String, preferredFileName: String?) = null
    }

    @Test
    fun `load more advances provider and appends without duplicates`() = runTest {
        val repo = repo(listOf(PagedProvider("p")))
        val first = repo.search(query)
        assertEquals(2, first.results.size)
        val more = repo.loadMore(query)
        assertEquals(3, more.results.size)
        assertEquals(3, more.results.distinctBy { it.resultId }.size)
    }

    @Test
    fun `offline provider yields OFFLINE`() = runTest {
        val offline = FakeProvider("a") { throw DiscoveryError.Network(DiscoveryError.NetworkReason.OFFLINE) }
        val state = repo(listOf(offline)).search(query)
        assertEquals(OverallSearchState.OFFLINE, state.overall)
        assertEquals(ProviderState.FAILED, state.perProvider[0].state)
    }

    @Test
    fun `provider selection restricts queried providers`() = runTest {
        val a = FakeProvider("a") { ProviderPageResult(listOf(result("1", "a")), false, null) }
        val b = FakeProvider("b") { ProviderPageResult(listOf(result("2", "b")), false, null) }
        val filtered = query.copy(filters = SearchFilters(providerIds = setOf("a")))
        val state = repo(listOf(a, b)).search(filtered)
        assertEquals(1, state.results.size)
        assertEquals("a", state.results[0].providerId)
    }

    @Test
    fun `category filter keeps only the selected category`() = runTest {
        fun r(id: String, cat: SearchCategory) = TorrentSearchResult(
            resultId = id, providerId = "p", providerName = "p", title = "T$id", description = null,
            category = cat, originalProviderCategory = null, infoHash = id, sizeBytes = null, seeders = 1,
            leechers = null, publishedAt = null, providerVerificationClaim = null, sourcePageUrl = null,
            resultSourceType = ResultSourceType.MAGNET, rightsStatus = RightsStatus.UNKNOWN,
            availableActions = emptySet(), contributingSources = emptyList(),
        )
        val p = FakeProvider("p") { ProviderPageResult(listOf(r("1", SearchCategory.MOVIES), r("2", SearchCategory.MUSIC)), false, null) }
        val state = repo(listOf(p)).search(query.copy(filters = SearchFilters(category = SearchCategory.MOVIES)))
        assertEquals(1, state.results.size)
        assertEquals(SearchCategory.MOVIES, state.results[0].category)
    }

    private fun titled(id: String, provider: String, title: String) = TorrentSearchResult(
        resultId = id, providerId = provider, providerName = provider, title = title,
        description = null, category = SearchCategory.MOVIES, originalProviderCategory = null,
        infoHash = id, sizeBytes = null, seeders = 1, leechers = null, publishedAt = null,
        providerVerificationClaim = null, sourcePageUrl = null, resultSourceType = ResultSourceType.MAGNET,
        rightsStatus = RightsStatus.UNKNOWN, availableActions = emptySet(),
        contributingSources = listOf(ContributingSource(provider, provider, null, id)),
    )

    @Test
    fun `search emits ranked progressive partials`() = runTest {
        val provider = FakeProvider("p") {
            ProviderPageResult(
                listOf(
                    titled("a", "p", "Microsoft Windows 11"),
                    titled("b", "p", "Samsung Galaxy S24"),
                ),
                false,
                null,
            )
        }
        val emissions = mutableListOf<List<TorrentSearchResult>>()
        val q = TorrentSearchQuery("samsng", SearchFilters())
        val state = repo(listOf(provider)).search(q) { _, partial -> emissions += partial }
        assertEquals(1, emissions.size)
        assertEquals(listOf("b", "a"), emissions.last().map { it.resultId })
        assertEquals(emissions.last().map { it.resultId }, state.results.map { it.resultId })
    }
}