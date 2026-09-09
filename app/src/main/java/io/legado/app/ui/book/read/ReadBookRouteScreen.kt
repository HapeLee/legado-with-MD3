package io.legado.app.ui.book.read

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.constant.ReadMenuBlurMode
import io.legado.app.feature.reader.ReaderBackgroundSurface
import io.legado.app.feature.reader.ReaderCanvasSurface
import io.legado.app.feature.reader.core.gesture.ReaderTapActionGrid
import io.legado.app.feature.reader.core.model.readerBackgroundAlpha
import io.legado.app.feature.reader.core.transition.ReaderTransitionMode
import io.legado.app.help.IntentHelp
import io.legado.app.model.ReadBook
import io.legado.app.model.SourceCallBack
import io.legado.app.model.translation.TranslationChapterStatus
import io.legado.app.ui.book.read.page.entities.PageDirection
import io.legado.app.ui.book.read.sheet.ReaderBookSheetRoute
import io.legado.app.ui.book.read.sheet.ReaderBookSourceActions
import io.legado.app.ui.book.read.sheet.TextSelectMenuConfigSheet
import io.legado.app.ui.book.searchContent.SearchContentResult
import io.legado.app.ui.login.SourceLoginType
import io.legado.app.ui.main.AndroidPlatformCapabilities
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.main.MainRouteReplaceEdit
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.ui.widget.components.image.cover.sharedCoverSourceRadius
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.takePersistablePermissionSafely
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


interface ReadBookRouteHost {

    val isInMultiWindowModeCompat: Boolean

    fun closeReadBook()

    fun previewBrightness(value: Int)

    fun upSystemUiVisibility()

    fun upSystemUiVisibility(
        isInMultiWindow: Boolean,
        toolBarHide: Boolean,
    )
}

/**
 * Narrow interface for hardware input delegation from Activity.
 * MainActivity holds this instead of the full bridge/controller.
 */
interface ReadBookInputHandler {
    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean
    fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean
    fun mouseWheelPage(direction: PageDirection)
    fun handleKeyPage(direction: PageDirection, longPress: Boolean = false)
    fun toggleMenu()
}

/**
 * Outer wrapper for ReadBookScreen — handles system UI state sync
 * and ActivityResult launcher registration.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ReadBookRouteScreen(
    viewModel: ReadBookViewModel,
    readerSessionViewModel: ReaderSessionViewModel,
    host: ReadBookRouteHost,
    controller: ReadBookController,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKey: String? = null,
    onEffectsReady: () -> Unit = {},
    onOpenSearch: (word: String?, bookUrl: String, autoFocus: Boolean) -> Unit = { _, _, _ -> },
    onOpenVoiceCasting: (bookUrl: String) -> Unit = {},
    onOpenTtsEnginesAndVoices: () -> Unit = {},
    onOpenTtsCache: () -> Unit = {},
    // null = 打开替换规则列表页；非 null = 直达某条规则的编辑页
    onOpenReplace: (edit: MainRouteReplaceEdit?) -> Unit = {},
    /**
     * 打开书籍详情页。详情页里把书删了要能通知回来（原 `BookInfoActivity` 的
     * `setResult(RESULT_OK)` 语义），所以回传由 host 层挂结果通道承担。
     */
    onOpenBookInfo: (name: String, author: String, bookUrl: String) -> Unit = { _, _, _ -> },
    /** 打开全屏目录页（picker）：回传选中章节的活由 host 层承担。 */
    onOpenChapterList: (bookUrl: String) -> Unit = {},
    /** 目录/书签抽屉点「全屏」：回传选中章节的活由 host 层承担。 */
    onOpenFullToc: (bookUrl: String, initialPage: Int) -> Unit = { _, _ -> },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val readPreferences by viewModel.readPreferences.collectAsStateWithLifecycle()
    val readerRenderState by readerSessionViewModel.uiState.collectAsStateWithLifecycle()
    val readerPageWindow = readerRenderState.pageWindow
    val readerPaginationError = readerRenderState.paginationError
    val readerBackground = readerRenderState.background
    val localDensity = LocalDensity.current
    val density = localDensity.density
    // 正文避让是配置驱动的（ReaderContentAvoidancePolicy，对照原版 PageView 占位 View）：
    // 菜单开关翻转系统栏可见性时，本 padding 保持恒定，正文不随 overlay 重排。
    val readerSystemBarInsets = rememberReaderSystemBarInsets()
    val readerContentPadding = ReaderContentAvoidancePolicy.padding(
        insets = readerSystemBarInsets,
        hideStatusBar = readPreferences.hideStatusBar,
        hideNavigationBar = readPreferences.hideNavigationBar,
        paddingDisplayCutouts = readPreferences.paddingDisplayCutouts,
        inMultiWindow = controller.isInMultiWindowModeCompat,
    )
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val appUiConfiguration = LocalAppUiConfiguration.current
    val isDarkTheme = appUiConfiguration.isDarkTheme
    val isEInkMode = appUiConfiguration.theme.appTheme == "4"
    val eyeProtectionActive = rememberEyeProtectionActive(
        enabled = state.eyeProtection.enabled,
        autoNight = state.eyeProtection.autoNight,
        isDark = isDarkTheme,
        schedule = state.eyeProtection.schedule,
        startTime = state.eyeProtection.startTime,
        endTime = state.eyeProtection.endTime,
    )
    val effectsReady = remember(viewModel) { CompletableDeferred<Unit>() }
    val menuBackdrop = rememberLayerBackdrop()
    val menuHazeState = remember { HazeState() }
    val useMenuHazeSource = state.menuConfig.readMenuTopBarBlurMode == ReadMenuBlurMode.Haze ||
            state.menuConfig.readMenuBottomBarBlurMode == ReadMenuBlurMode.Haze ||
            (
                    !state.menuConfig.readMenuFloatingBottomBar &&
                            state.menuConfig.readMenuBottomBarBlurMode == ReadMenuBlurMode.LiquidGlass
                    )
    BackHandler {
        when {
            state.activeSheet != null -> viewModel.onIntent(ReadBookIntent.DismissSheet)
            state.isShowingSearchResult -> viewModel.onIntent(ReadBookIntent.ExitSearch)
            state.isAutoPage -> viewModel.onIntent(ReadBookIntent.StopAutoPage)
            state.menuState.canNavigateBack -> viewModel.onIntent(ReadBookIntent.ReadMenuBack)
            else -> viewModel.onIntent(ReadBookIntent.CloseReadBook())
        }
    }
    DisposableEffect(controller) {
        controller.onComposeRendererAttached()
        onDispose {
            controller.onComposeRendererDetached()
            controller.clearAppThemeOverride()
        }
    }

    LaunchedEffect(viewModel, controller, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.readAloudProgress.collect { chapterStart ->
                chapterStart?.let(controller::updateReadAloudProgress)
            }
        }
    }

    // ── ActivityResult Launchers ──────────────────────────────────────

    val sourceEditLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onIntent(ReadBookIntent.SourceEditResult)
        }
    }

    val fontFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            it.takePersistablePermissionSafely(context)
            viewModel.onIntent(ReadBookIntent.FontFolderSelected(it))
        }
    }

    val booksDirPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            it.takePersistablePermissionSafely(context)
            viewModel.onIntent(ReadBookIntent.BooksDirSelected(it))
        }
    }

    val readStyleImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onIntent(ReadBookIntent.ReadStyleImageSelected(it)) }
    }

    var pendingReadStyleImageIsNight by remember { mutableStateOf(false) }
    val readStyleImagePickerForMode = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.onIntent(ReadBookIntent.ReadStyleImageSelectedForMode(it, pendingReadStyleImageIsNight))
        }
    }

    val readStyleImportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.onIntent(ReadBookIntent.ReadStyleConfigImportSelected(it)) }
    }

    val readStyleExportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { viewModel.onIntent(ReadBookIntent.ReadStyleConfigExportSelected(it)) }
    }

    var pendingMenuCustomIconId by remember { mutableStateOf<String?>(null) }
    val menuCustomIconPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val id = pendingMenuCustomIconId
        pendingMenuCustomIconId = null
        if (id != null && uri != null) {
            viewModel.onIntent(ReadBookIntent.SaveMenuCustomIcon(id, uri))
        }
    }

    var pendingTitleBarCustomIconId by remember { mutableStateOf<String?>(null) }
    val titleBarCustomIconPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val id = pendingTitleBarCustomIconId
        pendingTitleBarCustomIconId = null
        if (id != null && uri != null) {
            viewModel.onIntent(ReadBookIntent.SaveTitleBarCustomIcon(id, uri))
        }
    }

    val bookmarkBadgeImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onIntent(ReadBookIntent.BookmarkBadgeImageSelected(it)) }
    }

    val txtTocRuleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.getStringExtra("tocRegex")?.let { rule ->
                viewModel.onIntent(ReadBookIntent.TocRegexResult(rule))
            }
        }
    }

    val importHighlightRulePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.onIntent(ReadBookIntent.HighlightRuleImportFileSelected(it)) }
    }

    val exportHighlightRulePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.onIntent(ReadBookIntent.ExportHighlightRulesToFile(it)) }
    }


    AutoSuggestDayNightObserver(
        viewModel = viewModel,
        autoSuggestDayNight = readPreferences.autoSuggestDayNight,
        lifecycleOwner = lifecycleOwner,
    )

    // ── Effect collection: route handles launcher effects, rest goes to bridge ──

    LaunchedEffect(viewModel) {
        launch {
            viewModel.effects
                .onSubscription {
                    effectsReady.complete(Unit)
                    onEffectsReady()
                }
                .collect { effect ->
                    try {
                        when (effect) {
                            // Launcher-dependent effects — handled directly by route
                            is ReadBookEffect.OpenSourceEdit -> {
                                sourceEditLauncher.launch(
                                    MainActivity.createBookSourceEditIntent(
                                        context,
                                        effect.sourceUrl
                                    )
                                )
                            }
                            is ReadBookEffect.OpenChapterList -> {
                                // 目录页已收进主界面返回栈：压栈后由同栈结果通道回传选中章节，
                                // 不再跨 Activity 走 ActivityResult。
                                onOpenChapterList(effect.bookUrl)
                            }
                            is ReadBookEffect.OpenBookInfo -> {
                                onOpenBookInfo(effect.name, effect.author, effect.bookUrl)
                            }
                            is ReadBookEffect.ShowLogin -> {
                                context.startActivity(
                                    MainActivity.createSourceLoginIntent(
                                        context,
                                        SourceLoginType.ReadingBook
                                    )
                                )
                            }
                            is ReadBookEffect.OpenWebView -> {
                                context.startActivity(
                                    MainActivity.createWebViewIntent(
                                        context, effect.title, effect.url, effect.sourceOrigin,
                                        effect.sourceName, effect.sourceType, html = effect.html,
                                    )
                                )
                            }
                            is ReadBookEffect.RunSourceCustomButton -> {
                                (context as? AppCompatActivity)?.let { activity ->
                                    SourceCallBack.callBackBtn(
                                        activity,
                                        effect.event,
                                        effect.source,
                                        effect.book,
                                        effect.chapter,
                                        BookType.text,
                                    )
                                }
                            }
                            is ReadBookEffect.OpenSearch -> {
                                onOpenSearch(effect.word, effect.bookUrl, effect.autoFocus)
                            }
                            is ReadBookEffect.OpenBookVoiceCasting -> {
                                onOpenVoiceCasting(effect.bookUrl)
                            }
                            ReadBookEffect.OpenTtsEnginesAndVoices -> onOpenTtsEnginesAndVoices()
                            ReadBookEffect.OpenTtsCache -> onOpenTtsCache()
                            is ReadBookEffect.MenuSettingReplace -> {
                                onOpenReplace(null)
                            }
                            is ReadBookEffect.TextActionReplace -> {
                                val scopes = arrayListOf<String>()
                                effect.bookName?.let { scopes.add(it) }
                                effect.bookSourceUrl?.let { scopes.add(it) }
                                val text = effect.text.lineSequence().map { it.trim() }.joinToString("\n")
                                onOpenReplace(
                                    MainRouteReplaceEdit(
                                        id = -1, pattern = text,
                                        scope = scopes.joinToString(";"),
                                        isScopeTitle = false, isScopeContent = true,
                                    )
                                )
                            }
                            is ReadBookEffect.OpenReplaceEditor -> {
                                onOpenReplace(
                                    MainRouteReplaceEdit(id = effect.id, pattern = effect.pattern)
                                )
                            }
                            is ReadBookEffect.MenuTocRegex -> {
                                val intent = Intent(
                                    context,
                                    io.legado.app.ui.book.toc.rule.preview.TxtTocRulePreviewActivity::class.java
                                )
                                intent.putExtra("bookUrl", effect.bookUrl)
                                intent.putExtra("tocRegex", effect.tocRegex)
                                txtTocRuleLauncher.launch(intent)
                            }
                            is ReadBookEffect.OpenFontFolderPicker -> {
                                fontFolderPicker.launch(null)
                            }
                            is ReadBookEffect.OpenBooksDirPicker -> {
                                booksDirPicker.launch(null)
                            }
                            is ReadBookEffect.OpenReadStyleImagePicker -> {
                                readStyleImagePicker.launch("image/*")
                            }
                            is ReadBookEffect.OpenReadStyleImagePickerForMode -> {
                                pendingReadStyleImageIsNight = effect.isNight
                                readStyleImagePickerForMode.launch("image/*")
                            }
                            is ReadBookEffect.OpenReadStyleImport -> {
                                readStyleImportPicker.launch(
                                    arrayOf("application/zip", "application/octet-stream", "*/*")
                                )
                            }
                            is ReadBookEffect.OpenReadStyleExport -> {
                                readStyleExportPicker.launch(effect.fileName)
                            }
                            is ReadBookEffect.OpenMenuCustomIconPicker -> {
                                pendingMenuCustomIconId = effect.id
                                menuCustomIconPicker.launch("image/*")
                            }
                            is ReadBookEffect.OpenTitleBarCustomIconPicker -> {
                                pendingTitleBarCustomIconId = effect.id
                                titleBarCustomIconPicker.launch("image/*")
                            }
                            is ReadBookEffect.OpenSystemTtsSettings -> {
                                IntentHelp.openTTSSetting()
                            }
                            is ReadBookEffect.TtsCacheCleared -> {
                                context.toastOnUi(effect.message)
                            }
                            is ReadBookEffect.OpenHighlightRuleImportPicker -> {
                                importHighlightRulePicker.launch(
                                    arrayOf(
                                        "application/json",
                                        "text/plain"
                                    )
                                )
                            }

                            is ReadBookEffect.OpenHighlightRuleExportPicker -> {
                                exportHighlightRulePicker.launch("highlightRule.json")
                            }

                            // All other effects — delegate to bridge (View/Window/Activity operations)
                            else -> controller.handleEffect(effect)
                        }
                    } catch (e: Exception) {
                        AppLog.put("ReadBook effect处理异常: ${effect::class.simpleName}", e)
                    }
                }
        }
    }

    // ── System UI sync ────────────────────────────────────────────────

    LaunchedEffect(state.menuVisible) {
        host.upSystemUiVisibility(host.isInMultiWindowModeCompat, !state.menuVisible)
    }

    // ── Search result collection (from Navigation3 search route) ──────

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            SearchContentResult.results.collect { result ->
                effectsReady.await()
                if (result.bookUrl != ReadBook.book?.bookUrl) {
                    SearchContentResult.resetReplayCache()
                    return@collect
                }
                when (result) {
                    is SearchContentResult.Clear -> {
                        viewModel.onIntent(ReadBookIntent.SetSearchResults(emptyList(), 0, ""))
                        viewModel.onIntent(ReadBookIntent.ExitSearch)
                    }

                    is SearchContentResult.Result -> {
                        viewModel.onIntent(
                            ReadBookIntent.SetSearchResults(
                                result.searchResults,
                                result.index,
                                result.query
                            )
                        )
                        result.searchResults.getOrNull(result.index)?.let { searchResult ->
                            viewModel.onIntent(
                                ReadBookIntent.NavigateToSearchResult(searchResult, result.index)
                            )
                        }
                    }
                }
                SearchContentResult.resetReplayCache()
            }
        }
    }

    // ── View layer + Compose UI ───────────────────────────────────────

    var showSelectMenuConfigSheet by rememberSaveable { mutableStateOf(false) }
    var featureOverlaysInitialized by remember { mutableStateOf(false) }
    val featureOverlayRequested = state.activeSheet != null ||
        state.activeDialog != null ||
        state.pendingBookmarkTarget != null
    LaunchedEffect(featureOverlayRequested) {
        if (featureOverlayRequested) featureOverlaysInitialized = true
    }

    val firstFrameStartedAtNanos = remember(controller) {
        val requestedStart = controller.activity.intent.getLongExtra(
            EXTRA_FIRST_FRAME_STARTED_AT_NANOS,
            0L,
        )
        requestedStart.takeIf { it > 0L } ?: SystemClock.elapsedRealtimeNanos()
    }
    val loadingFrameTracker = remember(controller) {
        ReaderFirstFrameTracker(firstFrameStartedAtNanos)
    }
    val contentFrameTracker = remember(controller) {
        ReaderFirstFrameTracker(firstFrameStartedAtNanos)
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        loadingFrameTracker.report(ReaderStartupFramePhase.LOADING)
    }
    LaunchedEffect(isDarkTheme) {
        controller.onAppThemeChanged(isDarkTheme)
    }
    LaunchedEffect(readerPageWindow.current?.id, readerPageWindow.current?.layoutRevision) {
        if (readerPageWindow.current != null) {
            withFrameNanos { }
            contentFrameTracker.report(ReaderStartupFramePhase.CONTENT)
        }
    }

    val readerSurfaceColor = Color(
        readerBackground.meanColorArgb.takeIf { it != 0 } ?: runCatching {
            android.graphics.Color.parseColor(
                if (isDarkTheme) state.styleConfig.bgStrNight else state.styleConfig.bgStr
            )
        }.getOrDefault(
            if (isDarkTheme) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        )
    )
    val readerEntranceSettled = animatedVisibilityScope?.transition?.let { transition ->
        !transition.isRunning &&
            transition.currentState == EnterExitState.Visible &&
            transition.targetState == EnterExitState.Visible
    } ?: true
    LaunchedEffect(readerEntranceSettled) {
        controller.onReaderEntranceStateChanged(readerEntranceSettled)
        if (readerEntranceSettled) viewModel.onReaderEntranceSettled()
    }
    val hasReadablePage = readerPageWindow.current != null && state.msg == null
    var readerContentRevealAllowed by remember(sharedCoverKey) {
        mutableStateOf(sharedCoverKey == null || animatedVisibilityScope == null)
    }
    LaunchedEffect(sharedCoverKey, animatedVisibilityScope) {
        if (!readerContentRevealAllowed) {
            delay(240)
            readerContentRevealAllowed = true
        }
    }
    // 阅读页 sharedBounds 的裁剪圆角动画：从封面源圆角渐变到设备屏幕圆角，
    // 转场收尾时正文页与物理圆角贴合（Compose 不会自动插值两端 clip，需自行驱动）。
    val platformCapabilities = remember(controller) { AndroidPlatformCapabilities(controller.activity) }
    val displayConfiguration = LocalConfiguration.current
    val displayCornerRadiusPx = remember(displayConfiguration) { platformCapabilities.displayCornerRadiusPx }
    val readerClipRadiusDp = rememberReaderSharedClipRadiusDp(
        sharedCoverKey = sharedCoverKey,
        animatedVisibilityScope = animatedVisibilityScope,
        targetRadiusPx = displayCornerRadiusPx,
        density = density,
    )
    Box(
        Modifier
            .fillMaxSize()
            .then(
                with(sharedTransitionScope) {
                    if (this != null &&
                        animatedVisibilityScope != null &&
                        sharedCoverKey != null &&
                        readerClipRadiusDp != null
                    ) {
                        Modifier.sharedBounds(
                            sharedContentState = rememberSharedContentState(sharedCoverKey),
                            animatedVisibilityScope = animatedVisibilityScope,
                            enter = fadeIn(animationSpec = tween(600)),
                            exit = fadeOut(animationSpec = tween(600)),
                            clipInOverlayDuringTransition = OverlayClip(
                                RoundedCornerShape(readerClipRadiusDp)
                            ),
                        )
                    } else {
                        Modifier
                    }
                }
            )
            .background(readerSurfaceColor)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    controller.onComposeReaderViewportChanged(
                        widthPx = size.width,
                        heightPx = size.height,
                        density = density,
                        contentPadding = readerContentPadding,
                    )
                }
        ) {
            ReaderBackgroundSurface(
                backgroundImage = readerBackground.drawable,
                backgroundImageAlpha = readerBackgroundAlpha(state.styleConfig.bgAlpha),
                modifier = Modifier.fillMaxSize(),
                animateAppearance = true,
            )
            AnimatedVisibility(
                visible = readerContentRevealAllowed && hasReadablePage,
                enter = fadeIn(animationSpec = tween(400)),
                exit = fadeOut(animationSpec = tween(450)),
            ) {
                ReaderCanvasSurface(
                hostPages = readerPageWindow,
                transitionMode = ReaderTransitionMode.fromPageAnim(controller.pageAnim),
                backgroundColor = readerSurfaceColor,
                backgroundImage = readerBackground.drawable,
                backgroundRevision = readerBackground.revision,
                backgroundImageAlpha = readerBackgroundAlpha(state.styleConfig.bgAlpha),
                selectionColor = LegadoTheme.colorScheme.primary.copy(alpha = 0.28f),
                textAccentColor = Color(state.sheetConfig.textAccentColor),
                autoPageIndicatorColor = LegadoTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (useMenuHazeSource) {
                            Modifier.hazeSource(menuHazeState)
                        } else {
                            Modifier
                        }
                    )
                    .layerBackdrop(menuBackdrop),
                onPreviousPage = { controller.completeComposePageTurn(PageDirection.PREV) },
                onNextPage = { controller.completeComposePageTurn(PageDirection.NEXT) },
                onToggleMenu = controller::showComposeActionMenu,
                onToggleBookmark = { viewModel.onIntent(ReadBookIntent.ToggleBookmark) },
                swipeToBookmarkEnabled = readPreferences.swipeToAddBookmark,
                hasBookmarkOnCurrentPage = controller::hasBookmarkOnComposePage,
                cachedImage = controller::cachedReaderImage,
                loadImage = controller::loadReaderImage,
                autoPageActive = state.isAutoPage,
                autoPagePaused = state.menuVisible,
                autoReadSpeedSeconds = readPreferences.autoReadSpeed,
                isEInkMode = isEInkMode,
                onAutoPageStop = controller::stopAutoPage,
                onShowSelectionMenu = controller::showComposeTextActionMenu,
                onDismissSelectionMenu = controller::dismissTextActionMenu,
                onElementClick = controller::onComposeReaderElementClick,
                onElementLongPress = controller::onComposeReaderElementLongPress,
                selectionEnabled = readPreferences.selectText,
                selectionHapticsEnabled = readPreferences.selectVibrator,
                tapActionGrid = ReaderTapActionGrid.fromLegacyValues(
                    readPreferences.clickActionTL,
                    readPreferences.clickActionTC,
                    readPreferences.clickActionTR,
                    readPreferences.clickActionML,
                    readPreferences.clickActionMC,
                    readPreferences.clickActionMR,
                    readPreferences.clickActionBL,
                    readPreferences.clickActionBC,
                    readPreferences.clickActionBR,
                ),
                onTapAction = controller::onComposeTapAction,
                onReaderInteraction = controller::screenOffTimerStart,
                configuredTouchSlopPx = readPreferences.pageTouchSlop,
                noAnimationScrollPage = readPreferences.noAnimScrollPage,
                externalPageTurns = controller.composePageTurns,
                externalSelectionCancels = controller.composeSelectionCancels,
                    onVisibleBodyTextPositionProvider = controller::setComposeVisibleBodyTextPositionProvider,
                )
            }
            AnimatedVisibility(
                visible = readerEntranceSettled && !hasReadablePage,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300)),
            ) {
                val message = state.msg ?: if (readerPaginationError != null) {
                    stringResource(R.string.load_error_retry)
                } else {
                    stringResource(R.string.data_loading)
                }
                val retryable = state.msg == null && readerPaginationError != null
                val retryLabel = stringResource(R.string.dynamic_click_retry)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (retryable) {
                                Modifier.clickable(
                                    role = Role.Button,
                                    onClickLabel = retryLabel,
                                    onClick = controller::retryComposeReaderPagination,
                                )
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        text = if (retryable) "$message\n$retryLabel" else message,
                        color = LegadoTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        ReadBookColorTheme(
            styleConfig = state.styleConfig,
            preferences = readPreferences,
            isDarkTheme = isDarkTheme,
        ) {
            ReadBookMenuBar(
                state = state,
                preferences = readPreferences,
                eyeProtectionActive = eyeProtectionActive,
                onIntent = { intent ->
                    if (intent !is ReadBookIntent.SkipToPage ||
                        !controller.seekComposeChapterPage(intent.pageIndex)
                    ) {
                        viewModel.onIntent(intent)
                    }
                },
                onBrightnessPreview = host::previewBrightness,
                backdrop = menuBackdrop,
                hazeState = if (useMenuHazeSource) menuHazeState else null,
            )
            ReadBookSearchBar(state = state, onIntent = viewModel::onIntent)
            ReadBookFloatingActionBar(state = state, onIntent = viewModel::onIntent)
            AnimatedVisibility(
                visible = state.translationStatus == TranslationChapterStatus.Thinking,
                enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.88f),
                exit = fadeOut(tween(140)) + scaleOut(tween(180), targetScale = 0.88f),
            ) {
                TranslationThinkingCapsule()
            }
            AnimatedVisibility(
                visible = state.isReadAloudRunning &&
                    state.showReadAloudCapsule &&
                        !state.menuVisible,
                enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.88f),
                exit = fadeOut(tween(140)) + scaleOut(tween(180), targetScale = 0.88f),
            ) {
                ReadAloudCapsule(
                    book = state.book,
                    isPaused = state.isReadAloudPaused,
                    offsetXDp = state.readAloudCapsuleOffsetX,
                    offsetYDp = state.readAloudCapsuleOffsetY,
                    progress = state.readAloudChapterPosition.toFloat() /
                        state.readAloudChapterLength.coerceAtLeast(1),
                    autoCollapse = state.capsuleAutoCollapse,
                    onPositionChanged = { x, y ->
                        viewModel.onIntent(ReadBookIntent.SetReadAloudCapsulePosition(x, y))
                    },
                    onTogglePause = {
                        viewModel.onIntent(ReadBookIntent.ReadAloudTogglePause)
                    },
                    onStop = { viewModel.onIntent(ReadBookIntent.ReadAloudStop) },
                    onOpenPlayer = { viewModel.onIntent(ReadBookIntent.OpenReadAloudPlayer) },
                )
            }
            if (featureOverlaysInitialized) {
                ReadBookOverlayRoute(
                    viewModel = viewModel,
                    state = state,
                    preferences = readPreferences,
                    onOpenTextSelectMenuConfig = {
                        viewModel.onIntent(ReadBookIntent.DismissSheet)
                        showSelectMenuConfigSheet = true
                    },
                    onPickBookmarkBadgeImage = { bookmarkBadgeImagePicker.launch("image/*") },
                    onResetBookmarkBadge = {
                        viewModel.onIntent(ReadBookIntent.ClearBookmarkBadgeImage)
                    },
                )
            }
            val bookNavigationSheet = state.activeSheet as? ReadBookSheet.BookNavigation
            ReaderBookSheetRoute(
                show = bookNavigationSheet != null,
                bookUrl = state.book?.bookUrl.orEmpty(),
                initialTab = bookNavigationSheet?.initialTab
                    ?: io.legado.app.ui.book.read.sheet.ReaderBookSheetTab.Information,
                onDismissRequest = { viewModel.onIntent(ReadBookIntent.DismissSheet) },
                onChapterClick = { index, chapterPos ->
                    viewModel.onIntent(ReadBookIntent.DismissSheet)
                    viewModel.onIntent(ReadBookIntent.OpenChapterResult(index, chapterPos))
                },
                onBookmarkNavigate = { bookmark ->
                    viewModel.onIntent(ReadBookIntent.NavigateToBookmark(bookmark))
                },
                onMarkingNavigate = { item ->
                    viewModel.onIntent(
                        ReadBookIntent.NavigateToMarking(
                            marking = item.raw,
                        )
                    )
                },
                onMarkingEdit = { markingId ->
                    viewModel.onIntent(ReadBookIntent.EditMarking(markingId))
                },
                onOpenFullBookInfo = {
                    state.book?.let { book ->
                        viewModel.onIntent(ReadBookIntent.DismissSheet)
                        onOpenBookInfo(book.name, book.author, book.bookUrl)
                    }
                },
                onOpenFullToc = onOpenFullToc,
                bookSource = state.bookSource,
                onOpenChapterUrl = { viewModel.onIntent(ReadBookIntent.OpenChapterUrl) },
                onToggleReadUrlInBrowser = {
                    viewModel.onIntent(ReadBookIntent.ToggleReadUrlInBrowser)
                },
                sourceActions = ReaderBookSourceActions(
                    onLogin = { viewModel.onIntent(ReadBookIntent.ShowLogin) },
                    onPay = { viewModel.onIntent(ReadBookIntent.PayAction) },
                    onEdit = { viewModel.onIntent(ReadBookIntent.OpenSourceEdit) },
                    onDisable = { viewModel.onIntent(ReadBookIntent.DisableSource) },
                ),
            )
            ReaderTextSelectionOverlay(
                controller = controller,
                expandTextMenu = readPreferences.expandTextMenu,
                onOpenManage = { showSelectMenuConfigSheet = true },
            )
            var configItems by remember { mutableStateOf<List<ActionMenuItem>>(emptyList()) }
            LaunchedEffect(showSelectMenuConfigSheet) {
                if (showSelectMenuConfigSheet) {
                    configItems = controller.getActionMenuItems()
                } else {
                    configItems = emptyList()
                }
            }
            TextSelectMenuConfigSheet(
                show = showSelectMenuConfigSheet,
                items = configItems,
                expandTextMenu = readPreferences.expandTextMenu,
                showSelectMenuIcon = readPreferences.showSelectMenuIcon,
                onExpandTextMenuChange = {
                    viewModel.onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ExpandTextMenu(it)))
                },
                onShowSelectMenuIconChange = {
                    viewModel.onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ShowSelectMenuIcon(it)))
                    controller.refreshActionMenuItems()
                },
                onDismissRequest = { showSelectMenuConfigSheet = false },
                onSaved = { items -> controller.saveMenuConfig(items) }
            )
        }
    }
}

/**
 * 采样系统栏与刘海的原始尺寸（px），供 [ReaderContentAvoidancePolicy] 做配置驱动避让。
 * 状态栏/导航栏走 getInsetsIgnoringVisibility——系统栏隐藏或显隐动画期间同样返回真实
 * 高度（对照 shutiao 的事件化采样），菜单开关不改变采样值；刘海取当前 dispatch 值。
 * GlobalLayout 采样 + data class 去重：稳态零重组，仅屏幕形状/配置变化写回。
 */
@Composable
private fun rememberReaderSystemBarInsets(): ReaderContentAvoidancePolicy.SystemBarInsets {
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    var barInsets by remember(configuration) {
        mutableStateOf(sampleReaderSystemBarInsets(view))
    }
    DisposableEffect(view, configuration) {
        val observer = ViewTreeObserver.OnGlobalLayoutListener {
            barInsets = sampleReaderSystemBarInsets(view)
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(observer)
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(observer) }
    }
    return barInsets
}

private fun sampleReaderSystemBarInsets(
    view: View,
): ReaderContentAvoidancePolicy.SystemBarInsets {
    val rootInsets = ViewCompat.getRootWindowInsets(view)
    val cutout = rootInsets?.getInsets(WindowInsetsCompat.Type.displayCutout())
    return ReaderContentAvoidancePolicy.SystemBarInsets(
        statusBarTopPx = rootInsets
            ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars())?.top ?: 0,
        navigationBarBottomPx = rootInsets
            ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0,
        cutoutLeftPx = cutout?.left ?: 0,
        cutoutTopPx = cutout?.top ?: 0,
        cutoutRightPx = cutout?.right ?: 0,
        cutoutBottomPx = cutout?.bottom ?: 0,
    )
}

/**
 * 阅读页 sharedBounds 转场期的裁剪圆角：起点 = 封面在源页面的圆角
 * （sharedCoverSourceRadius，与封面端动画同源），终点 = 设备屏幕圆角；
 * 非转场返回 null，不参与裁剪。镜像 CoilBookCover.rememberSharedCoverTransitionRadius。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun rememberReaderSharedClipRadiusDp(
    sharedCoverKey: String?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    targetRadiusPx: Float,
    density: Float,
): Dp? {
    if (sharedCoverKey == null || animatedVisibilityScope == null) return null
    val targetRadius = (targetRadiusPx / density).dp
    val startRadius = sharedCoverSourceRadius(sharedCoverKey) ?: targetRadius
    val animatedRadius by animatedVisibilityScope.transition.animateFloat(
        label = "reader-clip-corner-radius",
    ) { state ->
        if (state == EnterExitState.Visible) targetRadius.value else startRadius.value
    }
    return animatedRadius.dp
}

@Composable
private fun ReaderTextSelectionOverlay(
    controller: ReadBookController,
    expandTextMenu: Boolean,
    onOpenManage: () -> Unit,
) {
    val textMenuState by controller.textMenuState.collectAsStateWithLifecycle()
    TextActionSelectionMenu(
        menuState = textMenuState,
        expandTextMenu = expandTextMenu,
        onDismiss = controller::dismissTextActionMenu,
        onItemClick = controller::onTextMenuItemClick,
        onOpenManage = {
            controller.dismissTextActionMenu()
            onOpenManage()
        },
    )
}


@Composable
private fun AutoSuggestDayNightObserver(
    viewModel: ReadBookViewModel,
    autoSuggestDayNight: Boolean,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
) {
    val context = LocalContext.current
    LaunchedEffect(autoSuggestDayNight) {
        if (!autoSuggestDayNight) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
            if (sensorManager != null && lightSensor != null) {
                while (isActive) {
                    if (!viewModel.isDayNightSwitchCoolingDown()) {
                        val finalLux = AtomicReference<Float?>(null)
                        val listener = object : SensorEventListener {
                            override fun onSensorChanged(event: SensorEvent?) {
                                event?.values?.firstOrNull()?.let { lux ->
                                    finalLux.set(lux)
                                }
                            }

                            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                        }
                        try {
                            sensorManager.registerListener(
                                listener,
                                lightSensor,
                                SensorManager.SENSOR_DELAY_NORMAL
                            )
                            delay(1.seconds)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            AppLog.put("lightSensor收集异常", e)
                        } finally {
                            sensorManager.unregisterListener(listener)
                        }

                        finalLux.get()?.let { lux ->
                            viewModel.onIntent(ReadBookIntent.CheckSwitchDayNight(lux))
                        }

                    }

                    delay(15.minutes)
                }
            }
        }
    }
}
