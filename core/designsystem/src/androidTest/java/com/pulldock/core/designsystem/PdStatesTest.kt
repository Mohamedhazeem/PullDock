package com.pulldock.core.designsystem

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pulldock.core.designsystem.components.PdEmptyState
import com.pulldock.core.designsystem.components.PdErrorState
import com.pulldock.core.designsystem.components.PdProgress
import com.pulldock.core.designsystem.theme.PullDockTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Design-system state components render their content and accessibility semantics
 * (T042/T051 subset; the full per-screen matrix runs on a device, T053/T055).
 */
@RunWith(AndroidJUnit4::class)
class PdStatesTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyStateShowsTitleAndMessage() {
        composeRule.setContent {
            PullDockTheme {
                PdEmptyState(title = "Nothing here", message = "Add something to begin.")
            }
        }
        composeRule.onNodesWithText("Nothing here").assertIsDisplayed()
        composeRule.onNodesWithText("Add something to begin.").assertIsDisplayed()
    }

    @Test
    fun errorStateShowsRetryAction() {
        composeRule.setContent {
            PullDockTheme {
                PdErrorState(
                    title = "Failed",
                    message = "Network unavailable.",
                    retryLabel = "Retry",
                    onRetry = {},
                )
            }
        }
        composeRule.onNodesWithText("Failed").assertIsDisplayed()
        composeRule.onNodesWithText("Retry").assertIsDisplayed()
    }

    @Test
    fun progressExposesSummaryForTalkBack() {
        composeRule.setContent {
            PullDockTheme {
                PdProgress(fraction = 0.5f, summary = "50 percent, running")
            }
        }
        composeRule.onNodeWithContentDescription("50 percent, running").assertIsDisplayed()
    }
}
