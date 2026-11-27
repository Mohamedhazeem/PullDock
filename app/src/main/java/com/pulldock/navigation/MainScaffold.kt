package com.pulldock.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.pulldock.R

/**
 * Main application scaffold with adaptive navigation: bottom bar on compact widths,
 * navigation rail on medium/expanded widths. The shell owns navigation chrome only.
 */
@Composable
fun MainScaffold(
    navController: NavHostController,
    useRail: Boolean,
    showNavigation: Boolean = true,
    content: @Composable (Modifier) -> Unit,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val navItems = PullDockDestination.Primary.navigationOrder

    // The player is a full-screen surface: no nav bar/rail while it is open.
    if (currentRoute == com.pulldock.navigation.Routes.PLAYER) {
        content(Modifier.fillMaxSize())
        return
    }

    // Onboarding/consent gate: no navigation chrome until the user acknowledges it,
    // so Home/Library/Settings cannot be reached (and onboarding cannot be skipped).
    if (!showNavigation) {
        content(Modifier.fillMaxSize())
        return
    }

    if (useRail) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                navItems.forEach { destination ->
                    val selected = currentRoute == destination.route
                    val label = stringResource(destination.titleRes)
                    NavigationRailItem(
                        selected = selected,
                        onClick = { navController.navigateToPrimary(destination) },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        label = { Text(label) },
                        alwaysShowLabel = true,
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                        modifier = Modifier.semantics { contentDescription = label },
                    )
                }
            }
            content(Modifier.fillMaxSize())
        }
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                ) {
                    navItems.forEach { destination ->
                        val selected = currentRoute == destination.route
                        val label = stringResource(destination.titleRes)
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToPrimary(destination) },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            label = { Text(label) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
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

private val PullDockDestination.Primary.icon: ImageVector
    get() = when (this) {
        PullDockDestination.Primary.HOME -> Icons.Rounded.Home
        PullDockDestination.Primary.LIBRARY -> Icons.Rounded.LibraryMusic
        PullDockDestination.Primary.SETTINGS -> Icons.Rounded.Settings
    }

/**
 * Honest empty state for destinations whose screens arrive in later slices. Title plus
 * explanation; no dead actions.
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