package com.pulldock.domain.download.model

import com.pulldock.core.model.DownloadType
import java.time.Instant

/**
 * A logical group of downloads (a YouTube playlist or a multi-file torrent). Children
 * reference the group through [DownloadTask.groupId] and the library item's `groupId`.
 * Aggregate progress/size/state is derived from children, never stored here.
 */
data class DownloadGroup(
    val id: String,
    val type: DownloadType,
    val title: String,
    /** Sanitized source (playlist URL / torrent reference); null when unknown. */
    val source: String?,
    val thumbnailUri: String?,
    val createdAt: Instant,
)