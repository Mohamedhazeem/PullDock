package com.pulldock.feature.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.pulldock.core.designsystem.components.PdButton
import com.pulldock.core.designsystem.components.PdErrorState
import com.pulldock.core.designsystem.theme.LocalPdTokens
import com.pulldock.core.model.DownloadError
import com.pulldock.domain.extractor.model.MediaDescriptor
import com.pulldock.domain.extractor.model.MediaFormat

/**
 * Add-download screen (T044/T087): URL/magnet entry with live classification, typed
 * validation errors, and honest submit behavior. Media resolution opens the format
 * sheet (FR-015/FR-016); failures surface as full error states with retry (US3
 * scenarios 4-5); torrent validation errors appear inline on the field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDownloadScreen(
    onClose: () -> Unit,
    viewModel: AddDownloadViewModel = hiltViewModel(),
) {
    val tokens = LocalPdTokens.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                AddDownloadEvent.TorrentAdded -> onClose()
                AddDownloadEvent.MediaEnqueued -> onClose()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
    ) {
        Text(
            text = stringResource(R.string.add_explanation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val inputError = state.inputError
        val detectedKind = state.sourceKind
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
            modifier = Modifier.fillMaxWidth(),
        )
        SourceKindBadge(kind = detectedKind)

        PdButton(
            label = stringResource(R.string.add_submit),
            onClick = viewModel::submit,
            loading = state.submitting,
            enabled = state.input.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )

        val picker = rememberTorrentFilePicker(viewModel::onTorrentFilePicked)
        com.pulldock.core.designsystem.components.PdTextButton(
            label = stringResource(R.string.add_pick_torrent),
            onClick = picker::launch,
            modifier = Modifier.fillMaxWidth(),
        )

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

    state.resolvedDescriptor?.let { descriptor ->
        FormatSelectionSheet(
            descriptor = descriptor,
            formats = state.formats,
            selected = state.selectedFormat,
            submitting = state.submitting,
            onSelect = viewModel::selectFormat,
            onEnqueue = viewModel::enqueueMedia,
            onDismiss = viewModel::dismissResolved,
        )
    }
}

/** Readable badge describing what the entry field currently classifies as. */
@Composable
private fun SourceKindBadge(kind: SourceKind) {
    if (kind != SourceKind.MAGNET && kind != SourceKind.MEDIA_URL) return
    val tokens = LocalPdTokens.current
    val (icon, label, color) = if (kind == SourceKind.MAGNET) {
        Triple(Icons.Filled.Link, stringResource(R.string.add_source_magnet), MaterialTheme.colorScheme.primary)
    } else {
        Triple(Icons.Filled.CloudDownload, stringResource(R.string.add_source_media), MaterialTheme.colorScheme.primary)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

/**
 * Format selection for a resolved media source (T087, FR-015/FR-016): resolution,
 * container, codecs, estimated size, and whether merging is required.
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
            modifier = Modifier.padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
        ) {
            Text(descriptor.title, style = MaterialTheme.typography.titleLarge)
            if (formats.isEmpty()) {
                PdErrorState(
                    title = stringResource(R.string.add_no_formats_title),
                    message = stringResource(R.string.add_no_formats_body),
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                    items(formats, key = { it.id }) { format ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(formatLabel(format), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = formatDetail(format),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            RadioButton(
                                selected = format.id == selected?.id,
                                onClick = { onSelect(format) },
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
private fun formatLabel(format: MediaFormat): String =
    format.resolution ?: stringResource(R.string.add_format_audio_only)

@Composable
private fun formatDetail(format: MediaFormat): String = buildString {
    append(format.container.uppercase())
    format.videoCodec?.let { append(" · ").append(it) }
    format.audioCodec?.let { append(" · ").append(it) }
    format.estimatedBytes?.let { append(" · ").append(formatBytes(it)) }
    if (format.mergeRequired) {
        append(" · ")
        append(stringResource(R.string.add_format_merge))
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) "%.1f GB".format(mb / 1024.0) else "%.1f MB".format(mb)
}

/** Maps typed errors to actionable user text (constitution XI); never raw details. */
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
