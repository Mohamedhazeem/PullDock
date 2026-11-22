package com.pulldock.data.extractor.update

import com.pulldock.domain.extractor.model.UpdateManifest
import java.security.KeyPair
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Update-manifest security tests (T124–T126, T131, T132): schema validation, signature
 * enforcement, expiration, rollback protection, cached last-known-good.
 */
class ManifestSecurityTest {

    private lateinit var keyPair: KeyPair
    private val verifier get() = SignatureVerifier(java.util.Base64.getEncoder().encodeToString(keyPair.public.encoded))
    private val now: Instant = Instant.parse("2026-11-22T12:00:00Z")

    private fun payload(publishedAt: String = "2026-11-20T00:00:00Z", expiresAt: String = "2026-11-30T00:00:00Z"): String = """
        {
          "publishedAt": "$publishedAt",
          "expiresAt": "$expiresAt",
          "recommendedAppVersion": "0.2.0",
          "approvedExtractorVersions": {"yt-dlp": ">=2026.1.1"},
          "approvedRuntimeVersions": {"python": "3.13.x"},
          "providerStatus": {"YOUTUBE": "CURRENT", "TIKTOK": "PROVIDER_UNSUPPORTED"},
          "releaseNotes": "fixture"
        }
    """.trimIndent()

    @Before
    fun setUp() {
        keyPair = UpdateChannel.testKeyPair()
    }

    private suspend fun fetchSigned(payload: String = payload(), published: Instant = Instant.parse("2026-11-20T00:00:00Z")) =
        run {
            val guard = ManifestGuard()
            val doc = UpdateChannel.buildManifestDocument(keyPair, payload, published)
            UpdateChannel.fetch(doc, now, verifier, guard) to guard
        }

    @Test
    fun `valid signed manifest is accepted and cached as last-known-good`() = runTest {
        val (result, guard) = fetchSigned()
        assertTrue(result is com.pulldock.domain.extractor.repository.UpdateManifestResult.Success)
        assertNotNull(guard.cached())
    }

    @Test
    fun `tampered payload fails signature verification (T131)`() = runTest {
        val doc = UpdateChannel.buildManifestDocument(keyPair, payload(), now)
        // Flip one byte inside the payload text (signature no longer covers it).
        val tampered = String(doc, Charsets.UTF_8).replace("fixture", "tampered!!").toByteArray()
        val guard = ManifestGuard()
        val result = UpdateChannel.fetch(tampered, now, verifier, guard)
        assertTrue(result is com.pulldock.domain.extractor.repository.UpdateManifestResult.Unavailable)
        assertNull(guard.cached())
    }

    @Test
    fun `wrong key is rejected`() = runTest {
        val otherKey = UpdateChannel.testKeyPair()
        val wrongVerifier = SignatureVerifier(java.util.Base64.getEncoder().encodeToString(otherKey.public.encoded))
        val doc = UpdateChannel.buildManifestDocument(keyPair, payload(), now)
        val guard = ManifestGuard()
        val result = UpdateChannel.fetch(doc, now, wrongVerifier, guard)
        assertTrue(result is com.pulldock.domain.extractor.repository.UpdateManifestResult.Unavailable)
    }

    @Test
    fun `expired manifest is rejected even with a valid signature (T132)`() = runTest {
        val expiredPayload = payload(publishedAt = "2026-01-01T00:00:00Z", expiresAt = "2026-02-01T00:00:00Z")
        val doc = UpdateChannel.buildManifestDocument(keyPair, expiredPayload, now)
        val guard = ManifestGuard()
        val result = UpdateChannel.fetch(doc, now, verifier, guard)
        assertTrue(result is com.pulldock.domain.extractor.repository.UpdateManifestResult.Unavailable)
        assertNull(guard.cached())
    }

    @Test
    fun `rollback is blocked when publishedAt is older than the accepted manifest (T132)`() = runTest {
        val guard = ManifestGuard()
        val newer = UpdateChannel.buildManifestDocument(
            keyPair,
            payload(publishedAt = "2026-11-21T00:00:00Z"),
            now,
        )
        val accepted = UpdateChannel.fetch(newer, now, verifier, guard)
        assertTrue(accepted is com.pulldock.domain.extractor.repository.UpdateManifestResult.Success)

        val older = UpdateChannel.buildManifestDocument(
            keyPair,
            payload(publishedAt = "2026-11-19T00:00:00Z"),
            now,
        )
        val rolledBack = UpdateChannel.fetch(older, now, verifier, guard)
        assertTrue(rolledBack is com.pulldock.domain.extractor.repository.UpdateManifestResult.Unavailable)
        // The cached manifest survives a rejected fetch (FR-060).
        assertNotNull(guard.cached())
        assertEquals("2026-11-21T00:00:00Z", guard.cached()?.publishedAt?.toString())
    }

    @Test
    fun `malformed schema is rejected without touching the cache`() = runTest {
        val guard = ManifestGuard()
        val badSchema = """
            {"schemaVersion": 99, "signature": "AAA", "payload": {"publishedAt": "$now", "expiresAt": "$now"}}
        """.trimIndent().toByteArray()
        val result = UpdateChannel.fetch(badSchema, now, verifier, guard)
        assertTrue(result is com.pulldock.domain.extractor.repository.UpdateManifestResult.Unavailable)
        assertNull(guard.cached())
    }
}
