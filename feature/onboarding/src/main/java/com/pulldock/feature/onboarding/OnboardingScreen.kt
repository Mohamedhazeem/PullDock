package com.pulldock.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.pulldock.core.designsystem.components.PdButton
import com.pulldock.core.designsystem.components.PdLoadingState
import com.pulldock.core.designsystem.theme.LocalPdTokens

/**
 * First-launch consent and capability explanation (US1, FR-013). Plain-language
 * sections: what PullDock does, local-first behavior, torrent peer-IP exposure,
 * copyright responsibility, and permission usage. Download features stay blocked
 * until [OnboardingViewModel.acknowledge] persists the acknowledgement (FR, US1).
 */
@Composable
fun OnboardingScreen(
    onAcknowledged: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val tokens = LocalPdTokens.current
    val consent by viewModel.consent.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                OnboardingEvent.Acknowledged -> onAcknowledged()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
    ) {
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        if (consent == null) {
            PdLoadingState(label = stringResource(R.string.onboarding_loading))
            return@Column
        }

        OnboardingSection(
            title = stringResource(R.string.onboarding_section_capabilities_title),
            body = stringResource(R.string.onboarding_section_capabilities_body),
        )
        OnboardingSection(
            title = stringResource(R.string.onboarding_section_local_first_title),
            body = stringResource(R.string.onboarding_section_local_first_body),
        )
        OnboardingSection(
            title = stringResource(R.string.onboarding_section_torrent_privacy_title),
            body = stringResource(R.string.onboarding_section_torrent_privacy_body),
        )
        OnboardingSection(
            title = stringResource(R.string.onboarding_section_rights_title),
            body = stringResource(R.string.onboarding_section_rights_body),
        )
        OnboardingSection(
            title = stringResource(R.string.onboarding_section_permissions_title),
            body = stringResource(R.string.onboarding_section_permissions_body),
        )

        val acknowledgeDescription = stringResource(R.string.onboarding_acknowledge_description)
        PdButton(
            label = stringResource(R.string.onboarding_acknowledge),
            onClick = viewModel::acknowledge,
            loading = uiState.acknowledging,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = acknowledgeDescription },
        )
    }
}

@Composable
private fun OnboardingSection(title: String, body: String) {
    val tokens = LocalPdTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
