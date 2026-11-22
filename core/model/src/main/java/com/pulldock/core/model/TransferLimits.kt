package com.pulldock.core.model

/**
 * Per-task transfer limits overriding the global policy (data-model.md §1.4). Shared
 * by both engine contracts (torrent rate/connection limits and extractor bandwidth
 * policy), so it lives in the shared-primitives ring.
 */
data class TransferLimits(
    val downloadRateBytesPerSec: Long?,
    val uploadRateBytesPerSec: Long?,
    val maxConnections: Int?,
) {
    companion object {
        /** No overrides: global policy applies. */
        val NONE = TransferLimits(downloadRateBytesPerSec = null, uploadRateBytesPerSec = null, maxConnections = null)
    }
}
