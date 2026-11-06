package io.legado.app.ui.book.readaloud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import io.legado.app.ui.book.read.ReadAloudSubPage
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
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

internal data class ReadAloudConfigReturn(
    val origin: List<NavKey>,
    val subRoute: NavKey,
) {
    fun isInSubRoute(backStack: List<NavKey>): Boolean =
        backStack.getOrNull(origin.size) == subRoute && backStack.take(origin.size) == origin
}

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
    onRestore: () -> Unit,
    onSwitchToClassic: (bookUrl: String) -> Unit,
    onOpenTtsEnginesAndVoices: (bookUrl: String) -> Unit,
    onOpenTtsCache: () -> Unit,
    onOpenBookVoiceCasting: (bookUrl: String) -> Unit,
    onOpenReadAloudSubPage: (ReadAloudSubPage) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navRouteTracker: MainNavRouteTracker = koinInject()
    val navBackStack by navRouteTracker.backStack.collectAsStateWithLifecycle()
    val settingsState by playerViewModel.readAloudSettings.collectAsStateWithLifecycle()
    var configVisible by rememberSaveable { mutableStateOf(false) }
    /**
     * 朗读设置停在哪个 tab（0=常规，1=引擎与音色，2=角色与配乐）。存在宿主而不是弹层里：
     * 压进整屏页会拆掉弹层那层 composition，`rememberPagerState` 的初值回到 0，
     * 回来就落在常规 tab。
     */
    var configTab by rememberSaveable { mutableIntStateOf(0) }
    // 播放器重新展开后再显示设置，展开期间系统返回仍由播放器接管。
    var configWaitsForPlayer by remember { mutableStateOf(false) }
    var pendingConfigReturn by remember { mutableStateOf<ReadAloudConfigReturn?>(null) }
    var activeNumberConfig by rememberSaveable {
        mutableStateOf<ReadAloudPlayerConfigHostAction?>(null)
    }
    val expanded by remember { derivedStateOf { morph.expanded } }
    val currentDismiss by rememberUpdatedState(onDismiss)
    val currentRestore by rememberUpdatedState(onRestore)
    val currentSwitch by rememberUpdatedState(onSwitchToClassic)
    val currentOpenTtsEnginesAndVoices by rememberUpdatedState(onOpenTtsEnginesAndVoices)
    val currentOpenTtsCache by rememberUpdatedState(onOpenTtsCache)
    val currentOpenBookVoiceCasting by rememberUpdatedState(onOpenBookVoiceCasting)
    val currentOpenReadAloudSubPage by rememberUpdatedState(onOpenReadAloudSubPage)

    suspend fun collapsePlayer() {
        configVisible = false
        activeNumberConfig = null
        morph.animateTo(0f)
        currentDismiss()
    }

    fun navigateFromPlayer(action: () -> Unit) {
        scope.launch {
            val origin = navRouteTracker.backStack.value
            // 先推目的地再收播放器：等收完再推，中间会露出阅读页一帧
            action()
            val destination = navRouteTracker.backStack.value
            if (destination == origin) return@launch
            pendingConfigReturn = if (configVisible && destination.dropLast(1) == origin) {
                ReadAloudConfigReturn(origin, destination.last())
            } else null
            collapsePlayer()
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

            ReadAloudPlayerConfigHostAction.OpenVoicePool -> navigateFromPlayer {
                currentOpenReadAloudSubPage(ReadAloudSubPage.VoicePool)
            }

            ReadAloudPlayerConfigHostAction.OpenBgmPool -> navigateFromPlayer {
                currentOpenReadAloudSubPage(ReadAloudSubPage.BgmPool)
            }

            ReadAloudPlayerConfigHostAction.OpenCapsuleStyle -> navigateFromPlayer {
                currentOpenReadAloudSubPage(ReadAloudSubPage.CapsuleStyle)
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
                configVisible = true
            }
        } else if (!configWaitsForPlayer) {
            configVisible = false
            activeNumberConfig = null
        }
    }
    LaunchedEffect(navBackStack, visible, pendingConfigReturn) {
        val pending = pendingConfigReturn ?: return@LaunchedEffect
        when {
            navBackStack == pending.origin -> {
                // 快速返回时收起动画可能还在运行，等播放器关闭后再展开。
                if (!visible) {
                    pendingConfigReturn = null
                    configWaitsForPlayer = true
                    currentRestore()
                }
            }
            // 登录页和人物详情等嵌套页仍在此次设置流程中。
            !pending.isInSubRoute(navBackStack) -> pendingConfigReturn = null
        }
    }
    LaunchedEffect(playerViewModel) {
        playerViewModel.effects.collect { effect ->
            when (effect) {
                ReadAloudPlayerEffect.ReturnToClassic -> {
                    pendingConfigReturn = null
                    configWaitsForPlayer = false
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
            // 正文内配乐浮层由阅读器宿主承载，播放页没有这套 ViewModel
            bgmSceneActionsEnabled = false,
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
