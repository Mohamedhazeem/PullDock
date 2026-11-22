package com.pulldock.data.extractor.update

import com.pulldock.domain.extractor.model.UpdateManifest
import java.time.Instant

/**
 * Manifest admission guard (T126, FR-056/FR-057/FR-060): expiration, rollback
 * protection, and the cached last-known-good policy. A manifest that fails any check
 * leaves the existing installation and cached metadata intact (FR-060).
 */
class ManifestGuard {

    /** Highest publishedAt ever accepted; persisted with the cache in production. */
    private val newestAcceptedPublishedAt = java.util.concurrent.atomic.AtomicReference<Instant?>(null)

    private var lastKnownGood: UpdateManifest? = null

    sealed interface Admission {
        /** Accepted; becomes the new last-known-good. */
        data class Accepted(val manifest: UpdateManifest) : Admission

        /** Rejected; the cached manifest (if any) stays authoritative. */
        data class Rejected(val reason: String) : Admission
    }

    /**
     * Admits or rejects a freshly parsed+verified manifest. Rollback protection: a
     * manifest published BEFORE the newest accepted one is rejected even if validly
     * signed (downgrade attack defense).
     */
    fun admit(manifest: UpdateManifest, now: Instant): Admission {
        if (!manifest.expiresAt.isAfter(now)) return Admission.Rejected("expired")
        val newest = newestAcceptedPublishedAt.get()
        if (newest != null && manifest.publishedAt.isBefore(newest)) {
            return Admission.Rejected("rollback blocked: publishedAt older than the accepted manifest")
        }
        newestAcceptedPublishedAt.set(manifest.publishedAt)
        lastKnownGood = manifest
        return Admission.Accepted(manifest)
    }

    /** The cached last-known-good manifest; null until one is accepted. */
    fun cached(): UpdateManifest? = lastKnownGood

    /** Restores the cache from persistence (called on startup). */
    fun restoreCache(manifest: UpdateManifest?) {
        lastKnownGood = manifest
        manifest?.let { newestAcceptedPublishedAt.updateAndGet { prev -> maxOf(prev ?: it.publishedAt, it.publishedAt) } }
    }
}
