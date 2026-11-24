package com.pulldock.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.pulldock.core.designsystem.components.PdEmptyState
import com.pulldock.core.designsystem.components.PdErrorState
import com.pulldock.core.designsystem.theme.LocalPdTokens
import kotlinx.coroutines.delay

/**
 * Production playback screen (T157/T158/T165, US5/US6, FR-044):
 * Media3 ExoPlayer with seek/skip, speed, volume, fullscreen, conditional track
 * selection, error recovery, throttled position persistence and lifecycle-aware pausing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val tokens = LocalPdTokens.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember {
        ExoPlayer.Builder(context)
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(10_000, 50_000, 1_500, 5_000).build())
            .build()
    }
    val uri = state.item?.fileUri?.let(::normalizeMediaUri)
    val resumeAt = state.resumePositionMs
    var trackMenuOpen by remember { mutableStateOf(false) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(uri) {
        if (uri != null) {
            playbackError = null
            player.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
            if (resumeAt > 0L) player.seekTo(resumeAt)
            player.prepare()
            player.playWhenReady = true
        }
        onDispose {
            viewModel.savePosition(player.currentPosition)
            player.stop()
            player.release()
        }
    }

    // Typed playback errors: never a blank/frozen player.
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.message ?: "Playback failed"
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Pause when the app goes to background; resume is explicit via the play button.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Throttled position persistence (FR-044/US5 acceptance 2).
    LaunchedEffect(uri) {
        while (true) {
            delay(5_000)
            if (player.isPlaying) viewModel.savePosition(player.currentPosition)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.item?.title ?: stringResource(R.string.player_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (fullscreen) {
                            toggleFullscreen(context, false)
                            fullscreen = false
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.player_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        if (state.loading) {
            Text(stringResource(R.string.player_loading), modifier = Modifier.padding(innerPadding))
        } else if (state.item == null || !state.item.fileAvailable) {
            PdEmptyState(
                title = stringResource(R.string.player_missing_title),
                message = stringResource(R.string.player_missing_body),
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(tokens.spacing.md),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
            ) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = player
                                useController = true
                                controllerShowTimeoutMs = 4_000
                                setShowNextButton(false)
                                setShowPreviousButton(false)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                playbackError?.let { error ->
                    PdErrorState(
                        title = stringResource(R.string.player_error_title),
                        message = error,
                        retryLabel = stringResource(R.string.player_retry),
                        onRetry = {
                            playbackError = null
                            player.clearMediaItems()
                            uri?.let { player.setMediaItem(MediaItem.fromUri(Uri.parse(it))) }
                            player.prepare()
                            player.playWhenReady = true
                        },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { player.seekBack() }) {
                        Icon(Icons.Filled.Replay10, contentDescription = stringResource(R.string.player_rewind))
                    }
                    IconButton(onClick = { player.seekForward() }) {
                        Icon(Icons.Filled.Forward10, contentDescription = stringResource(R.string.player_forward))
                    }
                    TextButton(onClick = { speedMenuOpen = true }) {
                        Text(stringResource(R.string.player_speed, player.playbackParameters.speed))
                    }
                    DropdownMenu(expanded = speedMenuOpen, onDismissRequest = { speedMenuOpen = false }) {
                        listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                            DropdownMenuItem(
                                text = { Text("${speed}x") },
                                onClick = {
                                    player.setPlaybackSpeed(speed)
                                    speedMenuOpen = false
                                },
                            )
                        }
                    }
                    IconButton(onClick = {
                        muted = !muted
                        player.volume = if (muted) 0f else 1f
                    }) {
                        Icon(
                            if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                            contentDescription = stringResource(R.string.player_volume),
                        )
                    }
                    val trackGroups = player.currentTracks.groups.filter { group ->
                        (group.type == C.TRACK_TYPE_AUDIO || group.type == C.TRACK_TYPE_TEXT) && group.length > 1
                    }
                    if (trackGroups.isNotEmpty()) {
                        TextButton(onClick = { trackMenuOpen = true }) {
                            Text(stringResource(R.string.player_tracks))
                        }
                        DropdownMenu(expanded = trackMenuOpen, onDismissRequest = { trackMenuOpen = false }) {
                            trackGroups.forEach { group ->
                                (0 until group.length).forEach { index ->
                                    val format = group.mediaTrackGroup.getFormat(index)
                                    DropdownMenuItem(
                                        text = { Text(format.language ?: format.label ?: "Track ${index + 1}") },
                                        onClick = {
                                            player.setTrackSelectionParameters(
                                                player.trackSelectionParameters.buildUpon()
                                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                                                    .build(),
                                            )
                                            trackMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = {
                        toggleFullscreen(context, !fullscreen)
                        fullscreen = !fullscreen
                    }) {
                        Icon(Icons.Filled.Fullscreen, contentDescription = stringResource(R.string.player_fullscreen))
                    }
                }
            }
        }
    }
}

/** Media3 accepts file:/…, but normalize to file:///… for all AndroidDataSource paths. */
private fun normalizeMediaUri(uri: String): String =
    if (uri.startsWith("file:/", ignoreCase = true) && !uri.startsWith("file:///", ignoreCase = true)) {
        "file://" + uri.removePrefix("file:")
    } else {
        uri
    }

private fun toggleFullscreen(context: Context, enable: Boolean) {
    val activity = generateSequence(context) { current ->
        (current as? ContextWrapper)?.baseContext
    }.filterIsInstance<Activity>().firstOrNull() ?: return
    if (enable) {
        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)?.hide(
            WindowInsetsControllerCompat.BOTTOM_SYSTEM_BARS or WindowInsetsControllerCompat.TOP_SYSTEM_BARS,
        )
    } else {
        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)?.show(
            WindowInsetsControllerCompat.BOTTOM_SYSTEM_BARS or WindowInsetsControllerCompat.TOP_SYSTEM_BARS,
        )
    }
}
