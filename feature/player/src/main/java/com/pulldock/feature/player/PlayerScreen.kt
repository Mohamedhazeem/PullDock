package com.pulldock.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
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
import com.pulldock.core.designsystem.components.PdErrorState
import com.pulldock.feature.player.R
import kotlinx.coroutines.delay

/**
 * Immersive player (MX-style): edge-to-edge video, tap-to-toggle chrome, centre play,
 * seek bar with time, ±10s, speed/volume/brightness gestures, screen lock, rotation,
 * fullscreen and PiP. Position is persisted throttled (FR-044/US5).
 */
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
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

    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    var trackMenuOpen by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var buffered by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var landscape by remember { mutableStateOf(false) }

    DisposableEffect(uri) {
        if (uri != null) {
            playbackError = null
            player.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
            if (resumeAt > 0L) player.seekTo(resumeAt)
            player.prepare()
            player.playWhenReady = true
        }
        onDispose {
            if (uri != null) viewModel.savePosition(player.currentPosition)
            player.stop()
        }
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.message ?: "Playback failed"
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(uri) {
        while (true) {
            playing = player.isPlaying
            duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            if (!dragging) position = player.currentPosition
            buffered = player.bufferedPercentage / 100f
            if (player.isPlaying) viewModel.savePosition(player.currentPosition)
            delay(500)
        }
    }

    if (state.loading) {
        Text(stringResource(R.string.player_loading), modifier = Modifier.padding(16.dp))
        return
    }
    val item = state.item
    if (item == null || !item.fileAvailable) {
        androidx.compose.material3.Scaffold { pad ->
            PdErrorState(
                title = stringResource(R.string.player_missing_title),
                message = stringResource(R.string.player_missing_body),
                modifier = Modifier.padding(pad),
            )
        }
        return
    }

    val isAudio = item.fileUri.hasAudioExtension()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (isAudio) {
            // Music mode: no video surface; artwork placeholder + track title.
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(104.dp),
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    item.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Text(
                    stringResource(R.string.player_audio_mode),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Tap toggles chrome; double-tap seeks ±10s; vertical drag adjusts brightness/volume.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { offset ->
                            if (!locked) {
                                val delta = if (offset.x < size.width / 2) -10_000L else 10_000L
                                player.seekTo((player.currentPosition + delta).coerceIn(0L, duration.takeIf { it > 0 } ?: Long.MAX_VALUE))
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        if (locked) return@detectVerticalDragGestures
                        if (change.position.x < size.width / 2) {
                            adjustBrightness(context, -dragAmount)
                        } else {
                            adjustVolume(context, -dragAmount)
                        }
                    }
                },
        )

        if (!locked && controlsVisible) {
            // Top chrome
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.player_back), tint = Color.White)
                }
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }

            // Centre play/pause
            if (!playing && playbackError == null) {
                IconButton(
                    onClick = { player.playWhenReady = true },
                    modifier = Modifier.align(Alignment.Center).size(72.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.player_play), tint = Color.White, modifier = Modifier.size(64.dp))
                }
            }

            // Bottom chrome
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Slider(
                    value = if (duration > 0) position.coerceAtMost(duration).toFloat() else 0f,
                    onValueChange = { dragging = true; position = it.toLong() },
                    onValueChangeFinished = {
                        player.seekTo(position)
                        dragging = false
                    },
                    valueRange = 0f..(duration.takeIf { it > 0 } ?: 1).toFloat(),
                )
                Text(
                    text = "${formatTime(position)} / ${formatTime(duration)}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerIcon(Icons.Filled.Replay10, stringResource(R.string.player_rewind)) {
                        player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                    }
                    PlayerIcon(Icons.Filled.PlayArrow, stringResource(R.string.player_play_pause)) {
                        if (player.isPlaying) player.pause() else player.playWhenReady = true
                    }
                    PlayerIcon(Icons.Filled.Forward10, stringResource(R.string.player_forward)) {
                        player.seekTo((player.currentPosition + 10_000L).coerceAtMost(duration.takeIf { it > 0 } ?: Long.MAX_VALUE))
                    }
                    TextButtonOverlay(stringResource(R.string.player_speed, player.playbackParameters.speed)) { speedMenuOpen = true }
                    PlayerIcon(Icons.Filled.VolumeUp, stringResource(R.string.player_volume)) { /* volume via side gesture */ }
                    val groups = player.currentTracks.groups.filter { g ->
                        (g.type == C.TRACK_TYPE_AUDIO || g.type == C.TRACK_TYPE_TEXT) && g.length > 1
                    }
                    if (groups.isNotEmpty()) {
                        TextButtonOverlay(stringResource(R.string.player_tracks)) { trackMenuOpen = true }
                    }
                    PlayerIcon(if (landscape) Icons.Filled.Fullscreen else Icons.Filled.ScreenRotation, stringResource(R.string.player_rotate)) {
                        landscape = !landscape
                        setOrientation(context, landscape)
                    }
                    PlayerIcon(Icons.Filled.Lock, stringResource(R.string.player_lock)) { locked = true }
                }
            }
        }

        if (locked) {
            IconButton(
                onClick = { locked = false },
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            ) {
                Icon(Icons.Filled.LockOpen, contentDescription = stringResource(R.string.player_unlock), tint = Color.White)
            }
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
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(16.dp),
            )
        }

        DropdownMenu(expanded = speedMenuOpen, onDismissRequest = { speedMenuOpen = false }) {
            listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                DropdownMenuItem(
                    text = { Text("${speed}x") },
                    onClick = { player.setPlaybackSpeed(speed); speedMenuOpen = false },
                )
            }
        }
        DropdownMenu(expanded = trackMenuOpen, onDismissRequest = { trackMenuOpen = false }) {
            player.currentTracks.groups.filter { g ->
                (g.type == C.TRACK_TYPE_AUDIO || g.type == C.TRACK_TYPE_TEXT) && g.length > 1
            }.forEach { group ->
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
}

@Composable
private fun PlayerIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = label, tint = Color.White)
    }
}

@Composable
private fun TextButtonOverlay(label: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) {
        Text(label, color = Color.White, fontWeight = FontWeight.Medium)
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

private fun String.hasAudioExtension(): Boolean =
    listOf("mp3", "m4a", "aac", "flac", "ogg", "wav", "opus", "wma")
        .any { endsWith(".$it", ignoreCase = true) }

private fun normalizeMediaUri(uri: String): String =
    if (uri.startsWith("file:/", ignoreCase = true) && !uri.startsWith("file:///", ignoreCase = true)) {
        "file://" + uri.removePrefix("file:")
    } else {
        uri
    }

private fun activityOf(context: Context): Activity? = generateSequence(context) { current ->
    (current as? ContextWrapper)?.baseContext
}.filterIsInstance<Activity>().firstOrNull()

private fun adjustBrightness(context: Context, delta: Float) {
    val activity = activityOf(context) ?: return
    val params = activity.window.attributes
    val next = (params.screenBrightness + delta / 2000f).coerceIn(0.01f, 1f)
    activity.window.attributes = params.apply { screenBrightness = next }
}

private fun adjustVolume(context: Context, delta: Float) {
    val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    if (kotlin.math.abs(delta) > 2f) {
        manager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (delta > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            0,
        )
    }
}

private fun setOrientation(context: Context, landscape: Boolean) {
    val activity = activityOf(context) ?: return
    activity.requestedOrientation = if (landscape) {
        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    } else {
        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}
