package io.legado.app.ui.book.read

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.viewModelScope
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.constant.ReadMenuBlurMode
import io.legado.app.constant.Status
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookProgress
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.repository.ReadAloudSettingsRepository
import io.legado.app.data.repository.ReadBookStyleConfigRepository
import io.legado.app.data.repository.ReadPreferences
import io.legado.app.data.repository.ReadSettingsRepository
import io.legado.app.domain.model.ReadingProgress
import io.legado.app.domain.usecase.GetReadingProgressUseCase
import io.legado.app.domain.usecase.UploadReadingProgressUseCase
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.isEpub
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isLocalModified
import io.legado.app.help.book.isLocalTxt
import io.legado.app.help.book.isMobi
import io.legado.app.help.book.removeType
import io.legado.app.help.book.simulatedTotalChapterNum
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.ui.book.read.config.HighlightRuleStore
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.source.getSourceType
import io.legado.app.model.ImageProvider
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.model.SourceCallBack
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setChapter
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.book.read.page.provider.TextChapterLayout
import splitties.init.appCtx
import io.legado.app.ui.book.searchContent.SearchResult
import io.legado.app.ui.config.otherConfig.OtherConfig
import io.legado.app.utils.ImageSaveUtils
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.hexString
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isTrue
import io.legado.app.utils.mapParallelSafe
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.toStringArray
import io.legado.app.utils.toastOnUi
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import kotlin.coroutines.coroutineContext

/**
 * 阅读界面 ViewModel — MVI/UDF 架构
 *
 * 实现 ReadBook.CallBack，桥接 ReadBook 单例回调到 StateFlow/Effect。
 * 保留 BaseViewModel 的 execute {} 模式用于后台任务。
 */
class ReadBookViewModel(
    application: Application,
    private val getReadingProgressUseCase: GetReadingProgressUseCase,
    private val uploadReadingProgressUseCase: UploadReadingProgressUseCase,
    val translateChapterUseCase: io.legado.app.domain.usecase.TranslateChapterUseCase,
    private val readSettingsRepository: ReadSettingsRepository,
    private val readBookStyleConfigRepository: ReadBookStyleConfigRepository,
    private val readAloudSettingsRepository: ReadAloudSettingsRepository
) : BaseViewModel(application), ReadBook.CallBack {

    // --- MVI State ---

    private val _uiState = MutableStateFlow(ReadBookUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ReadBookEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private val _readPreferences = MutableStateFlow(ReadPreferences())
    val readPreferences = _readPreferences.asStateFlow()

    private var changeSourceCoroutine: Coroutine<*>? = null
    private var pendingBooksDirReloadChapterList: Boolean = false

    val isInitFinish: Boolean get() = _uiState.value.isInitFinish

    fun setAutoPage(active: Boolean) {
        _uiState.update { it.copy(isAutoPage = active) }
    }

    init {
        AppConfig.detectClickArea()
        ReadBook.register(this)
        refreshButtonConfigs()
        collectReadPreferences()
        collectReadAloudPreferences()
        collectEventBus()
    }

    // --- MVI Intent Dispatcher ---

    fun onIntent(intent: ReadBookIntent) {
        when (intent) {
            is ReadBookIntent.InitData -> {
                justInitData = true
                initData(intent.intent)
            }
            is ReadBookIntent.InitReadBookConfig -> initReadBookConfig(intent.intent)
            is ReadBookIntent.NextPage -> ReadBook.moveToNextPage()
            is ReadBookIntent.PrevPage -> ReadBook.moveToPrevPage()
            is ReadBookIntent.NextChapter -> ReadBook.moveToNextChapter(upContent = true)
            is ReadBookIntent.PrevChapter -> ReadBook.moveToPrevChapter(upContent = true)
            is ReadBookIntent.OpenChapter -> openChapter(intent.index, intent.pos)
            is ReadBookIntent.SkipToPage -> ReadBook.skipToPage(intent.pageIndex)
            is ReadBookIntent.ToggleMenu -> _uiState.update {
                if (it.menuVisible) {
                    readBookStyleConfigRepository.save()
                    it.copy(menuState = ReadBookMenuState())
                } else {
                    it.copy(menuState = ReadBookMenuState(visible = true))
                }
            }

            is ReadBookIntent.ShowMenu -> _uiState.update {
                it.copy(menuState = ReadBookMenuState(visible = true))
            }

            is ReadBookIntent.HideMenu -> _uiState.update {
                readBookStyleConfigRepository.save()
                it.copy(menuState = ReadBookMenuState())
            }

            is ReadBookIntent.OpenReadMenuRoute -> _uiState.update {
                val currentStack = it.menuState.routeStack
                val nextStack = if (currentStack.lastOrNull() == intent.route) {
                    currentStack
                } else {
                    (currentStack + intent.route).toImmutableList()
                }
                it.copy(
                    menuState = it.menuState.copy(
                        visible = true,
                        routeStack = nextStack,
                    ),
                )
            }

            is ReadBookIntent.ReadMenuBack -> _uiState.update {
                if (it.menuState.canNavigateBack) {
                    readBookStyleConfigRepository.save()
                    val nextStack = it.menuState.routeStack.dropLast(1).toImmutableList()
                    it.copy(menuState = it.menuState.copy(routeStack = nextStack))
                } else {
                    readBookStyleConfigRepository.save()
                    it.copy(menuState = ReadBookMenuState())
                }
            }

            is ReadBookIntent.OpenSearch -> {
                _uiState.update { it.copy(searchContentQuery = intent.word ?: "") }
                ReadBook.book?.bookUrl?.let { bookUrl ->
                    _effects.tryEmit(ReadBookEffect.OpenSearchActivity(intent.word, bookUrl))
                }
            }

            is ReadBookIntent.ExitSearch -> exitSearch()
            is ReadBookIntent.ShowSearchMenu -> _uiState.update { it.copy(searchMenuVisible = true) }
            is ReadBookIntent.HideSearchMenu -> _uiState.update { it.copy(searchMenuVisible = false) }
            is ReadBookIntent.SetSearchResults -> {
                _uiState.update {
                    it.copy(
                        searchResultList = intent.results.toImmutableList(),
                        searchResultIndex = intent.index,
                        isShowingSearchResult = true,
                        searchContentQuery = intent.query ?: it.searchContentQuery,
                    )
                }
            }

            is ReadBookIntent.SetSearchResultIndex -> {
                _uiState.update { it.copy(searchResultIndex = intent.index) }
            }

            is ReadBookIntent.SetShowingSearchResult -> {
                _uiState.update { it.copy(isShowingSearchResult = intent.value) }
            }

            is ReadBookIntent.NavigateToSearchResult -> {
                _uiState.update { it.copy(searchResultIndex = intent.index) }
                _effects.tryEmit(ReadBookEffect.NavigateToSearchResult(intent.result))
            }

            is ReadBookIntent.ToggleReadAloud -> {
                if (!BaseReadAloudService.isRun) {
                    openReadMenuRoute(ReadBookMenuRoute.ReadAloud)
                }
                _effects.tryEmit(ReadBookEffect.ToggleReadAloud)
            }

            is ReadBookIntent.ToggleAutoPage -> _effects.tryEmit(ReadBookEffect.ToggleAutoPage)
            is ReadBookIntent.StopAutoPage -> _effects.tryEmit(ReadBookEffect.StopAutoPage)
            is ReadBookIntent.RefreshCurrentChapter -> refreshCurrentChapter()
            is ReadBookIntent.RefreshAllChapters -> refreshAllChapters()
            is ReadBookIntent.RefreshContentAfter -> refreshContentAfter()
            is ReadBookIntent.ChangeReplaceRule -> changeReplaceRule(intent.enabled)
            is ReadBookIntent.ToggleTranslation -> toggleTranslation()
            is ReadBookIntent.ChangeSource -> changeTo(intent.book, intent.toc)
            is ReadBookIntent.AddSourceAsNewBook -> addToBookshelf(intent.book, intent.toc)
            is ReadBookIntent.OpenChapterResult -> openChapter(intent.index, intent.chapterPos)
            is ReadBookIntent.SourceEditResult -> upBookSource()
            is ReadBookIntent.ReplaceRuleResult -> replaceRuleChanged()
            is ReadBookIntent.BookInfoResult -> {
                if (intent.bookDeleted) {
                    _effects.tryEmit(ReadBookEffect.Finish)
                } else {
                    ReadBook.loadOrUpContent()
                }
            }
            is ReadBookIntent.FontFolderSelected -> {
                setFontFolder(intent.uri.toString())
                _uiState.update { it.copy(activeSheet = null) }
                _uiState.update { it.copy(activeSheet = ReadBookSheet.FontSelect) }
            }
            is ReadBookIntent.SureNewProgress -> ReadBook.setProgress(intent.progress)
            is ReadBookIntent.SureSyncProgress -> ReadBook.setProgress(intent.progress)
            is ReadBookIntent.AddBookmark -> handleAddBookmark()
            is ReadBookIntent.SaveBookmark -> saveBookmark(intent.bookmark)
            is ReadBookIntent.DeleteBookmark -> deleteBookmark(intent.bookmark)
            is ReadBookIntent.CancelSelect -> _effects.tryEmit(ReadBookEffect.CancelSelect)
            is ReadBookIntent.UpSystemUiVisibility -> _effects.tryEmit(ReadBookEffect.UpSystemUiVisibility)
            is ReadBookIntent.UpContent -> ReadBook.loadOrUpContent()
            is ReadBookIntent.SetBrightness -> _effects.tryEmit(ReadBookEffect.SetBrightness(intent.value))
            is ReadBookIntent.ToggleBrightnessAuto -> _effects.tryEmit(ReadBookEffect.ToggleBrightnessAuto)
            is ReadBookIntent.SeekToChapter -> {
                ReadBook.saveCurrentBookProgress()
                openChapter(intent.index)
            }

            is ReadBookIntent.ShowSheet -> {
                if (intent.sheet is ReadBookSheet.Bookmark) {
                    // Bookmark is shown as a menu route, not a sheet
                    openReadMenuRoute(ReadBookMenuRoute.Bookmark(intent.sheet.bookmark))
                } else {
                    _uiState.update { it.copy(activeSheet = intent.sheet) }
                }
            }
            is ReadBookIntent.DismissSheet -> _uiState.update {
                if (it.activeSheet is ReadBookSheet.ContentEdit) {
                    it.copy(
                        activeSheet = null,
                        contentEditText = "",
                        contentEditTitle = "",
                        contentEditLoading = false,
                        contentEditSaveToSource = false,
                    )
                } else {
                    it.copy(activeSheet = null)
                }
            }
            is ReadBookIntent.SetActiveSheet -> _uiState.update {
                it.copy(activeSheet = intent.sheet)
            }
            is ReadBookIntent.ShowDialog -> _uiState.update { it.copy(activeDialog = intent.dialog) }
            is ReadBookIntent.DismissDialog -> _uiState.update { it.copy(activeDialog = null) }
            is ReadBookIntent.ShowLogin -> {
                ReadBook.bookSource?.bookSourceUrl?.let { sourceUrl ->
                    _effects.tryEmit(ReadBookEffect.ShowLogin(sourceUrl))
                }
            }
            is ReadBookIntent.PayAction -> showPayDialog()
            is ReadBookIntent.ConfirmPayAction -> confirmPayAction()
            is ReadBookIntent.DisableSource -> disableSource()
            is ReadBookIntent.OpenSourceEditByUrl -> {
                _effects.tryEmit(ReadBookEffect.OpenSourceEdit(intent.sourceUrl))
            }
            is ReadBookIntent.OpenSourceEdit -> {
                ReadBook.bookSource?.let { src ->
                    _effects.tryEmit(ReadBookEffect.OpenSourceEdit(src.bookSourceUrl))
                }
            }
            is ReadBookIntent.OpenBookInfo -> {
                ReadBook.book?.let { book ->
                    _effects.tryEmit(ReadBookEffect.OpenBookInfo(book.name, book.author, book.bookUrl))
                }
            }
            is ReadBookIntent.OpenChapterList -> {
                ReadBook.book?.bookUrl?.let { bookUrl ->
                    _effects.tryEmit(ReadBookEffect.OpenChapterList(bookUrl))
                }
            }
            is ReadBookIntent.LoadContentEdit -> loadContentEdit()
            is ReadBookIntent.SaveContentEdit -> saveContentEdit(intent.content, intent.saveToSource)
            is ReadBookIntent.ResetContentEdit -> resetContentEdit()
            is ReadBookIntent.SetContentEditText -> {
                _uiState.update { it.copy(contentEditText = intent.text) }
            }
            is ReadBookIntent.SetContentEditSaveToSource -> {
                _uiState.update { it.copy(contentEditSaveToSource = intent.value) }
            }
            is ReadBookIntent.RefreshImage -> refreshImage(intent.src)
            is ReadBookIntent.SaveImage -> saveImage(intent.src)
            is ReadBookIntent.ReverseContent -> reverseContent()
            is ReadBookIntent.ReverseRemoveSameTitle -> reverseRemoveSameTitle()
            is ReadBookIntent.RetranslateCurrentChapter -> retranslateCurrentChapter()
            // Menu actions
            is ReadBookIntent.MenuUpdateToc -> {
                ReadBook.book?.let { book ->
                    if (book.isEpub) {
                        io.legado.app.help.book.BookHelp.clearCache(book)
                        io.legado.app.model.localBook.EpubFile.clear()
                    }
                    if (book.isMobi) {
                        io.legado.app.model.localBook.MobiFile.clear()
                    }
                    loadChapterList(book)
                }
            }

            is ReadBookIntent.MenuCoverProgress -> {
                ReadBook.book?.let {
                    ReadBook.uploadProgress(true) { context.toastOnUi(R.string.upload_book_success) }
                }
            }

            is ReadBookIntent.MenuSameTitleRemoved -> {
                ReadBook.book?.let {
                    val contentProcessor = ContentProcessor.get(it)
                    val textChapter = ReadBook.curTextChapter
                    if (textChapter != null
                        && !textChapter.sameTitleRemoved
                        && !contentProcessor.removeSameTitleCache.contains(
                            textChapter.chapter.getFileName("nr")
                        )
                    ) {
                        context.toastOnUi("未找到可移除的重复标题")
                    }
                }
                reverseRemoveSameTitle()
            }

            is ReadBookIntent.MenuImageStyle -> {
                ReadBook.book?.setImageStyle(intent.style)
                if (intent.style == Book.imgStyleSingle) {
                    ReadBook.book?.setPageAnim(0)
                    _effects.tryEmit(ReadBookEffect.MenuImageStyleChanged(intent.style))
                }
                ReadBook.loadContent(false)
            }

            is ReadBookIntent.MenuGetProgress -> {
                ReadBook.book?.let { book ->
                    _effects.tryEmit(ReadBookEffect.SyncBookProgress(book))
                }
            }

            is ReadBookIntent.MenuChangeSource -> handleChangeSource()
            is ReadBookIntent.MenuBookChangeSource -> {
                _uiState.update { it.copy(activeSheet = ReadBookSheet.ChangeBookSource) }
            }
            is ReadBookIntent.MenuChapterChangeSource -> handleChapterChangeSource()
            is ReadBookIntent.MenuSettingReplace -> _effects.tryEmit(ReadBookEffect.MenuSettingReplace)
            is ReadBookIntent.MenuTocRegex -> {
                _effects.tryEmit(ReadBookEffect.MenuTocRegex(ReadBook.book?.tocUrl))
            }
            is ReadBookIntent.TocRegexResult -> {
                ReadBook.book?.let {
                    it.tocUrl = intent.tocRegex
                    loadChapterList(it)
                }
            }
            is ReadBookIntent.MenuRefreshDur -> {
                ReadBook.book?.let { book ->
                    if (ReadBook.bookSource == null) {
                        _effects.tryEmit(ReadBookEffect.UpContent(0, true))
                    } else {
                        ReadBook.curTextChapter = null
                        _effects.tryEmit(ReadBookEffect.UpContent(0, true))
                        refreshContentDur(book)
                    }
                }
            }

            is ReadBookIntent.MenuRefreshAfter -> {
                ReadBook.book?.let { book ->
                    if (ReadBook.bookSource == null) {
                        _effects.tryEmit(ReadBookEffect.UpContent(0, true))
                    } else {
                        ReadBook.clearTextChapter()
                        _effects.tryEmit(ReadBookEffect.UpContent(0, true))
                        refreshContentAfter(book)
                    }
                }
            }

            is ReadBookIntent.MenuRefreshAll -> {
                ReadBook.book?.let { book ->
                    if (ReadBook.bookSource == null) {
                        _effects.tryEmit(ReadBookEffect.UpContent(0, true))
                    } else {
                        ReadBook.clearTextChapter()
                        _effects.tryEmit(ReadBookEffect.UpContent(0, true))
                        refreshContentAll(book)
                    }
                }
            }

            is ReadBookIntent.MenuEnableReplace -> {
                ReadBook.book?.let {
                    it.setUseReplaceRule(!it.getUseReplaceRule())
                    ReadBook.saveRead()
                    replaceRuleChanged()
                }
            }

            is ReadBookIntent.MenuReSegment -> {
                ReadBook.book?.let {
                    it.setReSegment(!it.getReSegment())
                    ReadBook.loadContent(false)
                }
            }

            is ReadBookIntent.MenuDelRubyTag -> {
                ReadBook.book?.let {
                    if (it.getDelTag(Book.rubyTag)) it.removeDelTag(Book.rubyTag)
                    else it.addDelTag(Book.rubyTag)
                    refreshContentAll(it)
                }
            }

            is ReadBookIntent.MenuDelHTag -> {
                ReadBook.book?.let {
                    if (it.getDelTag(Book.hTag)) it.removeDelTag(Book.hTag)
                    else it.addDelTag(Book.hTag)
                    refreshContentAll(it)
                }
            }

            is ReadBookIntent.MenuReverseContent -> {
                ReadBook.book?.let { reverseContent(it) }
            }

            is ReadBookIntent.RemoveFromBookshelf -> removeFromBookshelf()
            is ReadBookIntent.OnConfigUpdated -> {
                _uiState.update { it.copy(styleConfig = buildStyleConfig()) }
                _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(intent.actions))
            }

            is ReadBookIntent.UpdateConfig -> {
                handleConfigUpdate(intent.update)
            }
            is ReadBookIntent.SaveMenuCustomIcon -> saveMenuCustomIcon(intent.id, intent.uri)
            is ReadBookIntent.SaveTitleBarCustomIcon -> saveTitleBarCustomIcon(intent.id, intent.uri)
            is ReadBookIntent.OpenMenuCustomIconPicker -> {
                _effects.tryEmit(ReadBookEffect.OpenMenuCustomIconPicker(intent.id))
            }
            is ReadBookIntent.OpenTitleBarCustomIconPicker -> {
                _effects.tryEmit(ReadBookEffect.OpenTitleBarCustomIconPicker(intent.id))
            }
            is ReadBookIntent.SaveMenuButtonConfig -> saveMenuButtonConfig(intent.items)
            is ReadBookIntent.SaveTitleBarButtonConfig -> saveTitleBarButtonConfig(intent.items)

            is ReadBookIntent.KeepLightChanged -> _effects.tryEmit(ReadBookEffect.UpScreenTimeOut)
            is ReadBookIntent.TextSelectAbleChanged -> _effects.tryEmit(
                ReadBookEffect.UpTextSelectAble(
                    intent.enabled
                )
            )

            is ReadBookIntent.MediaButtonPressed -> {
                if (intent.play) {
                    _effects.tryEmit(ReadBookEffect.ToggleReadAloud)
                } else {
                    ReadBook.readAloud(!BaseReadAloudService.pause)
                }
            }

            is ReadBookIntent.TtsProgress -> _effects.tryEmit(ReadBookEffect.UpTtsAloudSpan(intent.chapterStart))
            is ReadBookIntent.ReadAloudAction -> {
                openReadMenuRoute(ReadBookMenuRoute.ReadAloud)
            }

            is ReadBookIntent.ShowReadAloudConfig -> {
                _uiState.update { it.copy(activeSheet = ReadBookSheet.ReadAloudConfig) }
            }

            is ReadBookIntent.SelectSpeakEngine -> {
                showSpeakEngineConfig()
            }

            is ReadBookIntent.OpenPreDownloadNumPicker -> {
                _uiState.update {
                    it.copy(
                        preDownloadNum = AppConfig.preDownloadNum,
                        activeSheet = ReadBookSheet.PreDownloadConfig,
                    )
                }
            }

            is ReadBookIntent.OpenCacheCleanTimePicker -> {
                _uiState.update {
                    it.copy(
                        audioCacheCleanTime = AppConfig.audioCacheCleanTimeOrgin,
                        activeSheet = ReadBookSheet.AudioCacheCleanConfig,
                    )
                }
            }

            is ReadBookIntent.ApplySpeakEngine -> {
                AppConfig.ttsEngine = intent.value
                _uiState.update {
                    it.copy(
                        selectedTtsEngine = intent.value,
                        activeSheet = ReadBookSheet.ReadAloudConfig,
                    )
                }
            }

            is ReadBookIntent.ApplyPreDownloadNum -> {
                AppConfig.preDownloadNum = intent.value
                _uiState.update {
                    it.copy(
                        preDownloadNum = intent.value,
                        activeSheet = ReadBookSheet.ReadAloudConfig,
                    )
                }
            }

            is ReadBookIntent.ApplyAudioCacheCleanTime -> {
                context.putPrefInt(PreferKey.audioCacheCleanTime, intent.value)
                _uiState.update {
                    it.copy(
                        audioCacheCleanTime = intent.value,
                        activeSheet = ReadBookSheet.ReadAloudConfig,
                    )
                }
            }
            is ReadBookIntent.SetReadAloudIgnoreAudioFocus -> {
                viewModelScope.launch { readAloudSettingsRepository.setIgnoreAudioFocus(intent.value) }
            }
            is ReadBookIntent.SetReadAloudPauseOnPhoneCall -> {
                viewModelScope.launch { readAloudSettingsRepository.setPauseReadAloudWhilePhoneCalls(intent.value) }
            }
            is ReadBookIntent.SetReadAloudWakeLock -> {
                viewModelScope.launch { readAloudSettingsRepository.setReadAloudWakeLock(intent.value) }
            }
            is ReadBookIntent.SetReadAloudMediaButtonPerNext -> {
                viewModelScope.launch { readAloudSettingsRepository.setMediaButtonPerNext(intent.value) }
            }
            is ReadBookIntent.SetReadAloudByPage -> {
                viewModelScope.launch { readAloudSettingsRepository.setReadAloudByPage(intent.value) }
                if (intent.value) postEvent(EventBus.MEDIA_BUTTON, false)
            }
            is ReadBookIntent.SetReadAloudSystemMediaCompat -> {
                viewModelScope.launch { readAloudSettingsRepository.setSystemMediaControlCompatibilityChange(intent.value) }
            }
            is ReadBookIntent.SetReadAloudStreamAudio -> {
                viewModelScope.launch { readAloudSettingsRepository.setStreamReadAloudAudio(intent.value) }
                if (intent.value) postEvent(EventBus.MEDIA_BUTTON, false)
            }
            is ReadBookIntent.ReadAloudPrevParagraph -> ReadAloud.prevParagraph(context)
            is ReadBookIntent.ReadAloudTogglePause -> toggleReadAloudPause()
            is ReadBookIntent.ReadAloudStop -> {
                ReadAloud.stop(context)
                _uiState.update { it.copy(isReadAloudRunning = false, isReadAloudPaused = false) }
            }
            is ReadBookIntent.ReadAloudNextParagraph -> ReadAloud.nextParagraph(context)
            is ReadBookIntent.ReadAloudPrevChapter -> ReadBook.moveToPrevChapter(
                upContent = true,
                toLast = false
            )
            is ReadBookIntent.ReadAloudNextChapter -> ReadBook.moveToNextChapter(true)
            is ReadBookIntent.SetReadAloudTtsTimer -> setReadAloudTtsTimer(intent.value)
            is ReadBookIntent.SetReadAloudTtsFollowSys -> {
                viewModelScope.launch { readAloudSettingsRepository.setTtsFollowSys(intent.value) }
                _uiState.update { it.copy(readAloudTtsFollowSys = intent.value) }
            }
            is ReadBookIntent.SetReadAloudTtsSpeechRate -> setReadAloudTtsSpeechRate(intent.value)
            is ReadBookIntent.OpenSystemTtsSettings -> {
                _effects.tryEmit(ReadBookEffect.OpenSystemTtsSettings)
            }
            is ReadBookIntent.ClearTtsCache -> {
                io.legado.app.utils.TTSCacheUtils.clearTtsCache()
                _effects.tryEmit(ReadBookEffect.TtsCacheCleared(context.getString(R.string.clear_cache_success)))
            }

            is ReadBookIntent.SelectFont -> selectFont(intent.path)
            is ReadBookIntent.SelectSystemTypeface -> {
                AppConfig.systemTypefaces = intent.index
                ReadBookConfig.textFont = ""
                _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(
                    setOf(ConfigUpdateAction.UpdateStyle, ConfigUpdateAction.ReloadContent)
                ))
            }

            is ReadBookIntent.ColorSelected -> colorSelected(intent.dialogId, intent.color)
            is ReadBookIntent.ShowPageAnimConfig -> {
                _uiState.update { it.copy(activeSheet = ReadBookSheet.PageAnim) }
            }

            is ReadBookIntent.OpenReplaceEditor -> _effects.tryEmit(
                ReadBookEffect.OpenReplaceEditor(
                    intent.id,
                    intent.pattern
                )
            )

            is ReadBookIntent.ReplaceRuleChanged -> replaceRuleChanged()
            is ReadBookIntent.OpenFontFolderPicker -> _effects.tryEmit(ReadBookEffect.OpenFontFolderPicker)
            is ReadBookIntent.OpenReadStyleImagePicker -> {
                _effects.tryEmit(ReadBookEffect.OpenReadStyleImagePicker)
            }
            is ReadBookIntent.OpenReadStyleImagePickerForMode -> {
                _effects.tryEmit(ReadBookEffect.OpenReadStyleImagePickerForMode(intent.isNight))
            }
            is ReadBookIntent.OpenReadStyleImport -> {
                _effects.tryEmit(ReadBookEffect.OpenReadStyleImport)
            }
            is ReadBookIntent.OpenReadStyleExport -> {
                _effects.tryEmit(ReadBookEffect.OpenReadStyleExport)
            }
            is ReadBookIntent.ReadStyleImageSelected -> {
                applyReadStyleBackgroundImage(intent.uri)
            }
            is ReadBookIntent.ReadStyleImageSelectedForMode -> {
                applyReadStyleBackgroundImageForMode(intent.uri, intent.isNight)
            }
            is ReadBookIntent.ReadStyleConfigImportSelected -> {
                importReadStyleConfig(intent.uri)
            }
            is ReadBookIntent.ReadStyleConfigExportSelected -> {
                exportReadStyleConfig(intent.uri)
            }
            is ReadBookIntent.SaveReadStyleConfig -> {
                readBookStyleConfigRepository.save()
            }
            is ReadBookIntent.AddReadStyleConfig -> {
                val newIndex = readBookStyleConfigRepository.addStyle()
                handleConfigUpdate(ConfigUpdate.StyleSelect(newIndex))
            }
            is ReadBookIntent.DeleteCurrentReadStyleConfig -> {
                if (readBookStyleConfigRepository.deleteCurrentStyle()) {
                    _uiState.update {
                        it.copy(
                            styleConfig = buildStyleConfig(),
                            activeSheet = null,
                        )
                    }
                    _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(
                        setOf(ConfigUpdateAction.UpdateBackground, ConfigUpdateAction.UpdateStyle, ConfigUpdateAction.ReloadContent)
                    ))
                }
            }
            is ReadBookIntent.OpenBgTextConfig -> {
                ReadBookConfig.styleSelect = intent.index
                viewModelScope.launch {
                    readSettingsRepository.setStyleSelect(ReadBookConfig.isComic, intent.index)
                }
                _uiState.update { it.copy(activeSheet = ReadBookSheet.BgTextConfig) }
            }

            is ReadBookIntent.ToggleDayNight -> toggleDayNight()
            // Text action menu
            is ReadBookIntent.TextActionAloud -> {
                when (AppConfig.contentSelectSpeakMod) {
                    1 -> _effects.tryEmit(ReadBookEffect.TextActionAloudSelect)
                    else -> _effects.tryEmit(ReadBookEffect.TextActionSpeak(intent.text))
                }
            }

            is ReadBookIntent.TextActionBookmark -> {
                val book = ReadBook.book
                val page = ReadBook.curTextChapter?.getPage(ReadBook.durPageIndex)
                if (book != null && page != null) {
                    val bookmark = book.createBookMark().apply {
                        chapterIndex = ReadBook.durChapterIndex
                        chapterPos = ReadBook.durChapterPos
                        chapterName = page.title
                        bookText = page.text.replace(Regex("[袮꧁]"), "").trim()
                    }
                    _uiState.update {
                        it.copy(
                            menuState = ReadBookMenuState(
                                visible = true,
                                routeStack = kotlinx.collections.immutable.persistentListOf(
                                    ReadBookMenuRoute.Main,
                                    ReadBookMenuRoute.Bookmark(bookmark),
                                ),
                            ),
                        )
                    }
                } else {
                    context.toastOnUi(R.string.create_bookmark_error)
                }
            }

            is ReadBookIntent.TextActionReplace -> {
                _effects.tryEmit(
                    ReadBookEffect.TextActionReplace(
                        text = intent.text,
                        bookName = ReadBook.book?.name,
                        bookSourceUrl = ReadBook.bookSource?.bookSourceUrl,
                    )
                )
            }

            is ReadBookIntent.TextActionSearchContent -> {
                _uiState.update { it.copy(searchContentQuery = intent.text) }
                ReadBook.book?.bookUrl?.let { bookUrl ->
                    _effects.tryEmit(ReadBookEffect.OpenSearchActivity(intent.text, bookUrl))
                }
            }

            is ReadBookIntent.TextActionDict -> {
                _uiState.update { it.copy(activeSheet = ReadBookSheet.Dict(intent.text)) }
            }

            is ReadBookIntent.ApplySimulatedReading -> {
                ReadBook.clearTextChapter()
                execute {
                    ReadBook.book?.let { initBook(it) }
                }
            }

            is ReadBookIntent.PageAnimChanged -> {
                _effects.tryEmit(ReadBookEffect.PageAnimChanged)
            }

            is ReadBookIntent.DownloadChapters -> {
                _effects.tryEmit(ReadBookEffect.DownloadChapters(intent.start, intent.end))
            }

            is ReadBookIntent.SaveChapterContent -> {
                ReadBook.book?.let {
                    saveContent(it, intent.content, intent.chapterIndex)
                }
            }

            is ReadBookIntent.OnResume -> handleOnResume()
            is ReadBookIntent.OnPause -> handleOnPause()
            is ReadBookIntent.OnDispose -> handleOnDispose()
            is ReadBookIntent.CloseReadBook -> _effects.tryEmit(ReadBookEffect.Finish)
            is ReadBookIntent.OpenBooksDirPicker -> requestBooksDirPicker(reloadChapterList = false)
            is ReadBookIntent.BooksDirSelected -> onBooksDirSelected(intent.uri)
        }
    }

    // --- Lifecycle handlers (migrated from ReadBookController) ---

    private fun handleOnResume() {
        // Read time tracking
        ReadBook.readStartTime = System.currentTimeMillis()
        ReadBook.initReadTime()
        ReadBook.startAutoSaveSession()

        // Web book progress sync
        ReadBook.webBookProgress?.let {
            ReadBook.setProgress(it)
            ReadBook.webBookProgress = null
        }

        // View-layer operations via effects
        _effects.tryEmit(ReadBookEffect.UpSystemUiVisibility)
        _effects.tryEmit(ReadBookEffect.UpTime)
        _effects.tryEmit(ReadBookEffect.UpScreenTimeOut)

        // Activity-level operations
        _effects.tryEmit(ReadBookEffect.RegisterTimeBatteryReceiver)
        _effects.tryEmit(ReadBookEffect.RegisterNetworkListener)
    }

    private var justInitData = false

    private fun handleOnPause() {
        backupJob?.cancel()
        _effects.tryEmit(ReadBookEffect.StopAutoPage)

        // Read time tracking
        ReadBook.saveRead()
        ReadBook.stopAutoSaveSession()
        ReadBook.commitReadSession()
        ReadBook.cancelPreDownloadTask()

        // View-layer
        _effects.tryEmit(ReadBookEffect.UpSystemUiVisibility)

        // Activity-level operations
        _effects.tryEmit(ReadBookEffect.UnregisterTimeBatteryReceiver)
        _effects.tryEmit(ReadBookEffect.UnregisterNetworkListener)

        if (!BuildConfig.DEBUG) {
            if (AppConfig.syncBookProgressPlus) {
                ReadBook.syncProgress()
            } else {
                ReadBook.uploadProgress()
            }
            _effects.tryEmit(ReadBookEffect.BackupNow)
        }
        justInitData = false
    }

    private fun handleOnDispose() {
        // TTS and view cleanup — bridge handles via clearTts()
        backupJob?.cancel()
        ReadBook.cancelPreDownloadTask()
    }

    private fun showSpeakEngineConfig() {
        execute {
            buildList {
                add(ReadBookTtsEngineItem(context.getString(R.string.system_tts), null))
                appDb.httpTTSDao.all.forEach { httpTts ->
                    add(ReadBookTtsEngineItem(httpTts.name, httpTts.id.toString()))
                }
            }
        }.onSuccess { items ->
            _uiState.update {
                it.copy(
                    ttsEngineItems = items.toImmutableList(),
                    selectedTtsEngine = AppConfig.ttsEngine,
                    activeSheet = ReadBookSheet.SpeakEngineConfig,
                )
            }
        }
    }

    /**
     * Called from the network changed listener (registered by route).
     */
    fun onNetworkChanged() {
        if (AppConfig.syncBookProgressPlus && NetworkUtils.isAvailable() && !justInitData) {
            ReadBook.syncProgress(newProgressAction = { progress ->
                sureNewProgress(progress)
            })
        }
    }

    /**
     * Start the auto-backup job (called on page change).
     */
    fun startBackupJob() {
        backupJob?.cancel()
        backupJob = viewModelScope.launch(IO) {
            delay(5 * 60 * 1000) // 5 minutes
            ReadBook.book?.let { book ->
                uploadBookProgress(book)
                coroutineContext.ensureActive()
                _effects.tryEmit(ReadBookEffect.BackupNow)
            }
        }
    }

    private var backupJob: Job? = null

    private fun handleChangeSource() {
        viewModelScope.launch {
            if (AppConfig.defaultSourceChangeAll) {
                _uiState.update { it.copy(activeSheet = ReadBookSheet.ChangeBookSource) }
            } else {
                val book = ReadBook.book ?: return@launch
                val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
                    ?: return@launch
                _uiState.update {
                    it.copy(
                        activeSheet = ReadBookSheet.ChangeChapterSource(
                            chapter.index, chapter.title
                        )
                    )
                }
            }
        }
    }

    private fun handleChapterChangeSource() {
        viewModelScope.launch {
            val book = ReadBook.book ?: return@launch
            val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
                ?: return@launch
            _uiState.update {
                it.copy(
                    activeSheet = ReadBookSheet.ChangeChapterSource(
                        chapter.index, chapter.title
                    )
                )
            }
        }
    }

    private fun handleAddBookmark() {
        viewModelScope.launch(IO) {
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
            withContext(Main) {
                openReadMenuRoute(ReadBookMenuRoute.Bookmark(bookmark))
            }
        }
    }

    // --- ReadBook.CallBack Implementation ---

    override fun upMenuView() {
        _uiState.update { syncFromReadBook(it) }
    }

    override fun loadChapterList(book: Book) {
        ReadBook.upMsg(context.getString(R.string.toc_updateing))
        doLoadChapterList(book)
    }

    override fun upContent(
        relativePosition: Int,
        resetPageOffset: Boolean,
        success: (() -> Unit)?
    ) {
        _uiState.update { syncFromReadBook(it) }
        _effects.tryEmit(
            ReadBookEffect.UpContent(relativePosition, resetPageOffset)
        )
        success?.invoke()
    }

    override suspend fun upContentAwait(
        relativePosition: Int,
        resetPageOffset: Boolean,
        success: (() -> Unit)?
    ) {
        withContext(Main.immediate) {
            _uiState.update { syncFromReadBook(it) }
            _effects.tryEmit(
                ReadBookEffect.UpContent(relativePosition, resetPageOffset)
            )
            success?.invoke()
        }
    }

    override fun pageChanged() {
        _uiState.update { syncFromReadBook(it) }
        _effects.tryEmit(ReadBookEffect.PageChanged)
    }

    override fun contentLoadFinish() {
        _uiState.update { syncFromReadBook(it).copy(isInitFinish = true) }
        _effects.tryEmit(ReadBookEffect.ContentLoadFinish)
    }

    override fun upPageAnim(upRecorder: Boolean) {
        _effects.tryEmit(ReadBookEffect.UpPageAnim(upRecorder))
    }

    override fun notifyBookChanged() {
        _uiState.update { syncFromReadBook(it) }
        if (!ReadBook.inBookshelf) {
            removeFromBookshelf { _effects.tryEmit(ReadBookEffect.Finish) }
        }
    }

    override fun sureNewProgress(progress: BookProgress) {
        _uiState.update {
            it.copy(activeDialog = ReadBookDialog.ConfirmRestoreProgress(progress))
        }
    }

    override fun cancelSelect() {
        _effects.tryEmit(ReadBookEffect.CancelSelect)
    }

    // LayoutProgressListener
    override fun onLayoutPageCompleted(index: Int, page: TextPage) {
        _uiState.update { syncFromReadBook(it) }
        _effects.tryEmit(ReadBookEffect.LayoutPageCompleted(index, page))
    }

    override fun onLayoutCompleted() {
        _uiState.update { syncFromReadBook(it) }
    }

    override fun onLayoutException(e: Throwable) {
        // no-op: ReadView handles this internally
    }

    // --- EventBus Bridge ---

    private inline fun <reified T> eventFlow(tag: String) = callbackFlow {
        val obs = androidx.lifecycle.Observer<T> { trySend(it) }
        com.jeremyliao.liveeventbus.LiveEventBus.get<T>(tag).observeForever(obs)
        awaitClose {
            com.jeremyliao.liveeventbus.LiveEventBus.get<T>(tag).removeObserver(obs)
        }
    }

    private inline fun <reified T> eventFlowSticky(tag: String) = callbackFlow {
        val obs = androidx.lifecycle.Observer<T> { trySend(it) }
        com.jeremyliao.liveeventbus.LiveEventBus.get<T>(tag).observeStickyForever(obs)
        awaitClose {
            com.jeremyliao.liveeventbus.LiveEventBus.get<T>(tag).removeObserver(obs)
        }
    }

    private fun collectEventBus() {
        viewModelScope.launch {
            eventFlow<String>(EventBus.TIME_CHANGED).collect { time ->
                _uiState.update { it.copy(time = time) }
                _effects.tryEmit(ReadBookEffect.UpTime)
            }
        }
        viewModelScope.launch {
            eventFlow<Int>(EventBus.BATTERY_CHANGED).collect { level ->
                _uiState.update { it.copy(battery = level) }
                _effects.tryEmit(ReadBookEffect.UpBattery(level))
            }
        }
        viewModelScope.launch {
            eventFlow<ArrayList<Int>>(EventBus.UP_CONFIG).collect { values ->
                _uiState.update {
                    it.copy(styleConfig = buildStyleConfig())
                }
                // Convert legacy integer codes to ConfigUpdateAction set
                val actions = values.mapNotNull { code ->
                    when (code) {
                        0 -> ConfigUpdateAction.UpdateSystemUi
                        1 -> ConfigUpdateAction.UpdateBackground
                        2 -> ConfigUpdateAction.UpdateStyle
                        3 -> ConfigUpdateAction.UpdateBackgroundAlpha
                        4 -> ConfigUpdateAction.UpdatePageSlopSquare
                        5 -> ConfigUpdateAction.ReloadContent
                        6 -> ConfigUpdateAction.UpdateContent
                        8 -> ConfigUpdateAction.UpdateChapterStyle
                        9 -> ConfigUpdateAction.InvalidateTextPage
                        10 -> ConfigUpdateAction.UpdateLayout
                        11 -> ConfigUpdateAction.SubmitRenderTask
                        else -> null
                    }
                }.toSet()
                if (actions.isNotEmpty()) {
                    _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(actions))
                }
            }
        }
        viewModelScope.launch {
            eventFlow<Int>(EventBus.ALOUD_STATE).collect { state ->
                _uiState.update {
                    it.copy(
                        isReadAloudRunning = state != Status.STOP,
                        isReadAloudPaused = state == Status.PAUSE,
                    )
                }
                if (state == Status.STOP || state == Status.PAUSE) {
                    _effects.tryEmit(ReadBookEffect.UpAloudState)
                }
            }
        }
        viewModelScope.launch {
            @Suppress("UNCHECKED_CAST")
            eventFlow<List<SearchResult>>(EventBus.SEARCH_RESULT).collect { results ->
                _uiState.update { it.copy(searchResultList = results.toImmutableList()) }
            }
        }
        viewModelScope.launch {
            eventFlow<Boolean>(EventBus.UP_SEEK_BAR).collect {
                _uiState.update { syncFromReadBook(it) }
                _effects.tryEmit(ReadBookEffect.UpSeekBar)
            }
        }
        viewModelScope.launch {
            eventFlow<Boolean>(EventBus.REFRESH_BOOK_CONTENT).collect {
                _effects.tryEmit(ReadBookEffect.RefreshBookContent)
            }
        }
        viewModelScope.launch {
            eventFlow<Boolean>(EventBus.MEDIA_BUTTON).collect { play ->
                if (play) {
                    _effects.tryEmit(ReadBookEffect.ToggleReadAloud)
                } else {
                    ReadBook.readAloud(!BaseReadAloudService.pause)
                }
            }
        }
        viewModelScope.launch {
            eventFlowSticky<Int>(EventBus.TTS_PROGRESS).collect { chapterStart ->
                _effects.tryEmit(ReadBookEffect.UpTtsAloudSpan(chapterStart))
            }
        }
    }

    private fun collectReadPreferences() {
        viewModelScope.launch {
            var previous: ReadPreferences? = null
            readSettingsRepository.preferences.collect { preferences ->
                val old = previous
                previous = preferences
                ReadBookConfig.syncPreferences(preferences)
                AppConfig.syncReadPreferences(preferences)
                _readPreferences.value = preferences
                if (!preferences.hasMenuClickArea()) {
                    AppConfig.detectClickArea()
                    readSettingsRepository.setClickAction(PreferKey.clickActionMC, 0)
                }
                if (old != null && old.keepLight != preferences.keepLight) {
                    _effects.tryEmit(ReadBookEffect.UpScreenTimeOut)
                }
            }
        }
    }

    private fun collectReadAloudPreferences() {
        viewModelScope.launch {
            readAloudSettingsRepository.preferences.collect { prefs ->
                _uiState.update {
                    it.copy(
                        readAloudIgnoreAudioFocus = prefs.ignoreAudioFocus,
                        readAloudPauseOnPhoneCall = prefs.pauseReadAloudWhilePhoneCalls,
                        readAloudWakeLock = prefs.readAloudWakeLock,
                        readAloudMediaButtonPerNext = prefs.mediaButtonPerNext,
                        readAloudByPage = prefs.readAloudByPage,
                        readAloudSystemMediaCompat = prefs.systemMediaControlCompatibilityChange,
                        readAloudStreamAudio = prefs.streamReadAloudAudio,
                        readAloudTtsFollowSys = prefs.ttsFollowSys,
                        readAloudTtsSpeechRate = prefs.ttsSpeechRate,
                        readAloudTtsTimer = if (BaseReadAloudService.timeMinute > 0) {
                            BaseReadAloudService.timeMinute
                        } else {
                            prefs.ttsTimer
                        },
                    )
                }
            }
        }
    }

    private fun toggleReadAloudPause() {
        if (_uiState.value.isReadAloudPaused) {
            ReadAloud.resume(context)
            _uiState.update { it.copy(isReadAloudPaused = false) }
        } else {
            ReadAloud.pause(context)
            _uiState.update { it.copy(isReadAloudPaused = true) }
        }
    }

    private fun setReadAloudTtsTimer(value: Int) {
        viewModelScope.launch {
            readAloudSettingsRepository.setTtsTimer(value)
        }
        ReadAloud.setTimer(context, value)
        _uiState.update { it.copy(readAloudTtsTimer = value) }
    }

    private fun setReadAloudTtsSpeechRate(value: Int) {
        viewModelScope.launch {
            readAloudSettingsRepository.setTtsSpeechRate(value)
            ReadAloud.upTtsSpeechRate(context)
        }
        _uiState.update { it.copy(readAloudTtsSpeechRate = value) }
    }

    fun setFontFolder(value: String) {
        viewModelScope.launch {
            readSettingsRepository.setFontFolder(value)
        }
    }

    private fun ReadPreferences.hasMenuClickArea(): Boolean {
        return clickActionTL * clickActionTC * clickActionTR *
                clickActionML * clickActionMC * clickActionMR *
                clickActionBL * clickActionBC * clickActionBR == 0
    }

    // --- State Sync ---

    private fun buildStyleConfig(): ReadBookStyleConfig {
        val config = ReadBookConfig
        val dur = config.durConfig
        return ReadBookStyleConfig(
            styleSelect = config.styleSelect,
            styleName = dur.name.ifBlank { "文字" },
            bgAlpha = config.bgAlpha.toFloat(),
            bgType = dur.bgType,
            bgStr = dur.bgStr,
            darkStatusIcon = dur.getDarkStatusIcon(),
            bgTypeNight = dur.bgTypeNight,
            bgStrNight = dur.bgStrNight,
            darkStatusIconNight = dur.getDarkStatusIconNight(),
            bgTypeEInk = dur.bgTypeEInk,
            bgStrEInk = dur.bgStrEInk,
            darkStatusIconEInk = dur.getDarkStatusIconEInk(),
            textSize = config.textSize,
            textColor = dur.getTextColor(),
            textColorNight = dur.getTextColorNight(),
            textColorEInk = dur.getTextColorEInk(),
            pageAnim = dur.getPageAnim(),
            pageAnimEInk = dur.getPageAnimEInk(),
            shareLayout = config.shareLayout,
            configCount = config.configList.size,
        )
    }

    private fun syncFromReadBook(current: ReadBookUiState): ReadBookUiState {
        val book = ReadBook.book
        val textChapter = ReadBook.curTextChapter
        return current.copy(
            book = book,
            bookSource = ReadBook.bookSource,
            bookName = book?.name ?: "",
            chapterName = textChapter?.title ?: "",
            chapterUrl = textChapter?.chapter?.url ?: "",
            chapterSize = ReadBook.chapterSize,
            durChapterIndex = ReadBook.durChapterIndex,
            durChapterPos = ReadBook.durChapterPos,
            durPageIndex = ReadBook.durPageIndex,
            isLocalBook = ReadBook.isLocalBook,
            msg = ReadBook.msg,
            curTextChapter = textChapter,
            seekProgress = calculateSeekProgress(),
            seekMax = calculateSeekMax(),
            replaceRuleEnabled = book?.getUseReplaceRule() ?: false,
            effectiveReplaceCount = textChapter?.effectiveReplaceRules?.size ?: 0,
            translationMode = book?.getTranslationMode() ?: false,
            isLocalTxt = book?.isLocalTxt == true,
            isEpub = book?.isEpub == true,
            useReplaceRule = book?.getUseReplaceRule() ?: false,
            reSegment = book?.getReSegment() ?: false,
            delRubyTag = book?.getDelTag(Book.rubyTag) ?: false,
            delHTag = book?.getDelTag(Book.hTag) ?: false,
            sameTitleRemoved = textChapter?.sameTitleRemoved ?: false,
            isReadingProgressSyncConfigured = isReadingProgressSyncConfigured(),
            styleConfig = buildStyleConfig(),
            menuConfig = ReadMenuConfig(
                titleBarIconPosition = ReadBookConfig.titleBarIconPosition,
                showTitleBarIcons = ReadBookConfig.showTitleBarIcons,
                readMenuFloatingBottomBar = ReadBookConfig.readMenuFloatingBottomBar,
                readMenuBottomCornerRadius = ReadBookConfig.readMenuBottomCornerRadius,
                readMenuIconItemsPerRow = ReadBookConfig.readMenuIconItemsPerRow,
                readMenuIconRowCount = ReadBookConfig.readMenuIconRowCount,
                readMenuBorderWidth = ReadBookConfig.readMenuBorderWidth,
                readMenuBorderColor = ReadBookConfig.readMenuBorderColor,
                readMenuBorderColorNight = ReadBookConfig.readMenuBorderColorNight,
                readMenuBlurAlpha = ReadBookConfig.readMenuBlurAlpha,
                readMenuBlurRadius = ReadBookConfig.readMenuBlurRadius,
                readMenuLensRadius = ReadBookConfig.readMenuLensRadius,
                readMenuTopBarBlurMode = ReadBookConfig.readMenuTopBarBlurMode,
                readMenuBottomBarBlurMode = ReadBookConfig.readMenuBottomBarBlurMode,
                readMenuTopBarLiquidGlassButtons = ReadBookConfig.readMenuTopBarLiquidGlassButtons,
                readMenuBottomBarLiquidGlassButtons = ReadBookConfig.readMenuBottomBarLiquidGlassButtons,
                readMenuTopBarBlurStyle = ReadBookConfig.readMenuTopBarBlurStyle,
                readMenuBottomBarBlurStyle = ReadBookConfig.readMenuBottomBarBlurStyle,
                readMenuIconStyle = ReadBookConfig.readMenuIconStyle,
                readMenuIconShowText = ReadBookConfig.readMenuIconShowText,
                titleBarCustomIcons = ReadBookConfig.titleBarCustomIcons.toImmutableMap(),
                readMenuCustomIcons = ReadBookConfig.readMenuCustomIcons.toImmutableMap(),
                titleBarButtons = current.menuConfig.titleBarButtons,
                bottomBarButtons = current.menuConfig.bottomBarButtons,
            ),
        )
    }

    private fun refreshButtonConfigs() {
        val titleBarButtons = loadButtonConfig(TITLE_BAR_ICON_PREFS, TITLE_BAR_ICON_KEY)
        val bottomBarButtons = loadButtonConfig(TOOL_BUTTON_PREFS, TOOL_BUTTON_KEY)
        _uiState.update {
            it.copy(
                menuConfig = it.menuConfig.copy(
                    titleBarButtons = titleBarButtons.toImmutableList(),
                    bottomBarButtons = bottomBarButtons.toImmutableList(),
                ),
            )
        }
    }

    private fun saveTitleBarButtonConfig(items: List<ReadBookButtonConfigItem>) {
        val normalized = normalizeButtonConfig(items)
        saveButtonConfig(TITLE_BAR_ICON_PREFS, TITLE_BAR_ICON_KEY, normalized)
        _uiState.update {
            it.copy(
                menuConfig = it.menuConfig.copy(
                    titleBarButtons = normalized.toImmutableList(),
                ),
            )
        }
    }

    private fun saveMenuButtonConfig(items: List<ReadBookButtonConfigItem>) {
        val normalized = normalizeButtonConfig(items)
        saveButtonConfig(TOOL_BUTTON_PREFS, TOOL_BUTTON_KEY, normalized)
        _uiState.update {
            it.copy(
                menuConfig = it.menuConfig.copy(
                    bottomBarButtons = normalized.toImmutableList(),
                ),
            )
        }
    }

    private fun loadButtonConfig(
        preferenceName: String,
        key: String,
    ): List<ReadBookButtonConfigItem> {
        val prefs = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
        val raw = prefs.getString(key, null)
            ?.split(";")
            ?.mapNotNull { token ->
                val parts = token.split(",")
                val id = parts.getOrNull(0)?.takeIf { it in ReadBookButtonIds }
                val enabled = parts.getOrNull(1)?.toBooleanStrictOrNull()
                if (id != null && enabled != null) {
                    ReadBookButtonConfigItem(id, enabled)
                } else {
                    null
                }
            }
            ?: emptyList()

        return if (raw.isEmpty()) {
            ReadBookButtonIds.mapIndexed { index, id ->
                ReadBookButtonConfigItem(id, index < DEFAULT_ENABLED_BUTTON_COUNT)
            }
        } else {
            normalizeButtonConfig(raw)
        }
    }

    private fun saveButtonConfig(
        preferenceName: String,
        key: String,
        items: List<ReadBookButtonConfigItem>,
    ) {
        val value = items.joinToString(";") { "${it.id},${it.enabled}" }
        context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
            .edit()
            .putString(key, value)
            .apply()
    }

    private fun normalizeButtonConfig(
        items: List<ReadBookButtonConfigItem>,
    ): List<ReadBookButtonConfigItem> {
        val seen = mutableSetOf<String>()
        val normalized = items.mapNotNull { item ->
            val id = item.id
            if (id in ReadBookButtonIds && seen.add(id)) {
                ReadBookButtonConfigItem(id, item.enabled)
            } else {
                null
            }
        }.toMutableList()
        ReadBookButtonIds.forEach { id ->
            if (seen.add(id)) {
                normalized.add(ReadBookButtonConfigItem(id, true))
            }
        }
        return normalized
    }

    private fun calculateSeekProgress(): Int {
        return when (AppConfig.progressBarBehavior) {
            "page" -> ReadBook.durPageIndex
            else -> ReadBook.durChapterIndex
        }
    }

    private fun calculateSeekMax(): Int {
        return when (AppConfig.progressBarBehavior) {
            "page" -> (ReadBook.curTextChapter?.pages?.size ?: 1) - 1
            else -> ReadBook.chapterSize - 1
        }
    }

    // --- Business Logic (migrated from Activity / kept from old ViewModel) ---

    fun initReadBookConfig(intent: Intent) {
        val bookUrl = intent.getStringExtra("bookUrl")
        val book = when {
            bookUrl.isNullOrEmpty() -> appDb.bookDao.lastReadBook
            else -> appDb.bookDao.getBook(bookUrl)
        } ?: return
        ReadBook.upReadBookConfig(book)
    }

    fun initData(intent: Intent, success: (() -> Unit)? = null) {
        execute {
            ReadBook.inBookshelf = intent.getBooleanExtra("inBookshelf", true)
            ReadBook.chapterChanged = intent.getBooleanExtra("chapterChanged", false)
            val bookUrl = intent.getStringExtra("bookUrl")
            val book = when {
                bookUrl.isNullOrEmpty() -> appDb.bookDao.lastReadBook
                else -> appDb.bookDao.getBook(bookUrl)
            } ?: ReadBook.book
            when {
                book != null -> initBook(book)
                else -> {
                    ReadBook.upMsg(context.getString(R.string.no_book))
                    AppLog.put("未找到书籍\nbookUrl:$bookUrl")
                }
            }
            val index = intent.getIntExtra("index", -1)
            val chapterPos = intent.getIntExtra("chapterPos", -1)
            if (index >= 0 && chapterPos >= 0) {
                ReadBook.saveCurrentBookProgress()
                openChapter(index, chapterPos)
            }
        }.onSuccess {
            success?.invoke()
        }.onError {
            val msg = "初始化数据失败\n${it.localizedMessage}"
            ReadBook.upMsg(msg)
            AppLog.put(msg, it)
        }.onFinally {
            ReadBook.saveRead()
        }
    }

    private suspend fun initBook(book: Book) {
        val isSameBook = ReadBook.book?.bookUrl == book.bookUrl
        if (isSameBook) {
            ReadBook.upData(book)
        } else {
            ReadBook.resetData(book)
        }
        _uiState.update { it.copy(isInitFinish = true) }
        if (!book.isLocal && book.tocUrl.isEmpty() && !loadBookInfo(book)) {
            return
        }
        if (book.isLocal && !checkLocalBookFileExist(book)) {
            return
        }
        if ((ReadBook.chapterSize == 0 || book.isLocalModified()) && !loadChapterListAwait(book)) {
            return
        }
        ReadBook.upMsg(null)

        if (!isSameBook) {
            ReadBook.loadContent(resetPageOffset = true) {
                ReadBook.bookSource?.let {
                    SourceCallBack.callBackBook(
                        SourceCallBack.START_READ,
                        it,
                        book,
                        ReadBook.curTextChapter?.chapter
                    )
                }
            }
        } else {
            ReadBook.loadOrUpContent {
                ReadBook.bookSource?.let {
                    SourceCallBack.callBackBook(
                        SourceCallBack.START_READ,
                        it,
                        book,
                        ReadBook.curTextChapter?.chapter
                    )
                }
            }
        }
        if (ReadBook.chapterChanged) {
            ReadBook.chapterChanged = false
        } else if (!(isSameBook && BaseReadAloudService.isRun) && ReadBook.inBookshelf) {
            if (AppConfig.syncBookProgressPlus) {
                ReadBook.syncProgress({ progress -> sureNewProgress(progress) })
            } else {
                syncBookProgress(book)
            }
        }
        if (!book.isLocal && ReadBook.bookSource == null) {
            autoChangeSource(book.name, book.author)
            return
        }
    }

    private fun checkLocalBookFileExist(book: Book): Boolean {
        try {
            LocalBook.getBookInputStream(book)
            return true
        } catch (e: Throwable) {
            ReadBook.upMsg("打开本地书籍出错: ${e.localizedMessage}")
            if (e is SecurityException || e is FileNotFoundException) {
                requestBooksDirPicker(reloadChapterList = false)
            }
            return false
        }
    }

    private suspend fun loadBookInfo(book: Book): Boolean {
        val source = ReadBook.bookSource ?: return true
        try {
            WebBook.getBookInfoAwait(source, book, canReName = false)
            return true
        } catch (e: Throwable) {
            coroutineContext.ensureActive()
            ReadBook.upMsg("详情页出错: ${e.localizedMessage}")
            return false
        }
    }

    private fun doLoadChapterList(book: Book) {
        execute {
            if (loadChapterListAwait(book)) {
                ReadBook.upMsg(null)
            }
        }
    }

    private suspend fun loadChapterListAwait(book: Book): Boolean {
        if (book.isLocal) {
            kotlin.runCatching {
                LocalBook.getChapterList(book).let {
                    appDb.bookChapterDao.delByBook(book.bookUrl)
                    appDb.bookChapterDao.insert(*it.toTypedArray())
                    appDb.bookDao.update(book)
                    ReadBook.onChapterListUpdated(book)
                }
                return true
            }.onFailure {
                when (it) {
                    is SecurityException, is FileNotFoundException -> {
                        requestBooksDirPicker(reloadChapterList = true)
                    }
                    else -> {
                        AppLog.put("LoadTocError:${it.localizedMessage}", it)
                        ReadBook.upMsg("LoadTocError:${it.localizedMessage}")
                    }
                }
                return false
            }
        } else {
            ReadBook.bookSource?.let {
                val oldBook = book.copy()
                WebBook.getChapterListAwait(it, book, true)
                    .onSuccess { cList ->
                        if (oldBook.bookUrl == book.bookUrl) {
                            appDb.bookDao.update(book)
                        } else {
                            appDb.bookDao.replace(oldBook, book)
                            BookHelp.updateCacheFolder(oldBook, book)
                        }
                        appDb.bookChapterDao.delByBook(oldBook.bookUrl)
                        appDb.bookChapterDao.insert(*cList.toTypedArray())
                        ReadBook.onChapterListUpdated(book)
                        return true
                    }.onFailure {
                        coroutineContext.ensureActive()
                        ReadBook.upMsg(context.getString(R.string.error_load_toc))
                        return false
                    }
            }
        }
        return true
    }

    fun syncBookProgress(
        book: Book,
        alertSync: ((progress: BookProgress) -> Unit)? = null
    ) {
        if (!AppConfig.syncBookProgress) return
        execute {
            getReadingProgressUseCase.execute(book.name, book.author)?.toBookProgress()
        }.onError {
            AppLog.put("拉取阅读进度失败《${book.name}》\n${it.localizedMessage}", it)
        }.onSuccess { progress ->
            progress ?: return@onSuccess
            if (progress.durChapterIndex < book.durChapterIndex ||
                (progress.durChapterIndex == book.durChapterIndex
                        && progress.durChapterPos < book.durChapterPos)
            ) {
                alertSync?.invoke(progress)
            } else if (progress.durChapterIndex < book.simulatedTotalChapterNum()) {
                ReadBook.setProgress(progress)
                AppLog.put("自动同步阅读进度成功《${book.name}》 ${progress.durChapterTitle}")
            }
        }
    }

    fun isReadingProgressSyncConfigured(): Boolean {
        return getReadingProgressUseCase.isConfigured
    }

    suspend fun uploadBookProgress(book: Book) {
        uploadReadingProgressUseCase.execute(book.toReadingProgress())?.let { uploadTime ->
            book.syncTime = uploadTime
            appDb.bookDao.update(book)
        }
    }

    private fun Book.toReadingProgress() = ReadingProgress(
        name = name,
        author = author,
        durChapterIndex = durChapterIndex,
        durChapterPos = durChapterPos,
        durChapterTime = durChapterTime,
        durChapterTitle = durChapterTitle
    )

    private fun ReadingProgress.toBookProgress() = BookProgress(
        name = name,
        author = author,
        durChapterIndex = durChapterIndex,
        durChapterPos = durChapterPos,
        durChapterTime = durChapterTime,
        durChapterTitle = durChapterTitle
    )

    fun changeTo(book: Book, toc: List<BookChapter>) {
        changeSourceCoroutine?.cancel()
        changeSourceCoroutine = execute {
            ReadBook.upMsg(context.getString(R.string.loading))
            ReadBook.book?.migrateTo(book, toc)
            book.removeType(BookType.updateError)
            ReadBook.book?.delete()
            appDb.bookDao.insert(book)
            appDb.bookChapterDao.insert(*toc.toTypedArray())
            ReadBook.resetData(book)
            ReadBook.upMsg(null)
            ReadBook.loadContent(resetPageOffset = true)
        }.onError {
            AppLog.put("换源失败\n$it", it, true)
            ReadBook.upMsg(null)
        }.onFinally {
            postEvent(EventBus.SOURCE_CHANGED, book.bookUrl)
        }
    }

    private fun autoChangeSource(name: String, author: String) {
        if (!AppConfig.autoChangeSource) return
        execute {
            val sources = appDb.bookSourceDao.allTextEnabledPart
            flow {
                for (source in sources) {
                    source.getBookSource()?.let {
                        emit(it)
                    }
                }
            }.onStart {
                ReadBook.upMsg(context.getString(R.string.source_auto_changing))
            }.mapParallelSafe(OtherConfig.threadCount) { source ->
                val book = WebBook.preciseSearchAwait(source, name, author).getOrThrow()
                if (book.tocUrl.isEmpty()) {
                    WebBook.getBookInfoAwait(source, book)
                }
                val toc = WebBook.getChapterListAwait(source, book).getOrThrow()
                val chapter = toc.getOrElse(book.durChapterIndex) {
                    toc.last()
                }
                val nextChapter = toc.getOrElse(chapter.index) {
                    toc.first()
                }
                WebBook.getContentAwait(
                    bookSource = source,
                    book = book,
                    bookChapter = chapter,
                    nextChapterUrl = nextChapter.url
                )
                book to toc
            }.take(1).onEach { (book, toc) ->
                changeTo(book, toc)
            }.onEmpty {
                throw NoStackTraceException("没有合适书源")
            }.onCompletion {
                ReadBook.upMsg(null)
            }.catch {
                AppLog.put("自动换源失败\n${it.localizedMessage}", it)
                context.toastOnUi("自动换源失败\n${it.localizedMessage}")
            }.collect()
        }
    }

    fun openChapter(index: Int, durChapterPos: Int = 0, success: (() -> Unit)? = null) {
        ReadBook.openChapter(index, durChapterPos, success = success)
    }

    fun removeFromBookshelf(success: (() -> Unit)? = null) {
        val book = ReadBook.book
        Coroutine.async {
            book?.delete()
        }.onSuccess {
            success?.invoke()
        }
    }

    fun upBookSource(success: (() -> Unit)? = null) {
        execute {
            ReadBook.book?.let { book ->
                ReadBook.bookSource = appDb.bookSourceDao.getBookSource(book.origin)
            }
        }.onSuccess {
            success?.invoke()
        }
    }

    private fun refreshCurrentChapter() {
        execute {
            ReadBook.book?.let { book ->
                appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
                    ?.let { chapter ->
                        BookHelp.delContent(book, chapter)
                        ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
                    }
            }
        }
    }

    // Backward-compatible alias for Activity
    fun refreshContentDur(book: Book) {
        refreshCurrentChapter()
    }

    private fun refreshContentAfter() {
        execute {
            ReadBook.book?.let { book ->
                appDb.bookChapterDao.getChapterList(
                    book.bookUrl,
                    ReadBook.durChapterIndex,
                    book.totalChapterNum
                ).forEach { chapter ->
                    BookHelp.delContent(book, chapter)
                }
                ReadBook.loadContent(false)
            }
        }
    }

    // Backward-compatible alias for Activity
    fun refreshContentAfter(book: Book) {
        refreshContentAfter()
    }

    private fun refreshAllChapters() {
        execute {
            ReadBook.book?.let { book ->
                BookHelp.clearCache(book)
                ReadBook.loadContent(false)
            }
        }
    }

    // Backward-compatible alias for Activity
    fun refreshContentAll(book: Book) {
        refreshAllChapters()
    }

    fun saveContent(book: Book, content: String, chapterIndex: Int = ReadBook.durChapterIndex) {
        execute {
            appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex)
                ?.let { chapter ->
                    BookHelp.saveText(book, chapter, content)
                    ReadBook.loadContent(chapterIndex, resetPageOffset = false)
                }
        }
    }

    private fun loadContentEdit() {
        _uiState.update { it.copy(contentEditLoading = true, contentEditText = "") }
        execute {
            val book = ReadBook.book ?: return@execute
            val chapter = appDb.bookChapterDao
                .getChapter(book.bookUrl, ReadBook.durChapterIndex)
                ?: return@execute
            val title = chapter.getDisplayTitle()
            val contentProcessor = ContentProcessor.get(book.name, book.origin)
            val rawContent = BookHelp.getContent(book, chapter) ?: return@execute
            val text = contentProcessor.getContent(book, chapter, rawContent, includeTitle = false)
                .toString()
            _uiState.update {
                it.copy(
                    contentEditText = text,
                    contentEditTitle = title,
                    contentEditIsLocalTxt = book.isLocalTxt,
                )
            }
        }.onFinally {
            _uiState.update { it.copy(contentEditLoading = false) }
        }
    }

    private fun saveContentEdit(content: String, saveToSource: Boolean) {
        execute {
            val book = ReadBook.book ?: return@execute
            val chapter = appDb.bookChapterDao
                .getChapter(book.bookUrl, ReadBook.durChapterIndex)
                ?: return@execute
            BookHelp.saveText(book, chapter, content, saveToSource)
            ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
        }
    }

    private fun resetContentEdit() {
        _uiState.update { it.copy(contentEditLoading = true) }
        execute {
            val book = ReadBook.book ?: return@execute
            val chapter = appDb.bookChapterDao
                .getChapter(book.bookUrl, ReadBook.durChapterIndex)
                ?: return@execute
            BookHelp.delContent(book, chapter)
            if (!book.isLocal) {
                ReadBook.bookSource?.let { bookSource ->
                    WebBook.getContentAwait(bookSource, book, chapter)
                }
            }
            val contentProcessor = ContentProcessor.get(book.name, book.origin)
            val rawContent = BookHelp.getContent(book, chapter)
            val text = if (rawContent != null) {
                contentProcessor.getContent(book, chapter, rawContent, includeTitle = false)
                    .toString()
            } else {
                ""
            }
            _uiState.update { it.copy(contentEditText = text, contentEditLoading = false) }
            ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
        }.onError {
            _uiState.update { it.copy(contentEditLoading = false) }
        }
    }

    fun reverseContent() {
        execute {
            val book = ReadBook.book ?: return@execute
            val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
                ?: return@execute
            val content = BookHelp.getContent(book, chapter) ?: return@execute
            val stringBuilder = StringBuilder()
            content.toStringArray().forEach {
                stringBuilder.insert(0, it)
            }
            BookHelp.saveText(book, chapter, stringBuilder.toString())
            ReadBook.loadContent(ReadBook.durChapterIndex, resetPageOffset = false)
        }
    }

    // Backward-compatible overload for Activity
    fun reverseContent(book: Book) {
        reverseContent()
    }

    fun searchResultPositions(
        textChapter: TextChapter,
        searchResult: SearchResult
    ): Array<Int> {
        val pages = textChapter.pages
        val content = textChapter.getContent()
        val query = _uiState.value.searchContentQuery
        val queryLength = query.length

        var count = 0
        var index = content.indexOf(query)
        while (count != searchResult.resultCountWithinChapter) {
            index = content.indexOf(query, index + queryLength)
            count += 1
        }
        val contentPosition = index
        var pageIndex = 0
        var length = pages[pageIndex].text.length
        while (length < contentPosition && pageIndex + 1 < pages.size) {
            pageIndex += 1
            length += pages[pageIndex].text.length
        }

        val currentPage = pages[pageIndex]
        val curTextLines = currentPage.lines
        var lineIndex = 0
        var curLine = curTextLines[lineIndex]
        length = length - currentPage.text.length + curLine.text.length
        if (curLine.isParagraphEnd) length++
        while (length <= contentPosition && lineIndex + 1 < curTextLines.size) {
            lineIndex += 1
            curLine = curTextLines[lineIndex]
            length += curLine.text.length
            if (curLine.isParagraphEnd) length++
        }

        val currentLine = currentPage.lines[lineIndex]
        var curLineLength = currentLine.text.length
        if (currentLine.isParagraphEnd) curLineLength++
        length -= curLineLength

        val charIndex = contentPosition - length
        var addLine = 0
        var charIndex2 = 0
        if ((charIndex + queryLength) > curLineLength) {
            addLine = 1
            charIndex2 = charIndex + queryLength - curLineLength - 1
        }
        if ((lineIndex + addLine + 1) > currentPage.lines.size) {
            addLine = -1
            charIndex2 = charIndex + queryLength - curLineLength - 1
        }
        return arrayOf(pageIndex, lineIndex, charIndex, addLine, charIndex2)
    }

    fun reverseRemoveSameTitle() {
        execute {
            val book = ReadBook.book ?: return@execute
            val textChapter = ReadBook.curTextChapter ?: return@execute
            BookHelp.setRemoveSameTitle(
                book, textChapter.chapter, !textChapter.sameTitleRemoved
            )
            ReadBook.loadContent(ReadBook.durChapterIndex)
        }
    }

    fun refreshImage(src: String) {
        execute {
            ReadBook.book?.let { book ->
                val vFile = BookHelp.getImage(book, src)
                ImageProvider.bitmapLruCache.remove(vFile.absolutePath)
                vFile.delete()
            }
        }.onFinally {
            ReadBook.loadContent(false)
        }
    }

    fun saveImage(src: String?) {
        src ?: return
        val book = ReadBook.book ?: return

        execute {
            val image = BookHelp.getImage(book, src)
            val byteArray = image.readBytes()
            val success = ImageSaveUtils.saveImageToGallery(
                context,
                byteArray,
                folderName = "Legado"
            )
            if (!success) throw NoStackTraceException("保存到相册失败")
        }.onError {
            context.toastOnUi("保存图片失败: ${it.localizedMessage}")
        }.onSuccess {
            context.toastOnUi("已保存到相册")
        }
    }

    fun replaceRuleChanged() {
        execute {
            ReadBook.book?.let {
                ContentProcessor.get(it.name, it.origin).upReplaceRules()
                ReadBook.loadContent(resetPageOffset = false)
            }
        }
    }

    private fun changeReplaceRule(enabled: Boolean) {
        ReadBook.book?.let {
            it.setUseReplaceRule(enabled)
            ReadBook.saveRead()
            replaceRuleChanged()
        }
    }

    private fun saveBookmark(bookmark: Bookmark) {
        viewModelScope.launch(IO) {
            appDb.bookmarkDao.insert(bookmark)
            _uiState.update {
                it.copy(
                    activeSheet = null,
                    menuState = ReadBookMenuState(),
                )
            }
        }
    }

    private fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch(IO) {
            appDb.bookmarkDao.delete(bookmark)
            _uiState.update {
                it.copy(
                    activeSheet = null,
                    menuState = ReadBookMenuState(),
                )
            }
        }
    }

    private fun openReadMenuRoute(route: ReadBookMenuRoute) {
        _uiState.update {
            it.copy(
                menuState = ReadBookMenuState(
                    visible = true,
                    routeStack = kotlinx.collections.immutable.persistentListOf(
                        ReadBookMenuRoute.Main,
                        route,
                    ),
                ),
            )
        }
    }

    @Suppress("LongMethod")
    private fun handleConfigUpdate(update: ConfigUpdate) {
        when (update) {
            // --- Text style ---
            is ConfigUpdate.TextSize -> ReadBookConfig.textSize = update.value
            is ConfigUpdate.LetterSpacing -> ReadBookConfig.letterSpacing = update.value
            is ConfigUpdate.LineSpacing -> ReadBookConfig.lineSpacingExtra = update.value
            is ConfigUpdate.ParagraphSpacing -> ReadBookConfig.paragraphSpacing = update.value
            is ConfigUpdate.ParagraphIndent -> ReadBookConfig.paragraphIndent = update.value
            is ConfigUpdate.TextItalic -> ReadBookConfig.textItalic = update.value
            is ConfigUpdate.TextBold -> ReadBookConfig.textBold = update.value
            is ConfigUpdate.TextColor -> ReadBookConfig.durConfig.setCurTextColor(update.color)
            is ConfigUpdate.TextAccentColor -> ReadBookConfig.durConfig.setCurTextAccentColor(update.color)

            // --- Title style ---
            is ConfigUpdate.TitleMode -> ReadBookConfig.titleMode = update.value
            is ConfigUpdate.TitleBold -> ReadBookConfig.titleBold = update.value
            is ConfigUpdate.TitleSegScaling -> ReadBookConfig.titleSegScaling = update.value
            is ConfigUpdate.TitleLineSpacingExtra -> ReadBookConfig.titleLineSpacingExtra = update.value
            is ConfigUpdate.TitleLineSpacingSub -> ReadBookConfig.titleLineSpacingSub = update.value
            is ConfigUpdate.TitleSize -> ReadBookConfig.titleSize = update.value
            is ConfigUpdate.TitleTopSpacing -> ReadBookConfig.titleTopSpacing = update.value
            is ConfigUpdate.TitleBottomSpacing -> ReadBookConfig.titleBottomSpacing = update.value
            is ConfigUpdate.TitleColor -> ReadBookConfig.titleColor = update.color

            // --- Header / footer tips ---
            is ConfigUpdate.HeaderMode -> ReadBookConfig.headerMode = update.value
            is ConfigUpdate.FooterMode -> ReadBookConfig.footerMode = update.value
            is ConfigUpdate.TipHeaderLeft -> ReadBookConfig.tipHeaderLeft = update.value
            is ConfigUpdate.TipHeaderMiddle -> ReadBookConfig.tipHeaderMiddle = update.value
            is ConfigUpdate.TipHeaderRight -> ReadBookConfig.tipHeaderRight = update.value
            is ConfigUpdate.TipFooterLeft -> ReadBookConfig.tipFooterLeft = update.value
            is ConfigUpdate.TipFooterMiddle -> ReadBookConfig.tipFooterMiddle = update.value
            is ConfigUpdate.TipFooterRight -> ReadBookConfig.tipFooterRight = update.value
            is ConfigUpdate.HeaderFontSize -> ReadBookConfig.headerFontSize = update.value
            is ConfigUpdate.TipHeaderColor -> ReadBookConfig.tipHeaderColor = update.color
            is ConfigUpdate.TipFooterColor -> ReadBookConfig.tipFooterColor = update.color
            is ConfigUpdate.TipDividerColor -> ReadBookConfig.tipDividerColor = update.color

            // --- Layout / style ---
            is ConfigUpdate.StyleSelect -> {
                ReadBookConfig.styleSelect = update.index
                viewModelScope.launch {
                    readSettingsRepository.setStyleSelect(ReadBookConfig.isComic, update.index)
                }
            }
            is ConfigUpdate.ShareLayout -> {
                ReadBookConfig.shareLayout = update.value
                viewModelScope.launch {
                    readSettingsRepository.setShareLayout(update.value)
                }
            }
            is ConfigUpdate.PageAnim -> ReadBookConfig.pageAnim = update.value

            // --- Menu appearance ---
            is ConfigUpdate.MenuBgColor -> {
                ReadBookConfig.readMenuBgColor = update.color
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuBgColor(update.color)
                }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }
            is ConfigUpdate.MenuAccentColor -> {
                ReadBookConfig.readMenuAccentColor = update.color
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuAccentColor(update.color)
                }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }
            is ConfigUpdate.MenuContainerColor -> {
                ReadBookConfig.readMenuContainerColor = update.color
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuContainerColor(update.color)
                }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }
            is ConfigUpdate.MenuBgColorNight -> {
                ReadBookConfig.readMenuBgColorNight = update.color
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuBgColorNight(update.color)
                }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }
            is ConfigUpdate.MenuAccentColorNight -> {
                ReadBookConfig.readMenuAccentColorNight = update.color
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuAccentColorNight(update.color)
                }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }
            is ConfigUpdate.MenuContainerColorNight -> {
                ReadBookConfig.readMenuContainerColorNight = update.color
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuContainerColorNight(update.color)
                }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }
            is ConfigUpdate.MenuColorMode -> {
                val value = update.value.coerceIn(0, 1)
                ReadBookConfig.readMenuColorMode = value
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuColorMode(value)
                }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }
            is ConfigUpdate.ReadBarStyle -> {
                val value = update.value.coerceIn(0, 2)
                AppConfig.updateReadBarStyleCache(value)
                viewModelScope.launch {
                    readSettingsRepository.setReadBarStyle(value)
                }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }

            // --- Menu bar border ---
            is ConfigUpdate.BorderWidth -> {
                ReadBookConfig.readMenuBorderWidth = update.value
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuBorderWidth(update.value)
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(readMenuBorderWidth = update.value)) }
            }
            is ConfigUpdate.BorderColor -> {
                ReadBookConfig.readMenuBorderColor = update.color
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuBorderColor(update.color)
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(readMenuBorderColor = update.color)) }
            }
            is ConfigUpdate.BorderColorNight -> {
                ReadBookConfig.readMenuBorderColorNight = update.color
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuBorderColorNight(update.color)
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(readMenuBorderColorNight = update.color)) }
            }

            // --- Shadow ---
            is ConfigUpdate.TextShadow -> ReadBookConfig.textShadow = update.value
            is ConfigUpdate.ShadowRadius -> ReadBookConfig.shadowRadius = update.value
            is ConfigUpdate.ShadowDx -> ReadBookConfig.shadowDx = update.value
            is ConfigUpdate.ShadowDy -> ReadBookConfig.shadowDy = update.value
            is ConfigUpdate.ShadowColor -> ReadBookConfig.durConfig.setCurShadColor(update.color)

            // --- Underline ---
            is ConfigUpdate.Underline -> ReadBookConfig.underline = update.value
            is ConfigUpdate.DottedLine -> ReadBookConfig.dottedLine = update.value
            is ConfigUpdate.UnderlineExtend -> ReadBookConfig.underlineExtend = update.value
            is ConfigUpdate.UnderlineHeight -> ReadBookConfig.underlineHeight = update.value
            is ConfigUpdate.UnderlinePadding -> ReadBookConfig.underlinePadding = update.value
            is ConfigUpdate.DottedBase -> ReadBookConfig.durConfig.dottedBase = update.value
            is ConfigUpdate.DottedRatio -> ReadBookConfig.durConfig.dottedRatio = update.value
            is ConfigUpdate.UnderlineColor -> ReadBookConfig.durConfig.setUnderlineColor(update.color)

            // --- Body padding ---
            is ConfigUpdate.PaddingTop -> ReadBookConfig.paddingTop = update.value
            is ConfigUpdate.PaddingBottom -> ReadBookConfig.paddingBottom = update.value
            is ConfigUpdate.PaddingLeft -> ReadBookConfig.paddingLeft = update.value
            is ConfigUpdate.PaddingRight -> ReadBookConfig.paddingRight = update.value

            // --- Header padding ---
            is ConfigUpdate.HeaderPaddingTop -> ReadBookConfig.headerPaddingTop = update.value
            is ConfigUpdate.HeaderPaddingBottom -> ReadBookConfig.headerPaddingBottom = update.value
            is ConfigUpdate.HeaderPaddingLeft -> ReadBookConfig.headerPaddingLeft = update.value
            is ConfigUpdate.HeaderPaddingRight -> ReadBookConfig.headerPaddingRight = update.value
            is ConfigUpdate.ShowHeaderLine -> ReadBookConfig.showHeaderLine = update.value

            // --- Footer padding ---
            is ConfigUpdate.FooterPaddingTop -> ReadBookConfig.footerPaddingTop = update.value
            is ConfigUpdate.FooterPaddingBottom -> ReadBookConfig.footerPaddingBottom = update.value
            is ConfigUpdate.FooterPaddingLeft -> ReadBookConfig.footerPaddingLeft = update.value
            is ConfigUpdate.FooterPaddingRight -> ReadBookConfig.footerPaddingRight = update.value
            is ConfigUpdate.ShowFooterLine -> ReadBookConfig.showFooterLine = update.value

            // --- Background / display ---
            is ConfigUpdate.BgStr -> ReadBookConfig.durConfig.bgStr = update.value
            is ConfigUpdate.BgStrNight -> ReadBookConfig.durConfig.bgStrNight = update.value
            is ConfigUpdate.BgStrEInk -> ReadBookConfig.durConfig.bgStrEInk = update.value
            is ConfigUpdate.BgType -> ReadBookConfig.durConfig.bgType = update.value
            is ConfigUpdate.BgTypeNight -> ReadBookConfig.durConfig.bgTypeNight = update.value
            is ConfigUpdate.BgTypeEInk -> ReadBookConfig.durConfig.bgTypeEInk = update.value
            is ConfigUpdate.BgAlpha -> ReadBookConfig.bgAlpha = update.value
            is ConfigUpdate.StatusIconDark -> ReadBookConfig.durConfig.setCurStatusIconDark(update.value)
            is ConfigUpdate.MenuIconShowText -> {
                ReadBookConfig.readMenuIconShowText = update.value
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuIconShowText(update.value)
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(readMenuIconShowText = update.value)) }
            }
            is ConfigUpdate.MenuIconStyle -> {
                val value = update.value.coerceIn(0, 2)
                ReadBookConfig.readMenuIconStyle = value
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuIconStyle(value)
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(readMenuIconStyle = value)) }
            }
            is ConfigUpdate.MenuIconItemsPerRow -> {
                val value = update.value.coerceIn(2, 8)
                ReadBookConfig.readMenuIconItemsPerRow = value
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuIconItemsPerRow(value)
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(readMenuIconItemsPerRow = value)) }
            }
            is ConfigUpdate.MenuIconRowCount -> {
                val value = update.value.coerceIn(1, 2)
                ReadBookConfig.readMenuIconRowCount = value
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuIconRowCount(value)
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(readMenuIconRowCount = value)) }
            }
            is ConfigUpdate.MenuBottomCornerRadius -> {
                val value = update.value.coerceIn(0, 32)
                ReadBookConfig.readMenuBottomCornerRadius = value
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuBottomCornerRadius(value)
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(readMenuBottomCornerRadius = value)) }
            }
            is ConfigUpdate.FloatingBottomBar -> {
                ReadBookConfig.readMenuFloatingBottomBar = update.value
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuFloatingBottomBar(update.value)
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(readMenuFloatingBottomBar = update.value)) }
            }
            is ConfigUpdate.MenuTopBarBlurMode -> {
                val mode = update.value.coerceIn(0, 2).let {
                    if (it == ReadMenuBlurMode.LiquidGlass) ReadMenuBlurMode.Haze else it
                }
                ReadBookConfig.readMenuTopBarBlurMode = mode
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuTopBarBlurMode(mode)
                }
                _uiState.update {
                    it.copy(menuConfig = it.menuConfig.copy(readMenuTopBarBlurMode = mode))
                }
            }

            is ConfigUpdate.MenuBottomBarBlurMode -> {
                val mode = update.value.coerceIn(0, 2)
                ReadBookConfig.readMenuBottomBarBlurMode = mode
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuBottomBarBlurMode(mode)
                }
                _uiState.update {
                    it.copy(menuConfig = it.menuConfig.copy(readMenuBottomBarBlurMode = mode))
                }
            }

            is ConfigUpdate.MenuTopBarLiquidGlassButtons -> {
                ReadBookConfig.readMenuTopBarLiquidGlassButtons = update.value
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuTopBarLiquidGlassButtons(update.value)
                }
                _uiState.update {
                    it.copy(menuConfig = it.menuConfig.copy(readMenuTopBarLiquidGlassButtons = update.value))
                }
            }

            is ConfigUpdate.MenuBottomBarLiquidGlassButtons -> {
                ReadBookConfig.readMenuBottomBarLiquidGlassButtons = update.value
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuBottomBarLiquidGlassButtons(update.value)
                }
                _uiState.update {
                    it.copy(menuConfig = it.menuConfig.copy(readMenuBottomBarLiquidGlassButtons = update.value))
                }
            }

            is ConfigUpdate.MenuTopBarBlurSelection -> {
                val mode = update.mode.coerceIn(0, 2).let {
                    if (it == ReadMenuBlurMode.LiquidGlass) ReadMenuBlurMode.Haze else it
                }
                val style = update.style.coerceIn(0, 1)
                ReadBookConfig.readMenuTopBarBlurMode = mode
                ReadBookConfig.readMenuTopBarBlurStyle = style
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuTopBarBlurMode(mode)
                    readSettingsRepository.setReadMenuTopBarBlurStyle(style)
                }
                _uiState.update {
                    it.copy(
                        menuConfig = it.menuConfig.copy(
                            readMenuTopBarBlurMode = mode,
                            readMenuTopBarBlurStyle = style,
                        )
                    )
                }
            }

            is ConfigUpdate.MenuBottomBarBlurStyle -> {
                val style = update.value.coerceIn(0, 1)
                ReadBookConfig.readMenuBottomBarBlurStyle = style
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuBottomBarBlurStyle(style)
                }
                _uiState.update {
                    it.copy(menuConfig = it.menuConfig.copy(readMenuBottomBarBlurStyle = style))
                }
            }
            is ConfigUpdate.MenuBlurRadius -> {
                ReadBookConfig.readMenuBlurRadius = update.value
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuBlurRadius(update.value)
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(readMenuBlurRadius = update.value)) }
            }
            is ConfigUpdate.MenuBlurAlpha -> {
                ReadBookConfig.readMenuBlurAlpha = update.value
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuBlurAlpha(update.value)
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(readMenuBlurAlpha = update.value)) }
            }
            is ConfigUpdate.MenuLensRadius -> {
                ReadBookConfig.readMenuLensRadius = update.value
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuLensRadius(update.value)
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(readMenuLensRadius = update.value)) }
            }
            is ConfigUpdate.MenuCustomIcon -> {
                val icons = ReadBookConfig.readMenuCustomIcons.toMutableMap()
                if (update.path.isBlank()) {
                    icons.remove(update.id)?.let { path ->
                        runCatching { java.io.File(path).delete() }
                    }
                } else {
                    icons[update.id] = update.path
                }
                ReadBookConfig.readMenuCustomIcons = icons
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuCustomIcons(
                        ReadBookConfig.encodeReadMenuCustomIcons(icons)
                    )
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(readMenuCustomIcons = icons.toImmutableMap())) }
            }
            is ConfigUpdate.TitleBarCustomIcon -> {
                val icons = ReadBookConfig.titleBarCustomIcons.toMutableMap()
                if (update.path.isBlank()) {
                    icons.remove(update.id)?.let { path ->
                        runCatching { java.io.File(path).delete() }
                    }
                } else {
                    icons[update.id] = update.path
                }
                ReadBookConfig.titleBarCustomIcons = icons
                viewModelScope.launch {
                    readSettingsRepository.setTitleBarCustomIcons(
                        ReadBookConfig.encodeReadMenuCustomIcons(icons)
                    )
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(titleBarCustomIcons = icons.toImmutableMap())) }
            }
            is ConfigUpdate.TitleBarIconPosition -> {
                ReadBookConfig.titleBarIconPosition = update.value
                viewModelScope.launch {
                    readSettingsRepository.setTitleBarIconPosition(update.value)
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(titleBarIconPosition = update.value)) }
            }
            is ConfigUpdate.ShowTitleBarIcons -> {
                ReadBookConfig.showTitleBarIcons = update.value
                viewModelScope.launch {
                    readSettingsRepository.setShowTitleBarIcons(update.value)
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(showTitleBarIcons = update.value)) }
            }

            // --- System UI (also persists to DataStore) ---
            is ConfigUpdate.HideStatusBar -> {
                ReadBookConfig.hideStatusBar = update.value
                viewModelScope.launch {
                    readSettingsRepository.setHideStatusBar(update.value)
                }
            }
            is ConfigUpdate.HideNavigationBar -> {
                ReadBookConfig.hideNavigationBar = update.value
                viewModelScope.launch {
                    readSettingsRepository.setHideNavigationBar(update.value)
                }
            }

            // --- Display toggles ---
            is ConfigUpdate.PaddingDisplayCutouts -> {
                viewModelScope.launch {
                    readSettingsRepository.setPaddingDisplayCutouts(update.value)
                }
            }
            is ConfigUpdate.TitleBarMode -> {
                viewModelScope.launch {
                    readSettingsRepository.setTitleBarMode(update.value)
                }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }
            is ConfigUpdate.TextFullJustify -> {
                viewModelScope.launch {
                    readSettingsRepository.setTextFullJustify(update.value)
                }
            }
            is ConfigUpdate.TextBottomJustify -> {
                viewModelScope.launch {
                    readSettingsRepository.setTextBottomJustify(update.value)
                }
            }
            is ConfigUpdate.AdaptSpecialStyle -> {
                viewModelScope.launch {
                    readSettingsRepository.setAdaptSpecialStyle(update.value)
                }
            }
            is ConfigUpdate.UseZhLayout -> {
                ReadBookConfig.useZhLayout = update.value
                viewModelScope.launch {
                    readSettingsRepository.setUseZhLayout(update.value)
                }
            }
            is ConfigUpdate.ShowBrightnessView -> {
                viewModelScope.launch {
                    readSettingsRepository.setShowBrightnessView(update.value)
                }
                postEvent(PreferKey.showBrightnessView, "")
            }
            is ConfigUpdate.UseUnderlineGlobal -> {
                viewModelScope.launch {
                    readSettingsRepository.setUseUnderline(update.value)
                }
            }
            is ConfigUpdate.ReadSliderMode -> {
                viewModelScope.launch {
                    readSettingsRepository.setReadSliderMode(update.value)
                }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }
            is ConfigUpdate.DoubleHorizontalPage -> {
                viewModelScope.launch {
                    readSettingsRepository.setDoubleHorizontalPage(update.value)
                }
                ChapterProvider.upLayout()
                ReadBook.loadContent(false)
            }
            is ConfigUpdate.ProgressBarBehavior -> {
                viewModelScope.launch {
                    readSettingsRepository.setProgressBarBehavior(update.value)
                }
                _uiState.update { it.copy(styleConfig = buildStyleConfig()) }
            }
            is ConfigUpdate.NoAnimScrollPage -> {
                viewModelScope.launch {
                    readSettingsRepository.setNoAnimScrollPage(update.value)
                }
                _effects.tryEmit(ReadBookEffect.UpPageAnim(upRecorder = false))
            }
            is ConfigUpdate.ShowReadTitleAddition -> {
                viewModelScope.launch {
                    readSettingsRepository.setShowReadTitleAddition(update.value)
                }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }

            // --- Highlight rules ---
            is ConfigUpdate.HighlightRules -> {
                HighlightRuleStore.save(update.rules)
                TextChapterLayout.invalidateRegexCache()
            }

            // --- Auto read ---
            is ConfigUpdate.AutoReadSpeed -> {
                ReadBookConfig.autoReadSpeed = update.value
                viewModelScope.launch {
                    readSettingsRepository.setAutoReadSpeed(update.value)
                }
            }
        }

        // Notify rendering layer
        if (update.actions.isNotEmpty()) {
            _uiState.update { it.copy(styleConfig = buildStyleConfig()) }
            _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(update.actions))
        } else {
            _uiState.update { it.copy(styleConfig = buildStyleConfig()) }
        }
    }

    private fun saveMenuCustomIcon(id: String, uri: Uri) {
        execute {
            val iconFile = java.io.File(context.filesDir, "read_menu_icons/$id.png")
            iconFile.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                iconFile.outputStream().use { output -> input.copyTo(output) }
            }
            handleConfigUpdate(ConfigUpdate.MenuCustomIcon(id, iconFile.absolutePath))
        }
    }

    private fun saveTitleBarCustomIcon(id: String, uri: Uri) {
        execute {
            val iconFile = java.io.File(context.filesDir, "title_bar_icons/$id.png")
            iconFile.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                iconFile.outputStream().use { output -> input.copyTo(output) }
            }
            handleConfigUpdate(ConfigUpdate.TitleBarCustomIcon(id, iconFile.absolutePath))
        }
    }

    private fun selectFont(path: String) {
        ReadBookConfig.textFont = path
        _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(
            setOf(ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.ReloadContent, ConfigUpdateAction.UpdateStyle)
        ))
    }

    private fun toggleDayNight() {
        val nextMode = when (ThemeConfig.themeMode) {
            "0" -> "1"  // follow system → light
            "1" -> "2"  // light → dark
            else -> "0" // dark → follow system
        }
        ThemeConfig.themeMode = nextMode
        AppConfig.themeMode = nextMode
        _uiState.update { it.copy(styleConfig = buildStyleConfig()) }
        _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(
            setOf(
                ConfigUpdateAction.UpdateBackground,
                ConfigUpdateAction.UpdateStyle,
                ConfigUpdateAction.UpdateContent
            )
        ))
        postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
    }

    private fun applyReadStyleBackgroundImage(uri: Uri) {
        viewModelScope.launch(IO) {
            runCatching {
                val name = queryDisplayName(uri)
                val path = context.contentResolver.openInputStream(uri)?.use {
                    readBookStyleConfigRepository.saveBackgroundImage(it, name)
                } ?: throw FileNotFoundException(uri.toString())
                readBookStyleConfigRepository.setCurrentBackgroundImage(path)
                _uiState.update { it.copy(styleConfig = buildStyleConfig()) }
                _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(
                    setOf(ConfigUpdateAction.UpdateBackground)
                ))
                context.getString(R.string.success)
            }.onSuccess { message ->
                _effects.tryEmit(ReadBookEffect.ShowToast(message))
            }.onFailure { throwable ->
                AppLog.put("选择阅读背景图失败", throwable)
                _effects.tryEmit(ReadBookEffect.LongToast(throwable.localizedMessage ?: context.getString(R.string.error)))
            }
        }
    }

    private fun applyReadStyleBackgroundImageForMode(uri: Uri, isNight: Boolean) {
        viewModelScope.launch(IO) {
            runCatching {
                val name = queryDisplayName(uri)
                val path = context.contentResolver.openInputStream(uri)?.use {
                    readBookStyleConfigRepository.saveBackgroundImage(it, name)
                } ?: throw FileNotFoundException(uri.toString())
                readBookStyleConfigRepository.setCurrentBackgroundImageForMode(path, isNight)
                _uiState.update { it.copy(styleConfig = buildStyleConfig()) }
                _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(
                    setOf(ConfigUpdateAction.UpdateBackground)
                ))
                context.getString(R.string.success)
            }.onSuccess { message ->
                _effects.tryEmit(ReadBookEffect.ShowToast(message))
            }.onFailure { throwable ->
                AppLog.put("选择阅读背景图失败", throwable)
                _effects.tryEmit(ReadBookEffect.LongToast(throwable.localizedMessage ?: context.getString(R.string.error)))
            }
        }
    }

    private fun importReadStyleConfig(uri: Uri) {
        viewModelScope.launch(IO) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw FileNotFoundException(uri.toString())
                readBookStyleConfigRepository.importCurrentStyle(bytes)
                _uiState.update { it.copy(styleConfig = buildStyleConfig()) }
                _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(
                    setOf(ConfigUpdateAction.UpdateBackground, ConfigUpdateAction.UpdateStyle, ConfigUpdateAction.ReloadContent)
                ))
                context.getString(R.string.success)
            }.onSuccess { message ->
                _effects.tryEmit(ReadBookEffect.ShowToast(message))
            }.onFailure { throwable ->
                AppLog.put("导入阅读样式失败", throwable)
                _effects.tryEmit(ReadBookEffect.LongToast(throwable.localizedMessage ?: context.getString(R.string.error)))
            }
        }
    }

    private fun exportReadStyleConfig(uri: Uri) {
        viewModelScope.launch(IO) {
            runCatching {
                val bytes = readBookStyleConfigRepository.exportCurrentStyle()
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: throw FileNotFoundException(uri.toString())
                context.getString(R.string.export_success)
            }.onSuccess { message ->
                _effects.tryEmit(ReadBookEffect.ShowToast(message))
            }.onFailure { throwable ->
                AppLog.put("导出阅读样式失败", throwable)
                _effects.tryEmit(ReadBookEffect.LongToast(throwable.localizedMessage ?: context.getString(R.string.error)))
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        }
    }

    private fun colorSelected(dialogId: Int, color: Int) {
        ReadBookConfig.durConfig.apply {
            when (dialogId) {
                ReadBookColorPickerIds.SHADOW_COLOR -> {
                    setCurShadColor(color)
                    _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(
                        setOf(ConfigUpdateAction.UpdateStyle, ConfigUpdateAction.UpdateContent, ConfigUpdateAction.InvalidateTextPage, ConfigUpdateAction.SubmitRenderTask)
                    ))
                }

                ReadBookColorPickerIds.TEXT_COLOR -> {
                    setCurTextColor(color)
                    _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(
                        setOf(ConfigUpdateAction.UpdateStyle, ConfigUpdateAction.UpdateContent, ConfigUpdateAction.InvalidateTextPage, ConfigUpdateAction.SubmitRenderTask)
                    ))
                    if (AppConfig.readBarStyleFollowPage) {
                        postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                    }
                }

                ReadBookColorPickerIds.TEXT_ACCENT_COLOR -> {
                    setCurTextAccentColor(color)
                    _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(
                        setOf(ConfigUpdateAction.UpdateStyle, ConfigUpdateAction.UpdateContent, ConfigUpdateAction.InvalidateTextPage, ConfigUpdateAction.SubmitRenderTask)
                    ))
                    if (AppConfig.readBarStyleFollowPage) {
                        postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                    }
                }

                ReadBookColorPickerIds.BG_COLOR -> {
                    setCurBg(0, "#${color.hexString}")
                    _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(
                        setOf(ConfigUpdateAction.UpdateBackground)
                    ))
                    if (AppConfig.readBarStyleFollowPage) {
                        postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                    }
                }

                ReadBookColorPickerIds.TIP_HEADER_COLOR -> {
                    ReadBookConfig.tipHeaderColor = color
                    postEvent(EventBus.TIP_COLOR, "")
                    _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(
                        setOf(ConfigUpdateAction.UpdateStyle)
                    ))
                }

                ReadBookColorPickerIds.TIP_FOOTER_COLOR -> {
                    ReadBookConfig.tipFooterColor = color
                    postEvent(EventBus.TIP_COLOR, "")
                    _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(
                        setOf(ConfigUpdateAction.UpdateStyle)
                    ))
                }

                ReadBookColorPickerIds.TIP_DIVIDER_COLOR -> {
                    ReadBookConfig.tipDividerColor = color
                    postEvent(EventBus.TIP_COLOR, "")
                    _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(
                        setOf(ConfigUpdateAction.UpdateStyle)
                    ))
                }

                ReadBookColorPickerIds.TITLE_COLOR -> {
                    ReadBookConfig.titleColor = color
                    _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(
                        setOf(ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.ReloadContent)
                    ))
                }

                ReadBookColorPickerIds.HIGHLIGHT_RULE_COLOR -> {
                    val pos = ReadBookColorPickerIds.pendingHighlightRulePosition
                    val rules = HighlightRuleStore.load()
                    if (pos in rules.indices) {
                        HighlightRuleStore.update(rules[pos].copy(textColor = color))
                        TextChapterLayout.invalidateRegexCache()
                        _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(
                            setOf(ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.ReloadContent)
                        ))
                    }
                }

                ReadBookColorPickerIds.MENU_BG_COLOR -> {
                    ReadBookConfig.readMenuBgColor = color
                    viewModelScope.launch {
                        readSettingsRepository.setReadMenuBgColor(color)
                    }
                    postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                }

                ReadBookColorPickerIds.MENU_ACCENT_COLOR -> {
                    ReadBookConfig.readMenuAccentColor = color
                    viewModelScope.launch {
                        readSettingsRepository.setReadMenuAccentColor(color)
                    }
                    postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                }

                ReadBookColorPickerIds.UNDERLINE_COLOR -> {
                    setUnderlineColor(color)
                    _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(
                        setOf(ConfigUpdateAction.UpdateStyle, ConfigUpdateAction.UpdateContent, ConfigUpdateAction.InvalidateTextPage, ConfigUpdateAction.SubmitRenderTask)
                    ))
                }
            }
        }
    }

    private fun toggleTranslation() {
        val book = ReadBook.book ?: return
        book.setTranslationMode(!book.getTranslationMode())
        book.save()
        ReadBook.loadContent(false)
    }

    private fun retranslateCurrentChapter() {
        val book = ReadBook.book ?: return
        viewModelScope.launch {
            val chapter = appDb.bookChapterDao.getChapter(
                book.bookUrl, ReadBook.durChapterIndex
            ) ?: return@launch
            io.legado.app.model.translation.TranslationManager.deleteTranslationCache(book, chapter)
            book.setTranslationMode(true)
            book.save()
            ReadBook.loadContent(false)
        }
    }

    fun disableSource() {
        execute {
            ReadBook.bookSource?.let {
                it.enabled = false
                appDb.bookSourceDao.update(it)
            }
        }
    }

    private fun showPayDialog() {
        val book = ReadBook.book ?: return
        if (book.isLocal) return
        val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
        if (chapter == null) {
            context.toastOnUi(R.string.no_chapter)
            return
        }
        _uiState.update { it.copy(activeDialog = ReadBookDialog.ConfirmChapterPay(chapter.title)) }
    }

    private fun confirmPayAction() {
        val book = ReadBook.book ?: return
        if (book.isLocal) return
        execute {
            val source = ReadBook.bookSource ?: throw NoStackTraceException("no book source")
            val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
                ?: throw NoStackTraceException(context.getString(R.string.no_chapter))
            val payAction = source.getContentRule().payAction
            if (payAction.isNullOrBlank()) {
                throw NoStackTraceException("no pay action")
            }
            val analyzeRule = AnalyzeRule(book, source)
            analyzeRule.setCoroutineContext(coroutineContext)
            analyzeRule.setBaseUrl(chapter.url)
            analyzeRule.setChapter(chapter)
            analyzeRule.evalJS(payAction).toString() to chapter
        }.onSuccess(IO) { (result, chapter) ->
            if (result.isAbsUrl()) {
                _effects.tryEmit(
                    ReadBookEffect.OpenWebView(
                        title = context.getString(R.string.chapter_pay),
                        url = result,
                        sourceOrigin = ReadBook.bookSource?.bookSourceUrl,
                        sourceName = ReadBook.bookSource?.bookSourceName,
                        sourceType = ReadBook.bookSource?.getSourceType(),
                    )
                )
            } else if (result.isTrue()) {
                BookHelp.delContent(book, chapter)
                loadChapterList(book)
            }
        }.onError {
            AppLog.put("执行购买操作出错\n${it.localizedMessage}", it, true)
        }
    }

    private fun requestBooksDirPicker(reloadChapterList: Boolean) {
        pendingBooksDirReloadChapterList = reloadChapterList
        _effects.tryEmit(ReadBookEffect.OpenBooksDirPicker)
    }

    private fun onBooksDirSelected(uri: Uri) {
        OtherConfig.defaultBookTreeUri = uri.toString()
        val reloadChapterList = pendingBooksDirReloadChapterList
        pendingBooksDirReloadChapterList = false
        val book = ReadBook.book ?: return
        if (reloadChapterList) {
            doLoadChapterList(book)
        } else {
            execute { initBook(book) }
        }
    }

    private fun exitSearch() {
        _uiState.update {
            it.copy(
                isShowingSearchResult = false,
                searchMenuVisible = false
            )
        }
        _effects.tryEmit(ReadBookEffect.ExitSearch)
    }

    override fun onCleared() {
        super.onCleared()
        if (BaseReadAloudService.isRun && BaseReadAloudService.pause) {
            ReadAloud.stop(context)
        }
        ReadBook.unregister(this)
    }

    fun addToBookshelf(book: Book, toc: List<BookChapter>, success: (() -> Unit)? = null) {
        execute {
            book.removeType(BookType.notShelf)
            if (book.order == 0) {
                book.order = appDb.bookDao.minOrder - 1
            }
            appDb.bookDao.insert(book)
            appDb.bookChapterDao.insert(*toc.toTypedArray())
        }.onSuccess {
            success?.invoke()
        }.onError {
            AppLog.put("添加书籍到书架失败", it)
            context.toastOnUi("添加书籍失败")
        }
    }
}

private const val TITLE_BAR_ICON_PREFS = "title_bar_icons"
private const val TITLE_BAR_ICON_KEY = "icons"
private const val TOOL_BUTTON_PREFS = "tool_button_config"
private const val TOOL_BUTTON_KEY = "tool_buttons"
private const val DEFAULT_ENABLED_BUTTON_COUNT = 5
