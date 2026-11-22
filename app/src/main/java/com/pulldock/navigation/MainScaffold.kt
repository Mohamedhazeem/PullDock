package com.pulldock.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.pulldock.R
import com.pulldock.core.designsystem.theme.LocalPdTokens

/**
 * Main application scaffold: the navigation shell (bottom bar on compact widths; rail
 * and list-detail adaptation arrives with T052) hosting the root NavHost content. The
 * shell owns navigation chrome only — destination content is contributed by feature
 * screens as their slices land, so nothing here hardcodes feature logic
 * (plan.md §Navigation architecture).
 */
@Composable
fun MainScaffold(navController: NavHostController, content: @Composable (Modifier) -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route
            NavigationBar {
                PullDockDestination.Primary.navigationOrder.forEach { destination ->
                    val selected = currentRoute == destination.route
                    val label = stringResource(destination.titleRes)
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                // Single top-level instance per destination; back returns
                                // to the start destination instead of replaying history.
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            val icon = if (selected) destination.selectedIcon else destination.unselectedIcon
                            Icon(
                                imageVector = icon,
                                contentDescription = null, // the visible label suffices for TalkBack
                            )
                        },
                        label = { Text(label) },
                        alwaysShowLabel = true,
                        modifier = Modifier.semantics { contentDescription = label },
                    )
                }
            }
        },
    ) { innerPadding ->
        content(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

private val PullDockDestination.Primary.selectedIcon: ImageVector
    get() = when (this) {
        PullDockDestination.Primary.HOME -> Icons.Filled.Home
        PullDockDestination.Primary.DOWNLOADS -> Icons.Filled.Download
        PullDockDestination.Primary.LIBRARY -> Icons.Filled.LibraryMusic
        PullDockDestination.Primary.SETTINGS -> Icons.Filled.Settings
    }

private val PullDockDestination.Primary.unselectedIcon: ImageVector
    get() = when (this) {
        PullDockDestination.Primary.HOME -> Icons.Outlined.Home
        PullDockDestination.Primary.DOWNLOADS -> Icons.Outlined.Download
        PullDockDestination.Primary.LIBRARY -> Icons.Outlined.LibraryMusic
        PullDockDestination.Primary.SETTINGS -> Icons.Outlined.Settings
    }

/**
 * Slice 0 shell content for a primary destination: an honest empty state. Real screens
 * from `:feature:*` replace these as their slices land (T043/T045/T046/T047).
 */
@Composable
private fun ShellEmptyState(modifier: Modifier = Modifier, title: String, message: String) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(LocalPdTokens.current.spacing.md),
        verticalArrangement = Arrangement.spacedBy(LocalPdTokens.current.spacing.sm),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun ShellHomeContent(modifier: Modifier = Modifier) {
    ShellEmptyState(
        modifier = modifier,
        title = stringResource(R.string.shell_home_title),
        message = stringResource(R.string.shell_home_empty),
    )
}

@Composable
internal fun ShellDownloadsContent(modifier: Modifier = Modifier) {
    ShellEmptyState(
        modifier = modifier,
        title = stringResource(R.string.shell_downloads_title),
        message = stringResource(R.string.shell_downloads_empty),
    )
}

@Composable
internal fun ShellLibraryContent(modifier: Modifier = Modifier) {
    ShellEmptyState(
        modifier = modifier,
        title = stringResource(R.string.shell_library_title),
        message = stringResource(R.string.shell_library_empty),
    )
}

@Composable
internal fun ShellSettingsContent(modifier: Modifier = Modifier) {
    ShellEmptyState(
        modifier = modifier,
        title = stringResource(R.string.shell_settings_title),
        message = stringResource(R.string.shell_settings_empty),
    )
}
