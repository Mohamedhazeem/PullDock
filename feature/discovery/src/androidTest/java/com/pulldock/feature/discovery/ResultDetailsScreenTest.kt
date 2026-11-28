package com.pulldock.feature.discovery

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.pulldock.domain.discovery.model.RightsStatus
import com.pulldock.domain.discovery.model.SearchCategory
import com.pulldock.domain.discovery.model.TorrentResultDetails
import com.pulldock.feature.discovery.details.ResultDetailsScreen
import org.junit.Rule
import org.junit.Test

class ResultDetailsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun details(title: String) = TorrentResultDetails(
        resultId = "1", providerId = "bitsearch", providerName = "BitSearch", title = title,
        description = "desc", category = SearchCategory.MOVIES, infoHash = "abc", sizeBytes = 2048,
        seeders = 5, leechers = 2, publishedAt = null, sourcePageUrl = "https://example.org",
        rightsStatus = RightsStatus.UNKNOWN, licence = null, downloadDescriptor = null,
        availableActions = setOf(com.pulldock.domain.discovery.model.ResultAction.ADD),
    )

    @Test
    fun detailsShowTitleAndAddAction() {
        rule.setContent { ResultDetailsScreen(details = details("Movie"), loading = false, onBack = {}, onAdd = {}) }
        rule.onNodeWithText("Movie").assertIsDisplayed()
        rule.onNodeWithText("Add to PullDock").assertIsDisplayed()
    }

    @Test
    fun loadingShowsProgress() {
        rule.setContent { ResultDetailsScreen(details = null, loading = true, onBack = {}, onAdd = {}) }
        rule.onNodeWithText("Details").assertIsDisplayed()
    }
}