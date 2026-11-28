package com.pulldock.data.discovery.mapper

import com.pulldock.domain.discovery.model.SortOrder
import com.pulldock.domain.discovery.model.TorrentSearchResult
import com.pulldock.domain.discovery.search.FuzzyMatcher

/**
 * De-duplication and ranking (FR-043..FR-045). Dedupe priority: (1) exact normalized
 * infohash, (2) exact provider id, (3) conservative title + exact size. Same-infohash results
 * merge while preserving all contributing sources. Unknown metadata is never treated as zero.
 */
object ResultMerger {

    fun combineAndRank(
        incoming: List<TorrentSearchResult>,
        sort: SortOrder,
        query: String? = null,
    ): List<TorrentSearchResult> {
        val merged = LinkedHashMap<String, TorrentSearchResult>()
        incoming.forEach { result -> mergeInto(merged, result) }
        return rank(merged.values, sort, query)
    }

    private fun mergeInto(map: LinkedHashMap<String, TorrentSearchResult>, result: TorrentSearchResult) {
        val key = dedupeKey(result)
        val existing = map[key]
        if (existing == null) {
            map[key] = result
        } else {
            map[key] = existing.copy(
                contributingSources = (existing.contributingSources + result.contributingSources)
                    .distinctBy { it.providerId + "|" + it.nativeId },
                sizeBytes = existing.sizeBytes ?: result.sizeBytes,
                seeders = maxSeeders(existing.seeders, result.seeders),
                leechers = maxLeechers(existing.leechers, result.leechers),
                publishedAt = existing.publishedAt ?: result.publishedAt,
                infoHash = existing.infoHash ?: result.infoHash,
                description = existing.description ?: result.description,
            )
        }
    }

    private fun dedupeKey(r: TorrentSearchResult): String {
        val hash = r.infoHash
        return if (!hash.isNullOrBlank()) "btih:${hash.lowercase()}" else "pid:${r.providerId}:${r.resultId}"
    }

    private fun maxSeeders(a: Int?, b: Int?): Int? = when {
        a == null -> b
        b == null -> a
        else -> maxOf(a, b)
    }

    private fun maxLeechers(a: Int?, b: Int?): Int? = when {
        a == null -> b
        b == null -> a
        else -> maxOf(a, b)
    }

    private fun rank(
        results: Collection<TorrentSearchResult>,
        sort: SortOrder,
        query: String?,
    ): List<TorrentSearchResult> =
        when (sort) {
            SortOrder.SEEDERS -> results.sortedByDescending { it.seeders ?: -1 }
            SortOrder.NEWEST -> results.sortedByDescending { it.publishedAt }
            SortOrder.LARGEST -> results.sortedByDescending { it.sizeBytes ?: -1L }
            SortOrder.SMALLEST -> results.sortedBy { it.sizeBytes ?: Long.MAX_VALUE }
            SortOrder.RELEVANCE -> rankByRelevance(results, query)
        }

    /**
     * RELEVANCE ordering. When [query] is non-blank, typo-tolerant fuzzy similarity to the title
     * is the primary signal (precomputed once per result, never inside the comparator), with the
     * existing completeness/seeders/publishedAt ordering preserved as the stable tie-breaker.
     * A null/blank query keeps the original ordering byte-for-byte.
     */
    private fun rankByRelevance(
        results: Collection<TorrentSearchResult>,
        query: String?,
    ): List<TorrentSearchResult> {
        if (query.isNullOrBlank()) {
            return results.sortedWith(
                compareByDescending<TorrentSearchResult> { completeness(it) }
                    .thenByDescending { it.seeders ?: -1 }
                    .thenByDescending { it.publishedAt },
            )
        }

        val scored = results.map { result -> Scored(result, FuzzyMatcher.score(query, result.title)) }
        return scored.sortedWith(
            compareByDescending<Scored> { it.score }
                .thenByDescending { completeness(it.result) }
                .thenByDescending { it.result.seeders ?: -1 }
                .thenByDescending { it.result.publishedAt },
        ).map { it.result }
    }

    private data class Scored(val result: TorrentSearchResult, val score: Double)

    private fun completeness(r: TorrentSearchResult): Int =
        listOf(r.sizeBytes, r.seeders, r.leechers, r.publishedAt, r.infoHash, r.description)
            .count { it != null }
}