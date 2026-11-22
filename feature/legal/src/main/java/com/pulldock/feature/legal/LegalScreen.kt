package com.pulldock.feature.legal

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pulldock.core.designsystem.theme.LocalPdTokens

/**
 * Legal, privacy, and third-party license screen (T048, FR-075). Distribution is
 * stated honestly: store eligibility is unresolved pending a policy assessment; the
 * app is not marketed as enabling unauthorized downloads. The full license report is
 * generated for release (T178); the pinned core set is listed here.
 */
@Composable
fun LegalScreen(modifier: Modifier = Modifier) {
    val tokens = LocalPdTokens.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
    ) {
        Text(stringResource(R.string.legal_title), style = MaterialTheme.typography.headlineSmall)

        Section(stringResource(R.string.legal_privacy_title), stringResource(R.string.legal_privacy_body))
        Section(stringResource(R.string.legal_distribution_title), stringResource(R.string.legal_distribution_body))
        Section(stringResource(R.string.legal_rights_title), stringResource(R.string.legal_rights_body))
        Section(stringResource(R.string.legal_licenses_title), stringResource(R.string.legal_licenses_body))
        Section(stringResource(R.string.legal_extraction_title), stringResource(R.string.legal_extraction_body))
    }
}

@Composable
private fun Section(title: String, body: String) {
    val tokens = LocalPdTokens.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
