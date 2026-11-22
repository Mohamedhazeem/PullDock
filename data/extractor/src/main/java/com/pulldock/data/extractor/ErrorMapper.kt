package com.pulldock.data.extractor

import com.pulldock.core.model.DownloadError

/**
 * Extractor error mapping (T089, FR-017): provider failures map to typed, actionable
 * errors; raw provider output never surfaces (FR-049). URLs are redacted in details.
 */
object ErrorMapper {

    private const val MAX_DETAIL = 200

    /** HTTP-ish status markers mapped to provider reasons (from sanitized diagnostics). */
    fun reasonFromMarker(marker: String): DownloadError.ExtractorReason = when {
        marker.contains("429") || marker.contains("rate limit", ignoreCase = true) ->
            DownloadError.ExtractorReason.RATE_LIMITED
        marker.contains("sign in", ignoreCase = true) || marker.contains("login", ignoreCase = true) ||
            marker.contains("private", ignoreCase = true) || marker.contains("cookies", ignoreCase = true) ->
            DownloadError.ExtractorReason.AUTHORIZATION_REQUIRED
        marker.contains("unavailable", ignoreCase = true) || marker.contains("removed", ignoreCase = true) ->
            DownloadError.ExtractorReason.CONTENT_REMOVED
        marker.contains("expired", ignoreCase = true) -> DownloadError.ExtractorReason.EXPIRED_URL
        marker.contains("unsupported", ignoreCase = true) || marker.contains("no video", ignoreCase = true) ->
            DownloadError.ExtractorReason.UNSUPPORTED
        else -> DownloadError.ExtractorReason.PROVIDER_CHANGED
    }

    fun fromThrowable(throwable: Throwable): DownloadError = when (throwable) {
        is kotlinx.coroutines.CancellationException -> throw throwable
        else -> DownloadError.ExtractorEngine(
            reason = DownloadError.ExtractorReason.PROVIDER_CHANGED,
            detail = sanitize(throwable.message),
        )
    }

    fun fromMarker(marker: String): DownloadError = DownloadError.ExtractorEngine(
        reason = reasonFromMarker(marker),
        detail = sanitize(marker),
    )

    fun sanitize(text: String?): String? =
        text?.take(MAX_DETAIL)?.replace(Regex("https?://\\S+"), "<url>")
}
