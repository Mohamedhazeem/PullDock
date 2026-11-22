package com.pulldock.data.extractor

import com.pulldock.core.model.DownloadError
import kotlin.random.Random

/**
 * Bounded retry with exponential backoff and ±20% jitter (T090, FR-018).
 *
 * Retryable failures are network/provider-transient classes only; input, authorization,
 * and runtime-incompatibility failures never retry (fail fast, no hot loops).
 * Backoff is capped by `maxBackoffSeconds`; `maxAttempts` is a hard bound (US4
 * scenario 4: no infinite retry loops).
 */
class RetryPolicy(
    private val maxAttempts: Int = 3,
    private val initialBackoffSeconds: Long = 5,
    private val maxBackoffSeconds: Long = 900,
    private val multiplier: Double = 2.0,
    private val jitterFraction: Double = 0.2,
    private val random: Random = Random.Default,
) {

    init {
        require(maxAttempts >= 1) { "maxAttempts must be >= 1" }
        require(initialBackoffSeconds > 0) { "initialBackoffSeconds must be positive" }
        require(maxBackoffSeconds >= initialBackoffSeconds) { "maxBackoff must not be below initial" }
        require(multiplier >= 1.0) { "multiplier must not shrink backoff" }
        require(jitterFraction in 0.0..1.0) { "jitterFraction must be within [0,1]" }
    }

    /** `true` when the error class may retry (transient only). */
    fun isRetryable(error: DownloadError): Boolean = when (error) {
        is DownloadError.Network -> true
        is DownloadError.ExtractorEngine -> error.reason in setOf(
            DownloadError.ExtractorReason.RATE_LIMITED,
            DownloadError.ExtractorReason.PROVIDER_CHANGED,
        )
        else -> false
    }

    /**
     * Whether to retry after [attempt] (1-based) failed with [error]; returns the
     * jittered backoff in seconds, or null when retries are exhausted/not allowed.
     */
    fun nextBackoffSeconds(attempt: Int, error: DownloadError): Long? {
        if (attempt >= maxAttempts || !isRetryable(error)) return null
        val base = initialBackoffSeconds * Math.pow(multiplier, (attempt - 1).toDouble())
        val capped = base.coerceAtMost(maxBackoffSeconds.toDouble())
        val jitter = capped * jitterFraction
        val low = (capped - jitter).coerceAtLeast(0.0)
        val high = capped + jitter
        return (low + random.nextDouble() * (high - low)).toLong().coerceAtLeast(1)
    }
}
