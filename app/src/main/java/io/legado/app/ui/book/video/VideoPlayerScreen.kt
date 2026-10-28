package io.legado.app.ui.book.video

import android.content.Intent
import android.content.pm.ActivityInfo
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import io.legado.app.constant.BookType
import io.legado.app.model.SourceCallBack
import io.legado.app.model.VideoPlay
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel

private val speedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 2.5f, 3f)

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
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(bookUrl) {
        viewModel.init(bookUrl)
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is VideoPlayerEffect.ShowToast -> context.toastOnUi(effect.message)
                is VideoPlayerEffect.CopyPlayUrl -> copyPlayUrl(context, effect.url)
                is VideoPlayerEffect.OpenExternalPlayer -> openExternalPlayer(
                    context, effect.url, effect.title
                )
            }
        }
    }

    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        // 配置变化（旋转/分屏）重建时 ON_STOP 也会派发，此时不应暂停
        if (activity?.isChangingConfigurations != true) {
            viewModel.onIntent(VideoPlayerIntent.Pause)
            // 离开应用后画面交给悬浮小窗，避免两个 PlayerView 同时抢同一个 player 的渲染
            playerViewRef?.player = null
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        playerViewRef?.player = VideoPlay.player
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
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = true
                        player = VideoPlay.player
                        setControllerVisibilityListener(
                            PlayerView.ControllerVisibilityListener { visibility ->
                                controlsVisible.value = visibility == View.VISIBLE
                            }
                        )
                        setFullscreenButtonClickListener {
                            viewModel.onIntent(VideoPlayerIntent.ToggleFullscreen)
                        }
                        setupLongPressSpeed(viewModel)
                    }.also { playerViewRef = it }
                },
                onRelease = {
                    it.player = null
                    playerViewRef = null
                },
            )

            VideoTopBar(
                title = listOf(state.bookName, state.chapterTitle)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                visible = controlsVisible.value,
                onBack = onBack,
                menuExpanded = menuExpanded,
                onMenuExpand = { menuExpanded = true },
                onMenuDismiss = { menuExpanded = false },
                onCopyUrl = { viewModel.onIntent(VideoPlayerIntent.CopyPlayUrl) },
                onOpenExternal = { viewModel.onIntent(VideoPlayerIntent.OpenInExternalPlayer) },
            )

            if (state.error != null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = state.error ?: "", color = Color.White)
                    TextButton(
                        onClick = { viewModel.onIntent(VideoPlayerIntent.Retry) },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(text = stringResource(R.string.retry))
                    }
                }
            }

            if (state.hasEpisodeList) {
                VideoEpisodeBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    visible = controlsVisible.value,
                    hasPrevious = state.episodeIndex > 0 || state.volumeIndex > 0,
                    hasNext = state.episodeIndex < state.episodes.lastIndex ||
                        state.volumeIndex < state.volumes.lastIndex,
                    speed = state.speed,
                    onPrevious = { viewModel.onIntent(VideoPlayerIntent.PreviousEpisode) },
                    onNext = { viewModel.onIntent(VideoPlayerIntent.NextEpisode) },
                    onOpenEpisodes = {
                        viewModel.onIntent(VideoPlayerIntent.OpenSheet(VideoPlayerSheet.EPISODES))
                    },
                    onOpenSpeed = {
                        viewModel.onIntent(VideoPlayerIntent.OpenSheet(VideoPlayerSheet.SPEED))
                    },

                    onOpenSettings = {
                        viewModel.onIntent(VideoPlayerIntent.OpenSheet(VideoPlayerSheet.SETTINGS))
                    },
                    onToggleFloating = { viewModel.onIntent(VideoPlayerIntent.ToggleFloating) },
                )
            }
        }
    }

    when (state.sheet) {
        VideoPlayerSheet.EPISODES -> EpisodeSheet(
            volumes = state.volumes,
            volumeIndex = state.volumeIndex,
            episodes = state.episodes,
            episodeIndex = state.episodeIndex,
            onSelectVolume = { viewModel.onIntent(VideoPlayerIntent.SelectVolume(it)) },
            onSelectEpisode = { viewModel.onIntent(VideoPlayerIntent.SelectEpisode(it)) },
            onDismiss = { viewModel.onIntent(VideoPlayerIntent.CloseSheet) },
        )

        VideoPlayerSheet.SPEED -> SpeedSheet(
            current = state.speed,
            onSelect = { viewModel.onIntent(VideoPlayerIntent.SetSpeed(it)) },
            onDismiss = { viewModel.onIntent(VideoPlayerIntent.CloseSheet) },
        )

        VideoPlayerSheet.SETTINGS -> VideoSettingsSheet(
            autoPlay = state.autoPlay,
            startFull = state.startFull,
            longPressSpeed = state.longPressSpeed,
            showDanmaku = state.showDanmaku,
            onAutoPlayChange = {
                viewModel.onIntent(VideoPlayerIntent.SetAutoPlay(it))
            },
            onStartFullChange = {
                viewModel.onIntent(VideoPlayerIntent.SetStartFull(it))
            },
            onLongPressSpeedChange = {
                viewModel.onIntent(VideoPlayerIntent.SetLongPressSpeed(it))
            },
            onDanmakuChange = { viewModel.onIntent(VideoPlayerIntent.ToggleDanmaku) },
            floatingEnabled = state.floatingEnabled,
            onFloatingChange = {
                viewModel.onIntent(VideoPlayerIntent.SetFloatingEnabled(it))
            },
            onDismiss = { viewModel.onIntent(VideoPlayerIntent.CloseSheet) },
        )

        null -> Unit
    }
}

/** 长按倍速：只监听不消费事件，PlayerView 自己的控制条手势不受影响。 */
private fun PlayerView.setupLongPressSpeed(viewModel: VideoPlayerViewModel) {
    val detector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                viewModel.onIntent(VideoPlayerIntent.LongPressSpeed(true))
            }
        },
    )
    setOnTouchListener { _, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                viewModel.onIntent(VideoPlayerIntent.LongPressSpeed(false))
        }
        detector.onTouchEvent(event)
        false
    }
}

private fun copyPlayUrl(context: android.content.Context, url: String) {
    val book = VideoPlay.book
    val source = VideoPlay.bookSource
    val activity = context as? AppCompatActivity
    if (book != null && source != null && activity != null) {
        SourceCallBack.callBackBtn(
            activity = activity,
            event = SourceCallBack.CLICK_COPY_PLAY_URL,
            source = source,
            book = book,
            chapter = VideoPlay.chapter,
            bookType = BookType.video,
            result = url,
        ) {
            context.sendToClip(url)
        }
        return
    }
    context.sendToClip(url)
}

private fun openExternalPlayer(
    context: android.content.Context,
    url: String,
    title: String?,
) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(android.net.Uri.parse(url), "video/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }.onFailure {
        context.toastOnUi(it.localizedMessage ?: context.getString(R.string.load_failed))
    }
}

@Composable
private fun VideoTopBar(
    title: String,
    visible: Boolean,
    onBack: () -> Unit,
    menuExpanded: Boolean,
    onMenuExpand: () -> Unit,
    onMenuDismiss: () -> Unit,
    onCopyUrl: () -> Unit,
    onOpenExternal: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Text(text = "‹", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            text = title,
            color = Color.White,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            maxLines = 1,
            style = MaterialTheme.typography.titleMedium,
        )
        Box {
            IconButton(onClick = onMenuExpand) {
                Text(text = "⋮", color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = onMenuDismiss) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.copy_play_url)) },
                    onClick = {
                        onMenuDismiss()
                        onCopyUrl()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.open_other_video_player)) },
                    onClick = {
                        onMenuDismiss()
                        onOpenExternal()
                    },
                )
            }
        }
    }
}

@Composable
private fun VideoEpisodeBar(
    modifier: Modifier,
    visible: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    speed: Float,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenEpisodes: () -> Unit,
    onOpenSpeed: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleFloating: () -> Unit,
) {
    if (!visible) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 72.dp, start = 12.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onPrevious, enabled = hasPrevious) {
            Text(text = stringResource(R.string.previous_chapter), color = Color.White)
        }
        TextButton(onClick = onOpenEpisodes) {
            Text(text = stringResource(R.string.select_episode), color = Color.White)
        }
        TextButton(onClick = onOpenSpeed) {
            Text(text = "${speed}x", color = Color.White)
        }
        TextButton(onClick = onOpenSettings) {
            Text(text = stringResource(R.string.config_settings), color = Color.White)
        }
        TextButton(onClick = onToggleFloating) {
            Text(text = stringResource(R.string.video_floating), color = Color.White)
        }
        TextButton(onClick = onNext, enabled = hasNext) {
            Text(text = stringResource(R.string.next_chapter), color = Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpisodeSheet(
    volumes: List<String>,
    volumeIndex: Int,
    episodes: List<String>,
    episodeIndex: Int,
    onSelectVolume: (Int) -> Unit,
    onSelectEpisode: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (volumes.size > 1) {
                TabRow(selectedTabIndex = volumeIndex.coerceIn(0, volumes.lastIndex)) {
                    volumes.forEachIndexed { index, title ->
                        Tab(
                            selected = index == volumeIndex,
                            onClick = { onSelectVolume(index) },
                            text = { Text(title, maxLines = 1) },
                        )
                    }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 96.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(episodes) { index, title ->
                    val selected = index == episodeIndex
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = Modifier.clickable { onSelectEpisode(index) },
                    ) {
                        Text(
                            text = title,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            maxLines = 1,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedSheet(
    current: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                text = stringResource(R.string.play_speed),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            speedOptions.forEach { speed ->
                TextButton(
                    onClick = { onSelect(speed) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "${speed}x",
                        color = if (speed == current) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoSettingsSheet(
    autoPlay: Boolean,
    startFull: Boolean,
    longPressSpeed: Int,
    showDanmaku: Boolean,
    floatingEnabled: Boolean,
    onAutoPlayChange: (Boolean) -> Unit,
    onStartFullChange: (Boolean) -> Unit,
    onLongPressSpeedChange: (Int) -> Unit,
    onDanmakuChange: (Boolean) -> Unit,
    onFloatingChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                text = stringResource(R.string.config_settings),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            SettingsSwitch(
                title = stringResource(R.string.video_auto_play),
                checked = autoPlay,
                onCheckedChange = onAutoPlayChange,
            )
            SettingsSwitch(
                title = stringResource(R.string.video_start_full),
                checked = startFull,
                onCheckedChange = onStartFullChange,
            )
            SettingsSwitch(
                title = stringResource(R.string.video_danmaku),
                checked = showDanmaku,
                onCheckedChange = onDanmakuChange,
            )
            SettingsSwitch(
                title = stringResource(R.string.video_floating),
                checked = floatingEnabled,
                onCheckedChange = onFloatingChange,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = stringResource(R.string.press_speed))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        onLongPressSpeedChange((longPressSpeed - 5).coerceAtLeast(5))
                    }) {
                        Text("-")
                    }
                    Text(text = "${longPressSpeed / 10f}x")
                    TextButton(onClick = {
                        onLongPressSpeedChange((longPressSpeed + 5).coerceAtMost(60))
                    }) {
                        Text("+")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = title)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
