package com.pulldock.feature.discovery.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pulldock.core.designsystem.theme.LocalPdTokens
import com.pulldock.domain.discovery.model.ResultSourceType
import com.pulldock.domain.discovery.model.SearchCategory
import com.pulldock.domain.discovery.model.TorrentSearchResult

/** A single normalized result card: poster, title, provider, and honest metadata. */
@Composable
fun ResultCard(
    result: TorrentSearchResult,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
    onHide: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalPdTokens.current
    Card(modifier = modifier.fillMaxWidth(), onClick = onOpen, shape = RoundedCornerShape(tokens.radius.lg)) {
        Column {
            Row(Modifier.padding(tokens.spacing.md), verticalAlignment = Alignment.Top) {
                Poster(result.category)
                Spacer(Modifier.width(tokens.spacing.md))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
                    Text(result.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
                        ProviderBadge(result.providerName)
                        if (result.providerVerificationClaim == true) {
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                                Text("verified", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                    MetaLine(result)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(end = tokens.spacing.sm, bottom = tokens.spacing.xs),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onHide) { Text("Hide") }
                TextButton(onClick = onReport) { Text("Report") }
                TextButton(onClick = onAdd) { Text("Add") }
            }
        }
    }
}

@Composable
private fun Poster(category: SearchCategory) {
    val tokens = LocalPdTokens.current
    Box(
        Modifier.size(64.dp).clip(RoundedCornerShape(tokens.radius.md)).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(category.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun ProviderBadge(name: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
        Text(name, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
private fun MetaLine(result: TorrentSearchResult) {
    val parts = buildList {
        result.sizeBytes?.let { add(formatBytes(it)) }
        result.seeders?.let { add("$it↑") }
        result.leechers?.let { add("$it↓") }
        result.publishedAt?.let { add(it.toString().take(10)) }
        add(if (result.resultSourceType == ResultSourceType.MAGNET) "magnet" else "file")
    }
    Text(
        parts.joinToString("  ·  "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { contentDescription = parts.joinToString(", ") },
    )
}

fun formatBytes(bytes: Long?): String? = bytes?.let {
    when {
        it >= 1_000_000_000L -> "%.1f GB".format(it / 1e9)
        it >= 1_000_000L -> "%.1f MB".format(it / 1e6)
        it >= 1_000L -> "%.1f KB".format(it / 1e3)
        else -> "$it B"
    }
}

fun SearchCategory.icon(): ImageVector = when (this) {
    SearchCategory.MOVIES, SearchCategory.DOCUMENTARIES, SearchCategory.ANIME -> Icons.Rounded.Movie
    SearchCategory.TV_SERIES -> Icons.Rounded.Tv
    SearchCategory.MUSIC, SearchCategory.AUDIOBOOKS -> Icons.Rounded.MusicNote
    SearchCategory.SOFTWARE, SearchCategory.GAMES -> Icons.Rounded.Apps
    SearchCategory.BOOKS, SearchCategory.EBOOKS, SearchCategory.DOCUMENTS -> Icons.Rounded.Description
    else -> Icons.Rounded.PlayArrow
}