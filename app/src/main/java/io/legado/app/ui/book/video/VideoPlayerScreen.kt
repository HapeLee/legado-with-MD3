package io.legado.app.ui.book.video

import android.content.pm.ActivityInfo
import android.view.View
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import io.legado.app.R
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

@Composable
fun VideoPlayerScreen(
    bookUrl: String,
    onBack: () -> Unit,
    viewModel: VideoPlayerViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val view = LocalView.current
    val controlsVisible = remember { mutableStateOf(true) }

    LaunchedEffect(bookUrl) {
        viewModel.init(bookUrl)
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is VideoPlayerEffect.ShowToast -> context.toastOnUi(effect.message)
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        // 配置变化（旋转/分屏）重建时 ON_STOP 也会派发，此时不应暂停
        if (activity?.isChangingConfigurations != true) {
            viewModel.onIntent(VideoPlayerIntent.Pause)
        }
    }

    DisposableEffect(Unit) {
        val previousKeepScreenOn = view.keepScreenOn
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = previousKeepScreenOn
            activity?.window?.let { window ->
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    LaunchedEffect(state.isFullscreen, activity) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (state.isFullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    PlayerView(context).apply {
                        useController = true
                        player = viewModel.exoPlayer
                        setControllerVisibilityListener(
                            PlayerView.ControllerVisibilityListener { visibility ->
                                controlsVisible.value = visibility == View.VISIBLE
                            }
                        )
                        setFullscreenButtonClickListener {
                            viewModel.onIntent(VideoPlayerIntent.ToggleFullscreen)
                        }
                    }
                },
                onRelease = {
                    it.player = null
                },
            )
            if (state.chapterTitle.isNotBlank()) {
                val titleAlpha by animateFloatAsState(
                    targetValue = if (controlsVisible.value) 1f else 0f,
                    label = "videoTitleAlpha",
                )
                Text(
                    text = "${state.bookName} · ${state.chapterTitle}",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .alpha(titleAlpha),
                )
            }
            if (state.error != null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = state.error ?: "", color = Color.White)
                    Button(
                        onClick = { viewModel.onIntent(VideoPlayerIntent.Retry) },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(text = stringResource(R.string.retry))
                    }
                }
            }
            if (state.chapterCount > 1) {
                AnimatedVisibility(
                    visible = controlsVisible.value,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Row(
                        modifier = Modifier.padding(bottom = 88.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Button(
                            onClick = { viewModel.onIntent(VideoPlayerIntent.PreviousChapter) },
                            enabled = state.chapterIndex > 0,
                        ) {
                            Text(text = stringResource(R.string.previous_chapter))
                        }
                        Button(
                            onClick = { viewModel.onIntent(VideoPlayerIntent.NextChapter) },
                            enabled = state.chapterIndex < state.chapterCount - 1,
                        ) {
                            Text(text = stringResource(R.string.next_chapter))
                        }
                    }
                }
            }
        }
    }
}
