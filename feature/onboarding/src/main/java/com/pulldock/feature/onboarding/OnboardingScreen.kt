package com.pulldock.feature.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulldock.core.designsystem.components.PdBrandMark
import com.pulldock.core.designsystem.components.PdButton
import com.pulldock.core.designsystem.components.PdCard
import com.pulldock.core.designsystem.components.PdLoadingState
import com.pulldock.core.designsystem.components.PdScaffold
import com.pulldock.core.designsystem.theme.LocalPdTokens
import kotlinx.coroutines.launch

/**
 * First-launch consent and capability explanation. Three short steps keep the required
 * disclosures readable without overwhelming a first-time user.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onAcknowledged: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val tokens = LocalPdTokens.current
    val consent by viewModel.consent.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                OnboardingEvent.Acknowledged -> onAcknowledged()
            }
        }
    }

    PdScaffold(
        title = stringResource(R.string.onboarding_title),
        subtitle = stringResource(
            R.string.onboarding_step,
            pagerState.currentPage + 1,
            3,
        ),
    ) { innerPadding ->
        if (consent == null) {
            PdLoadingState(
                label = stringResource(R.string.onboarding_loading),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            return@PdScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                OnboardingPage(
                    page = page,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.md),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
            ) {
                TextButton(
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    },
                    enabled = pagerState.currentPage > 0,
                ) { Text(stringResource(R.string.onboarding_back)) }

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(3) { index ->
                        val selected = index == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .padding(horizontal = tokens.spacing.xs)
                                .size(width = if (selected) 22.dp else 8.dp, height = 8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                ),
                        )
                    }
                }

                if (pagerState.currentPage < 2) {
                    Button(
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        },
                        modifier = Modifier.height(48.dp),
                    ) { Text(stringResource(R.string.onboarding_next)) }
                } else {
                    PdButton(
                        label = stringResource(R.string.onboarding_acknowledge),
                        onClick = viewModel::acknowledge,
                        loading = uiState.acknowledging,
                        modifier = Modifier.height(48.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage(
    page: Int,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalPdTokens.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
    ) {
        PdBrandMark(size = 56.dp)
        Text(
            text = when (page) {
                0 -> stringResource(R.string.onboarding_step1_title)
                1 -> stringResource(R.string.onboarding_step2_title)
                else -> stringResource(R.string.onboarding_step3_title)
            },
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = when (page) {
                0 -> stringResource(R.string.onboarding_step1_body)
                1 -> stringResource(R.string.onboarding_step2_body)
                else -> stringResource(R.string.onboarding_step3_body)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (page) {
            0 -> {
                FeatureRow(
                    icon = Icons.Rounded.CloudDownload,
                    title = stringResource(R.string.onboarding_section_capabilities_title),
                    body = stringResource(R.string.onboarding_section_capabilities_body),
                )
                FeatureRow(
                    icon = Icons.Rounded.Link,
                    title = stringResource(R.string.onboarding_section_local_first_title),
                    body = stringResource(R.string.onboarding_section_local_first_body),
                )
                FeatureRow(
                    icon = Icons.Rounded.Movie,
                    title = stringResource(R.string.onboarding_section_rights_title),
                    body = stringResource(R.string.onboarding_section_rights_body),
                )
            }
            1 -> {
                FeatureRow(
                    icon = Icons.Rounded.Shield,
                    title = stringResource(R.string.onboarding_section_torrent_privacy_title),
                    body = stringResource(R.string.onboarding_section_torrent_privacy_body),
                )
                FeatureRow(
                    icon = Icons.Rounded.Movie,
                    title = stringResource(R.string.onboarding_section_rights_title),
                    body = stringResource(R.string.onboarding_section_rights_body),
                )
            }
            else -> {
                FeatureRow(
                    icon = Icons.Rounded.Shield,
                    title = stringResource(R.string.onboarding_section_permissions_title),
                    body = stringResource(R.string.onboarding_section_permissions_body),
                )
                PdCard {
                    Text(
                        text = stringResource(R.string.onboarding_consent_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    body: String,
) {
    val tokens = LocalPdTokens.current
    PdCard {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(tokens.radius.sm))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(tokens.spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}