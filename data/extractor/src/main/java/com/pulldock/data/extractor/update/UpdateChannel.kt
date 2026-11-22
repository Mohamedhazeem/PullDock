package com.pulldock.data.extractor.update

import com.pulldock.core.model.DownloadError
import com.pulldock.domain.extractor.repository.UpdateManifestRepository
import com.pulldock.domain.extractor.repository.UpdateManifestResult
import com.pulldock.domain.extractor.model.UpdateManifest
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

/**
 * Update-manifest pipeline wiring (T124–T126): parse → verify signature → admit
 * (expiration + rollback) → cache. Offline/failed checks return the cached manifest
 * as [UpdateManifestResult.Cached]; a failed check NEVER touches the existing
 * installation or user data (FR-060).
 *
 * Also provides the canonical manifest signing helper used by tests and the release
 * tooling — the PRIVATE key only ever exists outside the app.
 */
object UpdateChannel {

    suspend fun fetch(
        bytes: ByteArray,
        now: java.time.Instant,
        verifier: SignatureVerifier,
        guard: ManifestGuard,
    ): UpdateManifestResult {
        val parsed = ManifestParser.parse(bytes, now)
        val payload = when (parsed) {
            is ManifestParser.ParseResult.Invalid -> return UpdateManifestResult.Unavailable(
                DownloadError.Persistence(DownloadError.PersistenceReason.RECOVERY_DATA_INVALID, parsed.reason),
            )
            is ManifestParser.ParseResult.Parsed -> parsed
        }
        val verified = verifier.verify(payload.canonicalPayload, payload.manifest.signature, payload.manifest.algorithm)
        if (!verified) {
            return UpdateManifestResult.Unavailable(
                DownloadError.Persistence(DownloadError.PersistenceReason.RECOVERY_DATA_INVALID, "signature verification failed"),
            )
        }
        return when (val admission = guard.admit(payload.manifest, now)) {
            is ManifestGuard.Admission.Accepted -> UpdateManifestResult.Success(admission.manifest)
            is ManifestGuard.Admission.Rejected -> UpdateManifestResult.Unavailable(
                DownloadError.Persistence(DownloadError.PersistenceReason.RECOVERY_DATA_INVALID, admission.reason),
            )
        }
    }

    /** Test/release tooling: signs a canonical payload with the given private key. */
    fun sign(privateKey: java.security.PrivateKey, canonicalPayload: String): String {
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(canonicalPayload.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(signature.sign())
    }

    /** Test keypair generator (2048-bit RSA). */
    fun testKeyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    /** Builds a canonical manifest document for tests (payload + signature members). */
    fun buildManifestDocument(
        keyPair: KeyPair,
        payloadJson: String,
        now: java.time.Instant,
    ): ByteArray {
        val payload = org.json.JSONObject(payloadJson)
        val canonical = ManifestParser.canonicalPayload(payload)
        val signature = sign(keyPair.private, canonical)
        return org.json.JSONObject()
            .put("schemaVersion", 1)
            .put("signature", signature)
            .put("algorithm", "SHA256withRSA")
            .put("payload", payload)
            .toString().toByteArray()
    }
}
