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
}