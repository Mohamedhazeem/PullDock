package com.pulldock.core.designsystem.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Screen scaffold with the shared PullDock top bar and warm surface. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            PdTopAppBar(
                title = title,
                subtitle = subtitle,
                onBack = onBack,
                scrollBehavior = scrollBehavior,
                actions = actions,
            )
        },
        snackbarHost = {
            if (snackbarHostState != null) SnackbarHost(snackbarHostState)
        },
        floatingActionButton = floatingActionButton,
    ) { innerPadding ->
        content(innerPadding)
    }
}