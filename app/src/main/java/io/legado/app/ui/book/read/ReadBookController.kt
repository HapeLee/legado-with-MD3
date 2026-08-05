package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.script.rhino.runScriptWithContext
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookProgress
import io.legado.app.help.TTS
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.storage.Backup
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.model.CacheBook
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.model.ReadSessionState
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setChapter
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.model.analyzeRule.AnalyzeUrl.Companion.paramPattern
import io.legado.app.receiver.NetworkChangedListener
import io.legado.app.receiver.TimeBatteryReceiver
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.read.page.ContentTextView
import io.legado.app.ui.book.read.page.ReadView
import io.legado.app.ui.book.read.page.ReaderEvent
import io.legado.app.ui.book.read.page.entities.PageDirection
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.entities.TextPos
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.book.read.page.provider.TextPageFactory
import io.legado.app.ui.book.read.page.provider.TipStyleProvider
import io.legado.app.ui.config.readConfig.ReadConfig
import io.legado.app.ui.login.SourceLoginJsExtensions
import io.legado.app.ui.widget.PopupAction
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.Debounce
import io.legado.app.utils.GSON
import io.legado.app.utils.LogUtils
import io.legado.app.utils.buildMainHandler
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.invisible
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.longToastOnUi
import io.legado.app.utils.navigationBarGravity
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setLightStatusBar
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import io.legado.app.utils.share
import io.legado.app.utils.sysBattery
import io.legado.app.utils.sysScreenOffTime
import io.legado.app.utils.throttle
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext


/**
 * Encapsulates all the reader logic that used to be in ReadBookActivity.
 * This allows ReadBookRouteScreen to be hosted in any Activity (ReadBookActivity or MainActivity).
 */
class ReadBookController(
    val activity: AppCompatActivity,
    val viewModel: ReadBookViewModel,
) : ReadBookRouteHost,
    ReadBookInputHandler,
    ReadView.CallBack,
    ContentTextView.CallBack,
    ReadBook.ReaderRenderCallback {

    var refs: ReadBookViewRefs? = null

    internal val layoutController = ReaderLayoutCoordinator(
        updateLayoutSize = ChapterProvider::upViewSize,
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
    private var textMenuRequestVersion = 0L
    private val menuMutex = Mutex()
    @Volatile
    private var cachedActionMenuItems: List<ActionMenuItem>? = null

    fun dismissTextActionMenu() {
        textMenuRequestVersion++
        _textMenuState.value = null
    }
    private val popupAction by lazy { PopupAction(activity) }
    private var screenTimeOut: Long = 0
    private var pendingSearchResultMark: IntArray? = null
    private var appliedDarkTheme: Boolean? = null
    private val originalRequestedOrientation = activity.requestedOrientation
    private val originalScreenBrightness = activity.window.attributes.screenBrightness
    private val originalKeepScreenOn =
        (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
    // justInitData moved to ViewModel (set on InitData intent)

    val isAutoPage: Boolean get() = refs?.readView?.isAutoPage == true

    private fun speak(text: String) {
        if (tts == null) {
            tts = TTS()
        }
        tts?.speak(text)
    }

    fun clearTts() {
        ReadBook.unregisterRender(this)
        tts?.clearTts()
        tts = null
        dismissTextActionMenu()
        popupAction.dismiss()
        refs?.readView?.onDestroy()
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
        onUnhandledEffect(ReadBookEffect.UpSeekBar)
    }

    fun onRefsReady(newRefs: ReadBookViewRefs) {
        if (refs === newRefs) return
        refs = newRefs
        newRefs.readView.autoPager.onStop = {
            viewModel.setAutoPage(false)
        }
        newRefs.navigationBar.doOnAttach {
            newRefs.navigationBar.setOnApplyWindowInsetsListenerCompat { view, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.updateLayoutParams {
                    height = insets.bottom
                }
                windowInsets
            }
        }
        newRefs.readView.upTime()
        newRefs.readView.upBattery(activity.sysBattery)
        refreshActionMenuItems()
        // 视图就绪后接管渲染回调；registerRender 会在已有内容时立即同步一次，避免首帧漏渲染
        ReadBook.registerRender(this)
    }

    fun onMenuVisibilityChanged(visible: Boolean) {
        val autoPager = refs?.readView?.autoPager ?: return
        if (!autoPager.isRunning) return
        if (visible) {
            autoPager.pause()
        } else {
            autoPager.resume()
        }
    }

    fun onAppThemeChanged(isDarkTheme: Boolean) {
        if (
            appliedDarkTheme == isDarkTheme &&
            ReadSessionState.isDarkThemeOverride == isDarkTheme
        ) return
        val startedAt = System.nanoTime()
        val previous = ReadSessionState.isDarkThemeOverride
        appliedDarkTheme = isDarkTheme
        ReadSessionState.isDarkThemeOverride = isDarkTheme
        refs?.readView?.applyThemeColors()
        upSystemUiVisibility()
        LogUtils.d(
            "ReadBookTheme",
            "apply dark=$isDarkTheme previous=$previous refsReady=${refs != null} " +
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
        refs?.readView?.upTime()
        refs?.readView?.upBattery(activity.sysBattery)
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

    // ── ReadView 协作面 ────────────────────────────────────────────────
    // override 的四项是 ReadView.CallBack（瞬时 UI 副作用 + 首帧门闩）；
    // private 的几项是 ReaderEvent 的落点，只由下面的 onEvent 调用。

    // initData 还没跑完时, 只要 ReadBook 里已有本书可用的章节就让 ReadView 直接画正文,
    // 否则首帧必然是 "加载数据中" 占位页
    override val isInitFinish: Boolean
        get() = viewModel.uiState.value.isInitFinish || viewModel.isCachedChapterUsable()

    private fun showActionMenu() {
        val state = viewModel.uiState.value
        when {
            BaseReadAloudService.isRun -> viewModel.onIntent(
                ReadBookIntent.ReadAloudAction
            )

            isAutoPage -> {
                refs?.readView?.autoPager?.pause()
                viewModel.onIntent(ReadBookIntent.OpenReadMenuRoute(ReadBookMenuRoute.AutoRead))
            }
            state.isShowingSearchResult -> viewModel.onIntent(ReadBookIntent.ShowSearchMenu)
            else -> viewModel.onIntent(ReadBookIntent.ShowMenu)
        }
    }

    override fun screenOffTimerStart() {
        onScreenOffTimerStart?.invoke() ?: screenOffTimerStartInternal()
    }

    override fun showTextActionMenu() {
        val r = refs ?: return
        val text = selectedText
        val startX = r.textMenuPosition.x.toInt()
        val startTopY = r.textMenuPosition.y.toInt()
        val startBottomY = r.cursorLeft.y.toInt() + r.cursorLeft.height
        val endX = r.cursorRight.x.toInt()
        val endBottomY = r.cursorRight.y.toInt() + r.cursorRight.height
        val requestVersion = ++textMenuRequestVersion

        activity.lifecycleScope.launch {
            val items = getActionMenuItems()
            val readView = refs?.readView
            if (textMenuRequestVersion != requestVersion || readView?.isTextSelected != true) {
                return@launch
            }
            _textMenuState.value = TextMenuState(
                selectedText = text,
                startX = startX,
                startTopY = startTopY,
                startBottomY = startBottomY,
                endX = endX,
                endBottomY = endBottomY,
                items = items
            )
        }
    }

    private fun autoPageStop() {
        viewModel.onIntent(ReadBookIntent.StopAutoPage)
    }

    private fun openChapterList() {
        viewModel.onIntent(ReadBookIntent.OpenChapterList)
    }

    private fun openContentEdit() {
        viewModel.onIntent(ReadBookIntent.OpenContentEdit)
    }

    private fun addBookmark() {
        val book = ReadBook.book
        val page = ReadBook.curTextChapter?.getPage(ReadBook.durPageIndex)
        if (book != null && page != null) {
            val bookmark = book.createBookMark().apply {
                chapterIndex = ReadBook.durChapterIndex
                chapterPos = ReadBook.durChapterPos
                chapterName = page.title
                bookText = page.text.replace(Regex("[袮꧁]"), "").trim()
            }
            viewModel.onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.Bookmark(bookmark)))
        }
    }

    private fun changeReplaceRuleState() {
        viewModel.onIntent(ReadBookIntent.MenuEnableReplace)
    }

    private fun openSearch(searchWord: String?) {
        viewModel.onIntent(ReadBookIntent.OpenSearch(searchWord))
    }

    override fun upSystemUiVisibility() {
        val state = viewModel.uiState.value
        upSystemUiVisibility(isInMultiWindowModeCompat, !state.menuVisible)
    }

    private fun sureNewProgress(progress: BookProgress) {
        viewModel.onIntent(ReadBookIntent.SureNewProgress(progress))
    }

    // ── ReaderPageSource（Track D·D2：喂给渲染层的页数据 + 接下页位置命令）────

    override val durChapterIndex: Int get() = ReadBook.durChapterIndex

    override val durPageIndex: Int get() = ReadBook.durPageIndex

    override val simulatedChapterSize: Int get() = ReadBook.simulatedChapterSize

    override val pageAnim: Int get() = ReadBook.pageAnim()

    override val msg: String? get() = ReadBook.msg

    override fun textChapter(chapterOnDur: Int): TextChapter? =
        ReadBook.textChapter(chapterOnDur)

    override fun setPageIndex(index: Int) = ReadBook.setPageIndex(index)

    // upContentInPlace = false：取页器在命令返回后自己决定要不要 upContent，别刷两遍
    override fun moveToNextChapter(upContent: Boolean) {
        ReadBook.moveToNextChapter(upContent, upContentInPlace = false)
    }

    override fun moveToPrevChapter(upContent: Boolean) {
        ReadBook.moveToPrevChapter(upContent, upContentInPlace = false)
    }

    // ── ReaderEventListener（Track D·D1：ReadView 的出站业务意图）─────────

    override fun onEvent(event: ReaderEvent) {
        when (event) {
            ReaderEvent.ShowActionMenu -> showActionMenu()
            ReaderEvent.AutoPageStop -> autoPageStop()
            ReaderEvent.OpenChapterList -> openChapterList()
            ReaderEvent.OpenContentEdit -> openContentEdit()
            ReaderEvent.OpenSearch -> openSearch(null)
            ReaderEvent.AddBookmark -> addBookmark()
            ReaderEvent.ToggleBookmark -> viewModel.onIntent(ReadBookIntent.ToggleBookmark)
            ReaderEvent.ChangeReplaceRuleState -> changeReplaceRuleState()
            ReaderEvent.NextChapter -> viewModel.onIntent(ReadBookIntent.NextChapter)
            ReaderEvent.PrevChapter -> viewModel.onIntent(ReadBookIntent.PrevChapter)
            ReaderEvent.ReadAloudPrevParagraph ->
                viewModel.onIntent(ReadBookIntent.ReadAloudPrevParagraph)

            ReaderEvent.ReadAloudNextParagraph ->
                viewModel.onIntent(ReadBookIntent.ReadAloudNextParagraph)

            // 只做暂停/续读，与菜单栏的 toggleReadAloud()（含"朗读未启动则启动"编排）不同，
            // 保持点击区原语义：朗读没开着时这个手势是空操作。
            ReaderEvent.ToggleReadAloudPause -> if (BaseReadAloudService.isPlay()) {
                ReadAloud.pause(activity)
            } else {
                ReadAloud.resume(activity)
            }

            ReaderEvent.SyncProgress -> ReadBook.syncProgress(
                { progress -> sureNewProgress(progress) },
                { activity.longToastOnUi(activity.getString(R.string.upload_book_success)) },
                { activity.longToastOnUi(activity.getString(R.string.sync_book_progress_success)) }
            )
        }
    }

    /**
     * 从选择位置开始朗读：先把阅读位置推进到所选页，再按行/列换算章内位置起读。
     * 原先在 ReadView 内（Track D·D1 迁出——ReadView 不下达业务命令）。
     */
    private suspend fun aloudStartSelect(selectStartPos: TextPos) {
        val readView = refs?.readView ?: return
        var pagePos = selectStartPos.relativePagePos
        while (pagePos > 0) {
            if (!ReadBook.moveToNextPage()) {
                ReadBook.moveToNextChapterAwait(false)
            }
            pagePos--
        }
        val startPos = readView.posByLineColumn(
            selectStartPos.lineIndex,
            selectStartPos.columnIndex
        )
        ReadBook.readAloud(startPos = startPos)
    }

    // ── ContentTextView.CallBack ──────────────────────────────────────

    override val headerHeight: Int get() = refs?.readView?.curPage?.headerHeight ?: 0
    override val readerLayoutController: ReaderLayoutController get() = layoutController
    override val imgBgPaddingStart: Int get() = refs?.readView?.curPage?.imgBgPaddingStart ?: 0
    override val pageFactory: TextPageFactory
        get() = refs?.readView?.pageFactory ?: error("ReadView not ready")
    override val pageDelegate get() = refs?.readView?.pageDelegate
    override val isScroll: Boolean get() = refs?.readView?.isScroll ?: false
    override var isSelectingSearchResult = false
    override fun upSelectedStart(x: Float, y: Float, top: Float) {
        val r = refs ?: return
        r.cursorLeft.x = x - r.cursorLeft.width
        r.cursorLeft.y = y
        r.cursorLeft.visible(true)
        r.textMenuPosition.x = x
        r.textMenuPosition.y = top

        if (ReadConfig.selectVibrator) {
            r.root.performHapticFeedback(HapticFeedbackConstantsCompat.TEXT_HANDLE_MOVE)
        }
    }

    override fun upSelectedEnd(x: Float, y: Float) {
        val r = refs ?: return
        r.cursorRight.x = x
        r.cursorRight.y = y
        r.cursorRight.visible(true)
        if (ReadConfig.selectVibrator) {
            r.root.performHapticFeedback(HapticFeedbackConstantsCompat.TEXT_HANDLE_MOVE)
        }
    }

    override fun onImageLongPress(x: Float, y: Float, src: String) {
        val r = refs ?: return
        r.root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
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
                "menu" -> showActionMenu()
            }
            popupAction.dismiss()
        }
        val navigationBarHeight =
            if (!ReadBookConfig.hideNavigationBar && activity.navigationBarGravity == Gravity.BOTTOM) {
                r.navigationBar.height
            } else {
                0
            }
        popupAction.showAtLocation(
            r.readView,
            Gravity.BOTTOM or Gravity.LEFT,
            x.toInt(),
            r.root.height + navigationBarHeight - y.toInt()
        )
    }

    override fun onCancelSelect() {
        refs?.cursorLeft?.invisible()
        refs?.cursorRight?.invisible()
        dismissTextActionMenu()
    }

    override fun onLongScreenshotTouchEvent(event: MotionEvent): Boolean =
        refs?.readView?.onTouchEvent(event) ?: false

    override fun oldClickImg(src: String): Boolean {
        val urlMatcher = paramPattern.matcher(src)
        if (urlMatcher.find()) {
            val urlOptionStr = src.substring(urlMatcher.end())
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
                    val urlNoOption = src.take(urlMatcher.start())
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

    override fun clickImg(click: String, src: String) {
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

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        val r = refs ?: return false
        if (v == null || event == null) {
            return false
        }
        val action = event.action
        if (!r.readView.isTextSelected
            && action != MotionEvent.ACTION_UP
            && action != MotionEvent.ACTION_CANCEL
        ) {
            return false
        }
        when (action) {
            MotionEvent.ACTION_DOWN -> dismissTextActionMenu()
            MotionEvent.ACTION_MOVE -> {
                when (v.id) {
                    R.id.cursor_left -> if (!r.readView.curPage.getReverseStartCursor()) {
                        r.readView.curPage.selectStartMove(
                            event.rawX + r.cursorLeft.width,
                            event.rawY - r.cursorLeft.height
                        )
                    } else {
                        r.readView.curPage.selectEndMove(
                            event.rawX - r.cursorRight.width,
                            event.rawY - r.cursorRight.height
                        )
                    }

                    R.id.cursor_right -> if (r.readView.curPage.getReverseEndCursor()) {
                        r.readView.curPage.selectStartMove(
                            event.rawX + r.cursorLeft.width,
                            event.rawY - r.cursorLeft.height
                        )
                    } else {
                        r.readView.curPage.selectEndMove(
                            event.rawX - r.cursorRight.width,
                            event.rawY - r.cursorRight.height
                        )
                    }
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (action == MotionEvent.ACTION_UP) {
                    v.performClick()
                }
                r.readView.curPage.resetReverseCursor()
                if (r.readView.isTextSelected) {
                    showTextActionMenu()
                } else {
                    dismissTextActionMenu()
                }
            }
        }
        return true
    }

    val selectedText: String get() = refs?.readView?.getSelectText().orEmpty()

    fun onMenuItemSelected(itemId: Int): Boolean {
        when (itemId) {
            R.id.menu_aloud -> {
                viewModel.onIntent(
                    ReadBookIntent.TextActionAloud(
                        selectedText,
                        refs?.readView?.curPage?.selectStartPos?.copy(),
                    )
                )
                return true
            }

            R.id.menu_bookmark -> {
                refs?.readView?.curPage?.createBookmark()?.let {
                    viewModel.onIntent(ReadBookIntent.TextActionBookmark(it))
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
                refs?.readView?.curPage?.createBookmark()?.let { selection ->
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
                refs?.readView?.curPage?.createBookmark()?.let { selection ->
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
        refs?.readView?.cancelSelect()
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
                    val icon = if (ReadConfig.showSelectMenuIcon) {
                        runCatching { resolveInfo.loadIcon(pm) }.getOrNull()
                    } else null

                    thirdPartyItems.add(ActionMenuItem(id = -1, title = title, iconDrawable = icon, intent = processIntent))
                }
            }

            val allItems = items + thirdPartyItems
            val configStr = ReadConfig.textSelectMenuConfig
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
    // ReadBook 在其 IO 协程内调用这些渲染回调（见 contentLoadFinish 的 Coroutine.async
    // 默认跑在 Dispatchers.IO），而回调最终会触碰 refs.readView。旧路径靠 VM 的
    // SharedFlow → 生命周期收集器把渲染派发切回主线程；这里用 [postRender] 保留同样的
    // 异步-切主线程语义，复用既有的 handleEffect 渲染分支，零渲染逻辑漂移。

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
            refs?.readView?.onPageChange()
            viewModel.startBackupJob()
        }
    }

    override fun contentLoadFinish() {
        // isInitFinish 是纯业务/UI 标志（决定 ReadView 是否放行前后章排版），不属于渲染
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

    override fun onLayoutPageCompleted(index: Int, page: TextPage) {
        handler.post {
            layoutController.publishPageLayout(index)
            upSeekBarThrottle.invoke()
            refs?.readView?.onLayoutPageCompleted(index, page)
        }
    }

    // ── Effect handling ───────────────────────────────────────────────

    /**
     * Handles View-layer and Activity-API effects.
     * Launcher-dependent effects are handled by the route layer.
     */
    fun handleEffect(effect: ReadBookEffect) {
        when (effect) {
            // ── Already migrated (View-layer) ──
            is ReadBookEffect.Finish -> closeReadBook()
            is ReadBookEffect.UpdateReadViewConfig -> {
                // 两份快照都是配置的纯派生，在分发具体 action 之前先重建：下划线/虚线/下划线颜色
                // 等项的 action 集里并没有 UpdateStyle，靠 upStyle() 顺带重建会漏；而且
                // actions 是集合，无法保证「重建」排在 InvalidateTextPage/SubmitRenderTask 之前。
                // 重建必须排在 refs 判空之前——view 未挂载期收到的配置变更，至少快照不能滞留。
                ChapterProvider.upRenderStyle()
                TipStyleProvider.upTipStyle()
                val r = refs ?: return
                effect.actions.forEach { action ->
                    when (action) {
                        ConfigUpdateAction.UpdateSystemUi -> upSystemUiVisibility()
                        ConfigUpdateAction.UpdateBackground -> r.readView.upBg()
                        ConfigUpdateAction.UpdateStyle -> r.readView.upStyle()
                        ConfigUpdateAction.UpdateBackgroundAlpha -> r.readView.upBgAlpha()
                        ConfigUpdateAction.UpdatePageSlopSquare -> r.readView.upPageSlopSquare()
                        ConfigUpdateAction.ReloadContent -> if (viewModel.isInitFinish) ReadBook.loadContent(resetPageOffset = false)
                        ConfigUpdateAction.RelayoutContent -> if (viewModel.isInitFinish) {
                            layoutController.requestRelayout()
                        }
                        ConfigUpdateAction.UpdateContent -> r.readView.upContent(resetPageOffset = false)
                        ConfigUpdateAction.UpdateChapterStyle -> {
                            ChapterProvider.upStyle()
                            ReadBook.requestWholeBookPageEstimate()
                        }
                        ConfigUpdateAction.InvalidateTextPage -> r.readView.invalidateTextPage()
                        ConfigUpdateAction.UpdateLayout -> {
                            ChapterProvider.upLayout()
                            ReadBook.requestWholeBookPageEstimate()
                        }
                        ConfigUpdateAction.RebuildWholeBookPageIndex ->
                            ReadBook.requestWholeBookPageEstimate()
                        ConfigUpdateAction.UpdateWholeBookPageDemand ->
                            ReadBook.updateWholeBookPageDemand()
                        ConfigUpdateAction.SubmitRenderTask -> r.readView.submitRenderTask()
                        ConfigUpdateAction.UpdatePageAnim -> r.readView.upPageAnim()
                    }
                }
            }

            is ReadBookEffect.UpContent -> {
                refs?.readView?.upContent(effect.relativePosition, effect.resetPageOffset)
                effect.success?.invoke()
                refs?.readView?.post {
                    consumePendingSearchResultMark()
                }
                if (effect.relativePosition == 0) onUnhandledEffect(ReadBookEffect.UpSeekBar)
            }

            is ReadBookEffect.UpPageAnim -> refs?.readView?.upPageAnim(effect.upRecorder)
            is ReadBookEffect.UpTime -> refs?.readView?.upTime()
            is ReadBookEffect.UpBattery -> refs?.readView?.upBattery(effect.level)
            is ReadBookEffect.UpSystemUiVisibility -> upSystemUiVisibility()
            is ReadBookEffect.PageAnimChanged -> {
                refs?.readView?.upPageAnim()
                ReadBook.loadContent(false)
            }

            is ReadBookEffect.CancelSelect -> {
                pendingSearchResultMark = null
                refs?.readView?.cancelSelect()
            }
            is ReadBookEffect.MenuImageStyleChanged -> refs?.readView?.upPageAnim()

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

            is ReadBookEffect.UpTextSelectAble -> {
                refs?.readView?.curPage?.upSelectAble(effect.enabled)
            }

            is ReadBookEffect.UpAloudState -> {
                ReadBook.curTextChapter?.let { textChapter ->
                    val page = textChapter.getPageByReadPos(ReadBook.durChapterPos)
                    page?.removePageAloudSpan()
                    refs?.readView?.upContent(resetPageOffset = false)
                }
            }

            is ReadBookEffect.RefreshBookContent -> {
                ReadBook.curTextChapter = null
                refs?.readView?.upContent()
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
            is ReadBookEffect.TextActionAloudSelect -> {
                activity.lifecycleScope.launch {
                    aloudStartSelect(effect.selectStartPos.copy())
                }
            }

            is ReadBookEffect.TextActionSpeak -> speak(effect.text)
            is ReadBookEffect.NavigateToSearchResult -> {
                if (effect.pageIndex < 0) {
                    // Chapter not loaded — open it, then mark in the success callback
                    ReadBook.openChapter(
                        effect.chapterIndex,
                        0
                    ) {
                        val tc = ReadBook.curTextChapter ?: return@openChapter
                        val query = effect.result.query
                        val pos = viewModel.searchResultPositions(tc, effect.result, query)
                        if (pos[0] < 0) return@openChapter
                        activity.lifecycleScope.launch(Main) {
                            markSearchResultAfterNavigation(
                                intArrayOf(pos[0], pos[1], pos[2], pos[3], pos[4], pos[5])
                            )
                        }
                    }
                } else {
                    // Same chapter — navigate to page, then mark
                    val tc = ReadBook.curTextChapter ?: return
                    markSearchResultAfterNavigation(
                        intArrayOf(
                            effect.pageIndex, effect.lineIndex,
                            effect.startCharIndex, effect.endRelativePage,
                            effect.endLineIndex, effect.endCharIndex
                        )
                    )
                }
            }

            is ReadBookEffect.ExitSearch -> {
                pendingSearchResultMark = null
                ReadBook.clearSearchResult()
                refs?.readView?.cancelSelect(clearSearchResult = true)
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

            is ReadBookEffect.UpBookmarkBadge -> {
                refs?.readView?.upBookmarkBadge()
            }
        }
    }

    fun updateReadAloudProgress(chapterStart: Int) {
        if (!BaseReadAloudService.isPlay()) return
        val textChapter = ReadBook.curTextChapter ?: return
        if (textChapter.chapter.index != BaseReadAloudService.currentChapterIndex) return
        val pageIndex = textChapter.getPageIndexByCharIndex(chapterStart)
        if (pageIndex < 0 || pageIndex != ReadBook.durPageIndex) return
        val aloudSpanStart = chapterStart - textChapter.getReadLength(pageIndex)
        textChapter.getPage(pageIndex)?.upPageAloudSpan(aloudSpanStart)
        refs?.readView?.upContent(resetPageOffset = false)
    }

    // ── Key handling ──

    private fun toggleReadAloud() {
        viewModel.onIntent(ReadBookIntent.StopAutoPage)
        when {
            !BaseReadAloudService.isRun -> {
                ReadAloud.upReadAloudClass()
                val scrollPageAnim = ReadBook.pageAnim() == 3
                val readView = refs?.readView
                if (scrollPageAnim && readView != null) {
                    val pos = readView.getReadAloudPos()
                    if (pos != null) {
                        val (index, line) = pos
                        if (ReadBook.durChapterIndex != index) {
                            ReadBook.openChapter(index, line.chapterPosition, false) {
                                ReadBook.readAloud(startPos = line.pagePosition)
                            }
                        } else {
                            ReadBook.updateReadingPosition(line.chapterPosition)
                            ReadBook.readAloud(startPos = line.pagePosition)
                        }
                    } else {
                        ReadBook.readAloud()
                    }
                } else {
                    ReadBook.readAloud()
                }
            }

            BaseReadAloudService.pause -> {
                val scrollPageAnim = ReadBook.pageAnim() == 3
                val readView = refs?.readView
                if (scrollPageAnim && pageChanged && readView != null) {
                    pageChanged = false
                    val pos = readView.getReadAloudPos()
                    if (pos != null) {
                        val (index, line) = pos
                        if (ReadBook.durChapterIndex != index) {
                            ReadBook.openChapter(index, line.chapterPosition, false) {
                                ReadBook.readAloud(startPos = line.pagePosition)
                            }
                        } else {
                            ReadBook.updateReadingPosition(line.chapterPosition)
                            ReadBook.readAloud(startPos = line.pagePosition)
                        }
                    } else {
                        ReadBook.readAloud()
                    }
                } else {
                    ReadAloud.resume(activity)
                }
            }

            else -> ReadAloud.pause(activity)
        }
    }

    private fun toggleAutoPage() {
        ReadAloud.stop(activity)
        if (isAutoPage) {
            stopAutoPage()
        } else {
            refs?.readView?.autoPager?.start()
            if (viewModel.uiState.value.menuVisible) {
                refs?.readView?.autoPager?.pause()
            }
            viewModel.setAutoPage(true)
            onScreenOffTimerStart?.invoke()
        }
    }

    private fun stopAutoPage() {
        if (isAutoPage) {
            refs?.readView?.autoPager?.stop()
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
        if (menuLayoutIsVisible || !ReadConfig.mouseWheelPage) {
            return
        }
        keyPageDebounce(direction, mouseWheel = true, longPress = false)
    }

    private fun volumeKeyPage(direction: PageDirection, longPress: Boolean): Boolean {
        if (!ReadConfig.volumeKeyPage) {
            return false
        }
        if (!ReadConfig.volumeKeyPageOnPlay && BaseReadAloudService.isPlay()) {
            return false
        }
        handleKeyPage(direction, longPress)
        return true
    }

    override fun handleKeyPage(direction: PageDirection, longPress: Boolean) {
        if (ReadConfig.keyPageOnLongPress || direction == PageDirection.NONE) {
            keyPage(direction)
        } else {
            keyPageDebounce(direction, longPress = longPress)
        }
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
        refs?.readView?.cancelSelect()
        refs?.readView?.pageDelegate?.isCancel = false
        refs?.readView?.pageDelegate?.keyTurnPage(direction)
    }

    private fun upScreenTimeOut() {
        val keepLightPrefer = ReadConfig.keepLight.toLongOrNull() ?: 0L
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
        when (ReadConfig.screenOrientation) {
            "0" -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            "1" -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "2" -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            "3" -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
            "4" -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            "5" -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        }
    }

    // ── Search result navigation helpers ─────────────────────────────

    private fun markSearchResultAfterNavigation(pos: IntArray) {
        if (pos[0] < 0) return
        val readView = refs?.readView ?: return
        pendingSearchResultMark = pos
        ReadBook.skipToPage(pos[0]) {
            readView.post {
                consumePendingSearchResultMark()
            }
        }
    }

    private fun consumePendingSearchResultMark(): Boolean {
        val pos = pendingSearchResultMark ?: return false
        val readView = refs?.readView ?: return false
        if (ReadBook.durPageIndex != pos[0] || readView.curPage.textPage.index != pos[0]) {
            return false
        }
        pendingSearchResultMark = null
        markSearchResultOnPage(pos)
        return true
    }

    /**
     * Mark search result columns on the current page for highlighting.
     * @param pos array of [pageIndex, lineIndex, startCharIndex, endRelativePage, endLineIndex, endCharIndex]
     */
    private fun markSearchResultOnPage(pos: IntArray) {
        val readView = refs?.readView ?: return
        val lineIndex = pos[1]
        val startCharIndex = pos[2]
        val endRelativePage = pos[3]
        val endLineIndex = pos[4]
        val endCharIndex = pos[5]
        ReadBook.clearSearchResult()
        readView.cancelSelect(clearSearchResult = true)
        isSelectingSearchResult = true
        try {
            readView.curPage.selectStartMoveIndex(0, lineIndex, startCharIndex)
            readView.curPage.selectEndMoveIndex(endRelativePage, endLineIndex, endCharIndex)
            readView.isTextSelected = true
        } finally {
            isSelectingSearchResult = false
        }
    }
}

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
