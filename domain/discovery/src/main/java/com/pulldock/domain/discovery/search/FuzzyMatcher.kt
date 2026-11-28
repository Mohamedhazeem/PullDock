package com.pulldock.domain.discovery.search

/**
 * Typo-tolerant string similarity used to re-rank already-fetched discovery results
 * (reorder-only, RELEVANCE sort). Pure Kotlin, no Android/domain dependencies, total and
 * exception-free: [score] never throws on any non-null input.
 *
 * Similarity is `1 - damerauLevenshtein(normalize(a), normalize(b)) / max(len)`, in `[0.0, 1.0]`.
 * Normalization is locale-invariant `lowercase()` + `trim()` + collapsed internal whitespace.
 */
object FuzzyMatcher {

    /** Similarity of [query] to [target] in `[0.0, 1.0]`; `0.0` when either side is blank. */
    fun score(query: String, target: String): Double {
        val a = normalize(query)
        val b = normalize(target)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val longest = maxOf(a.length, b.length)
        val distance = damerauLevenshtein(a, b)
        return 1.0 - distance.toDouble() / longest.toDouble()
    }

    private fun normalize(input: String): String =
        input.trim().lowercase().replace(WHITESPACE, " ")

    /**
     * Optimal-string-alignment Damerau-Levenshtein distance (adds adjacent transposition).
     * Uses two rolling rows, so memory is O(min(n, m)).
     */
    private fun damerauLevenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previousPrevious = IntArray(b.length + 1)
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var value = minOf(
                    current[j - 1] + 1,
                    previous[j] + 1,
                    previous[j - 1] + cost,
                )
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    value = minOf(value, previousPrevious[j - 2] + 1)
                }
                current[j] = value
            }

            val recycle = previousPrevious
            previousPrevious = previous
            previous = current
            current = recycle
        }

        return previous[b.length]
    }

    private val WHITESPACE = Regex("\\s+")
}