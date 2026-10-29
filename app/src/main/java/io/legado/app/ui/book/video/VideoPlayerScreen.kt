package io.legado.app.ui.book.video

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.util.Rational
import android.view.View
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.PlayerView
import io.legado.app.R
import io.legado.app.constant.BookType
import io.legado.app.data.entities.RssArticle
import io.legado.app.model.SourceCallBack
import io.legado.app.model.VideoPlay
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppSlider
import io.legado.app.ui.widget.components.button.AppIconButton
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.player.AnimatedPlayPauseButton
import io.legado.app.ui.widget.components.player.PlayerProgressSlider
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.androidx.compose.koinViewModel

private val speedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 2.5f, 3f)

/** 播放区在全屏 / 非全屏之间切换时的尺寸过渡时长。 */
private const val VIDEO_FULLSCREEN_SIZE_ANIM_MILLIS = 300

/** 控制浮层显隐的淡入淡出时长。 */
private const val VIDEO_CONTROLS_FADE_MILLIS = 180

/** 默认 16:9 高度最多占屏幕高的比例：横屏窗口下 16:9 会比屏幕还高，压一层留信息区。 */
private const val PLAYER_DEFAULT_MAX_RATIO = 0.58f

/** 播放区拉高时给下方信息区留出的最小高度（dp），避免把信息区挤成一条缝。 */
private const val PLAYER_BOTTOM_RESERVE_DP = 96f

/** 简介折叠时保留的行数：再长也不至于把选集挤出屏幕。 */
private const val INTRO_COLLAPSED_LINES = 3

/** 简介展开 / 收起的动画时长。 */
private const val INTRO_EXPAND_ANIM_MILLIS = 220

/**
 * 画面是竖的（短剧常见）：按像素宽高比换算后的显示宽小于高。
 * 片源还没解析出尺寸（[VideoSize.UNKNOWN]）时不算竖屏，保持横屏窗口。
 */
private val VideoSize.isPortrait: Boolean
    get() = width > 0 && height > 0 && width.toFloat() * pixelWidthHeightRatio < height

/**
 * 让画面正好填满 [widthDp] 所需的高度（dp），即「贴合视频比例」的高度：
 * 到这个高度黑边收完，再高只会重新长出上下黑边。片源尺寸未知时返回 null。
 */
private fun VideoSize.fitHeight(widthDp: Float): Float? {
    val displayWidth = width.toFloat() * pixelWidthHeightRatio
    if (displayWidth <= 0f || height <= 0) return null
    return widthDp * height / displayWidth
}

@Composable
fun VideoPlayerScreen(
    bookUrl: String? = null,
    rssArticle: RssArticle? = null,
    onBack: () -> Unit,
    onOpenSourceLogin: (String) -> Unit = {},
    onOpenSourceEdit: (String) -> Unit = {},
    viewModel: VideoPlayerViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // 进度单独一条流：500ms 一拍。这里只拿 State、不在这里读值，
    // 读取下放到进度条那一小块，否则整页（含信息区）每 500ms 都要重组一次。
    val progressState = viewModel.progress.collectAsStateWithLifecycle(VideoPlaybackProgress())
    val context = LocalContext.current
    val activity = LocalActivity.current
    val view = LocalView.current
    // 播放页是全屏页面，可以临时把窗口改成横屏；退出全屏与离开页面都必须还原成
    // 「进入前的方向」。写死 UNSPECIFIED 会把阅读页（用户设了竖屏锁定 / 横屏）的方向冲掉。
    val restoreOrientation = remember(activity) {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    var controlsVisible by remember { mutableStateOf(true) }
    var menuExpanded by remember { mutableStateOf(false) }
    // 播放区高度：默认 16:9，拖底部把手可把它拉高到贴合视频比例，把黑边收掉。
    // null 表示还没被拖动过，用默认 16:9；拖动中关掉尺寸动画，否则会跟不上手。
    var playerHeightDp by remember { mutableStateOf<Float?>(null) }
    var draggingPlayerHeight by remember { mutableStateOf(false) }

    LaunchedEffect(bookUrl, rssArticle) {
        when {
            rssArticle != null -> viewModel.initRss(rssArticle)
            bookUrl != null -> viewModel.init(bookUrl)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is VideoPlayerEffect.ShowToast -> context.toastOnUi(effect.message)
                is VideoPlayerEffect.CopyPlayUrl -> copyPlayUrl(context, effect.url)
                is VideoPlayerEffect.OpenExternalPlayer -> openExternalPlayer(
                    context, effect.url, effect.title
                )

                is VideoPlayerEffect.OpenSourceLogin -> onOpenSourceLogin(effect.sourceUrl)
                is VideoPlayerEffect.OpenSourceEdit -> onOpenSourceEdit(effect.sourceUrl)
                is VideoPlayerEffect.RunSourceCallback ->
                    runSourceCustomButton(context, activity as? AppCompatActivity, effect)
            }
        }
    }

    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        // 配置变化（旋转/分屏）重建时 ON_STOP 也会派发，此时不应暂停；
        // 画中画里页面依然可见，也不能暂停和摘画面
        val keepPlaying = activity?.isChangingConfigurations == true ||
            activity?.isInPictureInPictureMode == true
        if (!keepPlaying) {
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
                activity.requestedOrientation = restoreOrientation
            }
        }
    }

    // 画面方向：书源里既有横屏番剧也有竖屏短剧，一律按横屏窗口开全屏，竖屏片源就会
    // 挂在两条大黑边中间。全屏时按画面自身的比例选窗口方向，片源没解析出尺寸时维持横屏。
    val videoSize by produceState(
        initialValue = runCatching { VideoPlay.player.videoSize }.getOrDefault(VideoSize.UNKNOWN),
    ) {
        val player = VideoPlay.player
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                value = size
            }
        }
        player.addListener(listener)
        value = player.videoSize
        awaitDispose { player.removeListener(listener) }
    }

    LaunchedEffect(state.isFullscreen, activity, videoSize) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (state.isFullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            activity.requestedOrientation =
                if (videoSize.isPortrait) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = restoreOrientation
        }
    }

    // 画中画由 MainActivity 上报：进 PiP 只留画面，浮层与信息区全撤
    val inPip = MainActivity.isInPip
    LaunchedEffect(inPip) {
        if (inPip) controlsVisible = false
    }

    val enterPip: () -> Unit = { activity?.enterVideoPip() }

    // 全屏按目标态请求：进入时先改状态（由状态驱动窗口），退出时先还原窗口再改状态，
    // 避免「窗口还在横屏、界面已经切成竖屏布局」的中间态（参考 animeko 的处理顺序）
    val requestFullscreen: (Boolean) -> Unit = { target ->
        if (!target) {
            activity?.requestedOrientation = restoreOrientation
        }
        viewModel.onIntent(VideoPlayerIntent.SetFullscreen(target))
    }

    // 全屏时返回键先退全屏，而不是直接退出页面（与主流播放器一致）
    BackHandler(enabled = state.isFullscreen && !inPip) {
        requestFullscreen(false)
    }

    // 退出前先摘画面：SurfaceView 不参与 Compose 的淡出，不摘会压在上一页上。
    // 左上角箭头与系统返回键保持同一语义：全屏时先退全屏，再按一次才离开播放页；
    // 否则横屏下点一次就直接出栈，页面没了而窗口还停在 SENSOR_LANDSCAPE。
    val exitPlayer = {
        if (state.isFullscreen) {
            requestFullscreen(false)
        } else {
            controlsVisible = false
            // 画面不用提前摘：PlayerView 走的是 TextureView，跟 Compose 的转场一起退。
            // 无条件还原方向：state.isFullscreen 和窗口方向是两条异步链路，
            // 「退出全屏的旋转还没落地就点返回」时状态已是 false 而窗口仍是横屏，
            // 页面一 pop，上一页就按横屏布局渲染一帧。
            activity?.requestedOrientation = restoreOrientation
            onBack()
        }
    }

    // 控制条自动收起：播放中 3.5 秒无操作就藏起来；暂停或菜单展开时保持常驻
    LaunchedEffect(controlsVisible, state.isPlaying, menuExpanded) {
        if (controlsVisible && state.isPlaying && !menuExpanded) {
            delay(3_500)
            controlsVisible = false
        }
    }

    val fullscreen = state.isFullscreen
    // 播放区高度：默认 16:9（横屏窗口下再压一层，别把信息区挤没），拖底部把手可以拉高到
    // 「贴合视频比例」——到那个高度画面正好填满宽度，竖屏片源的左右黑边就收完了；
    // 再往上拉只会重新长出上下黑边，所以它就是上限。信息区保底高度是另一条上限，
    // 极端的细长片源（9:21）不会把下方内容压没。
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.toFloat()
    val screenHeightDp = configuration.screenHeightDp.toFloat()
    val playerDefaultHeight = minOf(
        screenWidthDp * 9f / 16f,
        screenHeightDp * PLAYER_DEFAULT_MAX_RATIO,
    )
    val playerCapHeight = (screenHeightDp - PLAYER_BOTTOM_RESERVE_DP)
        .coerceAtLeast(playerDefaultHeight)
    val playerMaxHeight = minOf(
        videoSize.fitHeight(screenWidthDp) ?: playerCapHeight,
        playerCapHeight,
    ).coerceAtLeast(playerDefaultHeight)
    val playerHeight = (playerHeightDp ?: playerDefaultHeight)
        .coerceIn(playerDefaultHeight, playerMaxHeight)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (fullscreen || inPip) Color.Black else LegadoTheme.colorScheme.background
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 全屏 / 画中画铺满，竖屏是可拉高的固定高度块（高度按窗口在上面算好）。
            // 尺寸变化走 animateContentSize：竖屏片源全屏不旋转窗口，硬切会让画面「跳」
            // 一下；拖动把手期间必须关掉，否则尺寸变化会慢半拍、跟不上手。
            Box(
                modifier = (if (fullscreen || inPip) Modifier.fillMaxSize()
                else Modifier.fillMaxWidth())
                    .then(
                        if (draggingPlayerHeight) {
                            Modifier
                        } else {
                            Modifier.animateContentSize(
                                animationSpec = tween(
                                    durationMillis = VIDEO_FULLSCREEN_SIZE_ANIM_MILLIS,
                                    easing = FastOutSlowInEasing,
                                ),
                            )
                        }
                    ),
            ) {
                // 播放器常驻顶部：竖屏是一块可拉高的 16:9 区域，全屏/画中画铺满。
                // 三种状态复用同一个 PlayerView，只改约束，不重建播放器
                Box(
                    modifier = when {
                        fullscreen || inPip -> Modifier.fillMaxSize()
                        else -> Modifier
                            .fillMaxWidth()
                            .height(playerHeight.dp)
                            .background(Color.Black)
                            .statusBarsPadding()
                    },
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            // 走布局而不是 PlayerView(ctx)：surface_type 只能在 inflate 时
                            // 读，代码里没有等价 setter。见 player_view_texture.xml。
                            (LayoutInflater.from(ctx)
                                .inflate(R.layout.player_view_texture, null)
                                    as PlayerView).apply {
                                player = VideoPlay.player
                            }.also { playerViewRef = it }
                        },
                        onRelease = {
                            it.player = null
                            it.visibility = View.INVISIBLE
                            playerViewRef = null
                        },
                    )

                    // 画面手势层：压在 PlayerView 上但压不住下面的控制浮层。
                    // 单击切换控制条，长按临时提速。
                    if (!inPip) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        awaitFirstDown()
                                        val up = withTimeoutOrNull(
                                            viewConfiguration.longPressTimeoutMillis
                                        ) { waitForUpOrCancellation() }
                                        if (up == null) {
                                            // 长按：临时提速，抬手恢复
                                            viewModel.onIntent(
                                                VideoPlayerIntent.LongPressSpeed(true)
                                            )
                                            waitForUpOrCancellation()
                                            viewModel.onIntent(
                                                VideoPlayerIntent.LongPressSpeed(false)
                                            )
                                        } else {
                                            controlsVisible = !controlsVisible
                                        }
                                    }
                                },
                        )
                    }

                    // 浮层：顶栏 + 中央播放键 + 进度条与操作行，跟随控制条显隐。
                    // 淡入淡出而不是硬切：点画面切换控制条时，硬切会让画面「闪」一下。
                    // 用 alpha 而不是 AnimatedVisibility：这里处在 Column 作用域内，
                    // AnimatedVisibility 会被解析成 ColumnScope 的成员扩展。
                    val controlsAlpha by animateFloatAsState(
                        targetValue = if (!inPip && controlsVisible) 1f else 0f,
                        animationSpec = tween(durationMillis = VIDEO_CONTROLS_FADE_MILLIS),
                        label = "VideoControlsAlpha",
                    )
                    if (!inPip && controlsAlpha > 0f) {
                        VideoPlayerOverlay(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = controlsAlpha },
                            fullscreen = fullscreen,
                            topInset = fullscreen,
                            isPlaying = state.isPlaying,
                            isLoading = state.isLoading,
                            progress = progressState,
                            menuExpanded = menuExpanded,
                            // 书源相关菜单项按会话能力出现：RSS/直链没有书源，
                            // 未配登录/按钮的源也不给死入口
                            hasSource = state.hasSource,
                            hasSourceLogin = state.hasSourceLogin,
                            hasCustomButton = state.hasCustomButton,
                            canPrevious = state.episodeIndex > 0 || state.volumeIndex > 0,
                            canNext = state.episodeIndex < state.episodes.lastIndex ||
                                state.volumeIndex < state.volumes.lastIndex,
                            onBack = exitPlayer,
                            onTogglePlay = {
                                viewModel.onIntent(VideoPlayerIntent.TogglePlay)
                            },
                            onSeek = { viewModel.onIntent(VideoPlayerIntent.SeekTo(it)) },
                            onMenuExpand = { menuExpanded = true },
                            onMenuDismiss = { menuExpanded = false },
                            onPip = enterPip,
                            onToggleFullscreen = { requestFullscreen(!fullscreen) },
                            onIntent = viewModel::onIntent,
                        )
                    }

                    if (state.error != null) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AppText(
                                text = state.error.orEmpty(),
                                color = Color.White,
                                style = LegadoTheme.typography.bodyMedium,
                            )
                            MediumTonalButton(
                                onClick = { viewModel.onIntent(VideoPlayerIntent.Retry) },
                                modifier = Modifier.padding(top = 8.dp),
                                icon = AppIcons.Replay,
                                text = stringResource(R.string.retry),
                            )
                        }
                    }
                }
            }

            // 播放区与信息区之间的把手：上下拖它就是在改播放区高度，
            // 往下拖＝播放区变高，容器比例往视频比例靠，黑边跟着收
            if (!fullscreen && !inPip) {
                PlayerResizeHandle(
                    onDrag = { deltaDp ->
                        playerHeightDp = (playerHeight + deltaDp)
                            .coerceIn(playerDefaultHeight, playerMaxHeight)
                    },
                    onDragStart = { draggingPlayerHeight = true },
                    onDragEnd = { draggingPlayerHeight = false },
                    onReset = { playerHeightDp = null },
                )
            }

            // 下方信息区
            if (!fullscreen && !inPip) {
                VideoBottomPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    state = state,
                    onIntent = viewModel::onIntent,
                )
            }
        }
    }

    // 三个面板常驻组合、show 由状态推导：关掉面板时要能播出退场动画，
    // 用 when(sheet) 分支的话组合被直接摘掉，只剩硬切。
    EpisodeSheet(
        show = state.sheet == VideoPlayerSheet.EPISODES,
        volumes = state.volumes,
        volumeIndex = state.volumeIndex,
        episodes = state.episodes,
        episodeIndex = state.episodeIndex,
        onSelectVolume = { viewModel.onIntent(VideoPlayerIntent.SelectVolume(it)) },
        onSelectEpisode = { viewModel.onIntent(VideoPlayerIntent.SelectEpisode(it)) },
        onDismiss = { viewModel.onIntent(VideoPlayerIntent.CloseSheet) },
    )

    SpeedSheet(
        show = state.sheet == VideoPlayerSheet.SPEED,
        current = state.speed,
        onSelect = { viewModel.onIntent(VideoPlayerIntent.SetSpeed(it)) },
        onDismiss = { viewModel.onIntent(VideoPlayerIntent.CloseSheet) },
    )

    VideoSettingsSheet(
        show = state.sheet == VideoPlayerSheet.SETTINGS,
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

/** 书源自定义按钮：回调未消费（没有按钮/JS 报错）时提示，不做静默失败。 */
private fun runSourceCustomButton(
    context: android.content.Context,
    activity: AppCompatActivity?,
    effect: VideoPlayerEffect.RunSourceCallback,
) {
    if (activity == null) {
        context.toastOnUi(context.getString(R.string.source_no_custom_button))
        return
    }
    SourceCallBack.callBackBtn(
        activity = activity,
        event = effect.event,
        source = effect.source,
        book = effect.book,
        chapter = effect.chapter,
        bookType = BookType.video,
    ) {
        context.toastOnUi(context.getString(R.string.source_no_custom_button))
    }
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
    /** 全屏时系统栏隐藏，浮层要自己避让状态栏；竖屏下播放器已经让开了。 */
    topInset: Boolean,
    onBack: () -> Unit,
    menuExpanded: Boolean,
    onMenuExpand: () -> Unit,
    onMenuDismiss: () -> Unit,
    hasSource: Boolean,
    hasSourceLogin: Boolean,
    hasCustomButton: Boolean,
    onPip: () -> Unit,
    onIntent: (VideoPlayerIntent) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 横屏下挖孔落在左右两侧，返回键与菜单键必须让开，否则会压在挖孔上
            .windowInsetsPadding(
                WindowInsets.systemBars
                    .union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Horizontal)
            )
            .then(
                if (topInset) {
                    // 全屏时宿主收起了系统栏，statusBars 会变成 0，但挖孔 / 瀑布屏的
                    // displayCutout 仍然占位；只按 statusBars 避让，竖屏全屏时顶栏会压到挖孔上。
                    Modifier.windowInsetsPadding(
                        WindowInsets.systemBars
                            .union(WindowInsets.displayCutout)
                            .only(WindowInsetsSides.Top)
                    )
                } else {
                    Modifier
                }
            )
            .padding(top = 8.dp, start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OverlayIconButton(icon = AppIcons.Back, contentDescription = null, onClick = onBack)
        Spacer(Modifier.weight(1f))
        Box {
            OverlayIconButton(
                icon = AppIcons.MoreVert,
                contentDescription = null,
                onClick = onMenuExpand,
            )
            RoundDropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = onMenuDismiss,
            ) { dismiss ->
                RoundDropdownMenuItem(
                    text = stringResource(R.string.copy_play_url),
                    onClick = {
                        dismiss()
                        onIntent(VideoPlayerIntent.CopyPlayUrl)
                    },
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.open_other_video_player),
                    onClick = {
                        dismiss()
                        onIntent(VideoPlayerIntent.OpenInExternalPlayer)
                    },
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.video_floating),
                    onClick = {
                        dismiss()
                        onIntent(VideoPlayerIntent.ToggleFloating)
                    },
                    leadingIcon = {
                        AppIcon(
                            imageVector = AppIcons.MiniWindow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.video_picture_in_picture),
                    onClick = {
                        dismiss()
                        onPip()
                    },
                    leadingIcon = {
                        AppIcon(
                            imageVector = AppIcons.PictureInPicture,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.config_settings),
                    onClick = {
                        dismiss()
                        onIntent(VideoPlayerIntent.OpenSheet(VideoPlayerSheet.SETTINGS))
                    },
                    leadingIcon = {
                        AppIcon(
                            imageVector = AppIcons.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
                if (hasCustomButton) {
                    RoundDropdownMenuItem(
                        text = stringResource(R.string.custom_button),
                        onClick = {
                            dismiss()
                            onIntent(VideoPlayerIntent.RunCustomButton)
                        },
                    )
                }
                if (hasSourceLogin) {
                    RoundDropdownMenuItem(
                        text = stringResource(R.string.book_source_login),
                        onClick = {
                            dismiss()
                            onIntent(VideoPlayerIntent.OpenSourceLogin)
                        },
                    )
                }
                if (hasSource) {
                    RoundDropdownMenuItem(
                        text = stringResource(R.string.edit_book_source),
                        onClick = {
                            dismiss()
                            onIntent(VideoPlayerIntent.OpenSourceEdit)
                        },
                    )
                }
            }
        }
    }
}

/**
 * 播放区与信息区之间的拖动把手：上下拖改变播放区高度。
 *
 * 往下拖＝播放区变高，容器比例从 16:9 往视频比例靠，竖屏片源的左右黑边跟着收；
 * 拖到贴合比例时黑边正好消失。双击回到默认 16:9。
 */
@Composable
private fun PlayerResizeHandle(
    onDrag: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onReset: () -> Unit,
) {
    // 拖动手势的 pointerInput 只在进入组合时挂一次，回调必须走最新状态，
    // 否则整段拖动都会用第一次组合时捕获的播放区高度，越拖越对不上。
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnReset by rememberUpdatedState(onReset)
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { currentOnDragStart() },
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragEnd() },
                ) { _, dragAmount ->
                    // 屏幕 Y 向下为正，正好对应「往下拖＝播放区变高」
                    currentOnDrag(with(density) { dragAmount.toDp().value })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { currentOnReset() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(LegadoTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)),
        )
    }
}

/**
 * 播放器下方：书籍信息与横向选集。
 *
 * 播放器本身常驻顶部不参与滚动，这里只滚信息区。书籍信息直接铺在页面上、不套卡片
 * （和播放区之间已经有把手分隔）；选集区仍用 [GlassCard]，sheet 里的卡片则用 [NormalCard]。
 */
@Composable
private fun VideoBottomPanel(
    modifier: Modifier,
    state: VideoPlayerUiState,
    onIntent: (VideoPlayerIntent) -> Unit,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            BookHeader(state)
            Spacer(Modifier.height(16.dp))
            AppText(
                text = stringResource(R.string.book_intro),
                style = LegadoTheme.typography.titleSmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            ExpandableIntro(intro = state.intro)
        }

        if (state.hasEpisodeList) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    EpisodeSection(state, onIntent)
                }
            }
        }
    }
}

/** 封面 + 书名 + 作者/书源 + 当前章节，与封面垂直居中。 */
@Composable
private fun BookHeader(state: VideoPlayerUiState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (state.coverUrl.isNotBlank()) {
            CoilBookCover(
                name = state.bookName,
                author = state.author,
                path = state.coverUrl,
                bookUrl = state.bookUrl,
                modifier = Modifier.width(88.dp),
            )
            Spacer(Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = state.bookName.ifBlank { stringResource(R.string.play_video) },
                style = LegadoTheme.typography.titleLargeEmphasized,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // 作者 / 书源只给值，不加「作者 ·」这类前缀；集数交给下面的选集区，不在这里重复
            buildList {
                if (state.author.isNotBlank()) add(state.author)
                if (state.bookSourceName.isNotBlank()) add(state.bookSourceName)
                if (state.chapterTitle.isNotBlank()) add(state.chapterTitle)
            }.forEach { line ->
                Spacer(Modifier.height(4.dp))
                AppText(
                    text = line,
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 简介：默认只显示 [INTRO_COLLAPSED_LINES] 行，超出才给展开/收起入口。
 *
 * 简介动辄几十行，全展开会把选集挤出屏幕；折叠行数用 [AppText.onTextLayout] 量出来，
 * 没溢出就不显示入口。高度变化走 animateContentSize，箭头跟着转。
 */
@Composable
private fun ExpandableIntro(intro: String) {
    var expanded by remember(intro) { mutableStateOf(false) }
    var overflows by remember(intro) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        AppText(
            text = intro.ifBlank { stringResource(R.string.intro_show_null) },
            style = LegadoTheme.typography.bodyMedium,
            color = if (intro.isBlank()) {
                LegadoTheme.colorScheme.onSurfaceVariant
            } else {
                LegadoTheme.colorScheme.onSurface
            },
            maxLines = if (expanded) Int.MAX_VALUE else INTRO_COLLAPSED_LINES,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                // 只在折叠态测量：展开态必然不溢出，量它会把入口弄丢
                if (!expanded) overflows = result.hasVisualOverflow
            },
            modifier = Modifier.animateContentSize(
                animationSpec = tween(
                    durationMillis = INTRO_EXPAND_ANIM_MILLIS,
                    easing = FastOutSlowInEasing,
                ),
            ),
        )
        if (overflows || expanded) {
            val arrowRotation by animateFloatAsState(
                targetValue = if (expanded) 180f else 0f,
                animationSpec = tween(durationMillis = INTRO_EXPAND_ANIM_MILLIS),
                label = "introArrow",
            )
            Row(
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = !expanded }
                    .semantics { role = Role.Button }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                AppText(
                    text = stringResource(
                        if (expanded) R.string.collapse else R.string.expand
                    ),
                    style = LegadoTheme.typography.labelMedium,
                    color = LegadoTheme.colorScheme.primary,
                )
                AppIcon(
                    imageVector = AppIcons.ExpandMore,
                    contentDescription = null,
                    tint = LegadoTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(arrowRotation),
                )
            }
        }
    }
}

/**
 * 选集区：分卷 + 横向滑动的集数条 + 「更多」入口。
 *
 * 集数条只给最近的一段，横滑找集；全量选择走 [VideoPlayerSheet.EPISODES] 的网格 sheet，
 * 免得信息区被几十上百个格子撑成长列表。
 */
@Composable
private fun EpisodeSection(
    state: VideoPlayerUiState,
    onIntent: (VideoPlayerIntent) -> Unit,
) {
    val listState = rememberLazyListState()
    // 进入页面与换集后把当前集滚到可见处，别让用户在一长条里自己找
    LaunchedEffect(state.episodeIndex, state.episodes.size) {
        if (state.episodes.isNotEmpty()) {
            listState.animateScrollToItem(
                state.episodeIndex.coerceIn(0, state.episodes.lastIndex)
            )
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        if (state.volumes.size > 1) {
            VolumeTabs(
                volumes = state.volumes,
                selected = state.volumeIndex,
                onSelect = { onIntent(VideoPlayerIntent.SelectVolume(it)) },
            )
            Spacer(Modifier.height(12.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppText(
                text = stringResource(R.string.select_episode),
                style = LegadoTheme.typography.titleSmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            AppText(
                text = "${state.episodeIndex + 1} / ${state.episodes.size}",
                style = LegadoTheme.typography.labelLarge,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            SmallTonalButton(
                onClick = { onIntent(VideoPlayerIntent.OpenSheet(VideoPlayerSheet.EPISODES)) },
                icon = AppIcons.Episodes,
                text = stringResource(R.string.more),
            )
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(state.episodes) { index, title ->
                EpisodeChip(
                    title = title,
                    selected = index == state.episodeIndex,
                    onClick = { onIntent(VideoPlayerIntent.SelectEpisode(index)) },
                )
            }
        }
    }
}

/**
 * 播放器浮层：顶栏、中央播放键、左右侧快捷操作、进度条与操作行。
 *
 * 倍速/选集/设置/悬浮小窗/画中画/全屏都收在这里，信息区不再重复入口。
 */
@Composable
private fun VideoPlayerOverlay(
    modifier: Modifier,
    fullscreen: Boolean,
    topInset: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    progress: State<VideoPlaybackProgress>,
    menuExpanded: Boolean,
    hasSource: Boolean,
    hasSourceLogin: Boolean,
    hasCustomButton: Boolean,
    canPrevious: Boolean,
    canNext: Boolean,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onMenuExpand: () -> Unit,
    onMenuDismiss: () -> Unit,
    onPip: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onIntent: (VideoPlayerIntent) -> Unit,
) {
    Box(modifier = modifier) {
        // 顶部渐隐遮罩 + 顶栏
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                        )
                    ),
            )
            VideoTopBar(
                topInset = topInset,
                onBack = onBack,
                menuExpanded = menuExpanded,
                onMenuExpand = onMenuExpand,
                onMenuDismiss = onMenuDismiss,
                hasSource = hasSource,
                hasSourceLogin = hasSourceLogin,
                hasCustomButton = hasCustomButton,
                onPip = onPip,
                onIntent = onIntent,
            )
        }

        // 中央：项目统一播放键，自带加载态。压在画面上，不要容器色块挡视线
        AnimatedPlayPauseButton(
            isPlaying = isPlaying,
            contentDescription = stringResource(R.string.play_video),
            onClick = onTogglePlay,
            modifier = Modifier.align(Alignment.Center),
            isLoading = isLoading,
            showContainer = false,
            tint = Color.White,
        )

        // 底部：进度 + 图标操作行
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                    )
                )
                // 横屏挖孔在两侧，操作行同样要让开
                .windowInsetsPadding(
                    WindowInsets.systemBars
                        .union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Horizontal)
                )
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            ProgressBarRow(progress = progress, onSeek = onSeek)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 全屏时页面上没有选集入口，才在控制条补上；竖屏有「选集」页，不重复
                if (fullscreen) {
                    OverlayIconButton(
                        icon = AppIcons.Episodes,
                        contentDescription = stringResource(R.string.select_episode),
                    ) {
                        onIntent(VideoPlayerIntent.OpenSheet(VideoPlayerSheet.EPISODES))
                    }
                }
                // 换集靠左、倍速与全屏靠右：换集是「翻页」，和右侧的播放设置分开更好找
                OverlayIconButton(
                    icon = AppIcons.Previous,
                    contentDescription = stringResource(R.string.previous_chapter),
                    enabled = canPrevious,
                ) {
                    onIntent(VideoPlayerIntent.PreviousEpisode)
                }
                OverlayIconButton(
                    icon = AppIcons.Next,
                    contentDescription = stringResource(R.string.next_chapter),
                    enabled = canNext,
                ) {
                    onIntent(VideoPlayerIntent.NextEpisode)
                }
                Spacer(Modifier.weight(1f))
                OverlayIconButton(
                    icon = AppIcons.Speed,
                    contentDescription = stringResource(R.string.play_speed),
                ) {
                    onIntent(VideoPlayerIntent.OpenSheet(VideoPlayerSheet.SPEED))
                }
                OverlayIconButton(
                    icon = if (fullscreen) AppIcons.FullscreenExit else AppIcons.Fullscreen,
                    contentDescription = stringResource(R.string.full_screen),
                ) {
                    onToggleFullscreen()
                }
            }
        }
    }
}

/**
 * 进度条与时间文案。
 *
 * 单独一个 composable：进度是 500ms 一拍的 State，读取放在这里，
 * 重组范围就只有这一行，不会顺着外层 composable 一路重刷。
 */
@Composable
private fun ProgressBarRow(
    progress: State<VideoPlaybackProgress>,
    onSeek: (Long) -> Unit,
) {
    val value = progress.value
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppText(
            text = formatTime(value.positionMs),
            color = Color.White,
            style = LegadoTheme.typography.labelSmall,
        )
        // 复用听书的进度条：2dp 细轨道 + 拖动放大，比 M3 Slider 轻
        PlayerProgressSlider(
            value = value.fraction,
            onValueChange = { onSeek((it * value.durationMs).toLong()) },
            valueRange = 0f..1f,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            activeColor = Color.White,
            inactiveColor = Color.White.copy(alpha = 0.38f),
        )
        AppText(
            text = formatTime(value.durationMs),
            color = Color.White,
            style = LegadoTheme.typography.labelSmall,
        )
    }
}

/** 浮层上的图标按钮：白色图标、无背景，压在播放器画面上。 */
@Composable
private fun OverlayIconButton(
    icon: ImageVector,
    contentDescription: String?,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    AppIconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        enabled = enabled,
    ) {
        AppIcon(
            imageVector = icon,
            contentDescription = contentDescription,
            // 到头了就把图标压暗，别让用户点了没反应还以为卡了
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.38f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 时长文案：一小时以内 mm:ss，超过走 h:mm:ss。 */
private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}

/** 选集格子：信息区与选集面板共用。只用项目色板，不借 M3 的 Surface/shape 令牌。 */
@Composable
private fun EpisodeChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .widthIn(min = 56.dp)
            .clip(shape)
            .background(
                if (selected) {
                    LegadoTheme.colorScheme.primaryContainer
                } else {
                    LegadoTheme.colorScheme.surfaceContainerHigh
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = LegadoTheme.typography.bodyMedium,
            color = if (selected) {
                LegadoTheme.colorScheme.onPrimaryContainer
            } else {
                LegadoTheme.colorScheme.onSurface
            },
        )
    }
}

/** 分卷切换：信息区与选集面板共用。 */
@Composable
private fun VolumeTabs(
    volumes: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    if (volumes.size <= 1) return
    AppTabRow(
        tabTitles = volumes,
        selectedTabIndex = selected.coerceIn(0, volumes.lastIndex),
        onTabSelected = onSelect,
        modifier = Modifier.fillMaxWidth(),
        isScrollable = true,
    )
}


/**
 * 选集面板：分卷 + 集数网格。
 *
 * 面板走 [AppModalBottomSheet]，面板内的卡片一律 [NormalCard]（与屏幕上的 [GlassCard] 区分）。
 * 网格自己滚，高度封顶，别把面板顶出屏幕。
 */
@Composable
private fun EpisodeSheet(
    show: Boolean,
    volumes: List<String>,
    volumeIndex: Int,
    episodes: List<String>,
    episodeIndex: Int,
    onSelectVolume: (Int) -> Unit,
    onSelectEpisode: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.select_episode),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VolumeTabs(
                volumes = volumes,
                selected = volumeIndex,
                onSelect = onSelectVolume,
            )
            NormalCard(modifier = Modifier.fillMaxWidth()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 96.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(episodes) { index, title ->
                        EpisodeChip(
                            title = title,
                            selected = index == episodeIndex,
                            onClick = { onSelectEpisode(index) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 倍速面板：一个 [AppSlider] 在预设档位之间滑。
 *
 * 滑块的值是档位下标而不是倍速本身：预设档位不是等距的（0.5/0.75/1/1.25/1.5/2/2.5/3），
 * 用倍速当值会滑出 1.63x 这种不在档位上的数。拖动只改本地预览，松手才落档；
 * 落档不关面板，可以接着微调，退出走右上角对勾或下滑。
 */
@Composable
private fun SpeedSheet(
    show: Boolean,
    current: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    // 只按「面板开合」重置草稿：跟着 current 重置会让松手落档后滑块往回跳一格
    var draftIndex by remember(show) {
        mutableFloatStateOf(snapSpeedIndex(current).toFloat())
    }
    val selectedIndex = draftIndex.roundToInt().coerceIn(0, speedOptions.lastIndex)
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.play_speed),
        endAction = {
            SmallTonalButton(
                onClick = onDismiss,
                icon = AppIcons.Check,
                contentDescription = stringResource(R.string.close),
            )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppText(
                text = formatSpeed(speedOptions[selectedIndex]),
                style = LegadoTheme.typography.titleLargeEmphasized,
                color = LegadoTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            AppSlider(
                value = draftIndex,
                onValueChange = { draftIndex = it },
                onValueChangeFinished = { onSelect(speedOptions[selectedIndex]) },
                valueRange = 0f..speedOptions.lastIndex.toFloat(),
                modifier = Modifier.fillMaxWidth(),
                accessibilityLabel = stringResource(R.string.play_speed),
                accessibilityValue = formatSpeed(speedOptions[selectedIndex]),
            )
        }
    }
}

/** 播放设置面板：开关走 [TinySwitchSettingItem]，长按倍速走 [TinySliderSettingItem]。 */
@Composable
private fun VideoSettingsSheet(
    show: Boolean,
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
    // 拖动中只更新本地预览：每次写设置都会让整页 uiState 回流重组，滑块会卡手甚至跳回。
    var longPressDraft by remember(longPressSpeed) {
        mutableFloatStateOf(longPressSpeed / 10f)
    }
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.config_settings),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TinySwitchSettingItem(
                title = stringResource(R.string.video_auto_play),
                checked = autoPlay,
                onCheckedChange = onAutoPlayChange,
            )
            TinySwitchSettingItem(
                title = stringResource(R.string.video_start_full),
                checked = startFull,
                onCheckedChange = onStartFullChange,
            )
            TinySwitchSettingItem(
                title = stringResource(R.string.video_danmaku),
                checked = showDanmaku,
                onCheckedChange = onDanmakuChange,
            )
            TinySwitchSettingItem(
                title = stringResource(R.string.video_floating),
                checked = floatingEnabled,
                onCheckedChange = onFloatingChange,
            )
            TinySliderSettingItem(
                title = stringResource(R.string.press_speed),
                // value 保持「已提交值」不变，拖动期间只靠组件内部状态显示；
                // 每帧回写 value 会让组件内部的 remember(value) 反复重建。
                value = longPressSpeed / 10f,
                valueRange = 0.5f..6f,
                stepSize = 0.5f,
                steps = 10,
                valueFormat = { formatSpeed(it) },
                onValueChange = { longPressDraft = it },
                onValueChangeFinished = {
                    onLongPressSpeedChange((longPressDraft * 10f).roundToInt())
                },
            )
        }
    }
}

/** 倍速文案：整数倍不带小数位（1x），其余照实（1.25x）。 */
private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"

/** 把任意倍速落到最接近的预设档位上，滑块的初始位置用它。 */
private fun snapSpeedIndex(speed: Float): Int =
    speedOptions.indices.minByOrNull { abs(speedOptions[it] - speed) } ?: 0

/** 统一入口：播放页浮层按钮与宿主 `onUserLeaveHint` 自动进入都走这里。 */
internal fun Activity.enterVideoPip() {
    if (isFinishing || isDestroyed || isInPictureInPictureMode) return
    enterPictureInPictureMode(
        PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
    )
}
