package com.pulldock.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.pulldock.core.designsystem.theme.LocalPdTokens

/**
 * A grouped card with a clickable header that expands to reveal children. Used for
 * grouped downloads (playlists, multi-file torrents) on Home and Library. The header
 * shows an icon, title, optional subtitle, and an animated chevron; the body is revealed
 * with [AnimatedVisibility]. [toggleLabel] provides the accessibility action label.
 */
@Composable
fun PdExpandableGroup(
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    toggleLabel: String? = null,
    expandedLabel: String? = null,
    collapsedLabel: String? = null,
    headerContent: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val tokens = LocalPdTokens.current
    val stateLabel = if (expanded) expandedLabel else collapsedLabel
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = tokens.motion.base),
        label = "pd_group_chevron",
    )
    PdCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = toggleLabel) { onToggle() }
                    .semantics { stateLabel?.let { stateDescription = it } },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(tokens.spacing.md))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                if (headerContent != null) {
                    Spacer(Modifier.width(tokens.spacing.xs))
                    headerContent()
                    Spacer(Modifier.width(tokens.spacing.xs))
                }
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .rotate(rotation)
                        .size(22.dp),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
                    content()
                }
            }
        }
    }
}