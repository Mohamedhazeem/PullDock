package com.pulldock.feature.discovery

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.pulldock.feature.discovery.settings.DiscoverySettingsScreen
import org.junit.Rule
import org.junit.Test

class DiscoverySettingsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun settingsShowAdultToggleAndActions() {
        rule.setContent {
            DiscoverySettingsScreen(state = DiscoverUiState(), onEvent = {}, onBack = {})
        }
        rule.onNodeWithText("Show adult results").assertIsDisplayed()
        rule.onNodeWithText("Clear history").assertIsDisplayed()
        rule.onNodeWithText("Clear cache").assertIsDisplayed()
    }
}