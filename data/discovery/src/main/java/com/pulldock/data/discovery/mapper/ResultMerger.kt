package com.pulldock.data.discovery.mapper

import com.pulldock.domain.discovery.model.SortOrder
import com.pulldock.domain.discovery.model.TorrentSearchResult

/**
 * De-duplication and ranking (FR-043..FR-045). Dedupe priority: (1) exact normalized
 * infohash, (2) exact provider id, (3) conservative title + exact size. Same-infohash results
 * merge while preserving all contributing sources. Unknown metadata is never treated as zero.
 */
object ResultMerger {

    fun combineAndRank(incoming: List<TorrentSearchResult>, sort: SortOrder): List<TorrentSearchResult> {
        val merged = LinkedHashMap<String, TorrentSearchResult>()
        incoming.forEach { result -> mergeInto(merged, result) }
        return rank(merged.values, sort)
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

    private fun rank(results: Collection<TorrentSearchResult>, sort: SortOrder): List<TorrentSearchResult> =
        when (sort) {
            SortOrder.SEEDERS -> results.sortedByDescending { it.seeders ?: -1 }
            SortOrder.NEWEST -> results.sortedByDescending { it.publishedAt }
            SortOrder.LARGEST -> results.sortedByDescending { it.sizeBytes ?: -1L }
            SortOrder.SMALLEST -> results.sortedBy { it.sizeBytes ?: Long.MAX_VALUE }
            SortOrder.RELEVANCE -> results.sortedWith(
                compareByDescending<TorrentSearchResult> { completeness(it) }
                    .thenByDescending { it.seeders ?: -1 }
                    .thenByDescending { it.publishedAt },
            )
        }

    private fun completeness(r: TorrentSearchResult): Int =
        listOf(r.sizeBytes, r.seeders, r.leechers, r.publishedAt, r.infoHash, r.description)
            .count { it != null }
}