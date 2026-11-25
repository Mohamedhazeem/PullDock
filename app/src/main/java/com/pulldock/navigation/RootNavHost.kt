package com.pulldock.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pulldock.R
import com.pulldock.core.designsystem.components.PdLoadingState
import com.pulldock.domain.download.repository.ConsentState
import com.pulldock.domain.download.usecase.ObserveConsent
import com.pulldock.feature.add.AddDownloadScreen
import com.pulldock.feature.downloaddetail.DownloadDetailScreen
import com.pulldock.feature.home.HomeScreen
import com.pulldock.feature.legal.LegalScreen
import com.pulldock.feature.library.LibraryScreen
import com.pulldock.feature.onboarding.OnboardingScreen
import com.pulldock.feature.player.PlayerScreen
import com.pulldock.feature.settings.SettingsScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Root navigation graph. The consent gate decides the start destination; download
 * actions are unreachable until the current legal version is acknowledged.
 */
@Composable
fun RootNavHost(
    modifier: Modifier = Modifier,
    useRail: Boolean,
    pendingSource: String? = null,
    onPendingSourceConsumed: () -> Unit = {},
) {
    val navController: NavHostController = rememberNavController()
    val rootViewModel: RootViewModel = hiltViewModel()
    val consent by rootViewModel.consent.collectAsStateWithLifecycle()

    val resolved = consent
    if (resolved == null) {
        PdLoadingState(label = stringResource(R.string.root_loading))
        return
    }

    // A magnet/file opened from a browser or share sheet navigates straight to Add.
    // Consent comes first; the source is consumed exactly once.
    LaunchedEffect(pendingSource, resolved?.isAcknowledged) {
        val source = pendingSource
        if (source != null && resolved.isAcknowledged) {
            navController.navigate(Routes.addSource(source))
            onPendingSourceConsumed()
        }
    }

    MainScaffold(navController = navController, useRail = useRail) { contentModifier ->
        NavHost(
            navController = navController,
            startDestination = if (resolved.isAcknowledged) {
                PullDockDestination.Primary.HOME.route
            } else {
                Routes.ONBOARDING
            },
            modifier = modifier.then(contentModifier),
            enterTransition = {
                fadeIn(tween(180)) + slideInHorizontally(tween(240)) { it / 12 }
            },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(180)) },
            popExitTransition = {
                fadeOut(tween(120)) + slideOutHorizontally(tween(240)) { it / 12 }
            },
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onAcknowledged = {
                        navController.navigate(PullDockDestination.Primary.HOME.route) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            composable(PullDockDestination.Primary.HOME.route) {
                HomeScreen(
                    onAddDownload = { navController.navigate(Routes.ADD) },
                    onOpenTask = { id -> navController.navigate(Routes.download(id)) },
                )
            }
            composable(PullDockDestination.Primary.LIBRARY.route) {
                LibraryScreen(
                    onPlay = { itemId -> navController.navigate(Routes.player(itemId)) },
                    onAddDownload = { navController.navigate(Routes.ADD) },
                )
            }
            composable(PullDockDestination.Primary.SETTINGS.route) {
                SettingsScreen(onOpenLegal = { navController.navigate(Routes.LEGAL) })
            }
            composable(Routes.ADD) {
                AddDownloadScreen(
                    onClose = navController::navigateUp,
                    onDismiss = navController::navigateUp,
                )
            }
            composable(
                route = Routes.ADD_WITH_SOURCE,
                arguments = listOf(navArgument("source") { type = NavType.StringType }),
            ) {
                AddDownloadScreen(
                    onClose = navController::navigateUp,
                    onDismiss = navController::navigateUp,
                )
            }
            composable(Routes.LEGAL) {
                LegalScreen(onBack = navController::navigateUp)
            }
            composable(
                route = Routes.DOWNLOAD_DETAIL,
                arguments = listOf(navArgument("taskId") { type = NavType.StringType }),
            ) {
                DownloadDetailScreen(onNavigateBack = navController::navigateUp)
            }
            composable(
                route = Routes.PLAYER,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
            ) {
                PlayerScreen(onBack = navController::navigateUp)
            }
        }
    }
}

/** Route constants and builders; route strings are deep-link stable. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val ADD = "add"
    const val ADD_WITH_SOURCE = "add?source={source}"
    const val LEGAL = "legal"
    const val DOWNLOAD_DETAIL = "download/{taskId}"
    const val PLAYER = "player/{itemId}"

    fun addSource(source: String): String =
        "add?source=${android.net.Uri.encode(source)}"

    fun download(taskId: String): String = "download/$taskId"

    fun player(itemId: String): String = "player/$itemId"
}

/** Root-level consent observation for the navigation gate. */
@HiltViewModel
class RootViewModel @Inject constructor(observeConsent: ObserveConsent) : ViewModel() {
    val consent: StateFlow<ConsentState?> = observeConsent()
        .map<ConsentState, ConsentState?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}