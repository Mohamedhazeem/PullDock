package com.pulldock.domain.discovery.search

/**
 * Typo-tolerant string similarity used to re-rank already-fetched discovery results
 * (reorder-only, RELEVANCE sort). Pure Kotlin, no Android/domain dependencies, total and
 * exception-free: [score] never throws on any non-null input.
 *
 * Similarity is `max(wholeStringScore, tokenSetScore)` in `[0.0, 1.0]`, where the whole-string
 * score is `1 - damerauLevenshtein / max(len)` and the token-set score is the fraction of query
 * tokens covered by the target (word order and extra words are tolerated). Normalization is
 * locale-invariant `lowercase()` + `trim()` + collapsed internal whitespace.
 */
object FuzzyMatcher {

    /** Normalized query split into distinct tokens, computed once per ranking pass. */
    data class Prepared(val normalized: String, val tokens: List<String>)

    fun prepare(query: String): Prepared = Prepared(normalize(query), tokensOf(normalize(query)))

    /** Similarity of [query] to [target]; delegates to [prepare]. */
    fun score(query: String, target: String): Double = score(prepare(query), target)

    /** Similarity of a prepared [query] to [target] in `[0.0, 1.0]`; `0.0` when either is blank. */
    fun score(query: Prepared, target: String): Double {
        val b = normalize(target)
        if (query.normalized.isEmpty() || b.isEmpty()) return 0.0
        val whole = charSim(query.normalized, b)
        val token = tokenScore(query.tokens, tokensOf(b))
        return maxOf(whole, token)
    }

    private fun normalize(input: String): String =
        input.trim().lowercase().replace(WHITESPACE, " ")

    private fun tokensOf(normalized: String): List<String> =
        if (normalized.isEmpty()) emptyList()
        else normalized.split(" ").filter { it.isNotEmpty() }.distinct()

    private fun charSim(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val longest = maxOf(a.length, b.length)
        return 1.0 - damerauLevenshtein(a, b).toDouble() / longest.toDouble()
    }

    /**
     * Fraction of query tokens covered by the target. Tokens shorter than [MIN_TOKEN_LEN] count
     * only on exact equality (so a 1-char query cannot match everything); longer tokens use the
     * best per-token char similarity.
     */
    private fun tokenScore(queryTokens: List<String>, targetTokens: List<String>): Double {
        if (queryTokens.isEmpty() || targetTokens.isEmpty()) return 0.0
        var sum = 0.0
        for (token in queryTokens) {
            val best = if (token.length < MIN_TOKEN_LEN) {
                if (targetTokens.contains(token)) 1.0 else 0.0
            } else {
                targetTokens.maxOf { charSim(token, it) }
            }
            sum += best
        }
        return sum / queryTokens.size
    }

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

    private const val MIN_TOKEN_LEN = 2
    private val WHITESPACE = Regex("\\s+")
}