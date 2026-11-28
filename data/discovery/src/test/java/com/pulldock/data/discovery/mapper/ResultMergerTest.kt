package com.pulldock.data.discovery.mapper

import com.pulldock.domain.discovery.model.ContributingSource
import com.pulldock.domain.discovery.model.ResultSourceType
import com.pulldock.domain.discovery.model.RightsStatus
import com.pulldock.domain.discovery.model.SearchCategory
import com.pulldock.domain.discovery.model.SortOrder
import com.pulldock.domain.discovery.model.TorrentSearchResult
import org.junit.Assert.assertEquals
import org.junit.Test

class ResultMergerTest {

    private fun result(
        id: String,
        provider: String,
        infoHash: String?,
        title: String = "Title",
        size: Long? = null,
        seeders: Int? = null,
    ) = TorrentSearchResult(
        resultId = id,
        providerId = provider,
        providerName = provider,
        title = title,
        description = null,
        category = SearchCategory.MOVIES,
        originalProviderCategory = null,
        infoHash = infoHash,
        sizeBytes = size,
        seeders = seeders,
        leechers = null,
        publishedAt = null,
        providerVerificationClaim = null,
        sourcePageUrl = null,
        resultSourceType = ResultSourceType.MAGNET,
        rightsStatus = RightsStatus.UNKNOWN,
        availableActions = emptySet(),
        contributingSources = listOf(ContributingSource(provider, provider, null, id)),
    )

    @Test
    fun `same infohash merges and preserves both providers`() {
        val merged = ResultMerger.combineAndRank(
            listOf(
                result("a", "bitsearch", "ABCDEF", seeders = 5),
                result("b", "archive", "ABCDEF", seeders = 9),
            ),
            SortOrder.RELEVANCE,
        )
        assertEquals(1, merged.size)
        assertEquals(2, merged[0].contributingSources.size)
        assertEquals(9, merged[0].seeders)
    }

    @Test
    fun `different infohashes with identical title stay distinct`() {
        val merged = ResultMerger.combineAndRank(
            listOf(
                result("a", "bitsearch", "AAAA", title = "Same"),
                result("b", "bitsearch", "BBBB", title = "Same"),
            ),
            SortOrder.RELEVANCE,
        )
        assertEquals(2, merged.size)
    }

    @Test
    fun `sort by seeders descending treats unknown as unknown`() {
        val merged = ResultMerger.combineAndRank(
            listOf(
                result("a", "p", "A1", seeders = null),
                result("b", "p", "B2", seeders = 10),
                result("c", "p", "C3", seeders = 3),
            ),
            SortOrder.SEEDERS,
        )
        assertEquals(listOf("b", "c", "a"), merged.map { it.resultId })
    }

    @Test
    fun `relevance with misspelled query orders the correct title first`() {
        val merged = ResultMerger.combineAndRank(
            listOf(
                result("a", "p", "A1", title = "Microsoft Windows 11"),
                result("b", "p", "B2", title = "Samsung Galaxy S24"),
            ),
            SortOrder.RELEVANCE,
            query = "samsng",
        )
        assertEquals(listOf("b", "a"), merged.map { it.resultId })
    }

    @Test
    fun `relevance multi-word query ranks closer title above unrelated`() {
        val merged = ResultMerger.combineAndRank(
            listOf(
                result("a", "p", "A1", title = "Completely Unrelated Movie"),
                result("b", "p", "B2", title = "The Dark Knight"),
            ),
            SortOrder.RELEVANCE,
            query = "dark knight",
        )
        assertEquals("b", merged.first().resultId)
    }

    @Test
    fun `relevance blank query matches null query ordering`() {
        val results = listOf(
            result("a", "p", "A1", title = "Alpha", seeders = 1),
            result("b", "p", "B2", title = "Beta", seeders = 9),
        )
        val withNull = ResultMerger.combineAndRank(results, SortOrder.RELEVANCE, null)
        val withBlank = ResultMerger.combineAndRank(results, SortOrder.RELEVANCE, "   ")
        assertEquals(withNull.map { it.resultId }, withBlank.map { it.resultId })
    }

    @Test
    fun `non-relevance sorts ignore the query`() {
        val results = listOf(
            result("a", "p", "A1", title = "Samsung", seeders = 1),
            result("b", "p", "B2", title = "Microsoft", seeders = 9),
        )
        val merged = ResultMerger.combineAndRank(results, SortOrder.SEEDERS, query = "samsng")
        assertEquals(listOf("b", "a"), merged.map { it.resultId })
    }

    @Test
    fun `reorder only never drops results`() {
        val results = listOf(
            result("a", "p", "A1", title = "Alpha"),
            result("b", "p", "B2", title = "Beta"),
            result("c", "p", "C3", title = "Gamma"),
        )
        val merged = ResultMerger.combineAndRank(results, SortOrder.RELEVANCE, query = "alpha")
        assertEquals(results.size, merged.size)
    }

    @Test
    fun `equal fuzzy scores keep seeders tie-break order`() {
        val merged = ResultMerger.combineAndRank(
            listOf(
                result("a", "p", "A1", title = "Same", seeders = 5),
                result("b", "p", "B2", title = "Same", seeders = 9),
            ),
            SortOrder.RELEVANCE,
            query = "same",
        )
        assertEquals(listOf("b", "a"), merged.map { it.resultId })
    }

    @Test
    fun `ranking is deterministic across repeated calls`() {
        val results = listOf(
            result("a", "p", "A1", title = "Samsung Galaxy"),
            result("b", "p", "B2", title = "Microsoft Windows"),
            result("c", "p", "C3", title = "Samsung Notes"),
        )
        val first = ResultMerger.combineAndRank(results, SortOrder.RELEVANCE, "samsng")
        val second = ResultMerger.combineAndRank(results, SortOrder.RELEVANCE, "samsng")
        assertEquals(first.map { it.resultId }, second.map { it.resultId })
    }
}