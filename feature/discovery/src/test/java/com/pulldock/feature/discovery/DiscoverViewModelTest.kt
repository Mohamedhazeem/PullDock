package com.pulldock.feature.discovery

import com.pulldock.core.common.dispatchers.AppDispatchers
import com.pulldock.domain.discovery.ApiCredentialStore
import com.pulldock.domain.discovery.DiscoveryCache
import com.pulldock.domain.discovery.SearchHistoryRepository
import com.pulldock.domain.discovery.TorrentSearchRepository
import com.pulldock.domain.discovery.TorznabConfigRepository
import com.pulldock.domain.discovery.model.ProviderConfiguration
import com.pulldock.domain.discovery.model.ContributingSource
import com.pulldock.domain.discovery.model.DescriptorKind
import com.pulldock.domain.discovery.model.DownloadDescriptor
import com.pulldock.domain.discovery.model.OverallSearchState
import com.pulldock.domain.discovery.model.ProviderStatus
import com.pulldock.domain.discovery.model.ProviderState
import com.pulldock.domain.discovery.model.ResultSourceType
import com.pulldock.domain.discovery.model.RightsStatus
import com.pulldock.domain.discovery.model.SearchCategory
import com.pulldock.domain.discovery.model.SearchHistoryEntry
import com.pulldock.domain.discovery.model.SearchPageState
import com.pulldock.domain.discovery.model.TorrentResultDetails
import com.pulldock.domain.discovery.model.TorrentSearchQuery
import com.pulldock.domain.discovery.model.TorrentSearchResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoverViewModelTest {

    private val dispatchers = object : AppDispatchers {
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
    }

    private class FakeRepo(
        private val descriptor: DownloadDescriptor?,
        private val progress: List<TorrentSearchResult> = emptyList(),
    ) : TorrentSearchRepository {
        override suspend fun search(
            query: TorrentSearchQuery,
            onProgress: (ProviderStatus, List<TorrentSearchResult>) -> Unit,
        ): SearchPageState {
            if (progress.isNotEmpty()) {
                onProgress(ProviderStatus("p", "P", ProviderState.SUCCESS, progress.size), progress)
            }
            return SearchPageState(overall = OverallSearchState.RESULTS, results = progress)
        }
        override suspend fun loadMore(query: TorrentSearchQuery) = SearchPageState(overall = OverallSearchState.EMPTY)
        override suspend fun refresh(query: TorrentSearchQuery) = SearchPageState(overall = OverallSearchState.EMPTY)
        override suspend fun getDetails(result: TorrentSearchResult) = TorrentResultDetails(
            resultId = result.resultId, providerId = result.providerId, providerName = result.providerName,
            title = result.title, description = null, category = result.category, infoHash = result.infoHash,
            sizeBytes = null, seeders = null, leechers = null, publishedAt = null, sourcePageUrl = null,
            rightsStatus = RightsStatus.UNKNOWN, licence = null, downloadDescriptor = descriptor, availableActions = emptySet(),
        )
        override suspend fun resolveDownloadDescriptor(details: TorrentResultDetails, preferredFileName: String?) = descriptor
    }

    private class FakeHistory : SearchHistoryRepository {
        override val enabled = MutableStateFlow(true)
        override fun observe(): Flow<List<SearchHistoryEntry>> = emptyFlow()
        override suspend fun add(query: String, category: SearchCategory?) = Unit
        override suspend fun delete(entry: SearchHistoryEntry) = Unit
        override suspend fun clear() = Unit
        override suspend fun setEnabled(value: Boolean) = Unit
    }

    private class FakeCache : DiscoveryCache {
        override fun clear() = Unit
    }

    private class FakeTorznabConfig : TorznabConfigRepository {
        override fun observe(): Flow<List<ProviderConfiguration>> = emptyFlow()
        override suspend fun current(): List<ProviderConfiguration> = emptyList()
        override suspend fun replace(configs: List<ProviderConfiguration>) = Unit
        override suspend fun restoreDefaults() = Unit
    }

    private class FakeCredentials : ApiCredentialStore {
        override fun get(providerId: String): String? = null
        override fun set(providerId: String, key: String?) = Unit
    }

    private fun newVm(repo: TorrentSearchRepository) = DiscoverViewModel(
        repo, FakeHistory(), FakeCache(), FakeTorznabConfig(), FakeCredentials(), dispatchers,
    )

    private fun result(infoHash: String? = "h", sourceType: com.pulldock.domain.discovery.model.ResultSourceType = com.pulldock.domain.discovery.model.ResultSourceType.MAGNET) = TorrentSearchResult(
        resultId = "1", providerId = "p", providerName = "P", title = "T", description = null,
        category = SearchCategory.MOVIES, originalProviderCategory = null, infoHash = infoHash, sizeBytes = null,
        seeders = null, leechers = null, publishedAt = null, providerVerificationClaim = null, sourcePageUrl = null,
        resultSourceType = sourceType, rightsStatus = RightsStatus.UNKNOWN, availableActions = emptySet(),
        contributingSources = listOf(ContributingSource("p", "P", null, "1")),
    )

    @Test
    fun `add emits magnet source`() = runTest {
        val magnet = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"
        val vm = newVm(FakeRepo(DownloadDescriptor(DescriptorKind.MAGNET, magnet, null)))
        val emitted = mutableListOf<String>()
        backgroundScope.launch(Dispatchers.Unconfined) { vm.addSource.collect { emitted += it } }
        vm.onEvent(DiscoverUiEvent.AddToPullDock(result()))
        assertEquals(magnet, emitted.last())
    }

    @Test
    fun `add emits file uri for torrent file`() = runTest {
        val uri = "file:///tmp/x.torrent"
        val vm = newVm(FakeRepo(DownloadDescriptor(DescriptorKind.TORRENT_FILE, null, uri)))
        val emitted = mutableListOf<String>()
        backgroundScope.launch(Dispatchers.Unconfined) { vm.addSource.collect { emitted += it } }
        vm.onEvent(DiscoverUiEvent.AddToPullDock(result()))
        assertEquals(uri, emitted.last())
    }

    @Test
    fun `add with no descriptor sets message`() = runTest {
        val vm = newVm(FakeRepo(null))
        vm.onEvent(DiscoverUiEvent.AddToPullDock(result(infoHash = null, sourceType = com.pulldock.domain.discovery.model.ResultSourceType.TORRENT_FILE)))
        assertEquals("No usable torrent for this result.", vm.state.value.message)
    }

    private fun titled(id: String, title: String) = TorrentSearchResult(
        resultId = id, providerId = "p", providerName = "P", title = title, description = null,
        category = SearchCategory.MOVIES, originalProviderCategory = null, infoHash = id, sizeBytes = null,
        seeders = null, leechers = null, publishedAt = null, providerVerificationClaim = null, sourcePageUrl = null,
        resultSourceType = com.pulldock.domain.discovery.model.ResultSourceType.MAGNET,
        rightsStatus = RightsStatus.UNKNOWN, availableActions = emptySet(),
        contributingSources = listOf(ContributingSource("p", "P", null, id)),
    )

    @Test
    fun `progressive search updates ranked results`() = runTest {
        val ranked = listOf(titled("b", "Samsung Galaxy"), titled("a", "Microsoft Windows"))
        val vm = newVm(FakeRepo(null, ranked))
        vm.onEvent(DiscoverUiEvent.QueryChanged("samsng"))
        vm.onEvent(DiscoverUiEvent.Submit)
        assertEquals(listOf("b", "a"), vm.state.value.results.map { it.resultId })
    }
}