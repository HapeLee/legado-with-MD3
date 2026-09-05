package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.script.rhino.runScriptWithContext
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookProgress
import io.legado.app.data.repository.HighlightRuleRepository
import io.legado.app.feature.reader.core.gesture.ReaderTapAction
import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderImageCachePolicy
import io.legado.app.feature.reader.core.model.ReaderPage
import io.legado.app.feature.reader.core.model.ReaderPageId
import io.legado.app.feature.reader.core.model.ReaderPageWindow
import io.legado.app.feature.reader.core.model.ReaderRect
import io.legado.app.feature.reader.core.model.ReaderThemeColorChange
import io.legado.app.feature.reader.core.model.remapThemeColors
import io.legado.app.feature.reader.core.navigation.ReaderChapterPaginationSnapshot
import io.legado.app.feature.reader.core.navigation.ReaderRenderState
import io.legado.app.feature.reader.core.navigation.ReaderPageContext
import io.legado.app.feature.reader.core.navigation.ReaderPageNavigator
import io.legado.app.feature.reader.core.readaloud.ReaderVisibleTextPosition
import io.legado.app.feature.reader.core.selection.ReaderSearchMatcher
import io.legado.app.feature.reader.core.selection.ReaderSearchRequest
import io.legado.app.feature.reader.core.selection.ReaderSelection
import io.legado.app.feature.reader.core.selection.ReaderSelectionMenuAnchor
import io.legado.app.feature.reader.core.transition.ReaderTurnDirection
import io.legado.app.feature.reader.legacy.LegacyReaderChapterLayoutIdentity
import io.legado.app.feature.reader.legacy.LegacyReaderChapterPaginator
import io.legado.app.feature.reader.legacy.LegacyReaderPageDecorationFactory
import io.legado.app.feature.reader.legacy.LegacyReaderPaginationBatch
import io.legado.app.feature.reader.legacy.LegacyReaderPaginationStyleFactory
import io.legado.app.feature.reader.legacy.collectLegacyReaderPaginationBatch
import io.legado.app.feature.reader.legacy.failureReasonFor
import io.legado.app.feature.reader.legacy.paginateLegacyReaderChapterSafely
import io.legado.app.feature.reader.platform.ReaderAndroidPaginationStyle
import io.legado.app.help.TTS
import io.legado.app.help.book.isOnLineTxt
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.storage.Backup
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.model.CacheBook
import io.legado.app.model.ImageProvider
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.model.ReadSessionState
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setChapter
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.model.analyzeRule.AnalyzeUrl.Companion.paramPattern
import io.legado.app.model.reader.ReaderChapterInput
import io.legado.app.receiver.NetworkChangedListener
import io.legado.app.receiver.TimeBatteryReceiver
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.association.OpenUrlConfirmActivity
import io.legado.app.ui.book.read.page.entities.PageDirection
import io.legado.app.ui.login.SourceLoginJsExtensions
import io.legado.app.ui.widget.PopupAction
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.Debounce
import io.legado.app.utils.GSON
import io.legado.app.utils.LogUtils
import io.legado.app.utils.buildMainHandler
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.longToastOnUi
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setLightStatusBar
import io.legado.app.utils.share
import io.legado.app.utils.sysScreenOffTime
import io.legado.app.utils.throttle
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap


/**
 * Encapsulates all the reader logic that used to be in ReadBookActivity.
 * This allows ReadBookRouteScreen to be hosted in any Activity (ReadBookActivity or MainActivity).
 */
class ReadBookController(
    val activity: AppCompatActivity,
    val viewModel: ReadBookViewModel,
    private val readerSessionViewModel: ReaderSessionViewModel,
) : ReadBookRouteHost,
    ReadBookInputHandler,
    ReadBook.ReaderRenderCallback {

    private val readSettingsGateway get() = org.koin.core.context.GlobalContext.get().get<io.legado.app.domain.gateway.ReadSettingsGateway>()
    private val aloudSettingsGateway get() = org.koin.core.context.GlobalContext.get().get<io.legado.app.domain.gateway.ReadAloudSettingsGateway>()

    internal val layoutController = ReaderLayoutCoordinator(
        updateLayoutSize = { _, _ -> },
        relayoutContent = ReadBook::relayoutContent,
    )

    // Fallback handler for effects not yet migrated to controller
    var onUnhandledEffect: (ReadBookEffect) -> Unit = {}
    var onClose: (() -> Unit)? = null

    // Page state — moved from Activity
    var pageChanged: Boolean = false
        private set

    fun resetPageChanged() {
        pageChanged = false
    }

    private val readerImageCache = object : LruCache<String, android.graphics.Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap): Int = value.allocationByteCount
    }
    private val readerImageLoads = ConcurrentHashMap<String, Deferred<android.graphics.Bitmap?>>()
    private val readerImageGenerations = ConcurrentHashMap<String, Long>()

    private fun readerImageCacheKey(element: ReaderElement.Image): String =
        ReaderImageCachePolicy.withGeneration(
            ReaderImageCachePolicy.key(element),
            readerImageGenerations[element.source] ?: 0L,
        )

    fun cachedReaderImage(element: ReaderElement.Image): android.graphics.Bitmap? =
        readerImageCache.get(readerImageCacheKey(element))?.takeUnless(android.graphics.Bitmap::isRecycled)

    private fun invalidateReaderImage(source: String) {
        val generation = readerImageGenerations.compute(source) { _, current -> (current ?: 0L) + 1L } ?: 1L
        val currentGenerationSuffix = "|generation=$generation"
        readerImageCache.snapshot().keys
            .filter {
                ReaderImageCachePolicy.belongsToSource(it, source) &&
                    !it.endsWith(currentGenerationSuffix)
            }
            .forEach(readerImageCache::remove)
        readerImageLoads.entries
            .filter {
                ReaderImageCachePolicy.belongsToSource(it.key, source) &&
                    !it.key.endsWith(currentGenerationSuffix)
            }
            .forEach { (key, load) ->
            if (readerImageLoads.remove(key, load)) load.cancel()
        }
        val revisionSalt = System.nanoTime()
        var changed = false
        directReaderPages = directReaderPages.map { page ->
            if (page.elements.any { it is ReaderElement.Image && it.source == source }) {
                changed = true
                page.copy(revision = page.revision xor revisionSalt)
            } else {
                page
            }
        }
        if (changed) directReaderPageIndex?.let(::publishDirectReaderWindow)
    }

    /** Android image capability used by the Compose Canvas renderer. */
    suspend fun loadReaderImage(element: ReaderElement.Image): android.graphics.Bitmap? {
        cachedReaderImage(element)?.let { return it }
        val key = readerImageCacheKey(element)
        val candidate = activity.lifecycleScope.async(IO, start = CoroutineStart.LAZY) {
            readerImageCache.get(key)?.takeUnless(android.graphics.Bitmap::isRecycled) ?: ReadBook.book?.let { book ->
                ImageProvider.getImage(
                    book = book,
                    src = element.source,
                    width = element.bounds.width.toInt().coerceAtLeast(1),
                    height = element.bounds.height.toInt().coerceAtLeast(1),
                ).takeUnless(android.graphics.Bitmap::isRecycled)?.also { bitmap ->
                    if (readerImageCacheKey(element) == key) readerImageCache.put(key, bitmap)
                }
            }
        }
        val shared = readerImageLoads.putIfAbsent(key, candidate) ?: candidate.also { created ->
            created.invokeOnCompletion { readerImageLoads.remove(key, created) }
            created.start()
        }
        if (shared !== candidate) candidate.cancel()
        return shared.await()
    }

    override fun previewBrightness(value: Int) {
        val targetBrightness = value.coerceIn(0, 100) / 100f
        val attributes = activity.window.attributes
        if (attributes.screenBrightness != targetBrightness) {
            attributes.screenBrightness = targetBrightness
            activity.window.attributes = attributes
        }
    }

    // Callbacks to Activity for operations that require Activity-level state
    var onScreenOffTimerStart: (() -> Unit)? = null
    var onStartContentLoadFinish: (() -> Unit)? = null

    // Phase 4: callbacks for Activity-dependent effects
    var onToggleReadAloud: (() -> Unit)? = null
    var onToggleAutoPage: (() -> Unit)? = null
    var onStopAutoPage: (() -> Unit)? = null

    private var tts: TTS? = null
    private val timeBatteryReceiver = TimeBatteryReceiver()
    private var timeBatteryReceiverRegistered = false
    private val networkChangedListener by lazy { NetworkChangedListener(activity) }
    private val handler by lazy { buildMainHandler() }
    private val screenOffRunnable by lazy { Runnable { keepScreenOn(false) } }
    private val _textMenuState = MutableStateFlow<TextMenuState?>(null)
    val textMenuState = _textMenuState.asStateFlow()
    private val _readerPageWindow = MutableStateFlow(ReaderPageWindow())
    val readerPageWindow = _readerPageWindow.asStateFlow()
    private val _readerPaginationError = MutableStateFlow<String?>(null)
    val readerPaginationError = _readerPaginationError.asStateFlow()
    private val _readerBackground = MutableStateFlow(
        ReaderBackgroundState(
            drawable = ReadSessionState.background,
            meanColorArgb = ReadSessionState.backgroundMeanColor,
        )
    )
    val readerBackground = _readerBackground.asStateFlow()
    private var readerBackgroundLoadJob: Job? = null
    private var readerBackgroundLoadGeneration = 0L
    private val _composePageTurns = MutableSharedFlow<ReaderTurnDirection>(extraBufferCapacity = 16)
    val composePageTurns = _composePageTurns.asSharedFlow()
    private val _composeSelectionCancels = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val composeSelectionCancels = _composeSelectionCancels.asSharedFlow()
    private var textMenuRequestVersion = 0L
    private var composeSelectedText: String? = null
    private var composeSelection: ReaderSelection? = null
    private var searchSelection: ReaderSelection? = null
    private var pendingSearchNavigation: ReadBookEffect.NavigateToSearchResult? = null
    private var readAloudPosition: Pair<Int, Int>? = null
    private var composeVisibleBodyTextPositionProvider: (() -> ReaderVisibleTextPosition?)? = null
    private var composeImageClickAt = 0L
    private var composeImageDoubleClick = false
    private var directReaderLayoutJob: Job? = null
    private var directReaderLayoutKey: String? = null

    /** Only chapter-window changes may reuse adjacent pages; a reflow invalidates their geometry. */
    private var directReaderMayReuseAdjacentPages = false
    private var directReaderPages = emptyList<io.legado.app.feature.reader.core.model.ReaderPage>()

    /**
     * 页上下文缓存：跨页热路径（书签检查×3 + 进度上报 + 进度提交）每次跨页要取
     * 5 次 pageContext，每次 O(元素数) 遍历 + groupBy + anchorText 拼接，落在拖拽
     * 跨页帧上就是掉帧。按页 id 记忆，重排后失效（重排会重算位置与 endPosition）。
     */
    private val directReaderPageContexts = HashMap<ReaderPageId, ReaderPageContext>()

    private fun directReaderPageContext(index: Int): ReaderPageContext? {
        val page = directReaderPages.getOrNull(index) ?: return null
        return directReaderPageContexts.getOrPut(page.id) {
            ReaderPageNavigator.pageContext(directReaderPages, index) ?: return null
        }
    }
    private var directReaderChapterPageCounts = emptyMap<Int, Int>()
    private var directReaderPageIndex: Int? = null
    private val menuMutex = Mutex()
    @Volatile
    private var cachedActionMenuItems: List<ActionMenuItem>? = null

    init {
        readerSessionViewModel.submitBackground(_readerBackground.value)
        // Background decoding waits for the first measured reading viewport. Decoding once with
        // display metrics here was commonly cancelled by the real content bounds a frame later.
    }

    fun dismissTextActionMenu() {
        textMenuRequestVersion++
        _textMenuState.value = null
    }
    private val popupAction by lazy { PopupAction(activity) }
    private var screenTimeOut: Long = 0
    private var appliedDarkTheme: Boolean? = null
    private val originalRequestedOrientation = activity.requestedOrientation
    private val originalScreenBrightness = activity.window.attributes.screenBrightness
    private val originalKeepScreenOn =
        (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
    // justInitData moved to ViewModel (set on InitData intent)

    val isAutoPage: Boolean get() = viewModel.uiState.value.isAutoPage

    private fun speak(text: String) {
        if (tts == null) {
            tts = TTS()
        }
        tts?.speak(text)
    }

    fun clearTts() {
        ReadBook.unregisterRender(this)
        directReaderLayoutJob?.cancel()
        directReaderLayoutJob = null
        readerImageLoads.values.forEach { it.cancel() }
        readerImageLoads.clear()
        readerImageCache.evictAll()
        tts?.clearTts()
        tts = null
        dismissTextActionMenu()
        popupAction.dismiss()
        networkChangedListener.unRegister()
        unregisterTimeBatteryReceiver()
        restoreActivityWindowState()
    }

    // Phase 5: Key handling / page turn
    var bottomDialogCount: Int = 0

    private val menuLayoutIsVisible: Boolean
        get() = bottomDialogCount > 0 ||
                viewModel.uiState.value.menuVisible ||
                viewModel.uiState.value.searchMenuVisible

    private val nextPageDebounce by lazy { Debounce { keyPage(PageDirection.NEXT) } }
    private val prevPageDebounce by lazy { Debounce { keyPage(PageDirection.PREV) } }

    private val upSeekBarThrottle = throttle(200) {
        viewModel.refreshSeekState()
    }

    fun onComposeRendererAttached() {
        ReadBook.registerRender(this)
        publishReaderPageWindow()
    }

    /**
     * Pagination and background preparation continue during navigation, but their observable
     * Compose state is committed only after the reader entrance transition is idle.
     */
    fun onReaderEntranceStateChanged(settled: Boolean) {
        readerSessionViewModel.onEntranceStateChanged(settled)
    }

    private fun publishReaderRenderState() {
        readerSessionViewModel.submit(ReaderRenderUiState(
            renderState = ReaderRenderState(
                pageWindow = _readerPageWindow.value,
                paginationError = _readerPaginationError.value,
            ),
            background = _readerBackground.value,
        ))
    }

    private fun updateReaderPageWindow(value: ReaderPageWindow): ReaderPageWindow {
        _readerPageWindow.value = value
        readerSessionViewModel.submitPageWindow(value)
        return value
    }

    private fun updateReaderPaginationError(value: String?) {
        _readerPaginationError.value = value
        publishReaderRenderState()
    }

    private fun updateReaderBackground(value: ReaderBackgroundState) {
        _readerBackground.value = value
        readerSessionViewModel.submitBackground(value)
    }

    fun onComposeRendererDetached() {
        ReadBook.unregisterRender(this)
    }

    fun showComposeTextActionMenu(
        selection: ReaderSelection,
        text: String,
        anchor: ReaderSelectionMenuAnchor,
    ) {
        val resolvedText = selection.selectedText(directReaderPages).ifEmpty { text }
        composeSelection = selection
        composeSelectedText = resolvedText
        val requestVersion = ++textMenuRequestVersion
        activity.lifecycleScope.launch {
            val items = getActionMenuItems().filterNot {
                selection.includesTitle && it.id in setOf(
                    R.id.menu_mark, R.id.menu_ai_clean, R.id.menu_ai_rewrite,
                )
            }
            if (textMenuRequestVersion != requestVersion) return@launch
            _textMenuState.value = TextMenuState(
                selectedText = resolvedText,
                startX = anchor.startX.toInt(),
                startTopY = anchor.startTopY.toInt(),
                startBottomY = anchor.startBottomY.toInt(),
                endX = anchor.endX.toInt(),
                endBottomY = anchor.endBottomY.toInt(),
                items = items,
            )
        }
    }

    fun onComposeReaderElementClick(element: ReaderElement): Boolean = when (element) {
        is ReaderElement.Text -> when {
            element.markingId != null -> { onMarkingClick(element.markingId!!); true }
            element.link != null -> {
                activity.startActivity(Intent(activity, OpenUrlConfirmActivity::class.java).putExtra("uri", element.link))
                true
            }
            else -> false
        }
        is ReaderElement.Image -> handleComposeImageClick(element)
        is ReaderElement.Review -> { activity.toastOnUi("Button Pressed!"); true }
        is ReaderElement.Action -> { activity.toastOnUi("Button Pressed!"); true }
        is ReaderElement.Spacer -> false
        is ReaderElement.ParagraphMarker -> false
        is ReaderElement.Rule -> false
    }

    fun onComposeReaderElementLongPress(element: ReaderElement, x: Float, y: Float): Boolean {
        if (element !is ReaderElement.Image) return false
        onImageLongPress(x, y, element.source)
        return true
    }

    fun showComposeActionMenu() {
        val state = viewModel.uiState.value
        when {
            BaseReadAloudService.isRun -> viewModel.onIntent(ReadBookIntent.ReadAloudAction)
            isAutoPage -> viewModel.onIntent(ReadBookIntent.OpenReadMenuRoute(ReadBookMenuRoute.AutoRead))
            state.isShowingSearchResult -> viewModel.onIntent(ReadBookIntent.ShowSearchMenu)
            else -> viewModel.onIntent(ReadBookIntent.ShowMenu)
        }
    }

    fun onComposeTapAction(action: ReaderTapAction) {
        when (action) {
            ReaderTapAction.NONE,
            ReaderTapAction.MENU,
            ReaderTapAction.NEXT_PAGE,
            ReaderTapAction.PREVIOUS_PAGE -> Unit
            ReaderTapAction.NEXT_CHAPTER -> viewModel.onIntent(ReadBookIntent.NextChapter)
            ReaderTapAction.PREVIOUS_CHAPTER -> viewModel.onIntent(ReadBookIntent.PrevChapter)
            ReaderTapAction.READ_ALOUD_PREVIOUS_PARAGRAPH ->
                viewModel.onIntent(ReadBookIntent.ReadAloudPrevParagraph)
            ReaderTapAction.READ_ALOUD_NEXT_PARAGRAPH ->
                viewModel.onIntent(ReadBookIntent.ReadAloudNextParagraph)
            ReaderTapAction.ADD_BOOKMARK -> viewModel.onIntent(ReadBookIntent.AddBookmark)
            ReaderTapAction.OPEN_CONTENT_EDIT -> viewModel.onIntent(ReadBookIntent.OpenContentEdit)
            ReaderTapAction.TOGGLE_REPLACE -> viewModel.onIntent(ReadBookIntent.MenuEnableReplace)
            ReaderTapAction.OPEN_CHAPTER_LIST -> viewModel.onIntent(ReadBookIntent.OpenChapterList)
            ReaderTapAction.OPEN_SEARCH -> viewModel.onIntent(ReadBookIntent.OpenSearch(null))
            ReaderTapAction.SYNC_PROGRESS -> ReadBook.syncProgress(
                newProgressAction = { progress ->
                    activity.runOnUiThread {
                        viewModel.onIntent(ReadBookIntent.SureNewProgress(progress))
                    }
                },
                uploadSuccessAction = {
                    activity.longToastOnUi(activity.getString(R.string.upload_book_success))
                },
                syncSuccessAction = {
                    activity.longToastOnUi(activity.getString(R.string.sync_book_progress_success))
                },
            )
            ReaderTapAction.TOGGLE_READ_ALOUD_PAUSE -> if (BaseReadAloudService.isPlay()) {
                ReadAloud.pause(activity)
            } else {
                ReadAloud.resume(activity)
            }
        }
    }

    private fun handleComposeImageClick(image: ReaderElement.Image): Boolean {
        val now = System.currentTimeMillis()
        val debounce = now - composeImageClickAt < 300L
        composeImageClickAt = now
        composeImageDoubleClick = if (debounce) !composeImageDoubleClick else false
        return when (readSettingsGateway.currentSettings.clickImgWay) {
            "1" -> { viewModel.onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.Photo(image.source))); true }
            "2" -> if (!debounce && ReadBook.book?.isOnLineTxt == true) {
                image.action?.takeIf(String::isNotBlank)?.let { clickImg(it, image.source); true }
                    ?: oldClickImg(image.source)
            } else false
            "3" -> false
            "4" -> if (composeImageDoubleClick) {
                image.action?.takeIf(String::isNotBlank)?.let { clickImg(it, image.source); true } ?: false
            } else true
            else -> if (!debounce) {
                image.action?.takeIf(String::isNotBlank)?.let { clickImg(it, image.source); true } ?: false
            } else false
        }
    }

    fun onComposeReaderViewportChanged(
        widthPx: Int,
        heightPx: Int,
        density: Float,
        contentPadding: ReaderPadding,
    ) {
        val viewport = ReaderViewport(
            widthPx = widthPx,
            heightPx = heightPx,
            density = density,
            contentPadding = contentPadding,
        )
        val viewportChanged = layoutController.viewport.value != viewport
        layoutController.updateViewport(
            viewport
        )
        var viewportPaginationStyle: ReaderAndroidPaginationStyle? = null
        if (viewportChanged) {
            // Width, height, density, or content insets participate in every page's geometry.
            // Do not bridge this reflow with an adjacent page from the previous viewport.
            directReaderMayReuseAdjacentPages = false
            updateComposeReaderBackground(widthPx, heightPx)
            val style = LegacyReaderPaginationStyleFactory.create()
            viewportPaginationStyle = style
            ReadBook.publishReaderPaginationEnvironment(
                widthPx = widthPx,
                heightPx = heightPx,
                style = style,
                contentPaddingLeftPx = contentPadding.left,
                contentPaddingTopPx = contentPadding.top,
                contentPaddingRightPx = contentPadding.right,
                contentPaddingBottomPx = contentPadding.bottom,
            )
            ReadBook.requestWholeBookPageEstimate()
        }
        publishReaderPageWindow(
            paginationStyle = viewportPaginationStyle,
            paginationEnvironmentPublished = viewportChanged,
        )
    }

    private fun publishReaderPageWindow(
        paginationStyle: ReaderAndroidPaginationStyle? = null,
        paginationEnvironmentPublished: Boolean = false,
    ) {
        val viewport = layoutController.viewport.value ?: return
        val width = viewport.widthPx
        val height = viewport.heightPx
        if (width <= 0 || height <= 0) return
        publishDirectReaderPageWindow(
            width = width,
            height = height,
            paginationStyle = paginationStyle,
            paginationEnvironmentPublished = paginationEnvironmentPublished,
        )
        if (_readerPageWindow.value.current?.id?.chapterIndex != ReadBook.durChapterIndex) {
            updateReaderPageWindow(ReaderPageWindow())
        }
    }

    private fun rebuildDirectReaderPages() {
        directReaderLayoutJob?.cancel()
        directReaderLayoutJob = null
        directReaderLayoutKey = null
        directReaderMayReuseAdjacentPages = false
        ReadBook.clearReaderPagination()
        updateReaderPaginationError(null)
        publishReaderPageWindow()
    }

    fun retryComposeReaderPagination() {
        rebuildDirectReaderPages()
    }

    private fun directReaderWindow(index: Int): ReaderPageWindow {
        val window = ReaderPageNavigator.window(directReaderPages, index)
        val selection = searchSelection
        val aloudPosition = readAloudPosition
        val aloudParagraphIndex = aloudPosition?.let { (chapterIndex, chapterPosition) ->
            ReaderPageNavigator.bodyParagraphAt(directReaderPages, chapterIndex, chapterPosition)
        }
        fun highlight(page: io.legado.app.feature.reader.core.model.ReaderPage?, pageIndex: Int) = page?.let { source ->
            val chapterPageCount = directReaderChapterPageCounts[source.id.chapterIndex] ?: 0
            val dynamicState = viewModel.uiState.value
            val contentPadding = layoutController.viewport.value?.contentPadding ?: ReaderPadding()
            val decorated = source.copy(
                decoration = LegacyReaderPageDecorationFactory.create(
                    page = source,
                    chapterPageCount = chapterPageCount,
                    time = dynamicState.time,
                    batteryPercent = dynamicState.battery,
                    hasBookmark = hasBookmarkOnComposePage(pageIndex),
                    contentPaddingLeftPx = contentPadding.left,
                    contentPaddingTopPx = contentPadding.top,
                    contentPaddingRightPx = contentPadding.right,
                    contentPaddingBottomPx = contentPadding.bottom,
                ),
                revision = source.revision xor dynamicState.time.hashCode().toLong() xor dynamicState.battery.toLong(),
            )
            val pageHasSearchSelection = selection?.chapterIndex == source.id.chapterIndex
            val pageHasAloudParagraph = aloudPosition?.first == source.id.chapterIndex &&
                aloudParagraphIndex != null
            decorated.copy(
                // Search/read-aloud state must not clone every glyph in the visible window:
                // scroll draw data is keyed by the immutable layout element list.  The Canvas
                // resolves these compact dynamic ranges while drawing.
                searchStart = selection?.anchor?.takeIf { pageHasSearchSelection },
                searchEndInclusive = selection?.focus?.takeIf { pageHasSearchSelection },
                readAloudParagraphIndex = aloudParagraphIndex.takeIf { pageHasAloudParagraph },
                revision = decorated.revision xor (selection?.hashCode()?.toLong() ?: 0L) xor
                        (aloudPosition?.hashCode()?.toLong() ?: 0L),
            )
        }
        return ReaderPageWindow(
            previous = highlight(window.previous, index - 1),
            current = highlight(window.current, index),
            next = highlight(window.next, index + 1),
            nextPlus = highlight(window.nextPlus, index + 2),
        )
    }

    fun hasBookmarkOnComposePage(): Boolean = directReaderPageIndex?.let(::hasBookmarkOnComposePage) ?: false

    private fun hasBookmarkOnComposePage(index: Int): Boolean {
        val book = ReadBook.book ?: return false
        val page = directReaderPageContext(index) ?: return false
        return io.legado.app.model.ReaderBookmarkState.hasBookmarkInRange(
            bookName = book.name,
            bookAuthor = book.author,
            chapterIndex = page.chapterIndex,
            startPos = page.startPosition,
            endPos = page.endPosition,
        )
    }

    /** 同步平移页窗口并发布；返回发布的窗口，供滚动渲染层当帧折算使用。 */
    private fun publishDirectReaderWindow(index: Int): ReaderPageWindow? {
        if (directReaderPages.isEmpty()) return null
        val boundedIndex = ensureBoundaryPlaceholderPages(index.coerceIn(directReaderPages.indices))
        directReaderPageIndex = boundedIndex
        viewModel.updateComposeReaderPage(
            position = ReaderPageNavigator.chapterPosition(directReaderPages, boundedIndex),
            pageContext = directReaderPageContext(boundedIndex),
        )
        return updateReaderPageWindow(directReaderWindow(boundedIndex))
    }

    /**
     * 邻章未分页时预置"加载中"占位页（对照 shutiao 的占位页滚动继续语义）：
     * 预置后手势层的 window.next/previous 不再为空，拖拽、点按、滚动都能自然
     * 越过章节边界，装载完成后分页批次以同 id 真实页替换。返回当前页在插入后
     * 的列表中的新下标（前侧插入会使既有下标整体后移）。
     */
    private fun ensureBoundaryPlaceholderPages(index: Int): Int {
        val pages = directReaderPages
        val page = pages.getOrNull(index) ?: return index
        val missingChapters = ReaderPageNavigator.missingAdjacentChapters(
            pages,
            index,
            chapterCount = ReadBook.simulatedChapterSize,
        )
        if (missingChapters.isEmpty()) return index
        val updated = pages.toMutableList()
        var added = 0
        missingChapters.forEach { chapterIndex ->
            placeholderReaderPage(chapterIndex)?.let {
                updated.add(it)
                added++
            }
        }
        if (added == 0) return index
        updated.sortWith(compareBy({ it.id.chapterIndex }, { it.id.pageIndex }))
        directReaderPages = updated
        return updated.indexOfFirst { it === page }.coerceAtLeast(0)
    }

    private fun commitManualReaderPage(index: Int) {
        val page = directReaderPages.getOrNull(index) ?: return
        // 提交页边界偏移而非首段 chapterPosition：段落跨页时首段起点落在上一页，
        // 会把持久化进度、朗读定位与 locate 全部拖回上一页末段。pageStart 与
        // 分页快照的 pageStarts 同源（对照 moveToNextPage 的 nextPageStart 语义）。
        val chapterPosition = ReaderPageNavigator.pageStart(page)
        // 热路径安静更新：不发布快照（否则每次跨页触发一次全量 UiState 重建落在动画帧上）。
        ReadBook.updateReadingPosition(chapterPosition, publish = false)
        if (BaseReadAloudService.isRun && ReadBook.onComposeManualPageTurn()) {
            readAloudPosition = page.id.chapterIndex to chapterPosition
        }
    }

    fun seekComposeChapterPage(chapterPageIndex: Int): Boolean {
        val chapterIndex = _readerPageWindow.value.current?.id?.chapterIndex
            ?: ReadBook.durChapterIndex
        val globalIndex = ReaderPageNavigator.locateChapterPage(
            pages = directReaderPages,
            chapterIndex = chapterIndex,
            chapterPageIndex = chapterPageIndex,
        ) ?: return false
        commitManualReaderPage(globalIndex)
        publishDirectReaderWindow(globalIndex)
        pageChanged = true
        viewModel.startBackupJob()
        return true
    }

    override fun readerChapterInputChanged() {
        pendingSearchNavigation?.let { navigation ->
            ReadBook.readerChapterInputWindow.current
                ?.takeIf { it.chapter.index == navigation.result.chapterIndex }
                ?.let { resolveSearchNavigation(navigation, it) }
        }
        publishReaderPageWindow()
    }

    private fun resolveSearchNavigation(
        navigation: ReadBookEffect.NavigateToSearchResult,
        input: ReaderChapterInput,
    ) {
        val result = navigation.result
        val query = result.query.ifBlank { viewModel.uiState.value.searchContentQuery }
        // 选区锚点、updateReadingPosition、locate 都以 semanticContent 为字符空间，
        // 检索仓库的 queryIndexInChapter 在含标题/图片章节会偏移，交由 matcher 校验失败后按 occurrence 回退
        val match = ReaderSearchMatcher.find(
            content = input.source.semanticContent,
            query = query,
            request = ReaderSearchRequest(
                directIndex = result.queryIndexInChapter,
                directLength = result.matchLength,
                occurrence = result.resultCountWithinChapter,
                isRegex = result.isRegex,
            ),
        ) ?: run {
            pendingSearchNavigation = null
            return
        }
        pendingSearchNavigation = null
        searchSelection = ReaderSelection(
            chapterIndex = result.chapterIndex,
            anchor = match.start,
            focus = match.start + match.length - 1,
        )
        ReadBook.updateReadingPosition(match.start)
        directReaderPages.takeIf { pages ->
            pages.any { it.id.chapterIndex == result.chapterIndex }
        }?.let { pages ->
            publishDirectReaderWindow(
                ReaderPageNavigator.locate(pages, result.chapterIndex, match.start)
            )
        }
    }

    private fun publishDirectReaderPageWindow(
        width: Int,
        height: Int,
        paginationStyle: ReaderAndroidPaginationStyle? = null,
        paginationEnvironmentPublished: Boolean = false,
    ): Boolean {
        val contentPadding = layoutController.viewport.value?.contentPadding ?: ReaderPadding()
        val inputWindow = ReadBook.readerChapterInputWindow
        val chapter = inputWindow.current ?: run {
            // 内容未装载（进入书籍/目录跳转装载中）：发布"加载中"占位页窗口，让
            // 阅读画布保持组合、点击分区与菜单照常可用，装载完成后由分页批次
            // 整窗替换（对照 shutiao 的加载占位页正文渲染）。
            publishLoadingReaderWindow()
            return false
        }
        val chapters = listOf(
            inputWindow.previous,
            chapter,
            inputWindow.next,
        ).filterNotNull()
            .distinctBy { it.chapter.index }
            .sortedBy { it.chapter.index }
        val resolvedPaginationStyle = paginationStyle ?: LegacyReaderPaginationStyleFactory.create()
        if (!paginationEnvironmentPublished) {
            ReadBook.publishReaderPaginationEnvironment(
                widthPx = width,
                heightPx = height,
                style = resolvedPaginationStyle,
                contentPaddingLeftPx = contentPadding.left,
                contentPaddingTopPx = contentPadding.top,
                contentPaddingRightPx = contentPadding.right,
                contentPaddingBottomPx = contentPadding.bottom,
            )
        }
        val key = buildString {
            chapters.forEach { candidate ->
                append(LegacyReaderChapterLayoutIdentity(
                    chapterIndex = candidate.chapter.index,
                    chapterUrl = candidate.chapter.url,
                    chapterBaseUrl = candidate.chapter.baseUrl,
                    displayTitle = candidate.displayTitle,
                    isVolume = candidate.chapter.isVolume,
                    contentHash = candidate.contentHash,
                    contentProcessesHash = candidate.contentProcessesHash,
                    sourceHash = candidate.sourceHash,
                    bookUrl = candidate.book.bookUrl,
                    bookOrigin = candidate.book.origin,
                    bookSourceHash = candidate.bookSourceHash,
                )).append(',')
            }
            append('|').append(width).append('x').append(height)
            append('|').append(contentPadding.left).append(',').append(contentPadding.top)
            append(',').append(contentPadding.right).append(',').append(contentPadding.bottom)
            append('|').append(resolvedPaginationStyle.columnCount(width, height))
            append('|').append(resolvedPaginationStyle.isScroll)
            append('|').append(resolvedPaginationStyle.textBottomJustify)
            append('|').append(resolvedPaginationStyle.pageUnderline)
            append('|').append(resolvedPaginationStyle.emphasisUnderlineStyle)
            append('|').append(resolvedPaginationStyle.bodyPaint.textSize)
            append('|').append(resolvedPaginationStyle.titlePaint.textSize)
            append('|').append(resolvedPaginationStyle.bodyStyle)
            append('|').append(resolvedPaginationStyle.titleStyle)
            append('|').append(resolvedPaginationStyle.bodyPaint.letterSpacing)
            append('|').append(ReadBookConfig.textFont)
            append('|').append(ReadBookConfig.titleFont)
            append('|').append(ReadBookConfig.paragraphIndent)
            append('|').append(ReadBookConfig.textFullJustify)
            append('|').append(ReadBookConfig.titleMode)
            append('|').append(ReadBook.book?.getImageStyle())
            append('|').append(resolvedPaginationStyle.paddingLeftPx).append(',').append(resolvedPaginationStyle.paddingTopPx)
            append(',').append(resolvedPaginationStyle.paddingRightPx).append(',').append(resolvedPaginationStyle.paddingBottomPx)
            append('|').append(LegacyReaderPageDecorationFactory.headerExtentPx())
            append(',').append(LegacyReaderPageDecorationFactory.footerExtentPx())
            append('|').append(resolvedPaginationStyle.lineSpacingExtra)
            append('|').append(resolvedPaginationStyle.titleLineSpacingExtra)
            append('|').append(resolvedPaginationStyle.titleLineSpacingSub)
            append('|').append(resolvedPaginationStyle.titleSegmentation)
            append('|').append(resolvedPaginationStyle.titleTopSpacingPx)
            append('|').append(resolvedPaginationStyle.titleBottomSpacingPx)
            append('|').append(resolvedPaginationStyle.paragraphSpacing)
            append('|').append(ReadBookConfig.durConfig.highlightRules.hashCode())
        }
        if (directReaderLayoutKey == key && directReaderPages.isNotEmpty()) {
            // upContent 语义是"按 durChapterPos 重新定位"（对照旧 View upContent 重绘）：
            // 朗读跨页走 moveToNextPage → upContent，只有重定位页面才会前进；缓存下标
            // 会让这类发布变成空操作，页面跟随朗读随之失效。
            val index = ReaderPageNavigator.locate(
                directReaderPages,
                chapter.chapter.index,
                ReadBook.durChapterPos,
            ).also { directReaderPageIndex = it }
            publishDirectReaderWindow(index)
            return true
        }
        if (directReaderLayoutKey != key) {
            val paginationGeneration = ReadBook.readerPaginationGeneration
            directReaderLayoutJob?.cancel()
            directReaderLayoutKey = key
            updateReaderPaginationError(null)
            ReadBook.clearReaderPagination()
            directReaderLayoutJob = activity.lifecycleScope.launch(IO) {
                val highlightRules = HighlightRuleRepository()
                    .loadEnabled(ReadBookConfig.durConfig.name)
                suspend fun paginate(candidate: ReaderChapterInput) =
                    candidate.chapter.index to paginateLegacyReaderChapterSafely {
                        LegacyReaderChapterPaginator.paginate(
                                book = candidate.book,
                                bookSource = candidate.bookSource,
                                chapter = candidate.chapter,
                                displayTitle = candidate.displayTitle,
                                content = candidate.content,
                                source = candidate.source,
                                revision = 31L * key.hashCode() + candidate.chapter.index,
                                viewportWidthPx = width,
                                viewportHeightPx = height,
                                contentPaddingLeftPx = contentPadding.left,
                                contentPaddingTopPx = contentPadding.top,
                                contentPaddingRightPx = contentPadding.right,
                                contentPaddingBottomPx = contentPadding.bottom,
                                paginationStyle = resolvedPaginationStyle,
                                highlightRules = highlightRules,
                            )
                    }
                val currentResult = paginate(chapter)
                val currentBatch = collectLegacyReaderPaginationBatch(
                    currentChapterIndex = chapter.chapter.index,
                    results = listOf(currentResult),
                )
                withContext(Main) {
                    applyDirectReaderPaginationBatch(
                        key = key,
                        currentChapter = chapter,
                        chapters = chapters,
                        batch = currentBatch,
                        paginationGeneration = paginationGeneration,
                        layoutComplete = chapters.size == 1 || !currentBatch.hasCurrentChapter,
                    )
                }
                if (!currentBatch.hasCurrentChapter || chapters.size == 1) return@launch
                val adjacentResults = chapters
                    .filterNot { it.chapter.index == chapter.chapter.index }
                    .map { paginate(it) }
                val completeBatch = collectLegacyReaderPaginationBatch(
                    currentChapterIndex = chapter.chapter.index,
                    results = adjacentResults + currentResult,
                )
                withContext(Main) {
                    applyDirectReaderPaginationBatch(
                        key = key,
                        currentChapter = chapter,
                        chapters = chapters,
                        batch = completeBatch,
                        paginationGeneration = paginationGeneration,
                        layoutComplete = true,
                    )
                }
            }
        }
        return false
    }

    private fun applyDirectReaderPaginationBatch(
        key: String,
        currentChapter: ReaderChapterInput,
        chapters: List<ReaderChapterInput>,
        batch: LegacyReaderPaginationBatch,
        paginationGeneration: Long,
        layoutComplete: Boolean,
    ) {
        if (directReaderLayoutKey != key) return
        if (layoutComplete) directReaderLayoutJob = null
        batch.unsupportedChapters.forEach { (chapterIndex, reason) ->
            AppLog.putDebug("Compose reader pagination unsupported: chapter=$chapterIndex reason=$reason")
        }
        updateReaderPaginationError(batch.failureReasonFor(currentChapter.chapter.index))
        val previousPages = directReaderPages.associateBy { it.id }
        val replacementChapterIndexes = batch.pages.mapTo(mutableSetOf()) { it.id.chapterIndex }
        val retainedPages = if (directReaderMayReuseAdjacentPages) {
            directReaderPages.filterNot { it.id.chapterIndex in replacementChapterIndexes }
        } else {
            emptyList()
        }
        val replacementPages =
            batch.pages.takeIf { batch.hasCurrentChapter }.orEmpty().map { page ->
            previousPages[page.id]?.takeIf(page::hasSameGeometryAs)?.let { previous ->
                page.copy(layoutRevision = previous.layoutRevision)
            } ?: page
            }
        // Pagination deliberately publishes the current chapter before the adjacent chapters.
        // Keep an already shaped adjacent page during that first batch: replacing the whole
        // window here made every chapter turn recreate a "loading" placeholder even when the
        // target page was still valid in memory.
        directReaderPages = (retainedPages + replacementPages)
            .sortedWith(compareBy({ it.id.chapterIndex }, { it.id.pageIndex }))
        if (layoutComplete) {
            // A complete batch establishes one shared pagination environment. Subsequent
            // chapter-window changes may retain its already-shaped adjacent pages.
            directReaderMayReuseAdjacentPages = true
        }
        // 重排可能改变元素位置与页 endPosition，页上下文缓存全部失效。
        directReaderPageContexts.clear()
        directReaderChapterPageCounts = directReaderPages.groupingBy { it.id.chapterIndex }.eachCount()
        directReaderPageIndex = directReaderPages.takeIf { it.isNotEmpty() }?.let {
            ReaderPageNavigator.locate(
                it,
                currentChapter.chapter.index,
                ReadBook.durChapterPos,
            )
        }
        ReadBook.publishReaderPagination(
            directReaderPages.groupBy { it.id.chapterIndex }.mapNotNull { (chapterIndex, pages) ->
                chapters.firstOrNull { it.chapter.index == chapterIndex } ?: return@mapNotNull null
                val contentEnd = ReaderPageNavigator.pageContext(
                    pages,
                    pages.lastIndex,
                )?.endPosition ?: return@mapNotNull null
                ReaderChapterPaginationSnapshot(
                    chapterIndex = chapterIndex,
                    pageStarts = pages.map(ReaderPageNavigator::pageStart),
                    contentEnd = contentEnd,
                    generation = paginationGeneration,
                )
            }
        )
        directReaderPageIndex?.let(::publishDirectReaderWindow)
    }

    fun onAppThemeChanged(isDarkTheme: Boolean) {
        if (
            appliedDarkTheme == isDarkTheme &&
            ReadSessionState.isDarkThemeOverride == isDarkTheme
        ) return
        val startedAt = System.nanoTime()
        val previous = ReadSessionState.isDarkThemeOverride
        val oldTextColor = ReadBookConfig.textColor
        val oldTitleColor = ReadBookConfig.resolvedTitleColor.takeIf { it != 0 } ?: oldTextColor
        val oldShadowColor = ReadBookConfig.textShadowColor
        val oldPageUnderlineColor = ReadBookConfig.underlineColor
        appliedDarkTheme = isDarkTheme
        ReadSessionState.isDarkThemeOverride = isDarkTheme
        val newTextColor = ReadBookConfig.textColor
        val newTitleColor = ReadBookConfig.resolvedTitleColor.takeIf { it != 0 } ?: newTextColor
        val newShadowColor = ReadBookConfig.textShadowColor
        val newPageUnderlineColor = ReadBookConfig.underlineColor
        val colorChange = ReaderThemeColorChange(
            oldBodyArgb = oldTextColor,
            newBodyArgb = newTextColor,
            oldTitleArgb = oldTitleColor,
            newTitleArgb = newTitleColor,
            oldShadowArgb = oldShadowColor,
            newShadowArgb = newShadowColor,
            oldPageUnderlineArgb = oldPageUnderlineColor,
            newPageUnderlineArgb = newPageUnderlineColor,
        )
        if (directReaderPages.isNotEmpty() && colorChange.run {
                oldBodyArgb != newBodyArgb || oldTitleArgb != newTitleArgb ||
                    oldShadowArgb != newShadowArgb ||
                    oldPageUnderlineArgb != newPageUnderlineArgb
            }) {
            val salt = 31L * isDarkTheme.hashCode() + colorChange.hashCode()
            directReaderPages = directReaderPages.map { it.remapThemeColors(colorChange, salt) }
            directReaderPageIndex?.let(::publishDirectReaderWindow)
        }
        layoutController.viewport.value?.let { viewport ->
            updateComposeReaderBackground(viewport.widthPx, viewport.heightPx)
        }
        rebuildDirectReaderPages()
        upSystemUiVisibility()
        LogUtils.d(
            "ReadBookTheme",
            "apply dark=$isDarkTheme previous=$previous " +
                "durationMs=${(System.nanoTime() - startedAt) / 1_000_000}"
        )
    }

    fun clearAppThemeOverride() {
        appliedDarkTheme = null
        ReadSessionState.isDarkThemeOverride = null
    }

    fun onRouteInitialized() {
        applyReadBrightness()
        upScreenTimeOut()
    }

    /**
     * View/Window-only resume — business logic handled by ViewModel via OnResume intent.
     */
    fun onResume() {
        setOrientation()
        upSystemUiVisibility()
        screenOffTimerStart()
    }

    /**
     * View/Window-only pause — business logic handled by ViewModel via OnPause intent.
     */
    fun onPause() {
        upSystemUiVisibility()
    }

    override val isInMultiWindowModeCompat: Boolean
        get() = activity.isInMultiWindowMode

    override fun closeReadBook() {
        onClose?.invoke() ?: activity.finish()
    }

    @SuppressLint("WrongConstant")
    override fun upSystemUiVisibility(isInMultiWindow: Boolean, toolBarHide: Boolean) {
        val window = activity.window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.run {
                if (toolBarHide && ReadBookConfig.hideNavigationBar) {
                    hide(WindowInsets.Type.navigationBars())
                } else {
                    show(WindowInsets.Type.navigationBars())
                }
                if (toolBarHide && ReadBookConfig.hideStatusBar) {
                    hide(WindowInsets.Type.statusBars())
                } else {
                    show(WindowInsets.Type.statusBars())
                }
            }
        }

        // Legacy flags
        var flag = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_IMMERSIVE
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        if (!isInMultiWindow) {
            flag = flag or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
        if (ReadBookConfig.hideNavigationBar) {
            flag = flag or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            if (toolBarHide) {
                flag = flag or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            }
        }
        if (ReadBookConfig.hideStatusBar && toolBarHide) {
            flag = flag or View.SYSTEM_UI_FLAG_FULLSCREEN
        }
        window.decorView.systemUiVisibility = flag

        if (toolBarHide) {
            activity.setLightStatusBar(ReadBookConfig.durConfig.curStatusIconDark())
        } else {
            activity.setLightStatusBar(ColorUtils.isColorLight(ReadBookConfig.resolvedMenuBgColor))
        }
    }

    fun screenOffTimerStart() {
        onScreenOffTimerStart?.invoke() ?: screenOffTimerStartInternal()
    }

    override fun upSystemUiVisibility() {
        val state = viewModel.uiState.value
        upSystemUiVisibility(isInMultiWindowModeCompat, !state.menuVisible)
    }

    val pageAnim: Int get() = ReadBook.pageAnim()

    fun onImageLongPress(x: Float, y: Float, src: String) {
        val anchor = activity.window.decorView
        anchor.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        popupAction.setItems(
            listOf(
                SelectItem(activity.getString(R.string.show), "show"),
                SelectItem(activity.getString(R.string.refresh), "refresh"),
                SelectItem("保存到相册", "save"),
                SelectItem(activity.getString(R.string.menu), "menu"),
            )
        )
        popupAction.onActionClick = {
            when (it) {
                "show" -> viewModel.onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.Photo(src)))
                "refresh" -> viewModel.refreshImage(src)
                "save" -> viewModel.saveImage(src)
                "menu" -> toggleMenu()
            }
            popupAction.dismiss()
        }
        popupAction.showAtLocation(
            anchor,
            Gravity.BOTTOM or Gravity.LEFT,
            x.toInt(),
            anchor.height - y.toInt()
        )
    }

    fun onMarkingClick(markingId: String) {
        viewModel.onIntent(ReadBookIntent.EditMarking(markingId))
    }

    fun oldClickImg(src: String): Boolean {
        val urlMatch = paramPattern.find(src)
        if (urlMatch != null) {
            val urlOptionStr = src.substring(urlMatch.range.last + 1)
            val urlOptionMap = GSON.fromJsonObject<Map<String, String>>(urlOptionStr).getOrNull()
            val click = urlOptionMap?.get("click")
            if (click != null) {
                activity.lifecycleScope.launch(IO) {
                    try {
                        val source = ReadBook.bookSource ?: return@launch
                        val java = SourceLoginJsExtensions(activity, source, BookType.text)
                        val book = ReadBook.book ?: return@launch
                        val chapter = appDb.bookChapterDao.getChapter(
                            book.bookUrl,
                            ReadBook.durChapterIndex
                        ) ?: throw Exception("no find chapter")
                        runScriptWithContext {
                            source.evalJS(click) {
                                put("java", java)
                                put("book", book)
                                put("chapter", chapter)
                                put("result", src)
                            }
                        }
                    } catch (e: Throwable) {
                        AppLog.put("执行图片链接click键值出错\n${e.localizedMessage}", e, true)
                    }
                }
                return true
            }
            val jsStr = urlOptionMap?.get("js") ?: return false
            activity.lifecycleScope.launch(IO) {
                try {
                    val source = ReadBook.bookSource ?: return@launch
                    val book = ReadBook.book ?: return@launch
                    val chapter = appDb.bookChapterDao.getChapter(
                        book.bookUrl,
                        ReadBook.durChapterIndex
                    ) ?: throw Exception("no find chapter")
                    val urlNoOption = src.take(urlMatch.range.first)
                    AnalyzeRule(book, source).apply {
                        setCoroutineContext(coroutineContext)
                        setBaseUrl(chapter.url)
                        setChapter(chapter)
                        evalJS(jsStr, urlNoOption)
                    }
                } catch (e: Throwable) {
                    AppLog.put("执行图片链接js键值出错\n${e.localizedMessage}", e, true)
                }
            }
            return true
        }
        return false
    }

    fun clickImg(click: String, src: String) {
        activity.lifecycleScope.launch(IO) {
            try {
                val source = ReadBook.bookSource ?: return@launch
                val java = SourceLoginJsExtensions(activity, source, BookType.text)
                val book = ReadBook.book ?: return@launch
                val chapter =
                    appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
                        ?: throw Exception("no find chapter")
                runScriptWithContext {
                    source.evalJS(click) {
                        put("java", java)
                        put("book", book)
                        put("chapter", chapter)
                        put("result", src)
                    }
                }
            } catch (e: Throwable) {
                AppLog.put("执行图片链接click键值出错\n${e.localizedMessage}", e, true)
            }
        }
    }


    val selectedText: String get() = composeSelectedText.orEmpty()

    private fun composeSelectionBookmark(bodyOnly: Boolean = false) = composeSelection
        ?.takeUnless { bodyOnly && it.includesTitle }?.let { selection ->
            ReadBook.book?.createBookMark()?.apply {
                chapterIndex = selection.chapterIndex
                chapterPos = selection.bodyStart ?: 0
                chapterName = ReadBook.readerChapterInputWindow.current?.displayTitle.orEmpty()
                bookText = selectedText
            }
        }

    fun onMenuItemSelected(itemId: Int): Boolean {
        when (itemId) {
            R.id.menu_aloud -> {
                viewModel.onIntent(
                    ReadBookIntent.TextActionAloud(
                        selectedText,
                        composeSelection?.bodyStart,
                    )
                )
                return true
            }

            R.id.menu_bookmark -> {
                composeSelectionBookmark()?.let {
                    viewModel.onIntent(ReadBookIntent.TextActionBookmark(it))
                } ?: activity.toastOnUi(R.string.create_bookmark_error)
                return true
            }

            R.id.menu_mark -> {
                composeSelectionBookmark(bodyOnly = true)?.let {
                    viewModel.onIntent(ReadBookIntent.OpenMarking(it))
                } ?: activity.toastOnUi(R.string.create_bookmark_error)
                return true
            }

            R.id.menu_edit -> {
                viewModel.onIntent(ReadBookIntent.OpenContentEdit)
                return true
            }

            R.id.menu_replace -> {
                viewModel.onIntent(ReadBookIntent.TextActionReplace(selectedText))
                return true
            }

            R.id.menu_ai_clean -> {
                composeSelectionBookmark(bodyOnly = true)?.let { selection ->
                    viewModel.onIntent(
                        ReadBookIntent.OpenAiTextClean(
                            text = selection.bookText,
                            chapterIndex = selection.chapterIndex,
                            chapterPosition = selection.chapterPos,
                        )
                    )
                } ?: activity.toastOnUi(R.string.ai_text_clean_selection_error)
                return true
            }

            R.id.menu_ai_rewrite -> {
                composeSelectionBookmark(bodyOnly = true)?.let { selection ->
                    viewModel.onIntent(
                        ReadBookIntent.OpenAiTextRewrite(
                            text = selection.bookText,
                            chapterIndex = selection.chapterIndex,
                            chapterPosition = selection.chapterPos,
                        )
                    )
                } ?: activity.toastOnUi(R.string.ai_text_clean_selection_error)
                return true
            }

            R.id.menu_search_content -> {
                viewModel.onIntent(ReadBookIntent.TextActionSearchContent(selectedText))
                return true
            }

            R.id.menu_dict -> {
                viewModel.onIntent(ReadBookIntent.TextActionDict(selectedText))
                return true
            }
        }
        return false
    }

    fun onMenuActionFinally() {
        dismissTextActionMenu()
        composeSelection = null
        composeSelectedText = null
        _composeSelectionCancels.tryEmit(Unit)
    }

    suspend fun getActionMenuItems(): List<ActionMenuItem> = withContext(IO) {
        menuMutex.withLock {
            cachedActionMenuItems?.let { return@withContext it }

            val items = mutableListOf<ActionMenuItem>()
            items.add(ActionMenuItem(R.id.menu_copy, activity.getString(android.R.string.copy)))
            items.add(ActionMenuItem(R.id.menu_share_str, activity.getString(R.string.share)))
            items.add(ActionMenuItem(R.id.menu_browser, activity.getString(R.string.browser)))
            items.add(ActionMenuItem(R.id.menu_aloud, activity.getString(R.string.read_aloud)))
            items.add(ActionMenuItem(R.id.menu_bookmark, activity.getString(R.string.bookmark)))
            items.add(ActionMenuItem(R.id.menu_mark, activity.getString(R.string.menu_mark)))
            items.add(ActionMenuItem(R.id.menu_dict, activity.getString(R.string.dict)))
            items.add(ActionMenuItem(R.id.menu_replace, activity.getString(R.string.replace)))
            items.add(ActionMenuItem(R.id.menu_edit, activity.getString(R.string.edit)))
            items.add(ActionMenuItem(R.id.menu_ai_clean, activity.getString(R.string.ai_text_clean)))
            items.add(ActionMenuItem(R.id.menu_ai_rewrite, activity.getString(R.string.ai_text_rewrite)))
            items.add(ActionMenuItem(R.id.menu_search_content, activity.getString(R.string.search_content)))

            val thirdPartyItems = mutableListOf<ActionMenuItem>()
            runCatching {
                val pm = activity.packageManager
                val intent = Intent().setAction(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
                val resolveInfos = pm.queryIntentActivities(intent, 0)
                for (resolveInfo in resolveInfos) {
                    val processIntent = Intent()
                        .setAction(Intent.ACTION_PROCESS_TEXT)
                        .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
                        .setClassName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name)
                    
                    val title = resolveInfo.loadLabel(pm).toString()
                    val icon = if (readSettingsGateway.currentSettings.showSelectMenuIcon) {
                        runCatching { resolveInfo.loadIcon(pm) }.getOrNull()
                    } else null

                    thirdPartyItems.add(ActionMenuItem(id = -1, title = title, iconDrawable = icon, intent = processIntent))
                }
            }

            val allItems = items + thirdPartyItems
            val configStr = readSettingsGateway.currentSettings.textSelectMenuConfig
            val result = if (configStr.isEmpty()) {
                allItems
            } else {
                runCatching {
                    val savedConfigs = GSON.fromJsonObject<List<SelectionMenuConfigItem>>(configStr).getOrNull() ?: emptyList()
                    val savedMap = savedConfigs.associateBy { it.id }

                    val sortedItems = mutableListOf<ActionMenuItem>()
                    for (saved in savedConfigs) {
                        val found = allItems.find { it.uniqueId == saved.id }
                        if (found != null) {
                            val resolvedShowState = saved.showState ?: if (saved.enabled == false) 1 else 0
                            sortedItems.add(found.copy(showState = resolvedShowState))
                        }
                    }
                    for (item in allItems) {
                        val uniqueId = item.uniqueId
                        if (!savedMap.containsKey(uniqueId)) {
                            sortedItems.add(item)
                        }
                    }
                    sortedItems
                }.getOrDefault(allItems)
            }
            cachedActionMenuItems = result
            result
        }
    }

    fun refreshActionMenuItems() {
        activity.lifecycleScope.launch {
            menuMutex.withLock {
                cachedActionMenuItems = null
            }
            val menuItems = getActionMenuItems()
            _textMenuState.value?.let { currentState ->
                _textMenuState.value = currentState.copy(items = menuItems)
            }
        }
    }

    fun saveMenuConfig(items: List<ActionMenuItem>) {
        val configs = items.map { item ->
            SelectionMenuConfigItem(
                id = item.uniqueId,
                enabled = item.showState == 0,
                showState = item.showState
            )
        }
        viewModel.setTextSelectMenuConfig(GSON.toJson(configs))
        refreshActionMenuItems()
    }

    fun onTextMenuItemClick(item: ActionMenuItem) {
        if (item.intent != null) {
            runCatching {
                item.intent.putExtra(Intent.EXTRA_PROCESS_TEXT, selectedText)
                activity.startActivity(item.intent)
            }.onFailure { e ->
                AppLog.put("执行文本菜单操作出错\n$e", e, true)
            }
        } else {
            when (item.id) {
                R.id.menu_copy -> activity.sendToClip(selectedText)
                R.id.menu_share_str -> activity.share(selectedText)
                R.id.menu_browser -> {
                    runCatching {
                        val intent = if (selectedText.isAbsUrl()) {
                            Intent(Intent.ACTION_VIEW).apply {
                                data = selectedText.toUri()
                            }
                        } else {
                            Intent(Intent.ACTION_WEB_SEARCH).apply {
                                putExtra(SearchManager.QUERY, selectedText)
                            }
                        }
                        activity.startActivity(intent)
                    }.onFailure { e ->
                        e.printOnDebug()
                        activity.toastOnUi(e.localizedMessage ?: "ERROR")
                    }
                }
                else -> {
                    onMenuItemSelected(item.id)
                }
            }
        }
        onMenuActionFinally()
    }

    // ── ReadBook.ReaderRenderCallback（渲染子集，Track B2 从 ViewModel 下沉）──
    //
    // ReadBook 可在 IO 协程调用这些回调；统一经主线程 handler 发布 Compose 页面状态。

    private fun postRender(effect: ReadBookEffect) {
        handler.post { handleEffect(effect) }
    }

    override fun upContent(
        relativePosition: Int,
        resetPageOffset: Boolean,
        success: (() -> Unit)?
    ) {
        postRender(ReadBookEffect.UpContent(relativePosition, resetPageOffset, success))
    }

    override suspend fun upContentAwait(
        relativePosition: Int,
        resetPageOffset: Boolean,
        success: (() -> Unit)?
    ) {
        if (layoutController.awaitViewport() == null) {
            AppLog.putDebug("upContentAwait 等待 ReaderViewport 超时，继续旧内容更新路径")
        }
        withContext(Main.immediate) {
            handleEffect(ReadBookEffect.UpContent(relativePosition, resetPageOffset, success))
        }
    }

    // R2.3：pageChanged / contentLoadFinish / onLayoutPageCompleted 的 Effect 只有本类
    // 自产自销（postRender → 本类 handleEffect），从不经过 ViewModel 的 _effects。
    // 它们不属于 VM 的对外协议，故内联到渲染方法里，三个 Effect 类型随之从
    // ReadBookEffect 删除。仍在 postRender 上的 UpContent/UpPageAnim/CancelSelect
    // 有 VM/delegate 侧的生产者，必须留在 Effect 里。

    override fun pageChanged() {
        handler.post {
            this.pageChanged = true
            publishReaderPageWindow()
            viewModel.startBackupJob()
        }
    }

    override fun contentLoadFinish() {
        viewModel.markInitFinished()
        handler.post {
            viewModel.readAloudProgress.value?.let(::updateReadAloudProgress)
            onStartContentLoadFinish?.invoke()
        }
    }

    override fun upPageAnim(upRecorder: Boolean) {
        postRender(ReadBookEffect.UpPageAnim(upRecorder))
    }

    override fun cancelSelect() {
        postRender(ReadBookEffect.CancelSelect)
    }

    // ── Effect handling ───────────────────────────────────────────────

    /**
     * Handles reader-renderer and Activity-API effects.
     * Launcher-dependent effects are handled by the route layer.
     */
    fun handleEffect(effect: ReadBookEffect) {
        when (effect) {
            // ── Reader-renderer effects ──
            is ReadBookEffect.Finish -> closeReadBook()
            is ReadBookEffect.UpdateReaderConfig -> {
                if (ConfigUpdateAction.UpdateBackground in effect.actions) {
                    layoutController.viewport.value?.let { viewport ->
                        updateComposeReaderBackground(viewport.widthPx, viewport.heightPx)
                    }
                }
                layoutController.viewport.value?.let { viewport ->
                    ReadBook.publishReaderPaginationEnvironment(
                        widthPx = viewport.widthPx,
                        heightPx = viewport.heightPx,
                        style = LegacyReaderPaginationStyleFactory.create(),
                        contentPaddingLeftPx = viewport.contentPadding.left,
                        contentPaddingTopPx = viewport.contentPadding.top,
                        contentPaddingRightPx = viewport.contentPadding.right,
                        contentPaddingBottomPx = viewport.contentPadding.bottom,
                    )
                }
                effect.actions.forEach { action ->
                    when (action) {
                        ConfigUpdateAction.UpdateSystemUi -> upSystemUiVisibility()
                        ConfigUpdateAction.UpdateBackground,
                        ConfigUpdateAction.UpdateStyle,
                        ConfigUpdateAction.UpdateBackgroundAlpha,
                        ConfigUpdateAction.UpdatePageSlopSquare -> Unit
                        ConfigUpdateAction.ReloadContent -> if (viewModel.isInitFinish) ReadBook.loadContent(resetPageOffset = false)
                        ConfigUpdateAction.RelayoutContent -> if (viewModel.isInitFinish) {
                            layoutController.requestRelayout()
                        }
                        ConfigUpdateAction.UpdateContent -> Unit
                        ConfigUpdateAction.UpdateChapterStyle -> {
                            ReadBook.requestWholeBookPageEstimate()
                        }
                        ConfigUpdateAction.InvalidateTextPage -> Unit
                        ConfigUpdateAction.UpdateLayout -> {
                            ReadBook.requestWholeBookPageEstimate()
                        }
                        ConfigUpdateAction.RebuildWholeBookPageIndex ->
                            ReadBook.requestWholeBookPageEstimate()
                        ConfigUpdateAction.UpdateWholeBookPageDemand ->
                            ReadBook.updateWholeBookPageDemand()
                        ConfigUpdateAction.SubmitRenderTask,
                        ConfigUpdateAction.UpdatePageAnim -> Unit
                    }
                }
                if (effect.actions.any(ConfigUpdateAction::invalidatesDirectReaderPages)) {
                    rebuildDirectReaderPages()
                }
            }

            is ReadBookEffect.UpContent -> {
                publishReaderPageWindow()
                effect.success?.invoke()
                if (effect.relativePosition == 0) onUnhandledEffect(ReadBookEffect.UpSeekBar)
                if (effect.relativePosition == 0) viewModel.refreshSeekState()
            }

            is ReadBookEffect.UpPageAnim -> publishReaderPageWindow()
            is ReadBookEffect.UpTime -> {
                directReaderPageIndex?.let(::publishDirectReaderWindow)
            }
            is ReadBookEffect.UpBattery -> {
                directReaderPageIndex?.let(::publishDirectReaderWindow)
            }
            is ReadBookEffect.UpSystemUiVisibility -> upSystemUiVisibility()
            is ReadBookEffect.PageAnimChanged -> {
                ReadBook.loadContent(false)
            }

            is ReadBookEffect.CancelSelect -> {
                dismissTextActionMenu()
                composeSelection = null
                composeSelectedText = null
                _composeSelectionCancels.tryEmit(Unit)
            }
            is ReadBookEffect.MenuImageStyleChanged -> rebuildDirectReaderPages()
            is ReadBookEffect.InvalidateReaderImage -> invalidateReaderImage(effect.source)

            // ── Simple Activity-API effects ──
            is ReadBookEffect.ShowToast -> activity.toastOnUi(effect.message)
            is ReadBookEffect.LongToast -> activity.longToastOnUi(effect.message)
            is ReadBookEffect.SetBrightness -> {
                val lp = activity.window.attributes
                lp.screenBrightness = effect.value / 100f
                activity.window.attributes = lp
            }

            // ── Launcher-dependent effects — now handled by route layer ──

            // ── DB query + bookmark effects — now handled by ViewModel ──

            // ── Phase 2: ViewRefs-only effects ──
            is ReadBookEffect.UpSeekBar -> { /* no-op: Compose menu reads from state */
            }

            is ReadBookEffect.UpMenuView -> { /* no-op: Compose menu reads from state */
            }

            is ReadBookEffect.UpTextSelectAble -> Unit

            is ReadBookEffect.UpAloudState -> {
                readAloudPosition = null
                directReaderPageIndex?.let(::publishDirectReaderWindow)
            }

            is ReadBookEffect.RefreshBookContent -> {
                ReadBook.clearTextChapter()
                ReadBook.book?.let { viewModel.refreshContentDur(it) }
            }

            is ReadBookEffect.UpScreenTimeOut -> {
                upScreenTimeOut()
            }

            is ReadBookEffect.ToggleBrightnessAuto -> {
                val lp = activity.window.attributes
                if (effect.auto) {
                    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                } else {
                    lp.screenBrightness = effect.value / 100f
                }
                activity.window.attributes = lp
            }

            // ── Phase 4: Activity-dependent effects ──
            is ReadBookEffect.ToggleReadAloud -> onToggleReadAloud?.invoke() ?: toggleReadAloud()
            is ReadBookEffect.ToggleAutoPage -> onToggleAutoPage?.invoke() ?: toggleAutoPage()
            is ReadBookEffect.StopAutoPage -> onStopAutoPage?.invoke() ?: stopAutoPage()
            is ReadBookEffect.TextActionAloudPosition -> {
                ReadBook.updateReadingPosition(effect.chapterPosition)
                ReadBook.readAloud(chapterPosition = effect.chapterPosition)
            }

            is ReadBookEffect.TextActionSpeak -> speak(effect.text)
            is ReadBookEffect.NavigateToSearchResult -> {
                pendingSearchNavigation = effect
                val result = effect.result
                val currentInput = ReadBook.readerChapterInputWindow.current
                    ?.takeIf { it.chapter.index == result.chapterIndex }
                if (currentInput != null) {
                    resolveSearchNavigation(effect, currentInput)
                    publishReaderPageWindow()
                } else {
                    // 定位只对本次跳章有效；openChapter 成功仍未消费时放弃，
                    // 避免挂起导航在用户之后主动进入同一章时劫持阅读位置
                    ReadBook.openChapter(
                        result.chapterIndex,
                        result.queryIndexInChapter.coerceAtLeast(0),
                    ) {
                        pendingSearchNavigation = null
                    }
                }
            }

            is ReadBookEffect.ExitSearch -> {
                pendingSearchNavigation = null
                searchSelection = null
                directReaderPageIndex?.let(::publishDirectReaderWindow)
            }

            is ReadBookEffect.SyncBookProgress -> {
                viewModel.onIntent(ReadBookIntent.ShowDialog(
                    ReadBookDialog.SureSyncProgress(BookProgress(effect.book))
                ))
            }

            is ReadBookEffect.ShowConfirmSkipToChapter -> {
                viewModel.onIntent(ReadBookIntent.ShowDialog(ReadBookDialog.ConfirmSkipToChapter))
            }
            is ReadBookEffect.ToggleDayNight -> {
                // Handled directly by ViewModel — effect not currently emitted
            }
            is ReadBookEffect.DownloadChapters -> {
                ReadBook.book?.let { book ->
                    activity.lifecycleScope.launch {
                        CacheBook.start(activity, book, effect.start, effect.end)
                    }
                }
            }

            // ── Lifecycle — route/bridge Activity operations ──
            is ReadBookEffect.RegisterTimeBatteryReceiver -> {
                registerTimeBatteryReceiver()
            }

            is ReadBookEffect.UnregisterTimeBatteryReceiver -> {
                unregisterTimeBatteryReceiver()
            }

            is ReadBookEffect.RegisterNetworkListener -> {
                networkChangedListener.register()
                networkChangedListener.onNetworkChanged = {
                    viewModel.onNetworkChanged()
                }
            }

            is ReadBookEffect.UnregisterNetworkListener -> {
                networkChangedListener.unRegister()
            }

            is ReadBookEffect.SetOrientation -> {
                setOrientation()
            }

            is ReadBookEffect.BackupNow -> {
                Backup.autoBack(activity)
            }

            // Launcher-dependent effects — handled by route layer, ignored here
            is ReadBookEffect.OpenSourceEdit,
            is ReadBookEffect.OpenChapterList,
            is ReadBookEffect.OpenBookInfo,
            is ReadBookEffect.OpenSearch,
            is ReadBookEffect.ShowLogin,
            is ReadBookEffect.OpenWebView,
            is ReadBookEffect.RunSourceCustomButton,
            is ReadBookEffect.MenuSettingReplace,
            is ReadBookEffect.TextActionReplace,
            is ReadBookEffect.OpenReplaceEditor,
            is ReadBookEffect.MenuTocRegex,
            is ReadBookEffect.OpenFontFolderPicker,
            is ReadBookEffect.OpenBooksDirPicker,
            is ReadBookEffect.OpenReadStyleImagePicker,
            is ReadBookEffect.OpenReadStyleImagePickerForMode,
            is ReadBookEffect.OpenReadStyleImport,
            is ReadBookEffect.OpenReadStyleExport,
            is ReadBookEffect.OpenMenuCustomIconPicker,
            is ReadBookEffect.OpenTitleBarCustomIconPicker,
            is ReadBookEffect.OpenSystemTtsSettings,
            ReadBookEffect.OpenTtsEnginesAndVoices,
            ReadBookEffect.OpenTtsCache,
            is ReadBookEffect.OpenBookVoiceCasting,
            is ReadBookEffect.OpenHighlightRuleImportPicker,
            is ReadBookEffect.OpenHighlightRuleExportPicker,
            is ReadBookEffect.TtsCacheCleared,
            is ReadBookEffect.ExportJson,
            // DB query + bookmark effects — handled by ViewModel, ignored here
            is ReadBookEffect.MenuChangeSource,
            is ReadBookEffect.MenuBookChangeSource,
            is ReadBookEffect.MenuChapterChangeSource,
            is ReadBookEffect.AddBookmark -> {
                // Handled by route/ViewModel — no-op here
            }

            is ReadBookEffect.UpBookmarkBadge -> directReaderPageIndex?.let(::publishDirectReaderWindow)
        }
    }

    fun updateReadAloudProgress(chapterStart: Int) {
        if (!BaseReadAloudService.isPlay()) return
        // 只更新朗读高亮锚点。可见页的移动由朗读服务驱动（moveToReadAloudPage →
        // moveToNextPage → upContent）与用户导航负责；这里若再按朗读位置 locate
        // 回迁可见页，进入阅读器时会把页面先拉回朗读所在的上一段（跨页段落的
        // locate 落在段落起始页），随后 curPageChanged 的跟随重启又跳回当前页。
        val anchor = BaseReadAloudService.currentChapterIndex to chapterStart
        if (readAloudPosition == anchor) return
        readAloudPosition = anchor
        // 高亮在窗口发布时才计算绘制（directReaderWindow），锚点变化后必须重发布
        directReaderPageIndex?.let(::publishDirectReaderWindow)
    }

    fun setComposeVisibleBodyTextPositionProvider(
        provider: (() -> ReaderVisibleTextPosition?)?,
    ) {
        composeVisibleBodyTextPositionProvider = provider
    }

    private fun readAloudFromComposeVisibleStart(): Boolean {
        val position = composeVisibleBodyTextPositionProvider?.invoke() ?: return false
        // The Canvas window is normally kept on the logical current page. A crossing may
        // briefly expose a neighbor before its chapter becomes the active ReadBook chapter;
        // let the existing chapter-transition path handle that case rather than speaking
        // with mismatched chapter coordinates.
        if (position.chapterIndex != ReadBook.durChapterIndex) return false
        ReadBook.updateReadingPosition(position.chapterPosition)
        ReadBook.readAloud(chapterPosition = position.chapterPosition)
        return true
    }

    // ── Key handling ──

    private fun toggleReadAloud() {
        viewModel.onIntent(ReadBookIntent.StopAutoPage)
        when {
            !BaseReadAloudService.isRun -> {
                ReadAloud.upReadAloudClass()
                if (!readAloudFromComposeVisibleStart()) ReadBook.readAloud()
            }

            BaseReadAloudService.pause -> {
                val restartFromVisibleStart = pageChanged && readAloudFromComposeVisibleStart()
                pageChanged = false
                if (!restartFromVisibleStart) ReadAloud.resume(activity)
            }

            else -> ReadAloud.pause(activity)
        }
    }

    private fun toggleAutoPage() {
        ReadAloud.stop(activity)
        if (isAutoPage) {
            stopAutoPage()
        } else {
            viewModel.setAutoPage(true)
            onScreenOffTimerStart?.invoke()
        }
    }

    fun stopAutoPage() {
        if (isAutoPage) {
            viewModel.setAutoPage(false)
            viewModel.onIntent(ReadBookIntent.DismissSheet)
            onScreenOffTimerStart?.invoke()
        }
    }

    override fun toggleMenu() {
        viewModel.onIntent(ReadBookIntent.ToggleMenu)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (menuLayoutIsVisible) {
            return false
        }
        val longPress = event.repeatCount > 0
        when {
            isPrevKey(keyCode) -> {
                handleKeyPage(PageDirection.PREV, longPress)
                return true
            }

            isNextKey(keyCode) -> {
                handleKeyPage(PageDirection.NEXT, longPress)
                return true
            }
        }
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> if (volumeKeyPage(PageDirection.PREV, longPress)) {
                return true
            }

            KeyEvent.KEYCODE_VOLUME_DOWN -> if (volumeKeyPage(PageDirection.NEXT, longPress)) {
                return true
            }

            KeyEvent.KEYCODE_PAGE_UP -> {
                handleKeyPage(PageDirection.PREV, longPress)
                return true
            }

            KeyEvent.KEYCODE_PAGE_DOWN -> {
                handleKeyPage(PageDirection.NEXT, longPress)
                return true
            }

            KeyEvent.KEYCODE_SPACE -> {
                handleKeyPage(PageDirection.NEXT, longPress)
                return true
            }

            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT -> {
                handleKeyPage(PageDirection.PREV, longPress)
                return true
            }

            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                handleKeyPage(PageDirection.NEXT, longPress)
                return true
            }

            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (ReadBook.book != null) {
                    ReadBook.moveToNextChapter(true)
                }
                return true
            }

            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                if (ReadBook.book != null) {
                    ReadBook.moveToPrevChapter(upContent = true, toLast = false)
                }
                return true
            }
        }
        return false
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (volumeKeyPage(PageDirection.NONE, false)) {
                    return true
                }
            }
        }
        return false
    }

    override fun mouseWheelPage(direction: PageDirection) {
        if (menuLayoutIsVisible || !readSettingsGateway.currentSettings.mouseWheelPage) {
            return
        }
        keyPageDebounce(direction, mouseWheel = true, longPress = false)
    }

    private fun volumeKeyPage(direction: PageDirection, longPress: Boolean): Boolean {
        if (!readSettingsGateway.currentSettings.volumeKeyPage) {
            return false
        }
        if (!readSettingsGateway.currentSettings.volumeKeyPageOnPlay && BaseReadAloudService.isPlay()) {
            return false
        }
        handleKeyPage(direction, longPress)
        return true
    }

    override fun handleKeyPage(direction: PageDirection, longPress: Boolean) {
        if (readSettingsGateway.currentSettings.keyPageOnLongPress || direction == PageDirection.NONE) {
            keyPage(direction)
        } else {
            keyPageDebounce(direction, longPress = longPress)
        }
    }

    private fun updateComposeReaderBackground(widthPx: Int, heightPx: Int) {
        if (widthPx <= 0 || heightPx <= 0) return
        val generation = ++readerBackgroundLoadGeneration
        readerBackgroundLoadJob?.cancel()
        readerBackgroundLoadJob = activity.lifecycleScope.launch(IO) {
            val snapshot = ReadSessionState.loadBackground(widthPx, heightPx)
            withContext(Main.immediate) {
                if (generation != readerBackgroundLoadGeneration) return@withContext
                ReadSessionState.applyBackground(snapshot)
                updateReaderBackground(ReaderBackgroundState(
                    drawable = snapshot.drawable,
                    meanColorArgb = snapshot.meanColor,
                    revision = _readerBackground.value.revision + 1L,
                ))
            }
        }
    }

    /**
     * Completes a transition already animated by the Compose renderer.
     * 返回翻页后发布的页窗口（同步）；null 表示翻页被拒绝（边界/无内容）。
     * 滚动模式依赖这个同步返回在跨页帧内完成窗口替换与偏移折算，不走 StateFlow 往返。
     */
    fun completeComposePageTurn(direction: PageDirection): ReaderPageWindow? {
        if (directReaderPages.isEmpty()) return null
        val currentIndex = directReaderPageIndex ?: 0
        val delta = when (direction) {
            PageDirection.PREV -> -1
            PageDirection.NEXT -> 1
            PageDirection.NONE -> 0
        }
        val navigation = ReaderPageNavigator.move(directReaderPages, currentIndex, delta)
        if (delta == 0) return null
        if (navigation.hitBoundary) {
            return crossComposeChapterBoundary(currentIndex, delta)
        }
        val oldChapterIndex = directReaderPages[currentIndex].id.chapterIndex
        val newChapterIndex = navigation.window.current?.id?.chapterIndex ?: oldChapterIndex
        val chapterChanged = when {
            newChapterIndex > oldChapterIndex -> ReadBook.moveToNextChapter(
                upContent = false,
                upContentInPlace = false,
            )
            newChapterIndex < oldChapterIndex -> ReadBook.moveToPrevChapter(
                upContent = false,
                toLast = true,
                upContentInPlace = false,
            )
            else -> true
        }
        if (!chapterChanged) return null
        // 占位页没有真实章内位置：moveToNextChapter/PrevChapter 已把 durChapterPos
        // 设为目标章落点（首页 0 / 上一章末页 lastPageStart），提交会把它覆盖成 0。
        if (!directReaderPages[navigation.pageIndex].isPlaceholder) {
            commitManualReaderPage(navigation.pageIndex)
        }
        val window = publishDirectReaderWindow(navigation.pageIndex)
        pageChanged = true
        viewModel.startBackupJob()
        return window
    }

    /**
     * 章节边界而邻章未分页：滚入"加载中"占位页并触发装载（对照 shutiao 的占位页
     * 滚动继续语义）。占位页插入 directReaderPages 并正常发布——装载期间页码、
     * 反向跨页、进度保持一致；分页批次落地时同 id 真实页替换，重建后占位页自然
     * 消失，locate 按 durChapterPos 落到目标章（下一章首页/上一章末页）。
     */
    private fun crossComposeChapterBoundary(currentIndex: Int, delta: Int): ReaderPageWindow? {
        val fromChapterIndex = directReaderPages[currentIndex].id.chapterIndex
        val targetChapterIndex = fromChapterIndex + delta
        // 已处于该章占位/装载状态：不重复切章。
        if (ReadBook.durChapterIndex == targetChapterIndex) return null
        // 占位页是死端：邻章装载完成前不允许从占位页继续向更远处串章
        // （正常路径下占位页已由 ensureBoundaryPlaceholderPages 预置，不会走到这里）。
        if (directReaderPages[currentIndex].isPlaceholder) return null
        val moved = if (delta > 0) {
            ReadBook.moveToNextChapter(upContent = false, upContentInPlace = false)
        } else {
            ReadBook.moveToPrevChapter(upContent = false, toLast = true, upContentInPlace = false)
        }
        if (!moved) return null
        val placeholder = placeholderReaderPage(targetChapterIndex) ?: return null
        val pages = directReaderPages.toMutableList()
        pages.add(placeholder)
        pages.sortWith(compareBy({ it.id.chapterIndex }, { it.id.pageIndex }))
        directReaderPages = pages
        // 不提交进度：moveToNextChapter/PrevChapter 已把 durChapterPos 设为
        // 目标章的落点（首页 0 / 末页 lastPageStart），commit 会覆盖上一章的取值。
        val placeholderIndex = pages.indexOfFirst { it === placeholder }
        directReaderPageIndex = placeholderIndex
        val window = publishDirectReaderWindow(placeholderIndex)
        pageChanged = true
        viewModel.startBackupJob()
        return window
    }

    /** 内容装载前的整窗占位：窗口里只有"加载中"占位页，点击/菜单走画布正常路径。 */
    private fun publishLoadingReaderWindow() {
        val current = _readerPageWindow.value.current
        if (current?.isPlaceholder == true && current.id.chapterIndex == ReadBook.durChapterIndex) return
        placeholderReaderPage(ReadBook.durChapterIndex)?.let { page ->
            updateReaderPageWindow(ReaderPageWindow(current = page))
        }
    }

    /** 未装载章节的占位页：一屏居中的"加载中"文字，几何与普通页一致以保持滚动连续。 */
    private fun placeholderReaderPage(chapterIndex: Int): ReaderPage? {
        val viewport = layoutController.viewport.value ?: return null
        val paginationStyle = LegacyReaderPaginationStyleFactory.create()
        val contentTop = viewport.contentPadding.top.toFloat()
        val contentBottom = (viewport.heightPx - viewport.contentPadding.bottom).toFloat()
        if (contentBottom - contentTop <= 0f) return null
        val paint = paginationStyle.bodyPaint
        val text = activity.getString(R.string.loading)
        val textWidth = paint.measureText(text)
        val left = viewport.contentPadding.left.toFloat()
        val width = viewport.contentWidthPx.toFloat()
        val x = left + ((width - textWidth) / 2f).coerceAtLeast(0f)
        val y = contentTop + ((contentBottom - contentTop - paginationStyle.bodyTextHeightPx) / 2f)
            .coerceAtLeast(0f)
        val element = ReaderElement.Text(
            bounds = ReaderRect(x, y, x + textWidth, y + paginationStyle.bodyTextHeightPx),
            baselinePx = y + paginationStyle.bodyBaselineOffsetPx,
            value = text,
            style = paginationStyle.bodyStyle,
            selected = false,
            emphasized = true,
            chapterPosition = 0,
            paragraphIndex = -1,
        )
        return ReaderPage(
            id = ReaderPageId(chapterIndex, 0),
            chapterTitle = "",
            text = text,
            widthPx = viewport.widthPx,
            heightPx = viewport.heightPx,
            contentTopPx = contentTop,
            contentBottomPx = contentBottom,
            elements = listOf(element),
            revision = 1L,
            scrollExtentPx = contentBottom - contentTop,
            isPlaceholder = true,
        )
    }

    private fun keyPageDebounce(
        direction: PageDirection,
        mouseWheel: Boolean = false,
        longPress: Boolean
    ) {
        if (longPress) {
            return
        }
        nextPageDebounce.apply {
            wait = if (mouseWheel) 200L else 600L
            leading = !mouseWheel
            trailing = mouseWheel
        }
        prevPageDebounce.apply {
            wait = if (mouseWheel) 200L else 600L
            leading = !mouseWheel
            trailing = mouseWheel
        }
        when (direction) {
            PageDirection.NEXT -> nextPageDebounce.invoke()
            PageDirection.PREV -> prevPageDebounce.invoke()
            else -> {}
        }
    }

    private fun keyPage(direction: PageDirection) {
        val composeDirection = when (direction) {
            PageDirection.PREV -> ReaderTurnDirection.PREVIOUS
            PageDirection.NEXT -> ReaderTurnDirection.NEXT
            PageDirection.NONE -> null
        }
        if (directReaderPages.isNotEmpty() && composeDirection != null &&
            _composePageTurns.tryEmit(composeDirection)
        ) return
        completeComposePageTurn(direction)
    }

    private fun upScreenTimeOut() {
        val keepLightPrefer = readSettingsGateway.currentSettings.keepLight.toLongOrNull() ?: 0L
        screenTimeOut = keepLightPrefer * 1000L
        screenOffTimerStartInternal()
    }

    private fun applyReadBrightness() {
        val lp = activity.window.attributes
        lp.screenBrightness = if (ReadBookConfig.brightnessAuto) {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        } else {
            ReadBookConfig.readBrightness / 100f
        }
        activity.window.attributes = lp
    }

    private fun restoreActivityWindowState() {
        activity.requestedOrientation = originalRequestedOrientation
        val lp = activity.window.attributes
        lp.screenBrightness = originalScreenBrightness
        activity.window.attributes = lp
        keepScreenOn(originalKeepScreenOn)
        handler.removeCallbacks(screenOffRunnable)
    }

    private fun screenOffTimerStartInternal() {
        handler.post {
            if (screenTimeOut < 0) {
                keepScreenOn(true)
                return@post
            }
            val t = screenTimeOut - activity.sysScreenOffTime
            if (t > 0) {
                keepScreenOn(true)
                handler.removeCallbacks(screenOffRunnable)
                handler.postDelayed(screenOffRunnable, screenTimeOut)
            } else {
                keepScreenOn(false)
            }
        }
    }

    private fun keepScreenOn(on: Boolean) {
        val isScreenOn =
            (activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        if (on == isScreenOn) return
        if (on) {
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun registerTimeBatteryReceiver() {
        if (timeBatteryReceiverRegistered) return
        ContextCompat.registerReceiver(
            activity, timeBatteryReceiver, timeBatteryReceiver.filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        timeBatteryReceiverRegistered = true
    }

    private fun unregisterTimeBatteryReceiver() {
        if (!timeBatteryReceiverRegistered) return
        activity.unregisterReceiver(timeBatteryReceiver)
        timeBatteryReceiverRegistered = false
    }

    private fun isPrevKey(keyCode: Int): Boolean {
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            return false
        }
        val prevKeysStr = viewModel.readPreferences.value.prevKeys
        return prevKeysStr.split(",").contains(keyCode.toString())
    }

    private fun isNextKey(keyCode: Int): Boolean {
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            return false
        }
        val nextKeysStr = viewModel.readPreferences.value.nextKeys
        return nextKeysStr.split(",").contains(keyCode.toString())
    }

    fun setOrientation() {
        when (readSettingsGateway.currentSettings.screenOrientation) {
            "0" -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            "1" -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "2" -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            "3" -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
            "4" -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            "5" -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        }
    }

}

private fun ConfigUpdateAction.invalidatesDirectReaderPages(): Boolean = when (this) {
    ConfigUpdateAction.UpdateStyle,
    ConfigUpdateAction.ReloadContent,
    ConfigUpdateAction.RelayoutContent,
    ConfigUpdateAction.UpdateContent,
    ConfigUpdateAction.UpdateChapterStyle,
    ConfigUpdateAction.InvalidateTextPage,
    ConfigUpdateAction.UpdateLayout -> true
    ConfigUpdateAction.UpdateSystemUi,
    ConfigUpdateAction.UpdateBackground,
    ConfigUpdateAction.UpdateBackgroundAlpha,
    ConfigUpdateAction.UpdatePageSlopSquare,
    ConfigUpdateAction.RebuildWholeBookPageIndex,
    ConfigUpdateAction.UpdateWholeBookPageDemand,
    ConfigUpdateAction.SubmitRenderTask,
    ConfigUpdateAction.UpdatePageAnim -> false
}

data class ReaderBackgroundState(
    val drawable: Drawable? = null,
    val meanColorArgb: Int = 0,
    val revision: Long = 0L,
)


data class TextMenuState(
    val selectedText: String,
    val startX: Int,
    val startTopY: Int,
    val startBottomY: Int,
    val endX: Int,
    val endBottomY: Int,
    val items: List<ActionMenuItem>
)

data class ActionMenuItem(
    val id: Int,
    val title: String,
    val iconDrawable: android.graphics.drawable.Drawable? = null,
    val intent: Intent? = null,
    val showState: Int = 0 // 0: 一级, 1: 折叠, 2: 隐藏
) {
    val enabled: Boolean
        get() = showState == 0

    val uniqueId: String
        get() = if (intent != null) {
            val comp = intent.component
            if (comp != null) "${comp.packageName}/${comp.className}" else title
        } else {
            when (id) {
                R.id.menu_copy -> "menu_copy"
                R.id.menu_share_str -> "menu_share_str"
                R.id.menu_browser -> "menu_browser"
                R.id.menu_aloud -> "menu_aloud"
                R.id.menu_bookmark -> "menu_bookmark"
                R.id.menu_mark -> "menu_mark"
                R.id.menu_dict -> "menu_dict"
                R.id.menu_replace -> "menu_replace"
                R.id.menu_edit -> "menu_edit"
                R.id.menu_ai_clean -> "menu_ai_clean"
                R.id.menu_ai_rewrite -> "menu_ai_rewrite"
                R.id.menu_search_content -> "menu_search_content"
                else -> id.toString()
            }
        }
}

data class SelectionMenuConfigItem(
    val id: String,
    val enabled: Boolean? = null,
    val showState: Int? = null
)
