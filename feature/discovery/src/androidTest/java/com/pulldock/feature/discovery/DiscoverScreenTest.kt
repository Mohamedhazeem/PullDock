package com.pulldock.feature.discovery

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.pulldock.domain.discovery.model.OverallSearchState
import org.junit.Rule
import org.junit.Test

class DiscoverScreenTest {

    @get:Rule
    val rule = createComposeRule()

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
}