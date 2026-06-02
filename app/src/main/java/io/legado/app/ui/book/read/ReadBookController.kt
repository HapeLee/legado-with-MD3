package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.script.rhino.runScriptWithContext
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookProgress
import io.legado.app.data.entities.Bookmark
import io.legado.app.help.IntentData
import io.legado.app.help.TTS
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.storage.Backup
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setChapter
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.model.analyzeRule.AnalyzeUrl.Companion.paramPattern
import io.legado.app.receiver.NetworkChangedListener
import io.legado.app.receiver.TimeBatteryReceiver
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.read.page.ContentTextView
import io.legado.app.ui.book.read.page.ReadView
import io.legado.app.ui.book.read.page.entities.PageDirection
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.book.read.page.provider.TextPageFactory
import io.legado.app.ui.book.searchContent.SearchResult
import io.legado.app.ui.login.SourceLoginJsExtensions
import io.legado.app.ui.replace.ReplaceEditRoute
import io.legado.app.ui.replace.ReplaceRuleActivity
import io.legado.app.ui.widget.PopupAction
import io.legado.app.utils.Debounce
import io.legado.app.utils.GSON
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.buildMainHandler
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.invisible
import io.legado.app.utils.longToastOnUi
import io.legado.app.utils.navigationBarGravity
import io.legado.app.utils.setLightStatusBar
import io.legado.app.utils.setNavigationBarColorAuto
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import io.legado.app.utils.sysScreenOffTime
import io.legado.app.utils.themeColor
import io.legado.app.utils.throttle
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Encapsulates all the reader logic that used to be in ReadBookActivity.
 * This allows ReadBookRouteScreen to be hosted in any Activity (ReadBookActivity or MainActivity).
 */
class ReadBookController(
    val activity: AppCompatActivity,
    val viewModel: ReadBookViewModel,
) : ReadBookRouteHost,
    ReadView.CallBack,
    ContentTextView.CallBack,
    TextActionMenu.CallBack {

    var refs: ReadBookViewRefs? = null

    // Fallback handler for effects not yet migrated to controller
    var onUnhandledEffect: (ReadBookEffect) -> Unit = {}
    var onClose: (() -> Unit)? = null

    // ActivityResult Launchers — registered from Compose via registerLaunchers()
    private var tocLauncher: ActivityResultLauncher<String>? = null
    private var sourceEditLauncher: ActivityResultLauncher<(Intent.() -> Unit)?>? = null
    private var replaceLauncher: ActivityResultLauncher<Intent>? = null
    private var fontFolderPicker: ActivityResultLauncher<Uri?>? = null
    private var readStyleImagePicker: ActivityResultLauncher<Array<String>>? = null
    private var readStyleImportPicker: ActivityResultLauncher<Array<String>>? = null
    private var readStyleExportPicker: ActivityResultLauncher<String>? = null
    private var txtTocRuleLauncher: ActivityResultLauncher<Intent>? = null
    private var searchContentLauncher: ActivityResultLauncher<(Intent.() -> Unit)?>? = null
    private var bookInfoLauncher: ActivityResultLauncher<(Intent.() -> Unit)?>? = null

    // Page state — moved from Activity
    var pageChanged: Boolean = false
        private set

    fun resetPageChanged() {
        pageChanged = false
    }

    // Callbacks to Activity for operations that require Activity-level state
    var onScreenOffTimerStart: (() -> Unit)? = null
    var onStartBackupJob: (() -> Unit)? = null
    var onStartContentLoadFinish: (() -> Unit)? = null

    // Phase 4: callbacks for Activity-dependent effects
    var onToggleReadAloud: (() -> Unit)? = null
    var onToggleAutoPage: (() -> Unit)? = null
    var onStopAutoPage: (() -> Unit)? = null
    var onTextActionAloudSelect: (() -> Unit)? = null
    var onNavigateToSearchResult: ((SearchResult, Int) -> Unit)? = null
    var onShowPayDialog: (() -> Unit)? = null
    var onSyncBookProgress: ((io.legado.app.data.entities.Book) -> Unit)? = null
    var onShowConfirmSkipToChapter: (() -> Unit)? = null
    var onSelectSpeakEngine: (() -> Unit)? = null
    var onOpenPreDownloadNumPicker: (() -> Unit)? = null
    var onOpenCacheCleanTimePicker: (() -> Unit)? = null
    var onShowLogin: (() -> Unit)? = null
    var onShowStackTrace: ((String) -> Unit)? = null
    var onToggleDayNight: (() -> Unit)? = null
    var onDownloadChapters: ((Int, Int) -> Unit)? = null

    private var tts: TTS? = null
    private val timeBatteryReceiver = TimeBatteryReceiver()
    private val networkChangedListener by lazy { NetworkChangedListener(activity) }
    private val handler by lazy { buildMainHandler() }
    private val screenOffRunnable by lazy { Runnable { keepScreenOn(false) } }
    private val textActionMenu by lazy {
        TextActionMenu(
            context = activity,
            callBack = this,
            expandTextMenu = { viewModel.readPreferences.value.expandTextMenu }
        )
    }
    private val popupAction by lazy { PopupAction(activity) }
    private var screenTimeOut: Long = 0
    private var backupJob: Job? = null
    private var justInitData: Boolean = false

    val isAutoPage: Boolean get() = refs?.readView?.isAutoPage == true

    private fun speak(text: String) {
        if (tts == null) {
            tts = TTS()
        }
        tts?.speak(text)
    }

    fun clearTts() {
        tts?.clearTts()
        tts = null
        textActionMenu.dismiss()
        popupAction.dismiss()
        backupJob?.cancel()
        refs?.readView?.onDestroy()
        networkChangedListener.unRegister()
        kotlin.runCatching { activity.unregisterReceiver(timeBatteryReceiver) }
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

    init {
        activity.lifecycleScope.launch {
            viewModel.effects.collectLatest { effect ->
                handleEffect(effect)
            }
        }
    }

    /**
     * Called from Compose LaunchedEffect to wire all launchers at once.
     */
    fun registerLaunchers(
        tocLauncher: ActivityResultLauncher<String>,
        sourceEditLauncher: ActivityResultLauncher<(Intent.() -> Unit)?>,
        replaceLauncher: ActivityResultLauncher<Intent>,
        fontFolderPicker: ActivityResultLauncher<Uri?>,
        readStyleImagePicker: ActivityResultLauncher<Array<String>>,
        readStyleImportPicker: ActivityResultLauncher<Array<String>>,
        readStyleExportPicker: ActivityResultLauncher<String>,
        txtTocRuleLauncher: ActivityResultLauncher<Intent>,
        searchContentLauncher: ActivityResultLauncher<(Intent.() -> Unit)?>,
        bookInfoLauncher: ActivityResultLauncher<(Intent.() -> Unit)?>,
    ) {
        this.tocLauncher = tocLauncher
        this.sourceEditLauncher = sourceEditLauncher
        this.replaceLauncher = replaceLauncher
        this.fontFolderPicker = fontFolderPicker
        this.readStyleImagePicker = readStyleImagePicker
        this.readStyleImportPicker = readStyleImportPicker
        this.readStyleExportPicker = readStyleExportPicker
        this.txtTocRuleLauncher = txtTocRuleLauncher
        this.searchContentLauncher = searchContentLauncher
        this.bookInfoLauncher = bookInfoLauncher
    }

    fun onRefsReady(newRefs: ReadBookViewRefs) {
        if (refs === newRefs) return
        refs = newRefs
        newRefs.navigationBar.doOnAttach {
            newRefs.navigationBar.setOnApplyWindowInsetsListenerCompat { view, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.updateLayoutParams {
                    height = insets.bottom
                }
                windowInsets
            }
        }
        upNavigationBarColor()
        newRefs.readView.upTime()
    }

    fun onRouteInitialized() {
        justInitData = true
        upScreenTimeOut()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun onResume() {
        ReadBook.readStartTime = System.currentTimeMillis()
        ReadBook.initReadTime()
        ReadBook.startAutoSaveSession()
        ReadBook.webBookProgress?.let {
            ReadBook.setProgress(it)
            ReadBook.webBookProgress = null
        }
        upSystemUiVisibility()
        kotlin.runCatching {
            activity.registerReceiver(timeBatteryReceiver, timeBatteryReceiver.filter)
        }
        refs?.readView?.upTime()
        screenOffTimerStart()
        networkChangedListener.register()
        networkChangedListener.onNetworkChanged = {
            if (AppConfig.syncBookProgressPlus && NetworkUtils.isAvailable() && !justInitData) {
                ReadBook.syncProgress(newProgressAction = { progress ->
                    viewModel.onIntent(
                        ReadBookIntent.ShowDialog(
                            ReadBookDialog.ConfirmRestoreProgress(progress)
                        )
                    )
                })
            }
        }
    }

    fun onPause() {
        viewModel.onIntent(ReadBookIntent.StopAutoPage)
        backupJob?.cancel()
        ReadBook.saveRead()
        ReadBook.stopAutoSaveSession()
        ReadBook.commitReadSession()
        ReadBook.cancelPreDownloadTask()
        kotlin.runCatching { activity.unregisterReceiver(timeBatteryReceiver) }
        upSystemUiVisibility()
        if (!BuildConfig.DEBUG) {
            if (AppConfig.syncBookProgressPlus) {
                ReadBook.syncProgress()
            } else {
                ReadBook.uploadProgress()
            }
            Backup.autoBack(activity)
        }
        justInitData = false
        networkChangedListener.unRegister()
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
        }
        upNavigationBarColor()
    }

    override fun upNavigationBarColor() {
        val r = refs ?: return
        val state = viewModel.uiState.value
        val navColor = when {
            state.menuVisible -> activity.themeColor(com.google.android.material.R.attr.colorSurfaceContainer)
            state.searchMenuVisible -> activity.themeColor(com.google.android.material.R.attr.colorSurface)
            bottomDialogCount > 0 -> activity.themeColor(com.google.android.material.R.attr.colorSurface)
            else -> ReadBookConfig.bgMeanColor
        }
        activity.window.setNavigationBarColorAuto(navColor)
        r.navigationBar.setBackgroundColor(navColor)
    }

    // ── ReadView.CallBack ─────────────────────────────────────────────

    override val isInitFinish: Boolean get() = viewModel.uiState.value.isInitFinish

    override fun showActionMenu() {
        val state = viewModel.uiState.value
        when {
            BaseReadAloudService.isRun -> viewModel.onIntent(
                ReadBookIntent.OpenReadMenuRoute(ReadBookMenuRoute.ReadAloud)
            )

            isAutoPage -> viewModel.onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.AutoRead))
            state.isShowingSearchResult -> viewModel.onIntent(ReadBookIntent.ShowSearchMenu)
            else -> viewModel.onIntent(ReadBookIntent.ShowMenu)
        }
    }

    override fun screenOffTimerStart() {
        onScreenOffTimerStart?.invoke() ?: screenOffTimerStartInternal()
    }

    override fun showTextActionMenu() {
        val r = refs ?: return
        val navigationBarHeight =
            if (!ReadBookConfig.hideNavigationBar && activity.navigationBarGravity == Gravity.BOTTOM) {
                r.navigationBar.height
            } else {
                0
            }
        textActionMenu.upMenu()
        textActionMenu.show(
            r.textMenuPosition,
            r.root.height + navigationBarHeight,
            r.textMenuPosition.x.toInt(),
            r.textMenuPosition.y.toInt(),
            r.cursorLeft.y.toInt() + r.cursorLeft.height,
            r.cursorRight.x.toInt(),
            r.cursorRight.y.toInt() + r.cursorRight.height
        )
    }

    override fun autoPageStop() {
        viewModel.onIntent(ReadBookIntent.StopAutoPage)
    }

    override fun openChapterList() {
        ReadBook.book?.let { tocLauncher?.launch(it.bookUrl) }
    }

    override fun addBookmark() {
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

    override fun changeReplaceRuleState() {
        viewModel.onIntent(ReadBookIntent.MenuEnableReplace)
    }

    override fun openSearchActivity(searchWord: String?) {
        val book = ReadBook.book ?: return
        val lambda: (Intent.() -> Unit)? = { intent ->
            intent.putExtra("bookUrl", book.bookUrl)
            intent.putExtra("searchWord", searchWord)
            intent.putExtra("searchResultIndex", viewModel.searchResultIndex)
            viewModel.searchResultList?.first()?.let {
                if (it.query == viewModel.searchContentQuery) {
                    IntentData.put("searchResultList", viewModel.searchResultList)
                }
            }
        }
        searchContentLauncher?.launch(lambda)
    }

    override fun upSystemUiVisibility() {
        val state = viewModel.uiState.value
        upSystemUiVisibility(isInMultiWindowModeCompat, !state.menuVisible)
    }

    override fun sureNewProgress(progress: BookProgress) {
        viewModel.onIntent(ReadBookIntent.SureNewProgress(progress))
    }

    // ── ContentTextView.CallBack ──────────────────────────────────────

    override val headerHeight: Int get() = refs?.readView?.curPage?.headerHeight ?: 0
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

        if (AppConfig.selectVibrator) {
            r.root.performHapticFeedback(HapticFeedbackConstantsCompat.TEXT_HANDLE_MOVE)
        }
    }

    override fun upSelectedEnd(x: Float, y: Float) {
        val r = refs ?: return
        r.cursorRight.x = x
        r.cursorRight.y = y
        r.cursorRight.visible(true)
        if (AppConfig.selectVibrator) {
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
        textActionMenu.dismiss()
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
        if (v == null || event == null || !r.readView.isTextSelected) {
            return false
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN -> textActionMenu.dismiss()
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

            MotionEvent.ACTION_UP -> {
                r.readView.curPage.resetReverseCursor()
                showTextActionMenu()
            }
        }
        return true
    }

    override val selectedText: String get() = refs?.readView?.getSelectText().orEmpty()

    override fun onMenuItemSelected(itemId: Int): Boolean {
        when (itemId) {
            R.id.menu_aloud -> {
                viewModel.onIntent(ReadBookIntent.TextActionAloud(selectedText))
                return true
            }

            R.id.menu_bookmark -> {
                viewModel.onIntent(ReadBookIntent.TextActionBookmark(selectedText))
                return true
            }

            R.id.menu_edit -> {
                viewModel.onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.ContentEdit))
                return true
            }

            R.id.menu_replace -> {
                viewModel.onIntent(ReadBookIntent.TextActionReplace(selectedText))
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

    override fun onMenuActionFinally() {
        textActionMenu.dismiss()
        refs?.readView?.cancelSelect()
    }

    // ── Effect handling ───────────────────────────────────────────────

    private fun handleEffect(effect: ReadBookEffect) {
        when (effect) {
            // ── Already migrated (View-layer) ──
            is ReadBookEffect.Finish -> closeReadBook()
            is ReadBookEffect.Recreate -> activity.recreate()
            is ReadBookEffect.UpdateReadViewConfig -> {
                val r = refs ?: return
                effect.values.forEach { v ->
                    when (v) {
                        0 -> upSystemUiVisibility()
                        1 -> r.readView.upBg()
                        2 -> r.readView.upStyle()
                        3 -> r.readView.upBgAlpha()
                        4 -> r.readView.upPageSlopSquare()
                        5 -> if (viewModel.isInitFinish) ReadBook.loadContent(resetPageOffset = false)
                        6 -> r.readView.upContent(resetPageOffset = false)
                        8 -> ChapterProvider.upStyle()
                        9 -> r.readView.invalidateTextPage()
                        10 -> ChapterProvider.upLayout()
                        11 -> r.readView.submitRenderTask()
                    }
                }
            }

            is ReadBookEffect.UpContent -> {
                refs?.readView?.upContent(effect.relativePosition, effect.resetPageOffset)
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

            is ReadBookEffect.CancelSelect -> refs?.readView?.cancelSelect()
            is ReadBookEffect.MenuImageStyleChanged -> refs?.readView?.upPageAnim()

            // ── Simple Activity-API effects ──
            is ReadBookEffect.ShowToast -> activity.toastOnUi(effect.message)
            is ReadBookEffect.LongToast -> activity.longToastOnUi(effect.message)
            is ReadBookEffect.SetBrightness -> {
                val lp = activity.window.attributes
                lp.screenBrightness = effect.value / 255f
                activity.window.attributes = lp
            }

            // ── Launcher-dependent effects ──
            is ReadBookEffect.OpenChapterList -> openChapterList()
            is ReadBookEffect.OpenSourceEdit -> {
                ReadBook.bookSource?.let { src ->
                    val lambda: Intent.() -> Unit = { putExtra("sourceUrl", src.bookSourceUrl) }
                    sourceEditLauncher?.launch(lambda)
                }
            }

            is ReadBookEffect.OpenBookInfo -> {
                ReadBook.book?.let { book ->
                    val lambda: Intent.() -> Unit = {
                        putExtra("name", book.name)
                        putExtra("author", book.author)
                        putExtra("bookUrl", book.bookUrl)
                    }
                    bookInfoLauncher?.launch(lambda)
                }
            }

            is ReadBookEffect.OpenSearchActivity -> openSearchActivity(effect.word)
            is ReadBookEffect.MenuSettingReplace -> {
                replaceLauncher?.launch(Intent(activity, ReplaceRuleActivity::class.java))
            }

            is ReadBookEffect.TextActionReplace -> {
                val scopes = arrayListOf<String>()
                ReadBook.book?.name?.let { scopes.add(it) }
                ReadBook.bookSource?.bookSourceUrl?.let { scopes.add(it) }
                val text = effect.text.lineSequence().map { it.trim() }.joinToString("\n")
                val editRoute = ReplaceEditRoute(
                    id = -1, pattern = text,
                    scope = scopes.joinToString(";"),
                    isScopeTitle = false, isScopeContent = true,
                )
                replaceLauncher?.launch(ReplaceRuleActivity.startIntent(activity, editRoute))
            }

            is ReadBookEffect.OpenReplaceEditor -> {
                val editRoute = ReplaceEditRoute(id = effect.id, pattern = effect.pattern)
                replaceLauncher?.launch(ReplaceRuleActivity.startIntent(activity, editRoute))
            }

            is ReadBookEffect.MenuTocRegex -> {
                val intent =
                    Intent(activity, io.legado.app.ui.book.toc.rule.TxtTocRuleActivity::class.java)
                intent.putExtra("tocRegex", ReadBook.book?.tocUrl)
                txtTocRuleLauncher?.launch(intent)
            }

            is ReadBookEffect.OpenFontFolderPicker -> {
                fontFolderPicker?.launch(null)
            }

            is ReadBookEffect.OpenReadStyleImagePicker -> {
                readStyleImagePicker?.launch(arrayOf("image/*"))
            }

            is ReadBookEffect.OpenReadStyleImport -> {
                readStyleImportPicker?.launch(
                    arrayOf("application/zip", "application/octet-stream", "*/*")
                )
            }

            is ReadBookEffect.OpenReadStyleExport -> {
                readStyleExportPicker?.launch("readConfig.zip")
            }

            // ── DB query + sheet effects ──
            is ReadBookEffect.MenuChangeSource -> {
                activity.lifecycleScope.launch {
                    if (AppConfig.defaultSourceChangeAll) {
                        viewModel.onIntent(ReadBookIntent.HideMenu)
                        viewModel.onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.ChangeBookSource))
                    } else {
                        val book = ReadBook.book ?: return@launch
                        val chapter =
                            appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
                                ?: return@launch
                        viewModel.onIntent(ReadBookIntent.HideMenu)
                        viewModel.onIntent(
                            ReadBookIntent.ShowSheet(
                                ReadBookSheet.ChangeChapterSource(
                                    chapter.index,
                                    chapter.title
                                )
                            )
                        )
                    }
                }
            }

            is ReadBookEffect.MenuBookChangeSource -> {
                viewModel.onIntent(ReadBookIntent.HideMenu)
                viewModel.onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.ChangeBookSource))
            }

            is ReadBookEffect.MenuChapterChangeSource -> {
                activity.lifecycleScope.launch {
                    val book = ReadBook.book ?: return@launch
                    val chapter =
                        appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
                            ?: return@launch
                    viewModel.onIntent(
                        ReadBookIntent.ShowSheet(
                            ReadBookSheet.ChangeChapterSource(
                                chapter.index,
                                chapter.title
                            )
                        )
                    )
                }
            }

            // ── Bookmark ──
            is ReadBookEffect.AddBookmark -> {
                activity.lifecycleScope.launch(IO) {
                    val book = ReadBook.book ?: return@launch
                    val chapter = ReadBook.curTextChapter ?: return@launch
                    val page = chapter.pages.getOrNull(ReadBook.durPageIndex) ?: return@launch
                    val bookmark = Bookmark(
                        bookName = book.name,
                        bookAuthor = book.author,
                        chapterIndex = chapter.chapter.index,
                        chapterName = chapter.title,
                        chapterPos = ReadBook.durPageIndex,
                        bookText = page.text,
                        content = "",
                    )
                    viewModel.onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.Bookmark(bookmark)))
                }
            }

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

            is ReadBookEffect.UpTtsAloudSpan -> {
                activity.lifecycleScope.launch(IO) {
                    if (BaseReadAloudService.isPlay()) {
                        ReadBook.curTextChapter?.let { textChapter ->
                            val pageIndex = ReadBook.durPageIndex
                            val aloudSpanStart =
                                effect.chapterStart - textChapter.getReadLength(pageIndex)
                            textChapter.getPage(pageIndex)?.upPageAloudSpan(aloudSpanStart)
                            refs?.readView?.upContent()
                        }
                    }
                }
            }

            is ReadBookEffect.RefreshBookContent -> {
                ReadBook.curTextChapter = null
                refs?.readView?.upContent()
                ReadBook.book?.let { viewModel.refreshContentDur(it) }
            }

            is ReadBookEffect.PageChanged -> {
                pageChanged = true
                refs?.readView?.onPageChange()
                ReadBook.executor.execute { onStartBackupJob?.invoke() ?: startBackupJob() }
            }

            is ReadBookEffect.LayoutPageCompleted -> {
                upSeekBarThrottle.invoke()
                refs?.readView?.onLayoutPageCompleted(effect.index, effect.page)
            }

            is ReadBookEffect.ContentLoadFinish -> {
                onStartContentLoadFinish?.invoke()
            }

            is ReadBookEffect.UpScreenTimeOut -> {
                screenOffTimerStart()
            }

            is ReadBookEffect.ToggleBrightnessAuto -> { /* TODO */
            }

            // ── Phase 4: Activity-dependent effects ──
            is ReadBookEffect.ToggleReadAloud -> onToggleReadAloud?.invoke() ?: toggleReadAloud()
            is ReadBookEffect.ToggleAutoPage -> onToggleAutoPage?.invoke() ?: toggleAutoPage()
            is ReadBookEffect.StopAutoPage -> onStopAutoPage?.invoke() ?: stopAutoPage()
            is ReadBookEffect.TextActionAloudSelect -> {
                activity.lifecycleScope.launch { refs?.readView?.aloudStartSelect() }
            }

            is ReadBookEffect.TextActionSpeak -> speak(effect.text)
            is ReadBookEffect.NavigateToSearchResult -> {
                onNavigateToSearchResult?.invoke(effect.result, viewModel.searchResultIndex)
            }

            is ReadBookEffect.ExitSearch -> {
                if (viewModel.uiState.value.isShowingSearchResult) {
                    viewModel.onIntent(ReadBookIntent.SetShowingSearchResult(false))
                    ReadBook.clearSearchResult()
                    refs?.readView?.cancelSelect(true)
                }
            }

            is ReadBookEffect.ShowPayDialog -> onShowPayDialog?.invoke()
            is ReadBookEffect.SyncBookProgress -> {
                onSyncBookProgress?.invoke(effect.book)
            }

            is ReadBookEffect.ShowConfirmSkipToChapter -> onShowConfirmSkipToChapter?.invoke()
            is ReadBookEffect.SelectSpeakEngine -> onSelectSpeakEngine?.invoke()
            is ReadBookEffect.OpenPreDownloadNumPicker -> onOpenPreDownloadNumPicker?.invoke()
            is ReadBookEffect.OpenCacheCleanTimePicker -> onOpenCacheCleanTimePicker?.invoke()
            is ReadBookEffect.ShowLogin -> onShowLogin?.invoke()
            is ReadBookEffect.ShowStackTrace -> onShowStackTrace?.invoke(effect.text)
            is ReadBookEffect.ToggleDayNight -> onToggleDayNight?.invoke()
            is ReadBookEffect.DownloadChapters -> {
                onDownloadChapters?.invoke(effect.start, effect.end)
            }
        }
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
                            ReadBook.durChapterPos = line.chapterPosition
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
                            ReadBook.durChapterPos = line.chapterPosition
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
            onScreenOffTimerStart?.invoke()
        }
    }

    private fun stopAutoPage() {
        if (isAutoPage) {
            refs?.readView?.autoPager?.stop()
            viewModel.onIntent(ReadBookIntent.DismissSheet)
            onScreenOffTimerStart?.invoke()
        }
    }

    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
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
        }
        return false
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (volumeKeyPage(PageDirection.NONE, false)) {
                    return true
                }
            }
        }
        return false
    }

    fun mouseWheelPage(direction: PageDirection) {
        if (menuLayoutIsVisible || !AppConfig.mouseWheelPage) {
            return
        }
        keyPageDebounce(direction, mouseWheel = true, longPress = false)
    }

    private fun volumeKeyPage(direction: PageDirection, longPress: Boolean): Boolean {
        if (!AppConfig.volumeKeyPage) {
            return false
        }
        if (!AppConfig.volumeKeyPageOnPlay && BaseReadAloudService.isPlay()) {
            return false
        }
        handleKeyPage(direction, longPress)
        return true
    }

    fun handleKeyPage(direction: PageDirection, longPress: Boolean = false) {
        if (AppConfig.keyPageOnLongPress || direction == PageDirection.NONE) {
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
        val keepLightPrefer = viewModel.readPreferences.value.keepLight.toIntOrNull() ?: 0
        screenTimeOut = keepLightPrefer * 1000L
        screenOffTimerStartInternal()
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

    private fun startBackupJob() {
        backupJob?.cancel()
        backupJob = activity.lifecycleScope.launch(IO) {
            delay(300000)
            ReadBook.book?.let {
                viewModel.uploadBookProgress(it)
                ensureActive()
                Backup.autoBack(activity)
            }
        }
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
        when (AppConfig.screenOrientation) {
            "0" -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            "1" -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "2" -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            "3" -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
            "4" -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        }
    }
}
