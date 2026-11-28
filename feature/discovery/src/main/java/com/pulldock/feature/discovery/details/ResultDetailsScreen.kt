package com.pulldock.feature.discovery.details

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.pulldock.core.designsystem.theme.LocalPdTokens
import com.pulldock.domain.discovery.model.TorrentResultDetails
import com.pulldock.feature.discovery.components.formatBytes
import com.pulldock.feature.discovery.components.icon

/** Result details: hero, description, metadata, and Add-to-PullDock (existing Add flow). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultDetailsScreen(
    details: TorrentResultDetails?,
    loading: Boolean,
    onBack: () -> Unit,
    onAdd: (fileName: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalPdTokens.current
    val context = LocalContext.current
    var selectedFile by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Details") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(tokens.spacing.md).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
        ) {
            when {
                loading -> Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                details == null -> Text("Details unavailable.")
                else -> {
                    Hero(details)
                    val desc = details.description
                    if (!desc.isNullOrBlank()) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(tokens.radius.md)) {
                            Text(desc, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(tokens.spacing.md))
                        }
                    }
                    MetadataCard(details)
                    if (details.images.isNotEmpty()) {
                        ImageRow(details.images.take(4))
                    }
                    if (details.files.isNotEmpty()) {
                        Text("Files", style = MaterialTheme.typography.titleSmall)
                        details.files.take(50).forEach { file ->
                            DetailRow(file.path, file.sizeBytes?.let { formatBytes(it) ?: "$it bytes" } ?: "—")
                        }
                    }
                    if (details.availableTorrentFiles.size > 1) {
                        Text("Choose a torrent file", style = MaterialTheme.typography.titleSmall)
                        details.availableTorrentFiles.forEach { name ->
                            RowSelect(name, selected = selectedFile == name) { selectedFile = name }
                        }
                    }
                    Button(onClick = { onAdd(selectedFile) }, modifier = Modifier.fillMaxWidth()) { Text("Add to PullDock") }
                    ActionRow(details, context)
                }
            }
        }
    }
}

@Composable
private fun Hero(details: TorrentResultDetails) {
    val tokens = LocalPdTokens.current
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(88.dp).clip(RoundedCornerShape(tokens.radius.md)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(details.category.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.width(tokens.spacing.md))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
            Text(details.title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(details.providerName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(details.category.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MetadataCard(details: TorrentResultDetails) {
    val tokens = LocalPdTokens.current
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(tokens.radius.md)) {
        Column(Modifier.padding(tokens.spacing.md), verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
            details.sizeBytes?.let { Meta("Size", formatBytes(it) ?: "$it bytes") }
            details.seeders?.let { Meta("Seeders", "$it") }
            details.leechers?.let { Meta("Leechers", "$it") }
            details.publishedAt?.let { Meta("Published", it.toString().take(10)) }
            details.infoHash?.let { Meta("Infohash", it) }
            Meta("Source", details.sourcePageUrl ?: "—")
            Meta("Rights", "${details.rightsStatus.name.lowercase()}${details.licence?.let { " · $it" } ?: ""}")
        }
    }
}

@Composable
private fun Meta(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).padding(start = 8.dp))
    }
}

@Composable
private fun ActionRow(details: TorrentResultDetails, context: Context) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        details.sourcePageUrl?.let { url ->
            OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }) { Text("Open") }
        }
        details.infoHash?.let { hash ->
            OutlinedButton(onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("infohash", hash))
            }) { Text("Copy hash") }
        }
        details.sourcePageUrl?.let { url ->
            OutlinedButton(onClick = {
                val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url) }
                runCatching { context.startActivity(Intent.createChooser(intent, "Share source")) }
            }) { Text("Share") }
        }
    }
}

@Composable
private fun RowSelect(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ImageRow(images: List<String>) {
    val tokens = LocalPdTokens.current
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
    ) {
        images.forEach { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(120.dp).clip(RoundedCornerShape(tokens.radius.md)),
            )
        }
    }
}
@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
