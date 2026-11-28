package com.pulldock.feature.discovery

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pulldock.domain.discovery.model.ContributingSource
import com.pulldock.domain.discovery.model.OverallSearchState
import com.pulldock.domain.discovery.model.ProviderState
import com.pulldock.domain.discovery.model.ProviderStatus
import com.pulldock.domain.discovery.model.ResultSourceType
import com.pulldock.domain.discovery.model.RightsStatus
import com.pulldock.domain.discovery.model.SearchCategory
import com.pulldock.domain.discovery.model.TorrentSearchResult
import org.junit.Rule
import org.junit.Test

class DiscoverScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun result(id: String, provider: String) = TorrentSearchResult(
        resultId = id, providerId = provider, providerName = provider, title = "Result $id",
        description = null, category = SearchCategory.MOVIES, originalProviderCategory = null,
        infoHash = "h$id", sizeBytes = 1024, seeders = 5, leechers = 1, publishedAt = null,
        providerVerificationClaim = null, sourcePageUrl = null, resultSourceType = ResultSourceType.MAGNET,
        rightsStatus = RightsStatus.UNKNOWN, availableActions = emptySet(),
        contributingSources = listOf(ContributingSource(provider, provider, null, id)),
    )

    @Test
    fun searchFieldAndResultAndProviderAreShown() {
        val state = DiscoverUiState(
            query = "cats",
            overall = OverallSearchState.RESULTS,
            results = listOf(result("1", "BitSearch")),
            perProvider = listOf(ProviderStatus("BitSearch", "BitSearch", ProviderState.SUCCESS, 5)),
        )
        rule.setContent { DiscoverScreen(state, onEvent = {}, onOpenSettings = {}) }
        rule.onNodeWithText("Result 1").assertIsDisplayed()
        rule.onNodeWithText("BitSearch: 5").assertIsDisplayed()
    }

    @Test
    fun emptyStateIsShown() {
        rule.setContent { DiscoverScreen(DiscoverUiState(overall = OverallSearchState.EMPTY), onEvent = {}, onOpenSettings = {}) }
        rule.onNodeWithText("No results").assertIsDisplayed()
    }

    @Test
    fun allFailedStateIsShown() {
        rule.setContent { DiscoverScreen(DiscoverUiState(overall = OverallSearchState.ALL_FAILED), onEvent = {}, onOpenSettings = {}) }
        rule.onNodeWithText("All providers failed").assertIsDisplayed()
    }

    @Test
    fun filtersButtonOpensSheet() {
        rule.setContent { DiscoverScreen(DiscoverUiState(), onEvent = {}, onOpenSettings = {}) }
        rule.onNodeWithText("Filters").performClick()
        rule.onNodeWithText("Sort").assertIsDisplayed()
    }
}