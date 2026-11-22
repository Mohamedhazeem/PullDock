package com.pulldock.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.pulldock.R

/**
 * Main application scaffold with adaptive navigation (T052, FR-066): bottom bar on
 * compact widths, navigation rail on medium/expanded widths. The shell owns navigation
 * chrome only — destination content is contributed by feature screens.
 */
@Composable
fun MainScaffold(
    navController: NavHostController,
    useRail: Boolean,
    content: @Composable (Modifier) -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val navItems = PullDockDestination.Primary.navigationOrder

    if (useRail) {
        Row(modifier = Modifier.fillMaxSize()) {
            // material3 1.4.0: rail content is ColumnScope; items are ColumnScope extensions.
            NavigationRail {
                navItems.forEach { destination ->
                    val selected = currentRoute == destination.route
                    val label = stringResource(destination.titleRes)
                    NavigationRailItem(
                        selected = selected,
                        onClick = { navController.navigateToPrimary(destination) },
                        icon = {
                            Icon(
                                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = null, // the visible label suffices for TalkBack
                            )
                        },
                        label = { Text(label) },
                        alwaysShowLabel = true,
                        modifier = Modifier.semantics { contentDescription = label },
                    )
                }
            }
            content(Modifier.fillMaxSize())
        }
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                // material3 1.4.0: bar content is RowScope; items are RowScope extensions.
                NavigationBar {
                    navItems.forEach { destination ->
                        val selected = currentRoute == destination.route
                        val label = stringResource(destination.titleRes)
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToPrimary(destination) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = null,
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
}

/** Single top-level instance per destination; state preserved across switches. */
private fun NavHostController.navigateToPrimary(destination: PullDockDestination.Primary) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
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
 * Honest empty state for destinations whose screens arrive in later slices (Library
 * until Slice 6). Title plus explanation; no dead actions.
 */
@Composable
internal fun ShellEmptyState(title: String, message: String) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(com.pulldock.core.designsystem.theme.LocalPdTokens.current.spacing.md),
    ) {
        androidx.compose.material3.Text(text = title, style = MaterialTheme.typography.headlineSmall)
        androidx.compose.material3.Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
