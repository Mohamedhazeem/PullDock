package com.pulldock.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.pulldock.core.designsystem.components.PdErrorState
import com.pulldock.feature.player.R
import kotlinx.coroutines.delay

/**
 * Immersive player: edge-to-edge video, tap-to-toggle chrome, centre play, seek bar,
 * ±10s, speed/volume/brightness gestures, screen lock, rotation, fullscreen and PiP.
 * Playback position and play/pause state survive configuration changes.
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
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(10_000, 50_000, 1_500, 5_000)
                    .build(),
            )
            .build()
    }
    val uri = state.item?.fileUri?.let(::normalizeMediaUri)
    val resumeAt = state.resumePositionMs

    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var locked by rememberSaveable { mutableStateOf(false) }
    var speedMenuOpen by rememberSaveable { mutableStateOf(false) }
    var trackMenuOpen by rememberSaveable { mutableStateOf(false) }
    var playing by rememberSaveable { mutableStateOf(false) }
    var position by rememberSaveable { mutableLongStateOf(resumeAt) }
    var duration by rememberSaveable { mutableLongStateOf(0L) }
    var buffered by rememberSaveable { mutableFloatStateOf(0f) }
    var dragging by rememberSaveable { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var landscape by rememberSaveable { mutableStateOf(false) }
    var seekFeedback by remember { mutableStateOf<String?>(null) }
    var savedPositionMs by rememberSaveable { mutableLongStateOf(resumeAt) }
    var savedPlayWhenReady by rememberSaveable { mutableStateOf(true) }

    DisposableEffect(uri) {
        if (uri != null) {
            playbackError = null
            player.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
            player.playWhenReady = savedPlayWhenReady
            player.seekTo(savedPositionMs)
            player.prepare()
        }
        onDispose {
            if (uri != null) {
                savedPositionMs = player.currentPosition
                savedPlayWhenReady = player.playWhenReady
                viewModel.savePosition(player.currentPosition)
            }
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

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                savedPlayWhenReady = playWhenReady
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                savedPositionMs = newPosition.positionMs
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                savedPositionMs = player.currentPosition
                savedPlayWhenReady = player.playWhenReady
                player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(Unit) {
        onDispose { setOrientation(context, false) }
    }
    LaunchedEffect(uri) {
        while (true) {
            playing = player.isPlaying
            duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            if (!dragging) {
                position = player.currentPosition
                savedPositionMs = player.currentPosition
            }
            buffered = player.bufferedPercentage / 100f
            savedPlayWhenReady = player.playWhenReady
            if (player.isPlaying) viewModel.savePosition(player.currentPosition)
            delay(500)
        }
    }
    LaunchedEffect(playing, controlsVisible) {
        if (playing && controlsVisible) {
            delay(3_000)
            controlsVisible = false
        }
    }
    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(700)
            seekFeedback = null
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
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(CircleShape)
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
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { offset ->
                            if (!locked) {
                                // Seek only on the left/right thirds; the centre does nothing.
                                val third = size.width / 3
                                val delta = when {
                                    offset.x < third -> -10_000L
                                    offset.x > size.width - third -> 10_000L
                                    else -> 0L
                                }
                                if (delta != 0L) {
                                    val next = (player.currentPosition + delta)
                                        .coerceIn(0L, duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
                                    player.seekTo(next)
                                    seekFeedback = if (delta < 0) "−10s" else "+10s"
                                }
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

        AnimatedVisibility(
            visible = !locked && controlsVisible,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent),
                        ),
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.player_back),
                        tint = Color.White,
                    )
                }
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        AnimatedVisibility(
            visible = !locked && controlsVisible && playbackError == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            IconButton(
                onClick = {
                    if (player.isPlaying) player.pause() else player.playWhenReady = true
                },
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)),
            ) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.player_play_pause),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(52.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = seekFeedback != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(seekFeedback.orEmpty(), color = Color.White, fontWeight = FontWeight.Medium)
            }
        }

        if (!locked && controlsVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                        ),
                    )
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 40.dp),
            ) {
                Slider(
                    value = if (duration > 0) position.coerceAtMost(duration).toFloat() else 0f,
                    onValueChange = {
                        dragging = true
                        position = it.toLong()
                    },
                    onValueChangeFinished = {
                        player.seekTo(position)
                        dragging = false
                    },
                    valueRange = 0f..(duration.takeIf { it > 0 } ?: 1).toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.22f),
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatClock(position),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    if (duration > 0) {
                        Text(
                            text = formatClock(duration),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerIcon(Icons.Filled.Replay10, stringResource(R.string.player_rewind)) {
                        player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                    }
                    PlayerIcon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        stringResource(R.string.player_play_pause),
                    ) {
                        if (player.isPlaying) player.pause() else player.playWhenReady = true
                    }
                    PlayerIcon(Icons.Filled.Forward10, stringResource(R.string.player_forward)) {
                        player.seekTo(
                            (player.currentPosition + 10_000L)
                                .coerceAtMost(duration.takeIf { it > 0 } ?: Long.MAX_VALUE),
                        )
                    }
                    TextButtonOverlay(
                        stringResource(R.string.player_speed, player.playbackParameters.speed),
                    ) { speedMenuOpen = true }
                    PlayerIcon(Icons.Filled.VolumeUp, stringResource(R.string.player_volume)) {
                        // Volume is adjusted with the vertical side gesture.
                    }
                    val groups = player.currentTracks.groups.filter { group ->
                        (group.type == C.TRACK_TYPE_AUDIO || group.type == C.TRACK_TYPE_TEXT) &&
                            group.length > 1
                    }
                    if (groups.isNotEmpty()) {
                        TextButtonOverlay(stringResource(R.string.player_tracks)) {
                            trackMenuOpen = true
                        }
                    }
                    PlayerIcon(
                        if (landscape) Icons.Filled.Fullscreen else Icons.Filled.ScreenRotation,
                        stringResource(R.string.player_rotate),
                    ) {
                        landscape = !landscape
                        setOrientation(context, landscape)
                    }
                    PlayerIcon(Icons.Filled.Lock, stringResource(R.string.player_lock)) {
                        locked = true
                    }
                }
            }
        }

        if (locked) {
            IconButton(
                onClick = { locked = false },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
            ) {
                Icon(
                    Icons.Filled.LockOpen,
                    contentDescription = stringResource(R.string.player_unlock),
                    tint = Color.White,
                )
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
                    onClick = {
                        player.setPlaybackSpeed(speed)
                        speedMenuOpen = false
                    },
                )
            }
        }
        DropdownMenu(expanded = trackMenuOpen, onDismissRequest = { trackMenuOpen = false }) {
            player.currentTracks.groups.filter { group ->
                (group.type == C.TRACK_TYPE_AUDIO || group.type == C.TRACK_TYPE_TEXT) &&
                    group.length > 1
            }.forEach { group ->
                (0 until group.length).forEach { index ->
                    val format = group.mediaTrackGroup.getFormat(index)
                    DropdownMenuItem(
                        text = {
                            Text(format.language ?: format.label ?: "Track ${index + 1}")
                        },
                        onClick = {
                            player.setTrackSelectionParameters(
                                player.trackSelectionParameters.buildUpon()
                                    .setOverrideForType(
                                        TrackSelectionOverride(group.mediaTrackGroup, index),
                                    )
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
private fun PlayerIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
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

/**
 * Rotates only while the player is open. The activity is manifest-locked to portrait for
 * the app shell; the player explicitly requests landscape (full-screen, nav bar hidden),
 * then restores portrait on close. Using requestedOrientation (not a graphicsLayer) is
 * required because PlayerView renders on a SurfaceView, which ignores layer rotation.
 */
private fun setOrientation(context: Context, landscape: Boolean) {
    val activity = activityOf(context) ?: return
    activity.requestedOrientation = if (landscape) {
        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    } else {
        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}

/** Formats a millisecond duration as m:ss (e.g. 3:07). */
private fun formatClock(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}