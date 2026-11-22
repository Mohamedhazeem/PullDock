package com.pulldock.data.torrent

import com.pulldock.domain.torrent.model.ResumeBlob

/**
 * Resume-blob handling (T070, FR-010). Blobs are opaque engine bytes with an explicit
 * version so future engine upgrades can migrate or expire old blobs without parsing
 * them (data-model.md §4.4). Validation checks shape only: non-empty, version known.
 */
object ResumeData {

    /** Blob version produced by libtorrent4j 2.1.0-39. */
    const val CURRENT_VERSION = 1

    /** Upper bound for stored resume data (bounded storage, malformed-input guard). */
    private const val MAX_BLOB_BYTES = 8 * 1024 * 1024

    /** `true` when the blob can be handed to the engine for restore. */
    fun isUsable(blob: ResumeBlob?): Boolean =
        blob != null && blob.version == CURRENT_VERSION && blob.bytes.isNotEmpty() && blob.bytes.size <= MAX_BLOB_BYTES
}
