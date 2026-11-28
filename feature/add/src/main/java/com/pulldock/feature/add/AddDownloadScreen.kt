package com.pulldock.feature.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulldock.core.designsystem.components.PdButton
import com.pulldock.core.designsystem.components.PdCard
import com.pulldock.core.designsystem.components.PdErrorState
import com.pulldock.core.designsystem.components.PdScaffold
import com.pulldock.core.designsystem.components.PdStatusBadge
import com.pulldock.core.designsystem.components.PdStatusTone
import com.pulldock.core.designsystem.theme.LocalPdTokens
import com.pulldock.core.model.DownloadError
import com.pulldock.core.model.formatBytes
import com.pulldock.domain.extractor.model.MediaDescriptor
import com.pulldock.domain.extractor.model.MediaFormat
import com.pulldock.domain.extractor.model.PlaylistDescriptor
import com.pulldock.domain.extractor.model.PlaylistEntry

/**
 * Add-download screen: URL/magnet entry with live classification, typed validation
 * errors, and honest submit behavior. Media resolution opens the format sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDownloadScreen(
    onClose: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: AddDownloadViewModel = hiltViewModel(),
) {
    val tokens = LocalPdTokens.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                AddDownloadEvent.TorrentAdded -> onClose()
                AddDownloadEvent.MediaEnqueued -> onClose()
                AddDownloadEvent.PlaylistEnqueued -> onClose()
            }
        }
    }

    PdScaffold(
        title = stringResource(R.string.add_title),
        onBack = onDismiss,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = tokens.layout.maxContentWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.md),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
            ) {
                Text(
                    text = stringResource(R.string.add_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val inputError = state.inputError
                val detectedKind = state.sourceKind
                PdCard {
                    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                        OutlinedTextField(
                            value = state.input,
                            onValueChange = viewModel::onInputChanged,
                            label = { Text(stringResource(R.string.add_input_label)) },
                            supportingText = {
                                if (inputError != null) {
                                    Text(errorText(inputError))
                                } else {
                                    Text(stringResource(R.string.add_input_hint))
                                }
                            },
                            isError = inputError != null,
                            singleLine = true,
                            shape = RoundedCornerShape(tokens.radius.md),
                            trailingIcon = {
                                if (state.input.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onInputChanged("") }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Clear,
                                            contentDescription = stringResource(R.string.add_clear),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SourceKindBadge(kind = detectedKind)
                    }
                }

                PdButton(
                    label = if (state.resolvedDescriptor != null) {
                        stringResource(R.string.add_choose_format)
                    } else {
                        stringResource(R.string.add_submit)
                    },
                    onClick = {
                        if (state.resolvedDescriptor != null) viewModel.reopenSheet() else viewModel.submit()
                        focus.clearFocus()
                        keyboard?.hide()
                    },
                    loading = state.submitting,
                    enabled = state.input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )

                val picker = rememberTorrentFilePicker(viewModel::onTorrentFilePicked)
                PdCard(onClick = picker::launch) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(tokens.spacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.add_pick_torrent),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = stringResource(R.string.add_pick_torrent_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                val failedError = state.failedError
                when {
                    failedError != null -> PdErrorState(
                        title = stringResource(R.string.add_resolve_failed_title),
                        message = errorText(failedError),
                        retryLabel = stringResource(R.string.add_retry),
                        onRetry = viewModel::submit,
                    )
                    state.sourceKind == SourceKind.UNSUPPORTED && state.input.isNotBlank() -> PdErrorState(
                        title = stringResource(R.string.add_unsupported_title),
                        message = stringResource(R.string.add_unsupported_body),
                    )
                    else -> Unit
                }
            }
        }
    }

    state.resolvedDescriptor?.let { descriptor ->
        if (state.sheetOpen) {
            FormatSelectionSheet(
                descriptor = descriptor,
                formats = state.formats,
                selected = state.selectedFormat,
                submitting = state.submitting,
                onSelect = viewModel::selectFormat,
                onEnqueue = viewModel::enqueueMedia,
                onDismiss = viewModel::dismissSheet,
            )
        }
    }

    state.playlist?.let { playlist ->
        PlaylistSelectionSheet(
            playlist = playlist,
            selected = state.selectedPlaylistIndexes,
            submitting = state.submitting,
            resolving = state.playlistResolving,
            total = state.playlistTotal,
            onToggle = viewModel::togglePlaylistEntry,
            onConfirm = viewModel::confirmPlaylist,
            onDismiss = viewModel::dismissPlaylist,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistSelectionSheet(
    playlist: PlaylistDescriptor,
    selected: Set<Int>,
    submitting: Boolean,
    resolving: Int?,
    total: Int,
    onToggle: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalPdTokens.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
                Text(playlist.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2)
                Text(stringResource(R.string.add_playlist_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.add_playlist_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (playlist.entries.isEmpty()) {
                PdErrorState(
                    title = stringResource(R.string.add_playlist_title),
                    message = stringResource(R.string.add_playlist_empty),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
                ) {
                    items(playlist.entries, key = { it.index }) { entry ->
                        PlaylistEntryRow(
                            entry = entry,
                            selected = entry.index in selected,
                            onToggle = { onToggle(entry.index) },
                        )
                    }
                }
                val label = if (submitting && resolving != null) {
                    stringResource(R.string.add_playlist_resolving, resolving, total)
                } else {
                    stringResource(R.string.add_playlist_download, selected.size)
                }
                PdButton(
                    label = label,
                    onClick = onConfirm,
                    loading = submitting,
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PlaylistEntryRow(entry: PlaylistEntry, selected: Boolean, onToggle: () -> Unit) {
    val tokens = LocalPdTokens.current
    PdCard(onClick = onToggle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(tokens.spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                entry.durationSeconds?.let { seconds ->
                    Text(
                        text = "%d:%02d".format(seconds / 60, seconds % 60),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Readable badge describing what the entry field currently classifies as. */
@Composable
private fun SourceKindBadge(kind: SourceKind) {
    if (kind != SourceKind.MAGNET && kind != SourceKind.MEDIA_URL) return
    val tokens = LocalPdTokens.current
    val (icon, label) = if (kind == SourceKind.MAGNET) {
        Icons.Rounded.Link to stringResource(R.string.add_source_magnet)
    } else {
        Icons.Rounded.CloudDownload to stringResource(R.string.add_source_media)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Format selection for a resolved media source: resolution, container, codecs,
 * estimated size, and whether merging is required.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormatSelectionSheet(
    descriptor: MediaDescriptor,
    formats: List<MediaFormat>,
    selected: MediaFormat?,
    submitting: Boolean,
    onSelect: (MediaFormat) -> Unit,
    onEnqueue: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalPdTokens.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
                Text(descriptor.title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = stringResource(R.string.add_choose_format),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.add_format_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (formats.isEmpty()) {
                PdErrorState(
                    title = stringResource(R.string.add_no_formats_title),
                    message = stringResource(R.string.add_no_formats_body),
                )
            } else {
                val videoFormats = formats.filter { it.videoCodec != null || it.mergeRequired }
                val audioFormats = formats.filter { it.videoCodec == null && it.audioCodec != null }
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
                ) {
                    if (videoFormats.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.add_section_video)) }
                        items(videoFormats, key = { it.id }) { format ->
                            FormatRow(
                                format = format,
                                selected = format.id == selected?.id,
                                recommended = format.id == selected?.id,
                                onSelect = { onSelect(format) },
                            )
                        }
                    }
                    if (audioFormats.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.add_section_audio)) }
                        items(audioFormats, key = { it.id }) { format ->
                            FormatRow(
                                format = format,
                                selected = format.id == selected?.id,
                                recommended = format.id == selected?.id,
                                onSelect = { onSelect(format) },
                            )
                        }
                    }
                }
                PdButton(
                    label = stringResource(R.string.add_download_selected),
                    onClick = onEnqueue,
                    loading = submitting,
                    enabled = selected != null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = LocalPdTokens.current.spacing.sm),
    )
}

@Composable
private fun FormatRow(
    format: MediaFormat,
    selected: Boolean,
    recommended: Boolean,
    onSelect: () -> Unit,
) {
    val tokens = LocalPdTokens.current
    PdCard(onClick = onSelect) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(tokens.spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatLabel(format), style = MaterialTheme.typography.bodyLarge)
                    if (recommended) {
                        Spacer(Modifier.width(tokens.spacing.sm))
                        PdStatusBadge(
                            label = stringResource(R.string.add_recommended),
                            icon = Icons.Rounded.RadioButtonChecked,
                            tone = PdStatusTone.Success,
                        )
                    }
                }
                Text(
                    text = formatDetail(format),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun formatLabel(format: MediaFormat): String {
    val resolution = clean(format.resolution)
    val kind = formatKind(format)
    return when {
        resolution != null && resolution == "Unknown" -> "${stringResource(R.string.add_format_default)} · $kind"
        resolution != null -> "$resolution · $kind"
        kind == stringResource(R.string.add_format_audio_only) -> kind
        else -> stringResource(R.string.add_format_unknown)
    }
}

@Composable
private fun formatKind(format: MediaFormat): String {
    val hasVideo = clean(format.videoCodec) != null
    val hasAudio = clean(format.audioCodec) != null
    return when {
        hasVideo && hasAudio -> stringResource(R.string.add_format_video_audio)
        format.mergeRequired -> stringResource(R.string.add_format_video_audio)
        hasAudio -> stringResource(R.string.add_format_audio_only)
        else -> stringResource(R.string.add_format_unknown)
    }
}

@Composable
private fun formatDetail(format: MediaFormat): String = buildString {
    append(clean(format.container)?.uppercase() ?: "MP4")
    if (format.mergeRequired) {
        codecLabel(format.videoCodec)?.let { append(" · ").append(it) }
        append(" · ").append(stringResource(R.string.add_format_merge))
    } else {
        codecLabel(format.videoCodec)?.let { append(" · ").append(it) }
        codecLabel(format.audioCodec)?.let { append(" · ").append(it) }
    }
    clean(format.language)?.let { append(" · ").append(it.uppercase()) }
    format.estimatedBytes?.let { append(" · ≈").append(formatBytes(it)) }
}

@Composable
private fun codecLabel(codec: String?): String? {
    val value = clean(codec) ?: return null
    return when {
        value.contains("avc1", true) -> "H.264"
        value.contains("vp9", true) -> "VP9"
        value.contains("vp8", true) -> "VP8"
        value.contains("av01", true) -> "AV1"
        value.contains("mp4a", true) -> "AAC"
        value.contains("opus", true) -> "Opus"
        value.contains("vorbis", true) -> "Vorbis"
        value.equals("unknown", true) -> null
        else -> value
    }
}

private fun clean(value: String?): String? =
    value?.takeIf { it.isNotBlank() && it != "null" }

/** Maps typed errors to actionable user text; never raw details. */
@Composable
internal fun errorText(error: DownloadError): String = when (error) {
    is DownloadError.Input -> when (error.reason) {
        DownloadError.InputReason.MALFORMED_MAGNET -> stringResource(R.string.add_error_malformed_magnet)
        DownloadError.InputReason.UNSUPPORTED_SCHEME -> stringResource(R.string.add_error_unsupported_scheme)
        DownloadError.InputReason.UNSUPPORTED_PROVIDER -> stringResource(R.string.add_error_unsupported_provider)
        else -> stringResource(R.string.add_error_invalid_input)
    }
    is DownloadError.TorrentEngine ->
        if (error.reason == DownloadError.TorrentReason.DUPLICATE_INFOHASH) {
            stringResource(R.string.add_error_duplicate)
        } else {
            stringResource(R.string.add_error_torrent)
        }
    is DownloadError.ExtractorEngine -> when (error.reason) {
        DownloadError.ExtractorReason.RUNTIME_INCOMPATIBLE -> stringResource(R.string.add_error_runtime)
        DownloadError.ExtractorReason.UNSUPPORTED -> stringResource(R.string.add_error_unsupported_provider)
        else -> stringResource(R.string.add_error_extractor)
    }
    is DownloadError.Network -> stringResource(R.string.add_error_network)
    else -> stringResource(R.string.add_error_generic)
}