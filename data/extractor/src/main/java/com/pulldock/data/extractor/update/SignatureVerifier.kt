package com.pulldock.data.extractor.update

import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Manifest signature verification against the app-pinned public key (T125, FR-056/
 * FR-057). The key is compiled into the app (public half only — no secrets in the
 * binary); verification is `SHA256withRSA` over the canonical payload bytes. Any
 * mismatch rejects the manifest outright — no fallback to unsigned content (FR-057).
 */
class SignatureVerifier(private val pinnedPublicKeyBase64: String) {

    /** `true` only when the signature verifies over the exact canonical payload. */
    fun verify(canonicalPayload: String, signatureBase64: String, algorithm: String): Boolean = try {
        val publicKey = publicKey()
        val signature = Signature.getInstance(algorithm.ifBlank { "SHA256withRSA" })
        signature.initVerify(publicKey)
        signature.update(canonicalPayload.toByteArray(Charsets.UTF_8))
        val sigBytes = Base64.getDecoder().decode(signatureBase64)
        signature.verify(sigBytes)
    } catch (_: Exception) {
        false // malformed signature, wrong key, or bad encoding — all reject
    }

    private fun publicKey(): PublicKey {
        val decoded = Base64.getDecoder().decode(pinnedPublicKeyBase64)
        return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(decoded))
    }

    companion object {
        /**
         * Development pin. PRODUCTION MUST replace this with the release key's public
         * half at build time from a secured source (never a secret in VCS; a public
         * key is safe to commit but rotation must ship with the app release).
         */
        const val PINNED_DEVELOPMENT_PUBLIC_KEY: String =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAplaceholder-key-not-usable"
    }
}
