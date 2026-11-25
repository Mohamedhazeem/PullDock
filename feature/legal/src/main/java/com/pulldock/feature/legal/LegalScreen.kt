package com.pulldock.feature.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pulldock.core.designsystem.components.PdCard
import com.pulldock.core.designsystem.components.PdScaffold
import com.pulldock.core.designsystem.components.PdSectionHeader
import com.pulldock.core.designsystem.theme.LocalPdTokens

/** Legal, privacy, and third-party license screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalPdTokens.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    PdScaffold(
        title = stringResource(R.string.legal_title),
        subtitle = stringResource(R.string.legal_subtitle),
        onBack = onBack,
        scrollBehavior = scrollBehavior,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = tokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
        ) {
            item {
                Section(
                    title = stringResource(R.string.legal_privacy_title),
                    body = stringResource(R.string.legal_privacy_body),
                    icon = Icons.Rounded.Shield,
                )
            }
            item {
                Section(
                    title = stringResource(R.string.legal_distribution_title),
                    body = stringResource(R.string.legal_distribution_body),
                    icon = Icons.Rounded.Gavel,
                )
            }
            item {
                Section(
                    title = stringResource(R.string.legal_rights_title),
                    body = stringResource(R.string.legal_rights_body),
                    icon = Icons.Rounded.Description,
                )
            }
            item {
                Section(
                    title = stringResource(R.string.legal_licenses_title),
                    body = stringResource(R.string.legal_licenses_body),
                    icon = Icons.Rounded.Description,
                )
            }
            item {
                Section(
                    title = stringResource(R.string.legal_extraction_title),
                    body = stringResource(R.string.legal_extraction_body),
                    icon = Icons.Rounded.Description,
                )
            }
        }
    }
}

@Composable
private fun Section(
    title: String,
    body: String,
    icon: ImageVector,
) {
    val tokens = LocalPdTokens.current
    PdCard {
        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(tokens.radius.sm))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(tokens.spacing.sm))
                PdSectionHeader(title = title, modifier = Modifier.weight(1f))
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}