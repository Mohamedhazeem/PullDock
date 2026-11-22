package com.pulldock.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/**
 * Root navigation graph (plan.md §Navigation architecture). Slice 0 registers the four
 * primary shell destinations; the onboarding/consent gate (T049), the add-download
 * modal flow, download detail, player, provider status, and legal screens register
 * here as their slices land, with feature modules contributing their destinations.
 */
@Composable
fun RootNavHost(modifier: Modifier = Modifier) {
    val navController: NavHostController = rememberNavController()

    MainScaffold(navController = navController) { contentModifier ->
        NavHost(
            navController = navController,
            startDestination = PullDockDestination.Primary.HOME.route,
            modifier = modifier.then(contentModifier),
        ) {
            composable(PullDockDestination.Primary.HOME.route) {
                ShellHomeContent()
            }
            composable(PullDockDestination.Primary.DOWNLOADS.route) {
                ShellDownloadsContent()
            }
            composable(PullDockDestination.Primary.LIBRARY.route) {
                ShellLibraryContent()
            }
            composable(PullDockDestination.Primary.SETTINGS.route) {
                ShellSettingsContent()
            }
        }
    }
}
