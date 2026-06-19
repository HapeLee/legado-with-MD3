package io.legado.app.ui.book.read.video

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import io.legado.app.R
import io.legado.app.help.http.okHttpClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VideoPlaybackState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isLoading: Boolean = false
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class VideoPlayerManager(
    private val context: android.content.Context
) {
    private var exoPlayer: ExoPlayer? = null
    private val _state = MutableStateFlow(VideoPlaybackState())
    val state: StateFlow<VideoPlaybackState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)
            _state.value = _state.value.copy(
                isLoading = playbackState == Player.STATE_BUFFERING
            )
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            _state.value = _state.value.copy(isPlaying = isPlaying)
        }
    }

    fun getPlayer(): ExoPlayer {
        if (exoPlayer == null) {
            val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            val mediaSourceFactory = DefaultMediaSourceFactory(context)
                .setDataSourceFactory(dataSourceFactory)
            exoPlayer = ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .also { it.addListener(listener) }
        }
        return exoPlayer!!
    }

    fun updatePositionTracking() {
        exoPlayer?.let {
            _state.value = _state.value.copy(
                currentPositionMs = it.currentPosition,
                durationMs = it.duration.takeIf { d -> d > 0 } ?: _state.value.durationMs
            )
        }
    }

    fun setMediaItem(url: String, headers: Map<String, String> = emptyMap()) {
        val mediaItem = MediaItem.fromUri(Uri.parse(url))
        getPlayer().setMediaItem(mediaItem)
        getPlayer().prepare()
    }

    fun play() {
        getPlayer().play()
    }

    fun pause() {
        getPlayer().pause()
    }

    fun seekTo(positionMs: Long) {
        getPlayer().seekTo(positionMs)
    }

    fun release() {
        exoPlayer?.removeListener(listener)
        exoPlayer?.release()
        exoPlayer = null
    }

    fun getCurrentPosition(): Long = exoPlayer?.currentPosition ?: 0L
    fun getDuration(): Long = exoPlayer?.duration?.takeIf { it > 0 } ?: 0L
}

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayer(
    videoUrl: String,
    posterUrl: String? = null,
    modifier: Modifier = Modifier,
    onError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val manager = remember { VideoPlayerManager(context) }
    val playbackState by manager.state.collectAsState()
    var controlsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(videoUrl) {
        try {
            manager.setMediaItem(videoUrl)
        } catch (e: Exception) {
            onError(e.message ?: "视频加载失败")
        }
        while (true) {
            manager.updatePositionTracking()
            delay(500)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            manager.release()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).also {
                    it.player = manager.getPlayer()
                    it.useController = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (playbackState.isLoading) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (controlsVisible || !playbackState.isPlaying) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(8.dp),
                color = ComposeColor.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    val posMs = playbackState.currentPositionMs.coerceAtLeast(0L)
                    val durMs = playbackState.durationMs.coerceAtLeast(1L)
                    val progress = if (durMs > 0) (posMs.toFloat() / durMs) else 0f

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = formatDuration(posMs),
                            color = ComposeColor.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Slider(
                            value = progress,
                            onValueChange = { newProgress ->
                                manager.seekTo((newProgress * durMs).toLong())
                            },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = ComposeColor.White.copy(alpha = 0.3f)
                            )
                        )
                        Text(
                            text = formatDuration(durMs),
                            color = ComposeColor.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (playbackState.isPlaying) manager.pause() else manager.play()
                        }) {
                            Icon(
                                painter = if (playbackState.isPlaying) {
                                    painterResource(R.drawable.ic_pause)
                                } else {
                                    painterResource(R.drawable.ic_play)
                                },
                                contentDescription = null,
                                tint = ComposeColor.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
