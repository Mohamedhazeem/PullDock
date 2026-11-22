package com.pulldock.data.extractor.runtime

import com.pulldock.core.model.ProviderId
import com.pulldock.domain.extractor.model.ExtractorRuntimeInfo
import com.pulldock.domain.extractor.model.RuntimeVerificationStatus
import java.time.Instant

/**
 * Bundled extractor runtime inventory (FR-053). The versions below are the pinned,
 * immutable components distributed inside the signed application release; nothing here
 * is downloaded or swapped at runtime (FR-055/FR-058, ADR-0009).
 *
 * `runtimeCompatible` is `false` until the T080 device PoC verifies the Python/JS/
 * yt-dlp combination on arm64-v8a/x86_64 and the dependency is actually bundled. Every
 * resolve attempt therefore reports the typed `RUNTIME_INCOMPATIBLE` state (FR-054)
 * instead of pretending to succeed — when the verified runtime lands, this flips by
 * validation, not by faking.
 */
object BundledRuntimeCatalog {

    const val YTDLP_VERSION = "2026.11.18"
    const val YTDLP_EJS_VERSION = "1.2.0"
    const val PYTHON_VERSION = "3.13.1"
    const val JS_RUNTIME = "bundled-quickjs"
    const val JS_RUNTIME_VERSION = "0.5.0"
    const val FFMPEG_VERSION = "7.1-pulldock-1"
    const val CHANNEL = "stable"

    /** Components as shipped; verification flips to VERIFIED after the T080 PoC. */
    fun inventory(now: Instant): List<ExtractorRuntimeInfo> = listOf(
        ExtractorRuntimeInfo(
            component = "yt-dlp",
            installedVersion = YTDLP_VERSION,
            requiredRange = ">=2026.1.1",
            runtimeCompatible = RUNTIME_VERIFIED,
            channel = CHANNEL,
            verificationStatus = if (RUNTIME_VERIFIED) RuntimeVerificationStatus.VERIFIED else RuntimeVerificationStatus.UNAVAILABLE,
            knownLimitations = limitations(),
            checkedAt = now,
            inventoryId = "yt-dlp@$YTDLP_VERSION",
        ),
        ExtractorRuntimeInfo(
            component = "python",
            installedVersion = PYTHON_VERSION,
            requiredRange = "3.13.x",
            runtimeCompatible = RUNTIME_VERIFIED,
            channel = CHANNEL,
            verificationStatus = if (RUNTIME_VERIFIED) RuntimeVerificationStatus.VERIFIED else RuntimeVerificationStatus.UNAVAILABLE,
            knownLimitations = limitations(),
            checkedAt = now,
            inventoryId = "python@$PYTHON_VERSION",
        ),
        ExtractorRuntimeInfo(
            component = "yt-dlp-ejs",
            installedVersion = YTDLP_EJS_VERSION,
            requiredRange = ">=1.0.0",
            runtimeCompatible = RUNTIME_VERIFIED,
            channel = CHANNEL,
            verificationStatus = if (RUNTIME_VERIFIED) RuntimeVerificationStatus.VERIFIED else RuntimeVerificationStatus.UNAVAILABLE,
            knownLimitations = limitations(),
            checkedAt = now,
            inventoryId = "yt-dlp-ejs@$YTDLP_EJS_VERSION",
        ),
        ExtractorRuntimeInfo(
            component = "ffmpeg",
            installedVersion = FFMPEG_VERSION,
            requiredRange = ">=7.0",
            runtimeCompatible = RUNTIME_VERIFIED,
            channel = CHANNEL,
            verificationStatus = if (RUNTIME_VERIFIED) RuntimeVerificationStatus.VERIFIED else RuntimeVerificationStatus.UNAVAILABLE,
            knownLimitations = limitations(),
            checkedAt = now,
            inventoryId = "ffmpeg@$FFMPEG_VERSION",
        ),
    )

    /** v1 provider set with URL recognition patterns (FR-014). */
    val providerPatterns: Map<ProviderId, List<Regex>> = mapOf(
        ProviderId.YOUTUBE to listOf(
            Regex("""^https?://(www\.|m\.)?youtube\.com/watch\?.*""", RegexOption.IGNORE_CASE),
            Regex("""^https?://(www\.)?youtu\.be/[^/?&]+.*""", RegexOption.IGNORE_CASE),
            Regex("""^https?://(www\.|m\.)?youtube\.com/shorts/[^/?&]+.*""", RegexOption.IGNORE_CASE),
        ),
        ProviderId.X to listOf(
            Regex("""^https?://(www\.)?(twitter|x)\.com/[^/]+/status/[^/?&]+""", RegexOption.IGNORE_CASE),
        ),
        ProviderId.INSTAGRAM to listOf(
            Regex("""^https?://(www\.)?instagram\.com/(p|reel|tv)/[^/?&]+.*""", RegexOption.IGNORE_CASE),
        ),
        ProviderId.FACEBOOK to listOf(
            Regex("""^https?://(www\.|m\.)?facebook\.com/[^/]+/videos/[^/?&]+.*""", RegexOption.IGNORE_CASE),
            Regex("""^https?://(www\.|m\.)?fb\.watch/[^/?&]+.*""", RegexOption.IGNORE_CASE),
        ),
        ProviderId.TIKTOK to listOf(
            Regex("""^https?://(www\.)?tiktok\.com/@[^/]+/video/[^/?&]+.*""", RegexOption.IGNORE_CASE),
        ),
    )

    /**
     * Honest, documented v1 limitations pending verification (D3/D4): no TLS
     * impersonation in the free runtime; protected sources require user-supplied
     * cookies. Provider failures never bypass restrictions (FR-020).
     */
    private fun limitations(): List<String> = listOf(
        "No TLS fingerprint impersonation (free runtime tier); age-gated or bot-protected sources may require user-provided cookies.",
        "Runtime compatibility pending device verification (T080).",
    )

    /** Flips to `true` when the T080 PoC verifies the bundled combination on device. */
    const val RUNTIME_VERIFIED: Boolean = false
}
