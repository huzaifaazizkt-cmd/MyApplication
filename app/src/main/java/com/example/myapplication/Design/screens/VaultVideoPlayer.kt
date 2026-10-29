package com.example.myapplication.Design.screens

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.example.myapplication.R
import com.example.myapplication.security.AuthSession
import com.example.myapplication.vault.VaultMediaItem
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

@OptIn(UnstableApi::class)
internal fun createVaultVideoPlayer(context: Context): ExoPlayer =
    ExoPlayer.Builder(context.applicationContext)
        .setRenderersFactory(DefaultRenderersFactory(context.applicationContext).setEnableDecoderFallback(true))
        .build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            setHandleAudioBecomingNoisy(true)
        }

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VaultVideoPlayer(
    media: VaultMediaItem,
    active: Boolean,
    modifier: Modifier = Modifier,
    playerFactory: (Context) -> ExoPlayer = ::createVaultVideoPlayer
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val authenticated by AuthSession.isAuthenticated.collectAsState()
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val source = remember(media.vaultFilePath, media.uri) {
        media.vaultFilePath?.let { Uri.fromFile(File(it)) } ?: media.uri
    }
    var position by rememberSaveable(source.toString()) { mutableLongStateOf(0L) }
    var wantsPlayback by rememberSaveable(source.toString()) { mutableStateOf(true) }
    var duration by remember(source) { mutableLongStateOf(0L) }
    var seeking by remember(source) { mutableStateOf(false) }
    var failed by remember(source) { mutableStateOf(false) }
    var buffering by remember(source) { mutableStateOf(false) }
    val currentActive by rememberUpdatedState(active)
    fun canPlay() = currentActive && AuthSession.isAuthenticated.value &&
        lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    // Neighbouring pager pages must not allocate decoders or retain a stale video surface.
    val allowed = active && authenticated && resumed
    val player = if (allowed && !failed) remember(context, source, lifecycle) { playerFactory(context) } else null
    val currentPlayer by rememberUpdatedState(player)
    val currentWantsPlayback by rememberUpdatedState(wantsPlayback)

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            val isResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (!isResumed) currentPlayer?.pause()
            else if (currentWantsPlayback && canPlay()) currentPlayer?.play()
            resumed = isResumed
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                duration = player.duration.coerceAtLeast(0L)
                buffering = player.playbackState == Player.STATE_BUFFERING
                // Preparation and audio-focus callbacks also use the live privacy gate.
                if (!canPlay()) player.pause()
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    wantsPlayback = false
                    position = duration
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ||
                            reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)) wantsPlayback = false
            }

            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }
        }
        player?.apply {
            addListener(listener)
            setMediaItem(MediaItem.fromUri(source))
            seekTo(position)
            prepare()
        }
        onDispose {
            player?.let {
                if (!seeking) position = it.currentPosition.coerceAtLeast(0L)
                it.removeListener(listener)
                it.release()
            }
        }
    }
    LaunchedEffect(player, wantsPlayback) {
        // Pause on auth changes even while Compose has not yet removed the surface.
        if (player != null) AuthSession.isAuthenticated.collect { authenticatedNow ->
            player.playWhenReady = wantsPlayback && authenticatedNow && canPlay()
        }
    }
    LaunchedEffect(player) {
        while (player != null) {
            if (!seeking) position = player.currentPosition.coerceAtLeast(0L)
            delay(250)
        }
    }

    Column(modifier.background(Color.Black)) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (failed) Text(stringResource(R.string.video_playback_failed), color = Color.White)
            else if (player != null) {
                // TextureView stays in Compose's layer instead of punching a SurfaceView hole
                // through the moving pager. ContentFrame covers it until the first real frame.
                ContentFrame(player, Modifier.fillMaxSize(), surfaceType = SURFACE_TYPE_TEXTURE_VIEW)
                if (buffering) CircularProgressIndicator(color = Color.White)
            }
        }
        // Controls keep their own space above Unhide, including while buffering or paused.
        if (!failed) Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            val showPause = player != null && wantsPlayback
            IconButton(enabled = player != null && duration > 0, onClick = {
                wantsPlayback = !wantsPlayback
                if (wantsPlayback && (player?.playbackState == Player.STATE_ENDED || position >= duration)) {
                    position = 0L
                    player?.seekTo(0L)
                }
            }) {
                Icon(if (showPause) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(if (showPause) R.string.video_pause else R.string.video_play),
                    tint = Color.White)
            }
            val seekEnabled = player != null && duration > 0 && player.isCurrentMediaItemSeekable
            val appBlue = Color(0xFF0396FF)
            val seekColors = SliderDefaults.colors(
                thumbColor = appBlue,
                activeTrackColor = appBlue,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                disabledThumbColor = appBlue.copy(alpha = 0.38f),
                disabledActiveTrackColor = appBlue.copy(alpha = 0.38f),
                disabledInactiveTrackColor = Color.White.copy(alpha = 0.12f)
            )
            val seekInteraction = remember { MutableInteractionSource() }
            Slider(value = position.coerceIn(0L, duration).toFloat(),
                onValueChange = { seeking = true; position = it.toLong() },
                onValueChangeFinished = { player?.seekTo(position); seeking = false },
                enabled = seekEnabled,
                colors = seekColors,
                interactionSource = seekInteraction,
                thumb = {
                    SliderDefaults.Thumb(
                        interactionSource = seekInteraction,
                        colors = seekColors,
                        enabled = seekEnabled,
                        thumbSize = DpSize(12.dp, 12.dp)
                    )
                },
                track = { state ->
                    SliderDefaults.Track(
                        sliderState = state,
                        modifier = Modifier.height(4.dp),
                        enabled = seekEnabled,
                        colors = seekColors,
                        thumbTrackGapSize = 0.dp,
                        drawStopIndicator = null
                    )
                },
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp))
            Text(String.format(Locale.ROOT, "%d:%02d", position / 60_000, position / 1_000 % 60), color = Color.White)
        }
    }
}
