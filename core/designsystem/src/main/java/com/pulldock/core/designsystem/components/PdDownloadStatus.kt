package com.pulldock.core.designsystem.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.pulldock.core.designsystem.R
import com.pulldock.core.model.DownloadState
import com.pulldock.core.model.DownloadType
import com.pulldock.core.model.MediaKind
import com.pulldock.core.model.mediaKindOf

/** User-facing status label for a download state. */
@Composable
fun pdStatusLabel(state: DownloadState): String = stringResource(
    when (state) {
        DownloadState.CREATED -> R.string.pd_status_created
        DownloadState.VALIDATING -> R.string.pd_status_validating
        DownloadState.RESOLVING -> R.string.pd_status_resolving
        DownloadState.FETCHING_METADATA -> R.string.pd_status_fetching_metadata
        DownloadState.AWAITING_FILE_SELECTION -> R.string.pd_status_awaiting_file
        DownloadState.AWAITING_FORMAT_SELECTION -> R.string.pd_status_awaiting_format
        DownloadState.QUEUED -> R.string.pd_status_queued
        DownloadState.STARTING -> R.string.pd_status_starting
        DownloadState.RUNNING -> R.string.pd_status_running
        DownloadState.PAUSING -> R.string.pd_status_pausing
        DownloadState.PAUSED -> R.string.pd_status_paused
        DownloadState.RETRY_WAIT -> R.string.pd_status_retry_wait
        DownloadState.VERIFYING -> R.string.pd_status_verifying
        DownloadState.POST_PROCESSING -> R.string.pd_status_processing
        DownloadState.COMPLETED -> R.string.pd_status_completed
        DownloadState.FAILED -> R.string.pd_status_failed
        DownloadState.CANCELLED -> R.string.pd_status_cancelled
        DownloadState.REMOVING -> R.string.pd_status_removing
        DownloadState.NEEDS_PERMISSION -> R.string.pd_status_needs_permission
        DownloadState.NEEDS_USER_ACTION -> R.string.pd_status_needs_action
        DownloadState.BLOCKED -> R.string.pd_status_blocked
    },
)

/** Rounded status icon. */
fun pdStatusIcon(state: DownloadState): ImageVector = when (state) {
    DownloadState.RUNNING, DownloadState.STARTING -> Icons.Rounded.PlayArrow
    DownloadState.PAUSED, DownloadState.PAUSING -> Icons.Rounded.Pause
    DownloadState.QUEUED, DownloadState.RETRY_WAIT -> Icons.Rounded.Schedule
    DownloadState.COMPLETED -> Icons.Rounded.CheckCircle
    DownloadState.FAILED, DownloadState.NEEDS_USER_ACTION -> Icons.Rounded.ErrorOutline
    DownloadState.BLOCKED, DownloadState.NEEDS_PERMISSION -> Icons.Rounded.Block
    DownloadState.VERIFYING, DownloadState.POST_PROCESSING -> Icons.Rounded.Refresh
    DownloadState.REMOVING -> Icons.Rounded.CloudDownload
    DownloadState.CANCELLED -> Icons.Rounded.Block
    else -> Icons.Rounded.HourglassEmpty
}

/** Semantic tone for the status badge. */
fun pdStatusTone(state: DownloadState): PdStatusTone = when (state) {
    DownloadState.RUNNING, DownloadState.STARTING, DownloadState.COMPLETED -> PdStatusTone.Success
    DownloadState.QUEUED, DownloadState.RETRY_WAIT, DownloadState.VERIFYING,
    DownloadState.POST_PROCESSING, DownloadState.PAUSING -> PdStatusTone.Info
    DownloadState.PAUSED, DownloadState.BLOCKED, DownloadState.NEEDS_PERMISSION,
    DownloadState.NEEDS_USER_ACTION -> PdStatusTone.Warning
    DownloadState.FAILED -> PdStatusTone.Error
    else -> PdStatusTone.Neutral
}

/** User-facing download type label. */
@Composable
fun pdTypeLabel(type: DownloadType): String = stringResource(
    when (type) {
        DownloadType.TORRENT -> R.string.pd_type_torrent
        DownloadType.EXTRACTOR -> R.string.pd_type_media
    },
)

/** Rounded type icon. */
fun pdTypeIcon(type: DownloadType): ImageVector = when (type) {
    DownloadType.TORRENT -> Icons.Rounded.Link
    DownloadType.EXTRACTOR -> Icons.Rounded.CloudDownload
}

/** Library/content type icon by file extension. */
fun pdLibraryIcon(fileName: String): ImageVector = when (mediaKindOf(fileName)) {
    MediaKind.VIDEO -> Icons.Rounded.Movie
    MediaKind.AUDIO -> Icons.Rounded.MusicNote
    MediaKind.IMAGE -> Icons.Rounded.Image
    MediaKind.DOCUMENT, MediaKind.OTHER -> Icons.Rounded.InsertDriveFile
}