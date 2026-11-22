package com.pulldock.data.extractor

import com.pulldock.core.model.DownloadError
import com.pulldock.core.model.ProviderId
import com.pulldock.data.extractor.provider.InstagramProvider
import com.pulldock.data.extractor.provider.YouTubeProvider
import com.pulldock.data.extractor.runtime.BundledRuntimeCatalog
import com.pulldock.domain.extractor.model.ExtractorRuntimeInfo
import com.pulldock.domain.extractor.model.RuntimeVerificationStatus
import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Extractor runtime/core tests (T092/T093 subset): compatibility combinations, retry
 * policy bounds, provider recognition, and failure isolation. Live provider regression
 * against authorized fixtures is the device procedure (T094, open).
 */
class ExtractorCoreTest {

    private fun info(
        component: String,
        version: String,
        compatible: Boolean = true,
        status: RuntimeVerificationStatus = RuntimeVerificationStatus.VERIFIED,
    ): ExtractorRuntimeInfo = ExtractorRuntimeInfo(
        component = component,
        installedVersion = version,
        requiredRange = "",
        runtimeCompatible = compatible,
        channel = "stable",
        verificationStatus = status,
        knownLimitations = emptyList(),
        checkedAt = Instant.EPOCH,
        inventoryId = "$component@$version",
    )

    private fun verifiedInventory() = listOf(
        info("yt-dlp", BundledRuntimeCatalog.YTDLP_VERSION),
        info("yt-dlp-ejs", BundledRuntimeCatalog.YTDLP_EJS_VERSION),
        info("python", "3.13.1"),
        info("js-runtime", BundledRuntimeCatalog.JS_RUNTIME_VERSION),
        info("ffmpeg", BundledRuntimeCatalog.FFMPEG_VERSION),
        info("ffprobe", BundledRuntimeCatalog.FFMPEG_VERSION),
    )

    // ---- T092: compatible/mismatched combinations ----

    @Test
    fun `fully verified combination is compatible`() {
        val result = CompatibilityValidator.validate(verifiedInventory())
        assertTrue(result is CompatibilityValidator.Result.Compatible)
    }

    @Test
    fun `mismatched python fails the combination (yt-dlp 3_13 requirement)`() {
        val result = CompatibilityValidator.validate(
            verifiedInventory() + listOf(info("python", "3.12.0", compatible = false)),
        )
        assertTrue(result is CompatibilityValidator.Result.Incompatible)
    }

    @Test
    fun `missing js runtime fails the combination`() {
        val result = CompatibilityValidator.validate(verifiedInventory().filter { it.component != "js-runtime" })
        assertTrue(result is CompatibilityValidator.Result.Incompatible)
        val error = (result as CompatibilityValidator.Result.Incompatible).error
        assertEquals("missing component: js-runtime", (error as DownloadError.ExtractorEngine).detail)
    }

    @Test
    fun `unverified ffmpeg fails the combination`() {
        val result = CompatibilityValidator.validate(
            verifiedInventory().map {
                if (it.component == "ffmpeg") {
                    it.copy(verificationStatus = RuntimeVerificationStatus.UNAVAILABLE)
                } else {
                    it
                }
            },
        )
        assertTrue(result is CompatibilityValidator.Result.Incompatible)
    }

    @Test
    fun `bundled catalog reports honest incompatibility until the device PoC`() {
        val result = CompatibilityValidator.validateBundled()
        if (BundledRuntimeCatalog.RUNTIME_VERIFIED) {
            assertTrue(result is CompatibilityValidator.Result.Compatible)
        } else {
            assertTrue(result is CompatibilityValidator.Result.Incompatible)
        }
    }

    // ---- T090: retry policy ----

    @Test
    fun `retry policy gives bounded jittered backoff for transient errors`() {
        val policy = RetryPolicy(
            maxAttempts = 3,
            initialBackoffSeconds = 5,
            maxBackoffSeconds = 60,
            random = Random(42),
        )
        val error = DownloadError.Network(DownloadError.NetworkReason.TIMEOUT)
        val first = policy.nextBackoffSeconds(1, error)
        val second = policy.nextBackoffSeconds(2, error)
        assertNotNull(first)
        assertNotNull(second)
        assertTrue(first!! in 4..7) // 5s ±20%
        assertTrue(second!! in 8..13) // 10s ±20%
        assertNull(policy.nextBackoffSeconds(3, error)) // attempts exhausted
    }

    @Test
    fun `input and authorization failures never retry`() {
        val policy = RetryPolicy(maxAttempts = 5)
        assertNull(policy.nextBackoffSeconds(1, DownloadError.Input(DownloadError.InputReason.INVALID_URL)))
        assertNull(
            policy.nextBackoffSeconds(
                1,
                DownloadError.ExtractorEngine(DownloadError.ExtractorReason.AUTHORIZATION_REQUIRED),
            ),
        )
        assertNull(
            policy.nextBackoffSeconds(
                1,
                DownloadError.ExtractorEngine(DownloadError.ExtractorReason.RUNTIME_INCOMPATIBLE),
            ),
        )
        // Rate limiting is transient and may retry.
        assertNotNull(
            policy.nextBackoffSeconds(
                1,
                DownloadError.ExtractorEngine(DownloadError.ExtractorReason.RATE_LIMITED),
            ),
        )
    }

    // ---- T085: provider recognition ----

    @Test
    fun `provider patterns recognize the v1 set and reject unknown hosts`() {
        val expected = listOf(
            "https://www.youtube.com/watch?v=abc",
            "https://youtu.be/abc",
            "https://youtube.com/shorts/abc",
            "https://www.instagram.com/p/abc123/",
        )
        expected.forEach { url ->
            assertTrue("expected recognition for $url", com.pulldock.data.extractor.provider.UrlRecognizer.recognize(url) != null)
        }
        assertFalse(YouTubeProvider().recognizes("https://example.com/video"))
        assertNull(
            com.pulldock.data.extractor.provider.UrlRecognizer.recognize("https://example.com/video"),
        )
        // Non-media provider set: e.g. Vimeo is not in v1 (surfaced unsupported).
        assertNull(com.pulldock.data.extractor.provider.UrlRecognizer.recognize("https://vimeo.com/123456"))
    }

    // ---- T093: provider failure isolation ----

    @Test
    fun `a failing provider only affects its own health`() = runTest {
        val registry = ProviderRegistry()
        val failed = mutableListOf<ProviderId>()
        registry.onProviderFailure = { failed.add(it) }

        registry.isolated(ProviderId.YOUTUBE) { error("provider changed") }
        // The other provider stays unaffected and usable.
        val other = registry.isolated(ProviderId.INSTAGRAM) { "ok" }
        assertTrue(other.isSuccess)
        assertEquals(listOf(ProviderId.YOUTUBE), failed)
    }

    // ---- T089: provider error mapping ----

    @Test
    fun `provider markers map to typed reasons with redacted urls`() {
        assertEquals(
            DownloadError.ExtractorReason.RATE_LIMITED,
            (ErrorMapper.fromMarker("HTTP 429 rate limited") as DownloadError.ExtractorEngine).reason,
        )
        assertEquals(
            DownloadError.ExtractorReason.AUTHORIZATION_REQUIRED,
            (ErrorMapper.fromMarker("sign in required for private content") as DownloadError.ExtractorEngine).reason,
        )
        val error = ErrorMapper.fromMarker("failed at https://provider.example/x?token=1")
        val detail = (error as DownloadError.ExtractorEngine).detail.orEmpty()
        assertFalse(detail.contains("http"))
    }
}
