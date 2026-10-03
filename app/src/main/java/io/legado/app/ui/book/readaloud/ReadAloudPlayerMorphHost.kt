package io.legado.app.ui.book.readaloud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import io.legado.app.R
import io.legado.app.core.ui.player.PlayerMorphAppearance
import io.legado.app.core.ui.player.PlayerMorphHost
import io.legado.app.help.IntentHelp
import io.legado.app.ui.book.read.sheet.ReadAloudConfigContent
import io.legado.app.ui.book.read.sheet.ReadAloudNumberConfigSheet
import io.legado.app.ui.book.read.sheet.asReadBookUiState
import io.legado.app.ui.book.readaloud.morph.ReadAloudMorphState
import io.legado.app.ui.book.readaloud.player.ReadAloudConfigOption
import io.legado.app.ui.book.readaloud.player.ReadAloudPlayerConfigHostAction
import io.legado.app.ui.book.readaloud.player.ReadAloudPlayerEffect
import io.legado.app.ui.book.readaloud.player.ReadAloudPlayerScreenContent
import io.legado.app.ui.book.readaloud.player.ReadAloudPlayerUiState
import io.legado.app.ui.book.readaloud.player.ReadAloudPlayerViewModel
import io.legado.app.ui.book.readaloud.player.applyReadBookConfigIntent
import io.legado.app.ui.book.readaloud.player.rememberPlayerThemeOverride
import io.legado.app.ui.main.MainNavRouteTracker
import io.legado.app.ui.main.MainRouteReadBook
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** 朗读特有的设置与经典控制；几何、封面和返回手势由共用宿主处理。 */
@Composable
fun ReadAloudPlayerMorphHost(
    playerViewModel: ReadAloudPlayerViewModel,
    playerState: ReadAloudPlayerUiState,
    morph: ReadAloudMorphState,
    visible: Boolean,
    awaitCapsuleAnchor: Boolean = false,
    predictiveBackEnabled: Boolean = true,
    onDismiss: () -> Unit,
    onSwitchToClassic: (bookUrl: String) -> Unit,
    onOpenTtsEnginesAndVoices: (bookUrl: String) -> Unit,
    onOpenTtsCache: () -> Unit,
    onOpenBookVoiceCasting: (bookUrl: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navRouteTracker: MainNavRouteTracker = koinInject()
    val navBackStack by navRouteTracker.backStack.collectAsStateWithLifecycle()
    val topRoute = navBackStack.lastOrNull()
    val settingsState by playerViewModel.readAloudSettings.collectAsStateWithLifecycle()
    var configVisible by rememberSaveable { mutableStateOf(false) }
    /**
     * 朗读设置停在哪个 tab（0=常规，1=引擎与音色）。存在宿主而不是弹层里：
     * 压进整屏页会拆掉弹层那层 composition，`rememberPagerState` 的初值回到 0，
     * 回来就落在常规 tab。
     */
    var configTab by rememberSaveable { mutableStateOf(0) }
    /**
     * 从朗读设置跳去子页（引擎与音色 / 朗读缓存 / 人物配音）时置位：
     * 播放器是 Activity 级浮层，压在新页面上面，所以只能先收；这里只留一个"弹层在等
     * 播放器重新摊开"的标记，导航栈顶回到阅读页就把播放器重新叫出来、朗读设置跟着回来
     * ——「从哪进，出来就是哪」。不直接留着 `configVisible`：返回手势的闸门看的就是它。
     */
    var configWaitsForPlayer by remember { mutableStateOf(false) }
    /**
     * 跳子页时记下那个目的地：只有"从它返回阅读页"才算回来。中途去了别处（连按两次返回
     * 落到书架）就把标记作废，否则下次点进阅读页会凭空弹出播放器 + 朗读设置。
     */
    var pendingSubRoute by remember { mutableStateOf<NavKey?>(null) }
    var activeNumberConfig by rememberSaveable {
        mutableStateOf<ReadAloudPlayerConfigHostAction?>(null)
    }
    val expanded by remember { derivedStateOf { morph.expanded } }
    val currentDismiss by rememberUpdatedState(onDismiss)
    val currentSwitch by rememberUpdatedState(onSwitchToClassic)
    val currentOpenTtsEnginesAndVoices by rememberUpdatedState(onOpenTtsEnginesAndVoices)
    val currentOpenTtsCache by rememberUpdatedState(onOpenTtsCache)
    val currentOpenBookVoiceCasting by rememberUpdatedState(onOpenBookVoiceCasting)

    suspend fun collapsePlayer(keepConfig: Boolean = false) {
        configWaitsForPlayer = keepConfig
        if (!keepConfig) pendingSubRoute = null
        configVisible = false
        activeNumberConfig = null
        morph.animateTo(0f)
        currentDismiss()
    }

    fun navigateFromPlayer(action: () -> Unit) {
        scope.launch {
            val keepConfig = configVisible
            // 先推目的地再收播放器：等收完再推，中间会露出阅读页一帧
            action()
            pendingSubRoute = navRouteTracker.currentRoute
            collapsePlayer(keepConfig = keepConfig)
        }
    }

    fun showNumberConfig(action: ReadAloudPlayerConfigHostAction) {
        configVisible = false
        activeNumberConfig = action
    }

    fun handleHostAction(action: ReadAloudPlayerConfigHostAction) {
        when (action) {
            ReadAloudPlayerConfigHostAction.OpenTtsEnginesAndVoices -> navigateFromPlayer {
                currentOpenTtsEnginesAndVoices(playerViewModel.uiState.value.bookUrl)
            }

            ReadAloudPlayerConfigHostAction.OpenTtsCache -> navigateFromPlayer {
                currentOpenTtsCache()
            }

            ReadAloudPlayerConfigHostAction.OpenBookVoiceCasting -> navigateFromPlayer {
                currentOpenBookVoiceCasting(playerViewModel.uiState.value.bookUrl)
            }

            ReadAloudPlayerConfigHostAction.OpenSystemTtsSettings ->
                IntentHelp.openTTSSetting()

            ReadAloudPlayerConfigHostAction.OpenPreDownloadNumPicker,
            ReadAloudPlayerConfigHostAction.OpenPreSynthesisConcurrencyPicker,
            ReadAloudPlayerConfigHostAction.OpenParagraphIntervalPicker,
            ReadAloudPlayerConfigHostAction.OpenCacheCleanTimePicker -> showNumberConfig(action)
        }
    }

    LaunchedEffect(expanded) {
        if (expanded) {
            if (configWaitsForPlayer) {
                configWaitsForPlayer = false
                pendingSubRoute = null
                configVisible = true
            }
        } else if (!configWaitsForPlayer) {
            configVisible = false
            activeNumberConfig = null
        }
    }
    // 只有「从我推上去的那个子页」回到阅读页才把播放器叫回来；中途去了别处（连按两次返回
    // 落到书架）就作废，否则下次点进阅读页会凭空弹出朗读设置。
    LaunchedEffect(topRoute) {
        if (!configWaitsForPlayer) return@LaunchedEffect
        when (topRoute) {
            pendingSubRoute -> Unit
            is MainRouteReadBook -> {
                pendingSubRoute = null
                ReadAloudPlayerOverlayBus.request()
            }

            else -> {
                configWaitsForPlayer = false
                pendingSubRoute = null
            }
        }
    }
    LaunchedEffect(playerViewModel) {
        playerViewModel.effects.collect { effect ->
            when (effect) {
                ReadAloudPlayerEffect.ReturnToClassic -> {
                    collapsePlayer()
                    currentSwitch(playerViewModel.uiState.value.bookUrl)
                }

                ReadAloudPlayerEffect.TtsCacheCleared ->
                    context.toastOnUi(R.string.clear_cache_success)
            }
        }
    }
    PlayerMorphHost(
        appearance = PlayerMorphAppearance(
            playerState.bookName, playerState.author, playerState.coverPath,
            playerState.sourceOrigin, playerState.bgMode,
        ),
        playerTheme = rememberPlayerThemeOverride(playerState),
        morph = morph,
        visible = visible,
        awaitCapsuleAnchor = awaitCapsuleAnchor,
        predictiveBackEnabled = predictiveBackEnabled,
        backEnabled = !configVisible && activeNumberConfig == null &&
                playerState.activeSheet == null,
        onDismiss = onDismiss,
    ) { onCollapse ->
        ReadAloudPlayerScreenContent(
            state = playerState,
            onIntent = playerViewModel::onIntent,
            onBack = onCollapse,
            onOpenConfig = { configVisible = true },
        )
    }
    AppModalBottomSheet(
        show = expanded && configVisible,
        onDismissRequest = { configVisible = false },
        title = stringResource(R.string.aloud_config),
    ) {
        ReadAloudConfigContent(
            state = settingsState.asReadBookUiState(),
            playerState = playerState,
            onIntent = { intent ->
                playerViewModel.applyReadBookConfigIntent(intent, ::handleHostAction)
            },
            onPlayerIntent = playerViewModel::onIntent,
            selectedTab = configTab,
            onTabSelected = { configTab = it },
        )
    }

    val dismissNumberConfig = {
        activeNumberConfig = null
        configVisible = true
    }
    ReadAloudNumberConfigSheet(
        show = expanded &&
                activeNumberConfig == ReadAloudPlayerConfigHostAction.OpenPreDownloadNumPicker,
        title = stringResource(R.string.read_aloud_preload),
        description = stringResource(
            R.string.read_aloud_preload_summary,
            settingsState.preDownloadNum,
        ),
        value = settingsState.preDownloadNum,
        defaultValue = 10,
        valueRange = 0f..100f,
        onValueChange = {
            playerViewModel.onConfigIntent(ReadAloudConfigOption.PreDownloadNum, intValue = it)
        },
        onDismissRequest = dismissNumberConfig,
    )
    ReadAloudNumberConfigSheet(
        show = expanded && activeNumberConfig ==
                ReadAloudPlayerConfigHostAction.OpenPreSynthesisConcurrencyPicker,
        title = stringResource(R.string.tts_pre_synthesis_concurrency),
        description = stringResource(
            R.string.tts_pre_synthesis_concurrency_summary,
            settingsState.preSynthesisConcurrency,
        ),
        value = settingsState.preSynthesisConcurrency,
        defaultValue = 3,
        valueRange = 1f..8f,
        onValueChange = {
            playerViewModel.onConfigIntent(
                ReadAloudConfigOption.PreSynthesisConcurrency,
                intValue = it,
            )
        },
        onDismissRequest = dismissNumberConfig,
    )
    ReadAloudNumberConfigSheet(
        show = expanded &&
                activeNumberConfig == ReadAloudPlayerConfigHostAction.OpenParagraphIntervalPicker,
        title = stringResource(R.string.tts_paragraph_interval),
        description = stringResource(
            R.string.tts_paragraph_interval_summary,
            settingsState.readAloudParagraphInterval,
        ),
        value = settingsState.readAloudParagraphInterval,
        defaultValue = 0,
        valueRange = 0f..5000f,
        onValueChange = {
            playerViewModel.onConfigIntent(ReadAloudConfigOption.ParagraphInterval, intValue = it)
        },
        onDismissRequest = dismissNumberConfig,
    )
    ReadAloudNumberConfigSheet(
        show = expanded &&
                activeNumberConfig == ReadAloudPlayerConfigHostAction.OpenCacheCleanTimePicker,
        title = stringResource(R.string.audio_cache_clean_time),
        description = stringResource(
            R.string.audio_cache_clean_time_summary,
            settingsState.audioCacheCleanTime,
        ),
        value = settingsState.audioCacheCleanTime,
        defaultValue = 10,
        valueRange = 0f..10080f,
        onValueChange = {
            playerViewModel.onConfigIntent(ReadAloudConfigOption.AudioCacheCleanTime, intValue = it)
        },
        onDismissRequest = dismissNumberConfig,
    )
}
