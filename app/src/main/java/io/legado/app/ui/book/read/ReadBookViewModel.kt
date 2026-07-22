package io.legado.app.ui.book.read

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.util.Base64
import androidx.core.graphics.toColorInt
import androidx.lifecycle.viewModelScope
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.constant.ReadMenuBlurMode
import io.legado.app.data.appDb
import io.legado.app.data.entities.AiArtifact
import io.legado.app.data.entities.AiPromptPreset
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookContentProcess
import io.legado.app.data.entities.BookProgress
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.entities.HighlightRule
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.data.repository.HighlightRuleRepository
import io.legado.app.data.repository.ReadAloudSettingsRepository
import io.legado.app.data.repository.ReadPreferences
import io.legado.app.data.repository.ReadSettingsRepository
import io.legado.app.data.repository.ReplaceRuleRepository
import io.legado.app.data.repository.SettingsRepository
import io.legado.app.data.repository.UploadRepository
import io.legado.app.domain.gateway.AiArtifactGateway
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiPromptPresetGateway
import io.legado.app.domain.gateway.AppShellSettingsGateway
import io.legado.app.domain.gateway.AppUiConfigurationGateway
import io.legado.app.domain.gateway.BackupSettingsGateway
import io.legado.app.domain.gateway.BookContentProcessGateway
import io.legado.app.domain.gateway.ChangeSourceSettingsGateway
import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.ReadStyleBooleanKey
import io.legado.app.domain.gateway.ReadStyleColorKey
import io.legado.app.domain.gateway.ReadStyleFloatKey
import io.legado.app.domain.gateway.ReadStyleGateway
import io.legado.app.domain.gateway.ReadStyleIntKey
import io.legado.app.domain.gateway.ReadStyleMutation
import io.legado.app.domain.gateway.ReadStyleStringKey
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.PlaybackTimer
import io.legado.app.domain.model.ReadingProgress
import io.legado.app.domain.model.TextProcessAction
import io.legado.app.domain.model.TextProcessAnchor
import io.legado.app.domain.model.readaloud.ReadAloudSessionStatus
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.domain.model.readaloud.VoiceCatalogEntry
import io.legado.app.domain.usecase.AiTextFactoryUseCase
import io.legado.app.domain.usecase.ChangeBookSourceUseCase
import io.legado.app.domain.usecase.CleanSelectedTextUseCase
import io.legado.app.domain.usecase.GenerateChapterSummaryUseCase
import io.legado.app.domain.usecase.GetReadingProgressUseCase
import io.legado.app.domain.usecase.SaveBookContentProcessUseCase
import io.legado.app.domain.usecase.SyncReadAloudVoicesUseCase
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
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.http.decompressed
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.help.source.getSourceType
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.model.ImageProvider
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadAloudSessionStore
import io.legado.app.model.ReadBook
import io.legado.app.model.ReadSessionState
import io.legado.app.model.SourceCallBack
import io.legado.app.model.activeReadAloudProgress
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setChapter
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.translation.TranslationManager
import io.legado.app.model.webBook.WebBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.provider.TextChapterLayout
import io.legado.app.ui.book.searchContent.SearchResult
import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.importComponents.ImportItemWrapper
import io.legado.app.ui.widget.components.importComponents.ImportStatus
import io.legado.app.utils.GSON
import io.legado.app.utils.ImageSaveUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.StringUtils
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.hexString
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isDataUrl
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.isTrue
import io.legado.app.utils.mapParallelSafe
import io.legado.app.utils.openUrl
import io.legado.app.utils.postEvent
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toStringArray
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.FileNotFoundException
import java.util.Date
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.graphics.ColorUtils as AndroidColorUtils

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
    private val readBookStyleConfigRepository: ReadStyleGateway,
    private val readAloudSettingsRepository: ReadAloudSettingsRepository,
    private val localPreferencesRepository: SettingsRepository,
    private val highlightRuleRepository: HighlightRuleRepository,
    private val uploadRepository: UploadRepository,
    private val changeBookSourceUseCase: ChangeBookSourceUseCase,
    private val generateChapterSummaryUseCase: GenerateChapterSummaryUseCase,
    private val cleanSelectedTextUseCase: CleanSelectedTextUseCase,
    private val aiTextFactoryUseCase: AiTextFactoryUseCase,
    private val saveBookContentProcessUseCase: SaveBookContentProcessUseCase,
    private val bookContentProcessGateway: BookContentProcessGateway,
    private val aiArtifactGateway: AiArtifactGateway,
    private val aiPromptPresetGateway: AiPromptPresetGateway,
    private val aiProfileGateway: AiProfileGateway,
    private val syncReadAloudVoicesUseCase: SyncReadAloudVoicesUseCase,
    private val readAloudSessionStore: ReadAloudSessionStore,
    private val replaceRuleRepository: ReplaceRuleRepository,
    private val changeSourceSettingsGateway: ChangeSourceSettingsGateway,
    private val appShellSettingsGateway: AppShellSettingsGateway,
    private val appUiConfigurationGateway: AppUiConfigurationGateway,
    private val otherSettingsGateway: OtherSettingsGateway,
    private val downloadCacheSettingsGateway: DownloadCacheSettingsGateway,
    private val backupSettingsGateway: BackupSettingsGateway,
) : BaseViewModel(application), ReadBook.CallBack {

    // --- MVI State ---

    private val _uiState = MutableStateFlow(ReadBookUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ReadBookEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private val _readAloudProgress = MutableStateFlow(
        activeReadAloudProgress(
            isPlaying = BaseReadAloudService.isPlay(),
            currentProgress = BaseReadAloudService.currentProgress,
        )
    )
    val readAloudProgress = _readAloudProgress.asStateFlow()

    private suspend fun emitEffectWhenSubscribed(effect: ReadBookEffect) {
        _effects.subscriptionCount.first { it > 0 }
        _effects.emit(effect)
    }

    private fun closeReadMenu() {
        _uiState.update { it.copy(menuState = ReadBookMenuState()) }
    }

    private val sysEngines: List<TextToSpeech.EngineInfo> by lazy {
        val tts = TextToSpeech(context, null)
        val engines = tts.engines
        tts.shutdown()
        engines
    }

    private val _readPreferences = MutableStateFlow(ReadPreferences())
    val readPreferences = _readPreferences.asStateFlow()

    private var changeSourceCoroutine: Coroutine<*>? = null
    private var pendingBooksDirReloadChapterList: Boolean = false
    private var pendingContentEditCursorOffset: Int? = null
    private var pendingContentEditAnchor: String? = null
    private var chapterSummaryJob: Job? = null
    private var aiTextCleanJob: Job? = null
    private var aiTextRewriteJob: Job? = null
    private var pendingAiTextCleanRequest: PendingAiTextCleanRequest? = null
    private var pendingAiTextRewriteRequest: PendingAiTextRewriteRequest? = null

    val isInitFinish: Boolean get() = _uiState.value.isInitFinish

    private fun isNightTheme(): Boolean =
        appUiConfigurationGateway.currentConfiguration.isDarkTheme

    fun setAutoPage(active: Boolean) {
        _uiState.update { it.copy(isAutoPage = active) }
    }

    fun setTextSelectMenuConfig(value: String) {
        viewModelScope.launch {
            readSettingsRepository.update { it.copy(textSelectMenuConfig = value) }
        }
    }

    init {
        ReadBook.register(this)
        refreshButtonConfigs()
        collectReadPreferences()
        collectReadAloudPreferences()
        collectEventBus()
        execute { syncConfiguredTtsVoices() }
    }

    // --- MVI Intent Dispatcher ---

    fun onIntent(intent: ReadBookIntent) {
        when (intent) {
            is ReadBookIntent.InitData -> {
                initData(intent.intent)
                justInitData = true
            }
            is ReadBookIntent.InitReadBookConfig -> initReadBookConfig(intent.intent)
            is ReadBookIntent.CheckSwitchDayNight -> checkSwitchDayNight(intent.lux)
            is ReadBookIntent.DismissReminder -> dismissReminder()
            is ReadBookIntent.NextPage -> ReadBook.moveToNextPage()
            is ReadBookIntent.PrevPage -> ReadBook.moveToPrevPage()
            is ReadBookIntent.NextChapter -> ReadBook.moveToNextChapter(upContent = true)
            is ReadBookIntent.PrevChapter -> ReadBook.moveToPrevChapter(upContent = true, toLast = false)
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
                closeReadMenu()
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
                    val results = intent.results.toImmutableList()
                    val index = intent.index.coerceSearchResultIndex(results.size)
                    it.copy(
                        searchResultList = results,
                        searchResultIndex = index,
                        isShowingSearchResult = true,
                        searchMenuVisible = true,
                        menuState = ReadBookMenuState(),
                        searchContentQuery = intent.query ?: it.searchContentQuery,
                    )
                }
            }

            is ReadBookIntent.SetSearchResultIndex -> {
                _uiState.update {
                    it.copy(
                        searchResultIndex = intent.index.coerceSearchResultIndex(
                            it.searchResultList.size
                        )
                    )
                }
            }

            is ReadBookIntent.SetShowingSearchResult -> {
                _uiState.update { it.copy(isShowingSearchResult = intent.value) }
            }

            is ReadBookIntent.NavigateSearchResultByOffset -> {
                navigateSearchResultByOffset(intent.offset)
            }

            is ReadBookIntent.NavigateToSearchResult -> {
                ReadBook.saveCurrentBookProgress()
                _uiState.update {
                    it.copy(
                        searchResultIndex = intent.index.coerceSearchResultIndex(
                            it.searchResultList.size
                        )
                    )
                }
                navigateToSearchResult(intent.result)
            }

            is ReadBookIntent.RestoreLastBookProgress -> {
                _uiState.update { it.copy(activeDialog = null) }
                ReadBook.restoreLastBookProgress()
            }

            is ReadBookIntent.KeepCurrentBookProgress -> {
                ReadBook.lastBookProgress = null
                _uiState.update { it.copy(activeDialog = null) }
            }

            is ReadBookIntent.ToggleReadAloud -> {
                if (!BaseReadAloudService.isRun) {
                    openDefaultReadAloudInterface()
                }
                _effects.tryEmit(ReadBookEffect.ToggleReadAloud)
            }

            is ReadBookIntent.ToggleAutoPage -> _effects.tryEmit(ReadBookEffect.ToggleAutoPage)
            is ReadBookIntent.StopAutoPage -> _effects.tryEmit(ReadBookEffect.StopAutoPage)
            is ReadBookIntent.RefreshCurrentChapter -> refreshCurrentChapter()
            is ReadBookIntent.RefreshAllChapters -> refreshAllChapters()
            is ReadBookIntent.RefreshContentAfter -> refreshContentAfter()
            is ReadBookIntent.ChangeReplaceRule -> changeReplaceRule(intent.enabled)
            is ReadBookIntent.DisableEffectiveReplace -> viewModelScope.launch {
                replaceRuleRepository.insert(intent.rule.copy(isEnabled = false))
            }
            ReadBookIntent.DisableChineseConverter -> {
                handleConfigUpdate(ConfigUpdate.ChineseConverterType(0))
            }
            ReadBookIntent.DisableReSegment -> {
                ReadBook.book?.setReSegment(false)
                ReadBook.loadContent(false)
                _uiState.update { it.copy(reSegment = false) }
            }
            is ReadBookIntent.ToggleTranslation -> toggleTranslation()
            is ReadBookIntent.OpenChapterSummary -> openChapterSummary()
            is ReadBookIntent.OpenAiCurrentChapterRewrite -> openAiCurrentChapterRewrite()
            is ReadBookIntent.RetryChapterSummary -> retryChapterSummary()
            is ReadBookIntent.LoadContentProcesses -> loadContentProcesses()
            is ReadBookIntent.ToggleContentProcess -> toggleContentProcess(intent.id, intent.enabled)
            is ReadBookIntent.RequestDeleteContentProcess -> _uiState.update {
                it.copy(
                    contentProcessConfig = it.contentProcessConfig.copy(
                        deleteItem = intent.item
                    )
                )
            }
            is ReadBookIntent.ConfirmDeleteContentProcess -> deletePendingContentProcess()
            is ReadBookIntent.DismissDeleteContentProcess -> _uiState.update {
                it.copy(
                    contentProcessConfig = it.contentProcessConfig.copy(deleteItem = null)
                )
            }
            is ReadBookIntent.SelectAiRewritePreset -> selectAiRewritePreset(intent.presetId)
            is ReadBookIntent.SetAiRewriteTemporaryInstruction -> setAiRewriteTemporaryInstruction(
                intent.instruction
            )
            is ReadBookIntent.SelectAiRewriteHistory -> selectAiRewriteHistory(intent.artifactId)
            is ReadBookIntent.GenerateAiTextRewrite -> generateSelectedAiTextRewrite()
            is ReadBookIntent.RetryAiTextRewrite -> retryAiTextRewrite()
            is ReadBookIntent.ConfirmAiTextRewrite -> confirmAiTextRewrite()
            is ReadBookIntent.OpenAiRewritePresetConfig -> openAiRewritePresetConfig()
            is ReadBookIntent.CloseAiRewritePresetConfig -> closeAiRewritePresetConfig()
            is ReadBookIntent.AddAiRewritePreset -> startAddAiRewritePreset()
            is ReadBookIntent.EditAiRewritePreset -> startEditAiRewritePreset(intent.preset)
            is ReadBookIntent.SetAiRewritePresetName -> _uiState.update {
                it.copy(
                    aiRewritePresetConfig = it.aiRewritePresetConfig.copy(
                        editingName = intent.name,
                        errorMessage = null,
                    )
                )
            }
            is ReadBookIntent.SetAiRewritePresetInstruction -> _uiState.update {
                it.copy(
                    aiRewritePresetConfig = it.aiRewritePresetConfig.copy(
                        editingInstruction = intent.instruction,
                        errorMessage = null,
                    )
                )
            }
            is ReadBookIntent.SaveAiRewritePreset -> saveAiRewritePreset()
            is ReadBookIntent.CancelAiRewritePresetEdit -> clearAiRewritePresetDraft()
            is ReadBookIntent.RequestDeleteAiRewritePreset -> _uiState.update {
                it.copy(
                    aiRewritePresetConfig = it.aiRewritePresetConfig.copy(
                        deletePreset = intent.preset
                    )
                )
            }
            is ReadBookIntent.ConfirmDeleteAiRewritePreset -> deleteAiRewritePreset()
            is ReadBookIntent.DismissDeleteAiRewritePreset -> _uiState.update {
                it.copy(
                    aiRewritePresetConfig = it.aiRewritePresetConfig.copy(deletePreset = null)
                )
            }
            is ReadBookIntent.ChangeSourceBook -> changeTo(intent.book)
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
            is ReadBookIntent.SetBrightness -> {
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(readBrightness = intent.value)) }
                viewModelScope.launch {
                    readSettingsRepository.setReadBrightness(intent.value)
                }
                _effects.tryEmit(ReadBookEffect.SetBrightness(intent.value))
            }

            is ReadBookIntent.ToggleBrightnessAuto -> {
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(brightnessAuto = intent.auto)) }
                viewModelScope.launch {
                    readSettingsRepository.setBrightnessAuto(intent.auto)
                }
                _effects.tryEmit(
                    ReadBookEffect.ToggleBrightnessAuto(
                        intent.auto,
                        _uiState.value.menuConfig.readBrightness
                    )
                )
            }
            is ReadBookIntent.SeekToChapter -> {
                ReadBook.saveCurrentBookProgress()
                openChapter(intent.index)
            }

            is ReadBookIntent.ShowSheet -> {
                if (intent.sheet is ReadBookSheet.HighlightRuleConfig) {
                    loadHighlightRules()
                    _uiState.update { it.copy(activeSheet = intent.sheet) }
                } else if (intent.sheet is ReadBookSheet.ContentProcesses) {
                    _uiState.update { it.copy(activeSheet = intent.sheet) }
                    loadContentProcesses()
                } else if (intent.sheet is ReadBookSheet.AiRewritePresetConfig) {
                    openAiRewritePresetConfig()
                } else {
                    _uiState.update { it.copy(activeSheet = intent.sheet) }
                }
            }
            is ReadBookIntent.DismissSheet -> {
                when (_uiState.value.activeSheet) {
                    is ReadBookSheet.ChapterSummary -> chapterSummaryJob?.cancel()
                    is ReadBookSheet.AiTextClean -> {
                        aiTextCleanJob?.cancel()
                        pendingAiTextCleanRequest = null
                    }
                    else -> Unit
                }
                _uiState.update {
                    if (it.activeSheet is ReadBookSheet.ChapterSummary) {
                        it.copy(
                            activeSheet = null,
                            chapterSummary = ChapterSummaryUiState(),
                        )
                    } else if (it.activeSheet is ReadBookSheet.AiTextClean) {
                        it.copy(
                            activeSheet = null,
                            aiTextClean = AiTextCleanUiState(),
                        )
                    } else if (it.activeSheet is ReadBookSheet.AiTextRewrite) {
                        it.copy(activeSheet = null)
                    } else if (it.activeSheet is ReadBookSheet.AiRewritePresetConfig) {
                        it.copy(
                            activeSheet = null,
                            aiRewritePresetConfig = AiRewritePresetConfigUiState(),
                        )
                    } else if (it.activeSheet is ReadBookSheet.ContentEdit) {
                        it.copy(
                            activeSheet = null,
                            contentEditText = "",
                            contentEditTitle = "",
                            contentEditCursorOffset = 0,
                            contentEditLoading = false,
                            contentEditSaveToSource = false,
                        )
                    } else if (it.activeSheet is ReadBookSheet.HighlightRuleConfig) {
                        it.copy(
                            activeSheet = null,
                            highlightRuleConfig = it.highlightRuleConfig.copy(
                                editingRule = null,
                                showNewRule = false,
                                deleteRule = null,
                                importState = BaseImportUiState.Idle,
                            ),
                        )
                    } else if (it.activeSheet is ReadBookSheet.ContentProcesses) {
                        it.copy(
                            activeSheet = null,
                            contentProcessConfig = ContentProcessConfigUiState(),
                        )
                    } else {
                        it.copy(activeSheet = null)
                    }
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
                    closeReadMenu()
                    _effects.tryEmit(ReadBookEffect.OpenBookInfo(book.name, book.author, book.bookUrl))
                }
            }
            is ReadBookIntent.OpenChapterList -> {
                ReadBook.book?.bookUrl?.let { bookUrl ->
                    closeReadMenu()
                    _effects.tryEmit(ReadBookEffect.OpenChapterList(bookUrl))
                }
            }
            is ReadBookIntent.OpenChapterUrl -> openChapterUrl()
            is ReadBookIntent.SourceCustomButton -> runSourceCustomButton(intent.longClick)
            is ReadBookIntent.ToggleReadUrlInBrowser -> toggleReadUrlInBrowser()
            is ReadBookIntent.OpenContentEdit -> openContentEdit()
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
                    ReadBook.uploadProgress(true) {
                        _effects.tryEmit(
                            ReadBookEffect.ShowToast(context.getString(R.string.upload_book_success))
                        )
                    }
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
                        _effects.tryEmit(ReadBookEffect.ShowToast("未找到可移除的重复标题"))
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
            is ReadBookIntent.MenuSettingReplace -> {
                closeReadMenu()
                _effects.tryEmit(ReadBookEffect.MenuSettingReplace)
            }
            is ReadBookIntent.MenuTocRegex -> {
                closeReadMenu()
                val book = ReadBook.book
                _effects.tryEmit(ReadBookEffect.MenuTocRegex(book?.bookUrl ?: "", book?.tocUrl))
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
                    val enabled = !it.getUseReplaceRule(
                        otherSettingsGateway.currentSettings.replaceEnableDefault
                    )
                    it.setUseReplaceRule(enabled)
                    ReadBook.saveRead()
                    _uiState.update { state ->
                        state.copy(useReplaceRule = enabled, replaceRuleEnabled = enabled)
                    }
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
            is ReadBookIntent.AddHighlightRule -> _uiState.update {
                it.copy(highlightRuleConfig = it.highlightRuleConfig.copy(showNewRule = true))
            }

            is ReadBookIntent.EditHighlightRule -> _uiState.update {
                it.copy(highlightRuleConfig = it.highlightRuleConfig.copy(editingRule = intent.rule))
            }

            is ReadBookIntent.ToggleHighlightRule -> {
                val rules = _uiState.value.highlightRuleConfig.rules.map {
                    if (it.id == intent.rule.id) it.copy(enabled = intent.enabled) else it
                }
                saveHighlightRules(rules)
            }

            is ReadBookIntent.SaveHighlightRule -> {
                val currentRules = _uiState.value.highlightRuleConfig.rules
                val updatedRules = if (currentRules.any { it.id == intent.rule.id }) {
                    currentRules.map { if (it.id == intent.rule.id) intent.rule else it }
                } else {
                    currentRules + intent.rule
                }
                saveHighlightRules(updatedRules)
            }

            is ReadBookIntent.DismissHighlightRuleEdit -> _uiState.update {
                it.copy(
                    highlightRuleConfig = it.highlightRuleConfig.copy(
                        editingRule = null,
                        showNewRule = false,
                    ),
                )
            }

            is ReadBookIntent.RequestDeleteHighlightRule -> _uiState.update {
                it.copy(highlightRuleConfig = it.highlightRuleConfig.copy(deleteRule = intent.rule))
            }

            is ReadBookIntent.ConfirmDeleteHighlightRule -> deletePendingHighlightRule()
            is ReadBookIntent.DismissDeleteHighlightRule -> _uiState.update {
                it.copy(highlightRuleConfig = it.highlightRuleConfig.copy(deleteRule = null))
            }
            is ReadBookIntent.MoveHighlightRule -> moveHighlightRule(intent.from, intent.to)
            is ReadBookIntent.SaveHighlightRuleOrder -> {
                saveHighlightRules(_uiState.value.highlightRuleConfig.rules)
            }
            is ReadBookIntent.ImportHighlightRuleSource -> {
                importHighlightRuleSource(intent.text)
            }
            is ReadBookIntent.OpenHighlightRuleImportPicker -> {
                _effects.tryEmit(ReadBookEffect.OpenHighlightRuleImportPicker)
            }
            is ReadBookIntent.HighlightRuleImportFileSelected -> {
                importHighlightRuleFile(intent.uri)
            }
            is ReadBookIntent.CancelHighlightRuleImport -> cancelHighlightRuleImport()
            is ReadBookIntent.ToggleHighlightRuleImportSelection -> {
                toggleHighlightRuleImportSelection(intent.index)
            }
            is ReadBookIntent.ToggleHighlightRuleImportAll -> {
                toggleHighlightRuleImportAll(intent.isSelected)
            }
            is ReadBookIntent.UpdateHighlightRuleImportItem -> {
                updateHighlightRuleImportItem(intent.index, intent.rule)
            }
            is ReadBookIntent.SaveImportedHighlightRules -> saveImportedHighlightRules()
            is ReadBookIntent.ExportHighlightRules -> {
                _effects.tryEmit(ReadBookEffect.OpenHighlightRuleExportPicker)
            }
            is ReadBookIntent.ExportHighlightRulesAsUrl -> {
                exportHighlightRulesAsUrl()
            }
            is ReadBookIntent.ExportHighlightRulesToFile -> {
                exportHighlightRules(intent.uri)
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

            is ReadBookIntent.KeepLightChanged -> {
                _readPreferences.update { it.copy(keepLight = intent.value) }
                viewModelScope.launch {
                    readSettingsRepository.setKeepLight(intent.value)
                }
                _effects.tryEmit(ReadBookEffect.UpScreenTimeOut)
            }
            is ReadBookIntent.SetOrientation -> {
                viewModelScope.launch {
                    readSettingsRepository.setScreenOrientation(intent.value)
                }
                _effects.tryEmit(ReadBookEffect.SetOrientation)
            }
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

            is ReadBookIntent.TtsProgress -> updateReadAloudProgress(intent.chapterStart)
            is ReadBookIntent.ReadAloudAction -> openDefaultReadAloudInterface()
            is ReadBookIntent.ConfirmAddCurrentBookToBookshelf -> addCurrentBookToBookshelfAndFinish()
            is ReadBookIntent.ExitWithoutAddingCurrentBookToBookshelf -> removeCurrentNotShelfBookAndFinish()

            is ReadBookIntent.ShowReadAloudConfig -> {
                _uiState.update {
                    it.copy(
                        speakEngineName = computeSpeakEngineName(),
                        activeSheet = ReadBookSheet.ReadAloudConfig,
                    )
                }
                loadTtsEngineItems()
            }

            is ReadBookIntent.SelectSpeakEngine -> {
                showSpeakEngineConfig()
            }

            is ReadBookIntent.OpenPreDownloadNumPicker -> {
                _uiState.update {
                    it.copy(
                        preDownloadNum = _readPreferences.value.preDownloadNum,
                        activeSheet = ReadBookSheet.PreDownloadConfig,
                    )
                }
            }

            is ReadBookIntent.OpenPreSynthesisConcurrencyPicker -> {
                _uiState.update {
                    it.copy(
                        preSynthesisConcurrency =
                            readAloudSettingsRepository.currentSettings.ttsPreSynthesisConcurrency,
                        activeSheet = ReadBookSheet.PreSynthesisConcurrencyConfig,
                    )
                }
            }

            is ReadBookIntent.OpenParagraphIntervalPicker -> {
                _uiState.update {
                    it.copy(
                        readAloudParagraphInterval =
                            readAloudSettingsRepository.currentSettings.ttsParagraphInterval,
                        activeSheet = ReadBookSheet.ParagraphIntervalConfig,
                    )
                }
            }

            is ReadBookIntent.OpenCacheCleanTimePicker -> {
                _uiState.update {
                    it.copy(
                        audioCacheCleanTime =
                            readAloudSettingsRepository.currentSettings.audioCacheCleanTime,
                        activeSheet = ReadBookSheet.AudioCacheCleanConfig,
                    )
                }
            }

            is ReadBookIntent.ApplySpeakEngine -> {
                ReadBook.book?.setTtsEngine(null)
                viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    readAloudSettingsRepository.update { it.copy(ttsEngine = intent.value) }
                    ReadAloud.upReadAloudClass()
                    _uiState.update {
                        it.copy(
                            selectedTtsEngine = ReadAloud.ttsEngine,
                            speakEngineName = computeSpeakEngineName(),
                            activeSheet = ReadBookSheet.ReadAloudConfig,
                        )
                    }
                }
            }

            is ReadBookIntent.ApplyPreDownloadNum -> {
                viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    readSettingsRepository.setPreDownloadNum(intent.value)
                }
                _uiState.update {
                    it.copy(
                        preDownloadNum = intent.value,
                        activeSheet = ReadBookSheet.ReadAloudConfig,
                    )
                }
            }

            is ReadBookIntent.ApplyPreSynthesisConcurrency -> {
                viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    readAloudSettingsRepository.update {
                        it.copy(ttsPreSynthesisConcurrency = intent.value.coerceIn(1, 8))
                    }
                }
                _uiState.update {
                    it.copy(
                        preSynthesisConcurrency = intent.value,
                        activeSheet = ReadBookSheet.ReadAloudConfig,
                    )
                }
            }

            is ReadBookIntent.ApplyAudioCacheCleanTime -> {
                viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    readAloudSettingsRepository.update {
                        it.copy(audioCacheCleanTime = intent.value)
                    }
                }
                _uiState.update {
                    it.copy(
                        audioCacheCleanTime = intent.value,
                        activeSheet = ReadBookSheet.ReadAloudConfig,
                    )
                }
            }

            is ReadBookIntent.ApplyParagraphInterval -> {
                viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    readAloudSettingsRepository.update {
                        it.copy(ttsParagraphInterval = intent.value)
                    }
                }
                _uiState.update {
                    it.copy(
                        readAloudParagraphInterval = intent.value
                    )
                }
            }

            is ReadBookIntent.EditHttpTts -> {
                if (intent.engineId == null) {
                    _uiState.update {
                        it.copy(
                            editingHttpTts = HttpTTS(),
                            activeSheet = ReadBookSheet.HttpTtsEdit(),
                        )
                    }
                } else {
                    execute {
                        appDb.httpTTSDao.get(intent.engineId)
                    }.onSuccess { tts ->
                        _uiState.update {
                            it.copy(
                                editingHttpTts = tts,
                                activeSheet = ReadBookSheet.HttpTtsEdit(intent.engineId),
                            )
                        }
                    }
                }
            }

            is ReadBookIntent.DeleteHttpTts -> {
                execute {
                    appDb.httpTTSDao.get(intent.engineId)?.let { tts ->
                        appDb.httpTTSDao.delete(tts)
                    }
                }.onSuccess {
                    loadTtsEngineItems()
                    if (ReadAloud.ttsEngine == intent.engineId.toString()) {
                        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                            readAloudSettingsRepository.update { it.copy(ttsEngine = null) }
                            ReadAloud.upReadAloudClass()
                        }
                    }
                }
            }

            is ReadBookIntent.SaveHttpTts -> {
                execute {
                    appDb.httpTTSDao.insert(intent.httpTTS)
                }.onSuccess {
                    loadTtsEngineItems()
                    _uiState.update {
                        it.copy(
                            editingHttpTts = null,
                            activeSheet = ReadBookSheet.SpeakEngineConfig,
                        )
                    }
                }
            }

            is ReadBookIntent.ApplySpeakEnginePerBook -> {
                ReadBook.book?.setTtsEngine(intent.value)
                ReadAloud.upReadAloudClass()
                _uiState.update {
                    it.copy(
                        selectedTtsEngine = ReadAloud.ttsEngine,
                        speakEngineName = computeSpeakEngineName(),
                        activeSheet = ReadBookSheet.ReadAloudConfig,
                    )
                }
            }

            is ReadBookIntent.OpenHttpTtsLogin -> {
                _effects.tryEmit(ReadBookEffect.OpenHttpTtsLogin(intent.engineId))
            }

            is ReadBookIntent.ImportHttpTtsJson -> {
                importHttpTtsSource(intent.json)
            }

            is ReadBookIntent.ImportHttpTtsSource -> {
                importHttpTtsSource(intent.text)
            }

            is ReadBookIntent.ExportAllHttpTts -> {
                _effects.tryEmit(ReadBookEffect.OpenHttpTtsExportPicker)
            }

            is ReadBookIntent.ExportAllHttpTtsAsUrl -> {
                execute {
                    val json = exportHttpTtsJson()
                    uploadRepository.upload(
                        fileName = "httpTTS.json",
                        file = json,
                        contentType = "application/json"
                    )
                }.onSuccess { url ->
                    context.sendToClip(url)
                    _effects.tryEmit(ReadBookEffect.ShowToast(context.getString(R.string.copy_url)))
                }
            }

            is ReadBookIntent.ExportHttpTtsToFile -> {
                execute {
                    val json = exportHttpTtsJson()
                    context.contentResolver.openOutputStream(intent.uri)?.use { os ->
                        os.write(json.toByteArray())
                    }
                }.onSuccess {
                    _effects.tryEmit(ReadBookEffect.ShowToast(context.getString(R.string.export_success)))
                }
            }

            is ReadBookIntent.ImportHttpTtsFile -> {
                _effects.tryEmit(ReadBookEffect.OpenHttpTtsImportPicker)
            }

            is ReadBookIntent.ImportHttpTtsFileSelected -> {
                execute<String?> {
                    val text = context.contentResolver.openInputStream(intent.uri)
                        ?.use { it.reader().readText() }
                    text
                }.onSuccess { text ->
                    if (!text.isNullOrBlank()) importHttpTtsSource(text)
                }
            }

            ReadBookIntent.CancelHttpTtsImport -> cancelHttpTtsImport()

            is ReadBookIntent.ToggleHttpTtsImportSelection -> {
                toggleHttpTtsImportSelection(intent.index)
            }

            is ReadBookIntent.ToggleHttpTtsImportAll -> {
                toggleHttpTtsImportAll(intent.isSelected)
            }

            is ReadBookIntent.UpdateHttpTtsImportItem -> {
                updateHttpTtsImportItem(intent.index, intent.httpTTS)
            }

            ReadBookIntent.SaveImportedHttpTts -> saveImportedHttpTts()

            is ReadBookIntent.SetReadAloudIgnoreAudioFocus -> {
                viewModelScope.launch {
                    readAloudSettingsRepository.update { it.copy(ignoreAudioFocus = intent.value) }
                }
            }
            is ReadBookIntent.SetReadAloudPauseOnPhoneCall -> {
                viewModelScope.launch {
                    readAloudSettingsRepository.update {
                        it.copy(pauseReadAloudWhilePhoneCalls = intent.value)
                    }
                }
            }
            is ReadBookIntent.SetReadAloudWakeLock -> {
                viewModelScope.launch {
                    readAloudSettingsRepository.update { it.copy(readAloudWakeLock = intent.value) }
                }
            }
            is ReadBookIntent.SetShowReadAloudCapsule -> {
                viewModelScope.launch {
                    readAloudSettingsRepository.update { it.copy(showReadAloudCapsule = intent.value) }
                }
            }
            is ReadBookIntent.SetCapsuleAutoCollapse -> {
                viewModelScope.launch {
                    readAloudSettingsRepository.update { it.copy(capsuleAutoCollapse = intent.value) }
                }
            }
            ReadBookIntent.ResetReadAloudCapsulePosition -> {
                _uiState.update { it.copy(
                    readAloudCapsuleOffsetX = 0f,
                    readAloudCapsuleOffsetY = 0f,
                ) }
                viewModelScope.launch {
                    readAloudSettingsRepository.update {
                        it.copy(capsuleOffsetX = 0f, capsuleOffsetY = 0f)
                    }
                }
            }
            is ReadBookIntent.SetReadAloudCapsulePosition -> {
                _uiState.update { it.copy(
                    readAloudCapsuleOffsetX = intent.x,
                    readAloudCapsuleOffsetY = intent.y,
                ) }
                viewModelScope.launch {
                    readAloudSettingsRepository.update {
                        it.copy(capsuleOffsetX = intent.x, capsuleOffsetY = intent.y)
                    }
                }
            }
            is ReadBookIntent.SetReadAloudMediaButtonPerNext -> {
                viewModelScope.launch {
                    readAloudSettingsRepository.update { it.copy(mediaButtonPerNext = intent.value) }
                }
            }
            is ReadBookIntent.SetReadAloudByPage -> {
                viewModelScope.launch {
                    readAloudSettingsRepository.update { it.copy(readAloudByPage = intent.value) }
                }
                if (intent.value) postEvent(EventBus.MEDIA_BUTTON, false)
            }
            is ReadBookIntent.SetReadAloudSystemMediaCompat -> {
                viewModelScope.launch {
                    readAloudSettingsRepository.update {
                        it.copy(systemMediaControlCompatibilityChange = intent.value)
                    }
                }
            }
            is ReadBookIntent.SetReadAloudStreamAudio -> {
                viewModelScope.launch {
                    readAloudSettingsRepository.update { it.copy(streamReadAloudAudio = intent.value) }
                }
                if (intent.value) postEvent(EventBus.MEDIA_BUTTON, false)
            }
            is ReadBookIntent.ReadAloudPrevParagraph -> ReadAloud.prevParagraph(context)
            is ReadBookIntent.ReadAloudTogglePause -> _effects.tryEmit(ReadBookEffect.ToggleReadAloud)
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
                viewModelScope.launch {
                    readAloudSettingsRepository.update { it.copy(ttsFollowSys = intent.value) }
                }
                _uiState.update { it.copy(readAloudTtsFollowSys = intent.value) }
            }
            is ReadBookIntent.SetReadAloudTtsSpeechRate -> setReadAloudTtsSpeechRate(intent.value)
            is ReadBookIntent.SetSpeechAnalysisMode -> {
                viewModelScope.launch {
                    if (intent.value != "rule") {
                        val configured = aiProfileGateway.getTaskPreset(AiTaskType.ANALYZE_SPEECH)
                            ?: aiProfileGateway.getTaskPreset(AiTaskType.CHAT)
                        if (configured == null) {
                            _effects.emit(ReadBookEffect.ShowToast(
                                context.getString(R.string.speech_analysis_ai_model_required)
                            ))
                            return@launch
                        }
                    }
                    readAloudSettingsRepository.update {
                        it.copy(speechAnalysisMode = intent.value)
                    }
                    _uiState.update { it.copy(speechAnalysisMode = intent.value) }
                }
            }
            is ReadBookIntent.SetUseMultiSpeaker -> {
                viewModelScope.launch {
                    val shouldRestart = BaseReadAloudService.isRun
                    val resumePlaying = shouldRestart && !BaseReadAloudService.pause
                    val chapter = ReadBook.curTextChapter
                    val chapterPosition = readAloudSessionStore.state.value.playback.chapterPosition
                    readAloudSettingsRepository.update {
                        it.copy(useMultiSpeaker = intent.value)
                    }
                    _uiState.update { it.copy(useMultiSpeaker = intent.value) }
                    if (shouldRestart && chapter != null) {
                        val pageIndex = chapter.getPageIndexByCharIndex(chapterPosition)
                        val startPos = chapterPosition - chapter.getReadLength(pageIndex)
                        ReadAloud.stop(context)
                        val stopped = withTimeoutOrNull(2_000) {
                            readAloudSessionStore.state.first {
                                it.status == ReadAloudSessionStatus.Idle
                            }
                        }
                        if (stopped == null) return@launch
                        ReadAloud.refreshReadAloudClass()
                        ReadAloud.play(
                            context = context,
                            play = resumePlaying,
                            pageIndex = pageIndex,
                            startPos = startPos.coerceAtLeast(0),
                        )
                    }
                }
            }
            is ReadBookIntent.SetDefaultReadAloudInterface -> {
                viewModelScope.launch {
                    readAloudSettingsRepository.update {
                        it.copy(
                            defaultInterface = intent.value.takeIf { value ->
                                value in ReadAloudSettingsRepository.AVAILABLE_INTERFACES
                            } ?: ReadAloudSettingsRepository.DEFAULT_INTERFACE_CLASSIC
                        )
                    }
                }
                _uiState.update { it.copy(defaultReadAloudInterface = intent.value) }
            }
            is ReadBookIntent.OpenSystemTtsSettings -> {
                _effects.tryEmit(ReadBookEffect.OpenSystemTtsSettings)
            }
            is ReadBookIntent.ClearTtsCache -> {
                io.legado.app.utils.TTSCacheUtils.clearTtsCache()
                _effects.tryEmit(ReadBookEffect.TtsCacheCleared(context.getString(R.string.clear_cache_success)))
            }
            ReadBookIntent.OpenTtsEnginesAndVoices -> {
                _uiState.update { it.copy(activeSheet = null) }
                _effects.tryEmit(ReadBookEffect.OpenTtsEnginesAndVoices)
            }
            ReadBookIntent.OpenTtsCache -> {
                _uiState.update { it.copy(activeSheet = null) }
                _effects.tryEmit(ReadBookEffect.OpenTtsCache)
            }
            ReadBookIntent.OpenBookVoiceCasting -> {
                ReadBook.book?.bookUrl?.let { bookUrl ->
                    _uiState.update { it.copy(activeSheet = null) }
                    _effects.tryEmit(ReadBookEffect.OpenBookVoiceCasting(bookUrl))
                }
            }
            ReadBookIntent.OpenReadAloudPlayer -> {
                _uiState.update {
                    it.copy(menuState = ReadBookMenuState(), activeSheet = ReadBookSheet.ReadAloudPlayer)
                }
            }
            ReadBookIntent.OpenClassicReadAloudControls -> {
                _uiState.update { it.copy(activeSheet = null) }
                openReadMenuRoute(ReadBookMenuRoute.ReadAloud)
            }

            is ReadBookIntent.SelectFont -> selectFont(intent.path)
            is ReadBookIntent.SelectTitleFont -> selectTitleFont(intent.path)
            is ReadBookIntent.SelectTitleSystemTypeface -> {
                readBookStyleConfigRepository.updateCurrentStyle(
                    stringMutation(ReadStyleStringKey.TitleFont, "")
                )
                viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    readSettingsRepository.setSystemTypefaces(intent.index)
                }
                _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(
                    setOf(ConfigUpdateAction.UpdateStyle, ConfigUpdateAction.ReloadContent)
                ))
            }
            is ReadBookIntent.SelectSystemTypeface -> {
                readBookStyleConfigRepository.updateCurrentStyle(
                    stringMutation(ReadStyleStringKey.TextFont, "")
                )
                viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    readSettingsRepository.setSystemTypefaces(intent.index)
                }
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
                _effects.tryEmit(
                    ReadBookEffect.OpenReadStyleExport(
                        readStyleExportFileName(_uiState.value.styleConfig.styleName)
                    )
                )
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
                        setOf(
                            ConfigUpdateAction.UpdateBackground,
                            ConfigUpdateAction.UpdateStyle,
                            ConfigUpdateAction.ReloadContent,
                            ConfigUpdateAction.UpdatePageAnim
                        )
                    ))
                }
            }
            is ReadBookIntent.ApplyPresetTheme -> {
                if (!readBookStyleConfigRepository.applyPreset(intent.presetIndex)) {
                    return@onIntent
                }
                _uiState.update { it.copy(styleConfig = buildStyleConfig()) }
                _effects.tryEmit(
                    ReadBookEffect.UpdateReadViewConfig(
                        setOf(
                            ConfigUpdateAction.UpdateBackground,
                            ConfigUpdateAction.UpdateBackgroundAlpha,
                            ConfigUpdateAction.UpdateStyle,
                            ConfigUpdateAction.UpdateSystemUi,
                            ConfigUpdateAction.ReloadContent,
                            ConfigUpdateAction.UpdatePageAnim
                        )
                    )
                )
            }
            is ReadBookIntent.OpenBgTextConfig -> {
                viewModelScope.launch {
                    readSettingsRepository.setStyleSelect(ReadSessionState.isComic, intent.index)
                }
                _uiState.update { it.copy(activeSheet = ReadBookSheet.BgTextConfig) }
            }

            is ReadBookIntent.ToggleDayNight -> toggleDayNight()
            // Text action menu
            is ReadBookIntent.TextActionAloud -> {
                when (readAloudSettingsRepository.currentSettings.contentSelectSpeakMode) {
                    1 -> intent.selectStartPos?.let {
                        _effects.tryEmit(ReadBookEffect.TextActionAloudSelect(it.copy()))
                    } ?: _effects.tryEmit(ReadBookEffect.TextActionSpeak(intent.text))
                    else -> _effects.tryEmit(ReadBookEffect.TextActionSpeak(intent.text))
                }
            }

            is ReadBookIntent.TextActionBookmark -> {
                _uiState.update {
                    it.copy(
                        menuState = ReadBookMenuState(),
                        activeSheet = ReadBookSheet.Bookmark(intent.bookmark),
                    )
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

            is ReadBookIntent.OpenAiTextClean -> {
                openAiTextClean(
                    text = intent.text,
                    chapterIndex = intent.chapterIndex,
                    chapterPosition = intent.chapterPosition,
                )
            }

            is ReadBookIntent.RetryAiTextClean -> retryAiTextClean()
            is ReadBookIntent.ConfirmAiTextClean -> confirmAiTextClean()

            is ReadBookIntent.OpenAiTextRewrite -> {
                openAiTextRewrite(
                    text = intent.text,
                    chapterIndex = intent.chapterIndex,
                    chapterPosition = intent.chapterPosition,
                )
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
            is ReadBookIntent.CloseReadBook -> closeReadBook(intent.keepReadAloud)
            is ReadBookIntent.OpenBooksDirPicker -> requestBooksDirPicker(reloadChapterList = false)
            is ReadBookIntent.BooksDirSelected -> onBooksDirSelected(intent.uri)

            ReadBookIntent.ToggleEyeProtection -> toggleEyeProtection()
            is ReadBookIntent.EyeProtectionEnabledChanged -> {
                viewModelScope.launch {
                    readSettingsRepository.update { it.copy(eyeProtectionEnabled = intent.value) }
                }
            }
            is ReadBookIntent.EyeProtectionIntensityChanged -> {
                viewModelScope.launch {
                    readSettingsRepository.update { it.copy(eyeProtectionIntensity = intent.value) }
                }
            }
            is ReadBookIntent.EyeProtectionAutoNightChanged -> {
                viewModelScope.launch {
                    readSettingsRepository.update { it.copy(eyeProtectionAutoNight = intent.value) }
                }
            }
            is ReadBookIntent.SyncEyeProtectionForTheme -> {
                EyeProtection.syncEnabledForNight(
                    isNight = intent.isNight,
                    autoNight = _readPreferences.value.eyeProtectionAutoNight,
                )?.let { enabled ->
                    viewModelScope.launch {
                        readSettingsRepository.update {
                            it.copy(eyeProtectionEnabled = enabled)
                        }
                    }
                }
            }
        }
    }

    // --- Lifecycle handlers (migrated from ReadBookController) ---

    private fun handleOnResume() {
        // Read time tracking
        ReadBook.isUiActive = true
        ReadBook.startReadSession()

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
        ReadBook.isUiActive = false
        ReadBook.saveRead()
        if (!BaseReadAloudService.isPlay()) {
            ReadBook.stopAutoSaveSession()
            ReadBook.commitReadSession()
        }
        ReadBook.cancelPreDownloadTask()

        // View-layer
        _effects.tryEmit(ReadBookEffect.UpSystemUiVisibility)

        // Activity-level operations
        _effects.tryEmit(ReadBookEffect.UnregisterTimeBatteryReceiver)
        _effects.tryEmit(ReadBookEffect.UnregisterNetworkListener)

        if (!BuildConfig.DEBUG) {
            if (backupSettingsGateway.currentSettings.syncBookProgressPlus) {
                ReadBook.syncProgress()
            } else {
                ReadBook.uploadProgress()
            }
            _effects.tryEmit(ReadBookEffect.BackupNow)
        }
        justInitData = false
    }

    private fun handleOnDispose() {
        backupJob?.cancel()
        ReadBook.cancelPreDownloadTask()
    }

    private fun showSpeakEngineConfig() {
        loadTtsEngineItems {
            _uiState.update { it.copy(activeSheet = ReadBookSheet.SpeakEngineConfig) }
        }
    }

    private fun loadTtsEngineItems(onSuccess: (() -> Unit)? = null) {
        execute {
            val systemTtsLabel = context.getString(R.string.system_tts)
            val httpTtsList = appDb.httpTTSDao.all
            syncConfiguredTtsVoices(systemTtsLabel, httpTtsList)
            buildList {
                add(ReadBookTtsEngineItem(systemTtsLabel, null))
                sysEngines.forEach { engine ->
                    add(
                        ReadBookTtsEngineItem(
                            title = engine.label,
                            value = GSON.toJson(SelectItem(engine.label, engine.name)),
                        )
                    )
                }
                httpTtsList.forEach { httpTts ->
                    add(
                        ReadBookTtsEngineItem(
                            title = httpTts.name,
                            value = httpTts.id.toString(),
                            loginUrl = httpTts.loginUrl,
                        )
                    )
                }
            }
        }.onSuccess { items ->
            _uiState.update {
                it.copy(
                    ttsEngineItems = items.toImmutableList(),
                    selectedTtsEngine = ReadAloud.ttsEngine,
                )
            }
            onSuccess?.invoke()
        }
    }

    private suspend fun syncConfiguredTtsVoices(
        systemTtsLabel: String = context.getString(R.string.system_tts),
        httpTtsList: List<HttpTTS> = appDb.httpTTSDao.all,
    ) {
        syncReadAloudVoicesUseCase(
            entries = buildList {
                add(
                    VoiceCatalogEntry(
                        engineType = ReadAloudVoice.ENGINE_SYSTEM,
                        engineId = "",
                        displayName = systemTtsLabel,
                    )
                )
                sysEngines.forEach { engine ->
                    add(
                        VoiceCatalogEntry(
                            engineType = ReadAloudVoice.ENGINE_SYSTEM,
                            engineId = engine.name,
                            displayName = engine.label,
                        )
                    )
                }
                httpTtsList.forEach { httpTts ->
                    add(
                        VoiceCatalogEntry(
                            engineType = ReadAloudVoice.ENGINE_HTTP,
                            engineId = httpTts.id.toString(),
                            displayName = httpTts.name,
                            sourceRevision = httpTts.lastUpdateTime,
                        )
                    )
                }
            },
            managedSources = setOf(ReadAloudVoice.MANAGED_BY_CONFIGURED_TTS),
            removeMissingEngineTypes = setOf(ReadAloudVoice.ENGINE_HTTP),
        )
    }

    private fun importHttpTtsSource(text: String) {
        _uiState.update {
            it.copy(httpTtsImportState = BaseImportUiState.Loading)
        }
        execute {
            val list = importHttpTtsSourceAwait(text.trim())
            val items = list.map { httpTTS ->
                val old = appDb.httpTTSDao.get(httpTTS.id)
                val status = when {
                    old == null -> ImportStatus.New
                    httpTTS.lastUpdateTime > old.lastUpdateTime -> ImportStatus.Update
                    else -> ImportStatus.Existing
                }
                ImportItemWrapper(
                    data = httpTTS,
                    oldData = old,
                    status = status,
                    isSelected = status != ImportStatus.Existing,
                )
            }
            if (items.isEmpty()) {
                throw NoStackTraceException(context.getString(R.string.wrong_format))
            }
            BaseImportUiState.Success(
                source = text,
                items = items,
            )
        }.onSuccess { importState ->
            _uiState.update {
                it.copy(httpTtsImportState = importState)
            }
        }.onError {
            AppLog.put("导入朗读引擎失败\n${it.localizedMessage}", it, true)
            _uiState.update { state ->
                state.copy(
                    httpTtsImportState = BaseImportUiState.Error(
                        it.localizedMessage ?: context.getString(R.string.wrong_format)
                    )
                )
            }
        }
    }

    private suspend fun importHttpTtsSourceAwait(text: String): List<HttpTTS> {
        return when {
            text.isHttpTtsImportUri() -> {
                val src = Uri.parse(text).getQueryParameter("src")
                    ?: throw NoStackTraceException(context.getString(R.string.wrong_format))
                importHttpTtsSourceAwait(src)
            }
            text.isJsonObject() -> listOf(HttpTTS.fromJson(text).getOrThrow())
            text.isJsonArray() -> HttpTTS.fromJsonArray(text).getOrThrow()
            text.isDataUrl() -> {
                val data = AppPattern.dataUriRegex.find(text)?.groupValues?.getOrNull(1)
                    ?: throw NoStackTraceException(context.getString(R.string.wrong_format))
                val body = Base64.decode(data, Base64.DEFAULT).toString(Charsets.UTF_8)
                importHttpTtsSourceAwait(body)
            }
            text.isAbsUrl() -> {
                val body = okHttpClient.newCallResponseBody {
                    if (text.endsWith("#requestWithoutUA")) {
                        url(text.substringBeforeLast("#requestWithoutUA"))
                        header(AppConst.UA_NAME, "null")
                    } else {
                        url(text)
                    }
                }.decompressed().text()
                importHttpTtsSourceAwait(body)
            }
            else -> throw NoStackTraceException(context.getString(R.string.wrong_format))
        }
    }

    private fun cancelHttpTtsImport() {
        _uiState.update {
            it.copy(
                httpTtsImportState = BaseImportUiState.Idle,
                activeSheet = ReadBookSheet.SpeakEngineConfig,
            )
        }
    }

    private fun toggleHttpTtsImportSelection(index: Int) {
        val importState = _uiState.value.httpTtsImportState
            as? BaseImportUiState.Success<HttpTTS> ?: return
        if (index !in importState.items.indices) return
        val items = importState.items.toMutableList()
        val item = items[index]
        items[index] = item.copy(isSelected = !item.isSelected)
        _uiState.update {
            it.copy(httpTtsImportState = importState.copy(items = items))
        }
    }

    private fun toggleHttpTtsImportAll(isSelected: Boolean) {
        val importState = _uiState.value.httpTtsImportState
            as? BaseImportUiState.Success<HttpTTS> ?: return
        _uiState.update {
            it.copy(
                httpTtsImportState = importState.copy(
                    items = importState.items.map { item ->
                        item.copy(isSelected = isSelected)
                    }
                )
            )
        }
    }

    private fun updateHttpTtsImportItem(index: Int, httpTTS: HttpTTS) {
        val importState = _uiState.value.httpTtsImportState
            as? BaseImportUiState.Success<HttpTTS> ?: return
        if (index !in importState.items.indices) return
        val items = importState.items.toMutableList()
        items[index] = items[index].copy(data = httpTTS)
        _uiState.update {
            it.copy(
                httpTtsImportState = importState.copy(
                    items = items,
                    version = importState.version + 1,
                )
            )
        }
    }

    private fun saveImportedHttpTts() {
        val importState = _uiState.value.httpTtsImportState
            as? BaseImportUiState.Success<HttpTTS> ?: return
        val selected = importState.items
            .filter { it.isSelected }
            .map { it.data }
        if (selected.isEmpty()) return
        execute {
            appDb.httpTTSDao.insert(*selected.toTypedArray())
        }.onSuccess {
            loadTtsEngineItems()
            _uiState.update {
                it.copy(
                    httpTtsImportState = BaseImportUiState.Idle,
                    activeSheet = ReadBookSheet.SpeakEngineConfig,
                )
            }
            _effects.tryEmit(ReadBookEffect.ShowToast(context.getString(R.string.success)))
        }.onError {
            AppLog.put("保存朗读引擎失败\n${it.localizedMessage}", it, true)
            _uiState.update { state ->
                state.copy(
                    httpTtsImportState = BaseImportUiState.Error(
                        it.localizedMessage ?: context.getString(R.string.wrong_format)
                    )
                )
            }
        }
    }

    private fun exportHttpTtsJson(): String {
        return GSON.toJson(appDb.httpTTSDao.all)
    }

    private fun computeSpeakEngineName(): String {
        val ttsEngine = ReadAloud.ttsEngine
            ?: return context.getString(R.string.system_tts)
        if (StringUtils.isNumeric(ttsEngine)) {
            return appDb.httpTTSDao.getName(ttsEngine.toLong())
                ?: context.getString(R.string.system_tts)
        }
        return GSON.fromJsonObject<SelectItem<String>>(ttsEngine)
            .getOrNull()?.title
            ?: context.getString(R.string.system_tts)
    }

    /**
     * Called from the network changed listener (registered by route).
     */
    fun onNetworkChanged() {
        if (
            backupSettingsGateway.currentSettings.syncBookProgressPlus &&
            NetworkUtils.isAvailable() &&
            !justInitData
        ) {
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
            if (readSettingsRepository.currentSettings.defaultSourceChangeAll) {
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
                _uiState.update {
                    it.copy(
                        menuState = ReadBookMenuState(),
                        activeSheet = ReadBookSheet.Bookmark(bookmark),
                    )
                }
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
            ReadBookEffect.UpContent(relativePosition, resetPageOffset, success)
        )
    }

    override suspend fun upContentAwait(
        relativePosition: Int,
        resetPageOffset: Boolean,
        success: (() -> Unit)?
    ) {
        withContext(Main.immediate) {
            _uiState.update { syncFromReadBook(it) }
            _effects.tryEmit(
                ReadBookEffect.UpContent(relativePosition, resetPageOffset, success)
            )
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
                        12 -> ConfigUpdateAction.RelayoutContent
                        else -> null
                    }
                }.toSet()
                if (actions.isNotEmpty()) {
                    emitEffectWhenSubscribed(ReadBookEffect.UpdateReadViewConfig(actions))
                }
            }
        }
        viewModelScope.launch {
            var previousStatus: ReadAloudSessionStatus? = null
            readAloudSessionStore.state.collect { session ->
                val status = session.status
                val info = session.playback
                _uiState.update { state ->
                    state.copy(
                        isReadAloudRunning = status != ReadAloudSessionStatus.Idle,
                        isReadAloudPaused = status == ReadAloudSessionStatus.Paused,
                        readAloudEngineName = info.engineName,
                        readAloudCharacterName = info.characterName,
                        readAloudRoleType = info.roleType,
                        readAloudChapterPosition = info.chapterPosition,
                        readAloudChapterLength = info.chapterLength,
                        readAloudTtsTimer = session.timerMinutes,
                    )
                }
                if (previousStatus != null && previousStatus != status &&
                    (status == ReadAloudSessionStatus.Idle ||
                        status == ReadAloudSessionStatus.Paused)
                ) {
                    _readAloudProgress.value = null
                    _effects.tryEmit(ReadBookEffect.UpAloudState)
                }
                if (previousStatus != ReadAloudSessionStatus.Paused &&
                    status == ReadAloudSessionStatus.Paused
                ) {
                    _effects.tryEmit(
                        ReadBookEffect.ShowToast(context.getString(R.string.read_aloud_pause))
                    )
                }
                previousStatus = status
            }
        }
        viewModelScope.launch {
            eventFlow<Int>(EventBus.READ_ALOUD_DS).collect { minute ->
                _uiState.update { it.copy(readAloudTtsTimer = minute.coerceAtLeast(0)) }
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
                updateReadAloudProgress(chapterStart)
            }
        }
    }

    private fun updateReadAloudProgress(chapterStart: Int) {
        if (BaseReadAloudService.isPlay() && chapterStart > 0) {
            _readAloudProgress.value = chapterStart
        }
    }

    private fun collectReadPreferences() {
        viewModelScope.launch {
            var previous: ReadPreferences? = null
            readSettingsRepository.preferences.collect { preferences ->
                val old = previous
                previous = preferences
                _readPreferences.value = preferences
                _uiState.update { syncFromReadBook(it) }
                if (!preferences.hasMenuClickArea()) {
                    readSettingsRepository.setClickAction(PreferKey.clickActionMC, 0)
                }
                if (old != null && old.keepLight != preferences.keepLight) {
                    _effects.tryEmit(ReadBookEffect.UpScreenTimeOut)
                }
                if (old != null && old.screenOrientation != preferences.screenOrientation) {
                    _effects.tryEmit(ReadBookEffect.SetOrientation)
                }
            }
        }
    }

    private suspend fun syncReadPreferencesSnapshot() {
        val preferences = readSettingsRepository.preferences.first()
        _readPreferences.value = preferences
    }

    private fun collectReadAloudPreferences() {
        viewModelScope.launch {
            readAloudSettingsRepository.preferences.collect { prefs ->
                _uiState.update {
                    it.copy(
                        readAloudIgnoreAudioFocus = prefs.ignoreAudioFocus,
                        readAloudPauseOnPhoneCall = prefs.pauseReadAloudWhilePhoneCalls,
                        readAloudWakeLock = prefs.readAloudWakeLock,
                        showReadAloudCapsule = prefs.showReadAloudCapsule,
                        capsuleAutoCollapse = prefs.capsuleAutoCollapse,
                        readAloudCapsuleOffsetX = prefs.capsuleOffsetX,
                        readAloudCapsuleOffsetY = prefs.capsuleOffsetY,
                        readAloudMediaButtonPerNext = prefs.mediaButtonPerNext,
                        readAloudByPage = prefs.readAloudByPage,
                        readAloudSystemMediaCompat = prefs.systemMediaControlCompatibilityChange,
                        readAloudStreamAudio = prefs.streamReadAloudAudio,
                        readAloudTtsFollowSys = prefs.ttsFollowSys,
                        readAloudTtsSpeechRate = prefs.ttsSpeechRate,
                        readAloudTtsTimer = prefs.ttsTimer,
                        speechAnalysisMode = prefs.speechAnalysisMode,
                        useMultiSpeaker = prefs.useMultiSpeaker,
                        defaultReadAloudInterface = prefs.defaultInterface,
                        selectedTtsEngine = prefs.ttsEngine,
                        preDownloadNum = _readPreferences.value.preDownloadNum,
                        audioCacheCleanTime = prefs.audioCacheCleanTime,
                        readAloudParagraphInterval = prefs.ttsParagraphInterval,
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
        val timer = PlaybackTimer.normalize(value)
        ReadAloud.setTimer(context, timer)
        viewModelScope.launch {
            readAloudSettingsRepository.update { it.copy(ttsTimer = timer) }
        }
        _uiState.update { it.copy(readAloudTtsTimer = timer) }
    }

    private fun setReadAloudTtsSpeechRate(value: Int) {
        viewModelScope.launch {
            readAloudSettingsRepository.update {
                it.copy(ttsSpeechRate = value.coerceIn(0, 80))
            }
            ReadAloud.upTtsSpeechRate(context)
        }
        _uiState.update { it.copy(readAloudTtsSpeechRate = value) }
    }

    fun setFontFolder(value: String) {
        viewModelScope.launch {
            readSettingsRepository.setFontFolder(value)
        }
    }

    private fun toggleEyeProtection() {
        viewModelScope.launch {
            readSettingsRepository.update {
                it.copy(eyeProtectionEnabled = !_readPreferences.value.eyeProtectionEnabled)
            }
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
        val actualConfig = config.config
        val dur = config.durConfig
        val styleState = readBookStyleConfigRepository.currentState
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
            textFont = config.textFont,
            titleFont = config.titleFont,
            pageAnim = actualConfig.getPageAnim(),
            pageAnimEInk = actualConfig.getPageAnimEInk(),
            shareLayout = config.shareLayout,
            configCount = styleState.items.size,
            styleItems = styleState.items.toImmutableList(),
        )
    }

    private fun buildSheetConfig(): ReadSheetConfigUiState = ReadSheetConfigUiState(
        letterSpacing = ReadBookConfig.letterSpacing,
        lineSpacing = ReadBookConfig.lineSpacingExtra,
        paragraphSpacing = ReadBookConfig.paragraphSpacing,
        paragraphIndentCount = ReadBookConfig.paragraphIndent.length,
        textItalic = ReadBookConfig.textItalic,
        textBold = ReadBookConfig.textBold,
        chineseConverterType = readSettingsRepository.currentSettings.chineseConverterType,
        textColor = ReadBookConfig.durConfig.curTextColor(),
        textAccentColor = ReadBookConfig.durConfig.curTextAccentColor(),
        titleMode = ReadBookConfig.titleMode,
        titleBold = ReadBookConfig.titleBold,
        titleSegType = ReadBookConfig.titleSegType,
        titleSegDistance = ReadBookConfig.titleSegDistance,
        titleSegFlag = ReadBookConfig.titleSegFlag,
        titleSegScaling = ReadBookConfig.titleSegScaling,
        titleLineSpacingExtra = ReadBookConfig.titleLineSpacingExtra,
        titleLineSpacingSub = ReadBookConfig.titleLineSpacingSub,
        titleSize = ReadBookConfig.titleSize,
        titleTopSpacing = ReadBookConfig.titleTopSpacing,
        titleBottomSpacing = ReadBookConfig.titleBottomSpacing,
        titleColor = ReadBookConfig.titleColor,
        titleColorNight = ReadBookConfig.titleColorNight,
        textColorDay = ReadBookConfig.textColor,
        textColorNight = ReadBookConfig.textColorNight,
        textShadow = ReadBookConfig.textShadow,
        textShadowColor = ReadBookConfig.durConfig.curTextShadowColor(),
        shadowRadius = ReadBookConfig.shadowRadius,
        shadowDx = ReadBookConfig.shadowDx,
        shadowDy = ReadBookConfig.shadowDy,
        underline = ReadBookConfig.underline,
        dottedLine = ReadBookConfig.dottedLine,
        underlineExtend = ReadBookConfig.underlineExtend,
        underlineColor = ReadBookConfig.durConfig.curUnderlineColor(),
        underlineHeight = ReadBookConfig.underlineHeight,
        underlinePadding = ReadBookConfig.underlinePadding,
        dottedBase = ReadBookConfig.durConfig.dottedBase,
        dottedRatio = ReadBookConfig.durConfig.dottedRatio,
        paddingTop = ReadBookConfig.paddingTop,
        paddingBottom = ReadBookConfig.paddingBottom,
        paddingLeft = ReadBookConfig.paddingLeft,
        paddingRight = ReadBookConfig.paddingRight,
        headerPaddingTop = ReadBookConfig.headerPaddingTop,
        headerPaddingBottom = ReadBookConfig.headerPaddingBottom,
        headerPaddingLeft = ReadBookConfig.headerPaddingLeft,
        headerPaddingRight = ReadBookConfig.headerPaddingRight,
        footerPaddingTop = ReadBookConfig.footerPaddingTop,
        footerPaddingBottom = ReadBookConfig.footerPaddingBottom,
        footerPaddingLeft = ReadBookConfig.footerPaddingLeft,
        footerPaddingRight = ReadBookConfig.footerPaddingRight,
        configNames = readBookStyleConfigRepository.currentState.items.map { it.name }
            .filter { it.isNotBlank() }
            .toImmutableList(),
    )

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
            replaceRuleEnabled = book?.getUseReplaceRule(
                otherSettingsGateway.currentSettings.replaceEnableDefault
            ) ?: false,
            effectiveReplaceCount = textChapter?.effectiveReplaceRules?.size ?: 0,
            effectiveContentProcessCount = textChapter?.effectiveContentProcesses?.size ?: 0,
            effectiveReplaceRules = textChapter?.effectiveReplaceRules.orEmpty().toImmutableList(),
            chineseConverterActive = readSettingsRepository.currentSettings.chineseConverterType > 0,
            translationMode = book?.getTranslationMode() ?: false,
            isLocalTxt = book?.isLocalTxt == true,
            isEpub = book?.isEpub == true,
            useReplaceRule = book?.getUseReplaceRule(
                otherSettingsGateway.currentSettings.replaceEnableDefault
            ) ?: false,
            reSegment = book?.getReSegment() ?: false,
            delRubyTag = book?.getDelTag(Book.rubyTag) ?: false,
            delHTag = book?.getDelTag(Book.hTag) ?: false,
            sameTitleRemoved = textChapter?.sameTitleRemoved ?: false,
            isReadingProgressSyncConfigured = isReadingProgressSyncConfigured(),
            styleConfig = buildStyleConfig(),
            sheetConfig = buildSheetConfig(),
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
                readMenuTextColor = ReadBookConfig.readMenuTextColor,
                readMenuTextColorNight = ReadBookConfig.readMenuTextColorNight,
                readMenuBlurAlpha = ReadBookConfig.readMenuBlurAlpha,
                readMenuBlurColor = ReadBookConfig.readMenuBlurColor,
                readMenuBlurColorNight = ReadBookConfig.readMenuBlurColorNight,
                readMenuPaletteStyle = ReadBookConfig.readMenuPaletteStyle,
                readMenuBlurRadius = ReadBookConfig.readMenuBlurRadius,
                readMenuLensRadius = ReadBookConfig.readMenuLensRadius,
                readMenuTopBarBlurMode = ReadBookConfig.readMenuTopBarBlurMode,
                readMenuBottomBarBlurMode = ReadBookConfig.readMenuBottomBarBlurMode,
                readMenuTopBarLiquidGlassButtons = ReadBookConfig.readMenuTopBarLiquidGlassButtons,
                readMenuTopBarTitleCapsule = ReadBookConfig.readMenuTopBarTitleCapsule,
                readMenuBottomBarLiquidGlassButtons = ReadBookConfig.readMenuBottomBarLiquidGlassButtons,
                readMenuFloatingIconLiquidGlass = ReadBookConfig.readMenuFloatingIconLiquidGlass,
                readMenuTopBarBlurStyle = ReadBookConfig.readMenuTopBarBlurStyle,
                readMenuBottomBarBlurStyle = ReadBookConfig.readMenuBottomBarBlurStyle,
                readMenuIconStyle = ReadBookConfig.readMenuIconStyle,
                titleBarIconStyle = ReadBookConfig.titleBarIconStyle,
                readMenuIconShowText = ReadBookConfig.readMenuIconShowText,
                readSliderMode = ReadBookConfig.readSliderMode,
                titleBarCustomIcons = ReadBookConfig.titleBarCustomIcons.toImmutableMap(),
                readMenuCustomIcons = ReadBookConfig.readMenuCustomIcons.toImmutableMap(),
                titleBarButtons = current.menuConfig.titleBarButtons,
                bottomBarButtons = current.menuConfig.bottomBarButtons,
                showBrightnessView = ReadBookConfig.showBrightnessView,
                brightnessVwPos = ReadBookConfig.brightnessVwPos,
                readBrightness = ReadBookConfig.readBrightness,
                brightnessAuto = ReadBookConfig.brightnessAuto,
                showMenuIcon = ReadBookConfig.showMenuIcon,
                titleBarCompact = ReadBookConfig.titleBarCompact,
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
            ReadBookButtonIds.map { id ->
                ReadBookButtonConfigItem(
                    id = id,
                    enabled = id in DEFAULT_ENABLED_BUTTON_IDS,
                )
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
                normalized.add(ReadBookButtonConfigItem(id, false))
            }
        }
        return normalized
    }

    private fun calculateSeekProgress(): Int {
        return when (readSettingsRepository.currentSettings.progressBarBehavior) {
            "page" -> ReadBook.durPageIndex
            else -> ReadBook.durChapterIndex
        }
    }

    private fun calculateSeekMax(): Int {
        return when (readSettingsRepository.currentSettings.progressBarBehavior) {
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
            syncReadPreferencesSnapshot()
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

    fun markJustInitData() {
        justInitData = true
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
            if (backupSettingsGateway.currentSettings.syncBookProgressPlus) {
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
        if (!backupSettingsGateway.currentSettings.syncBookProgress) return
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
            applyChangeSource(book, toc)
        }.onSuccess {
            postEvent(EventBus.SOURCE_CHANGED, book.bookUrl)
        }.onError {
            AppLog.put("换源失败\n$it", it, true)
            ReadBook.upMsg(null)
        }
    }

    fun changeTo(book: Book) {
        changeSourceCoroutine?.cancel()
        changeSourceCoroutine = execute {
            ReadBook.upMsg(context.getString(R.string.loading))
            val source = appDb.bookSourceDao.getBookSource(book.origin)
                ?: throw NoStackTraceException("书源不存在")
            if (book.tocUrl.isEmpty()) {
                WebBook.getBookInfoAwait(source, book)
            }
            val toc = WebBook.getChapterListAwait(source, book).getOrThrow()
            applyChangeSource(book, toc)
        }.onSuccess {
            postEvent(EventBus.SOURCE_CHANGED, book.bookUrl)
        }.onError {
            AppLog.put("换源失败\n$it", it, true)
            ReadBook.upMsg(null)
        }
    }

    private suspend fun applyChangeSource(book: Book, toc: List<BookChapter>) {
        if (toc.isEmpty()) {
            throw NoStackTraceException("换源目录为空")
        }
        val oldBook = ReadBook.book ?: throw NoStackTraceException("书籍不存在")
        changeBookSourceUseCase.changeTo(
            oldBook = oldBook,
            newBook = book,
            chapters = toc,
            options = changeSourceSettingsGateway.currentSettings.migrationOptions(),
        )
        ReadBook.resetData(book)
        ReadBook.upMsg(null)
        ReadBook.loadContent(resetPageOffset = true)
    }

    private fun autoChangeSource(name: String, author: String) {
        if (!readSettingsRepository.currentSettings.autoChangeSource) return
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
            }.mapParallelSafe(downloadCacheSettingsGateway.currentSettings.threadCount) { source ->
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
                _effects.tryEmit(ReadBookEffect.ShowToast("自动换源失败\n${it.localizedMessage}"))
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

    private var closeReadBookKeepReadAloud = false

    private fun closeReadBook(keepReadAloud: Boolean = false) {
        closeReadBookKeepReadAloud = keepReadAloud
        val book = ReadBook.book
        if (!ReadBook.inBookshelf && book != null && otherSettingsGateway.currentSettings.showAddToShelfAlert) {
            _uiState.update {
                it.copy(activeDialog = ReadBookDialog.ConfirmAddToBookshelf(book.name))
            }
        } else if (!ReadBook.inBookshelf) {
            removeCurrentNotShelfBookAndFinish()
        } else {
            stopReadAloudForClose()
            _effects.tryEmit(ReadBookEffect.Finish)
        }
    }

    private fun stopReadAloudForClose() {
        if (closeReadBookKeepReadAloud || !BaseReadAloudService.isRun) {
            return
        }
        ReadAloud.stop(context)
        _uiState.update { it.copy(isReadAloudRunning = false, isReadAloudPaused = false) }
    }

    private fun addCurrentBookToBookshelfAndFinish() {
        val book = ReadBook.book ?: return removeCurrentNotShelfBookAndFinish()
        execute {
            val toc = appDb.bookChapterDao.getChapterList(book.bookUrl)
            book.removeType(BookType.notShelf)
            if (book.order == 0) {
                book.order = appDb.bookDao.minOrder - 1
            }
            appDb.bookDao.insert(book)
            if (toc.isNotEmpty()) {
                appDb.bookChapterDao.insert(*toc.toTypedArray())
            }
            ReadBook.inBookshelf = true
        }.onSuccess {
            _uiState.update { it.copy(activeDialog = null) }
            stopReadAloudForClose()
            _effects.tryEmit(ReadBookEffect.Finish)
        }.onError {
            AppLog.put("添加书籍到书架失败", it)
            _effects.tryEmit(ReadBookEffect.ShowToast("添加书籍失败"))
        }
    }

    private fun removeCurrentNotShelfBookAndFinish() {
        _uiState.update { it.copy(activeDialog = null) }
        removeFromBookshelf {
            stopReadAloudForClose()
            _effects.tryEmit(ReadBookEffect.Finish)
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

    private fun openChapterSummary() {
        val book = ReadBook.book ?: return
        val chapterIndex = ReadBook.durChapterIndex
        val chapterTitle = _uiState.value.chapterName
        closeReadMenu()
        _uiState.update {
            it.copy(
                activeSheet = ReadBookSheet.ChapterSummary,
                chapterSummary = ChapterSummaryUiState(
                    bookUrl = book.bookUrl,
                    chapterIndex = chapterIndex,
                    chapterTitle = chapterTitle,
                    isLoading = true,
                ),
            )
        }
        generateChapterSummary(book.bookUrl, chapterIndex)
    }

    private fun retryChapterSummary() {
        val summary = _uiState.value.chapterSummary
        if (summary.bookUrl.isBlank() || summary.chapterIndex < 0) return
        _uiState.update {
            it.copy(
                chapterSummary = summary.copy(
                    isLoading = true,
                    summary = "",
                    reasoningText = "",
                    thinkingDuration = 0,
                    errorMessage = null,
                )
            )
        }
        generateChapterSummary(summary.bookUrl, summary.chapterIndex)
    }

    private fun generateChapterSummary(bookUrl: String, chapterIndex: Int) {
        chapterSummaryJob?.cancel()
        chapterSummaryJob = viewModelScope.launch {
            val book = ReadBook.book
            if (book == null || book.bookUrl != bookUrl) {
                updateChapterSummaryError(
                    bookUrl,
                    chapterIndex,
                    context.getString(R.string.ai_chapter_changed),
                )
                return@launch
            }
            val chapter = withContext(IO) {
                appDb.bookChapterDao.getChapter(bookUrl, chapterIndex)
            }
            if (chapter == null) {
                updateChapterSummaryError(
                    bookUrl,
                    chapterIndex,
                    context.getString(R.string.no_chapter),
                )
                return@launch
            }
            val content = withContext(IO) {
                getEffectiveChapterContent(book, chapter)
            }
            if (content.isBlank()) {
                updateChapterSummaryError(
                    bookUrl,
                    chapterIndex,
                    context.getString(R.string.ai_chapter_content_unavailable),
                )
                return@launch
            }
            try {
                withContext(IO) {
                    generateChapterSummaryUseCase.start(
                        book = book,
                        bookChapter = chapter,
                        contentOverride = content,
                    )
                }
                generateChapterSummaryUseCase.observeTask(bookUrl, chapterIndex).collect { task ->
                    if (!isCurrentChapterSummary(
                            bookUrl,
                            chapterIndex
                        ) || task == null
                    ) return@collect
                    _uiState.update { state ->
                        val summary = state.chapterSummary
                        when (task.status) {
                            AiArtifact.STATUS_RUNNING -> state.copy(
                                chapterSummary = summary.copy(
                                    isLoading = true,
                                    summary = task.output.orEmpty(),
                                    reasoningText = task.reasoning,
                                    errorMessage = null,
                                ),
                            )

                            AiArtifact.STATUS_SUCCESS -> state.copy(
                                chapterSummary = summary.copy(
                                    isLoading = false,
                                    summary = task.output.orEmpty(),
                                    reasoningText = task.reasoning,
                                    errorMessage = null,
                                ),
                            )

                            AiArtifact.STATUS_FAILED -> state.copy(
                                chapterSummary = summary.copy(
                                    isLoading = false,
                                    errorMessage = task.errorMessage
                                        ?: context.getString(R.string.load_failed),
                                ),
                            )

                            else -> state
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                updateChapterSummaryError(
                    bookUrl,
                    chapterIndex,
                    aiErrorMessage(error),
                )
            }
        }
    }

    private fun getEffectiveChapterContent(book: Book, chapter: BookChapter): String {
        val sourceContent = if (book.getTranslationMode()) {
            TranslationManager.getCachedTranslation(book, chapter)
                ?: BookHelp.getContent(book, chapter)
        } else {
            BookHelp.getContent(book, chapter)
        } ?: return ""
        return ContentProcessor.get(book)
            .getContent(book, chapter, sourceContent, includeTitle = false)
            .toString()
    }

    private fun isCurrentChapterSummary(bookUrl: String, chapterIndex: Int): Boolean {
        val state = _uiState.value
        return state.activeSheet is ReadBookSheet.ChapterSummary &&
                state.chapterSummary.bookUrl == bookUrl &&
                state.chapterSummary.chapterIndex == chapterIndex
    }

    private fun updateChapterSummaryError(
        bookUrl: String,
        chapterIndex: Int,
        message: String,
    ) {
        if (!isCurrentChapterSummary(bookUrl, chapterIndex)) return
        _uiState.update {
            it.copy(
                chapterSummary = it.chapterSummary.copy(
                    isLoading = false,
                    errorMessage = message,
                )
            )
        }
    }

    private fun openAiTextClean(
        text: String,
        chapterIndex: Int,
        chapterPosition: Int,
    ) {
        val book = ReadBook.book ?: return
        if (text.isBlank()) {
            _effects.tryEmit(
                ReadBookEffect.ShowToast(context.getString(R.string.ai_text_clean_empty_selection))
            )
            return
        }
        if (chapterIndex != ReadBook.durChapterIndex) {
            _effects.tryEmit(
                ReadBookEffect.ShowToast(context.getString(R.string.ai_chapter_changed))
            )
            return
        }
        val chapterTitle = _uiState.value.chapterName
        val visibleContent = ReadBook.curTextChapter?.getContent().orEmpty()
        val (contextBefore, contextAfter) = buildSelectionContext(
            content = visibleContent,
            selectedText = text,
            approximatePosition = chapterPosition,
        )
        val request = PendingAiTextCleanRequest(
            bookUrl = book.bookUrl,
            chapterIndex = chapterIndex,
            chapterTitle = chapterTitle,
            chapterPosition = chapterPosition,
            originalText = text,
            contextBefore = contextBefore,
            contextAfter = contextAfter,
        )
        pendingAiTextCleanRequest = request
        closeReadMenu()
        _uiState.update {
            it.copy(
                activeSheet = ReadBookSheet.AiTextClean,
                aiTextClean = AiTextCleanUiState(
                    bookUrl = request.bookUrl,
                    chapterIndex = request.chapterIndex,
                    chapterTitle = request.chapterTitle,
                    isLoading = true,
                    originalText = request.originalText,
                ),
            )
        }
        generateAiTextClean(request)
    }

    private fun retryAiTextClean() {
        val request = pendingAiTextCleanRequest ?: return
        _uiState.update {
            it.copy(
                aiTextClean = it.aiTextClean.copy(
                    isLoading = true,
                    replacementText = "",
                    streamingText = "",
                    reasoningText = "",
                    thinkingDuration = 0,
                    errorMessage = null,
                )
            )
        }
        generateAiTextClean(request)
    }

    private fun generateAiTextClean(request: PendingAiTextCleanRequest) {
        aiTextCleanJob?.cancel()
        aiTextCleanJob = viewModelScope.launch {
            try {
                val taskId = withContext(IO) {
                    cleanSelectedTextUseCase.start(
                        bookUrl = request.bookUrl,
                        chapterIndex = request.chapterIndex,
                        chapterTitle = request.chapterTitle,
                        selectedText = request.originalText,
                        contextBefore = request.contextBefore,
                        contextAfter = request.contextAfter,
                    )
                }
                cleanSelectedTextUseCase.observeTaskById(taskId).collect { task ->
                    val snapshot = task ?: return@collect
                    if (!isCurrentAiTextClean(request)) return@collect
                    val clean = _uiState.value.aiTextClean
                    when (snapshot.status) {
                        AiArtifact.STATUS_RUNNING -> _uiState.update {
                            it.copy(
                                aiTextClean = clean.copy(
                                    isLoading = true,
                                    streamingText = snapshot.output.orEmpty(),
                                    reasoningText = snapshot.reasoning,
                                    errorMessage = null,
                                )
                            )
                        }

                        AiArtifact.STATUS_SUCCESS -> _uiState.update {
                            it.copy(
                                aiTextClean = clean.copy(
                                    isLoading = false,
                                    replacementText = snapshot.output.orEmpty(),
                                    streamingText = "",
                                    reasoningText = snapshot.reasoning,
                                    errorMessage = null,
                                )
                            )
                        }

                        AiArtifact.STATUS_FAILED -> _uiState.update {
                            it.copy(
                                aiTextClean = clean.copy(
                                    isLoading = false,
                                    errorMessage = snapshot.errorMessage
                                        ?: context.getString(R.string.load_failed),
                                )
                            )
                        }

                        else -> Unit
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrentAiTextClean(request)) {
                    _uiState.update {
                        it.copy(
                            aiTextClean = it.aiTextClean.copy(
                                isLoading = false,
                                errorMessage = aiErrorMessage(error),
                            )
                        )
                    }
                }
            }
        }
    }

    private fun confirmAiTextClean() {
        val cleanState = _uiState.value.aiTextClean
        val book = ReadBook.book ?: return
        if (cleanState.isLoading || cleanState.isApplying || cleanState.errorMessage != null) return
        if (book.bookUrl != cleanState.bookUrl ||
            ReadBook.durChapterIndex != cleanState.chapterIndex
        ) {
            _uiState.update {
                it.copy(
                    aiTextClean = it.aiTextClean.copy(
                        errorMessage = context.getString(R.string.ai_chapter_changed)
                    )
                )
            }
            return
        }
        val pattern = normalizeAiReplacementText(cleanState.originalText)
        val replacement = normalizeAiReplacementText(cleanState.replacementText)
        if (pattern.isBlank()) {
            _uiState.update {
                it.copy(
                    aiTextClean = it.aiTextClean.copy(
                        errorMessage = context.getString(R.string.ai_text_clean_empty_selection)
                    )
                )
            }
            return
        }
        if (pattern == replacement) {
            _uiState.update {
                it.copy(
                    aiTextClean = it.aiTextClean.copy(
                        errorMessage = context.getString(R.string.ai_text_clean_no_change)
                    )
                )
            }
            return
        }

        _uiState.update {
            it.copy(aiTextClean = it.aiTextClean.copy(isApplying = true))
        }
        viewModelScope.launch {
            try {
                saveBookContentProcessUseCase.saveReplacement(
                    bookUrl = cleanState.bookUrl,
                    chapterIndex = cleanState.chapterIndex,
                    chapterPosition = pendingAiTextCleanRequest?.chapterPosition ?: 0,
                    selectedText = pattern,
                    contextBefore = pendingAiTextCleanRequest?.contextBefore.orEmpty(),
                    contextAfter = pendingAiTextCleanRequest?.contextAfter.orEmpty(),
                    replacementText = replacement,
                ).getOrThrow()
                reloadCurrentChapterAfterContentProcessChanged(
                    bookUrl = cleanState.bookUrl,
                    chapterIndex = cleanState.chapterIndex,
                )
                pendingAiTextCleanRequest = null
                _uiState.update {
                    it.copy(
                        activeSheet = null,
                        aiTextClean = AiTextCleanUiState(),
                    )
                }
                _effects.tryEmit(
                    ReadBookEffect.ShowToast(
                        context.getString(R.string.ai_text_clean_rule_created)
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        aiTextClean = it.aiTextClean.copy(
                            isApplying = false,
                            errorMessage = error.localizedMessage
                                ?: context.getString(R.string.error),
                        )
                    )
                }
            }
        }
    }

    private fun openAiCurrentChapterRewrite() {
        val book = ReadBook.book ?: return
        val chapter = ReadBook.curTextChapter?.chapter ?: return
        viewModelScope.launch {
            val text = withContext(IO) {
                getEffectiveChapterContent(book, chapter).trim()
            }
            if (text.isBlank()) {
                _effects.tryEmit(
                    ReadBookEffect.ShowToast(context.getString(R.string.ai_chapter_content_unavailable))
                )
                return@launch
            }
            openAiTextRewrite(
                text = text,
                chapterIndex = ReadBook.durChapterIndex,
                chapterPosition = 0,
            )
        }
    }

    private fun openAiTextRewrite(
        text: String,
        chapterIndex: Int,
        chapterPosition: Int,
    ) {
        val book = ReadBook.book ?: return
        val chapterTitle = appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex)?.title
            ?: _uiState.value.chapterName
        val visibleContent = ReadBook.curTextChapter?.getContent().orEmpty()
        val (contextBefore, contextAfter) = buildSelectionContext(
            content = visibleContent,
            selectedText = text,
            approximatePosition = chapterPosition,
        )
        val presets = loadAiRewritePresets()
        val selectedPresetId = _uiState.value.aiTextRewrite.selectedPresetId
            .takeIf { id -> presets.any { it.id == id } }
            ?: presets.firstOrNull()?.id.orEmpty()
        val request = PendingAiTextRewriteRequest(
            bookUrl = book.bookUrl,
            chapterIndex = chapterIndex,
            chapterTitle = chapterTitle,
            chapterPosition = chapterPosition,
            originalText = text,
            sourceContentHash = buildAiRewriteSourceContentHash(text),
            contextBefore = contextBefore,
            contextAfter = contextAfter,
        )
        pendingAiTextRewriteRequest = request
        closeReadMenu()
        val currentRewriteState = _uiState.value.aiTextRewrite
        val isSameRewriteTarget = currentRewriteState.bookUrl == request.bookUrl &&
                currentRewriteState.chapterIndex == request.chapterIndex &&
                currentRewriteState.originalText == request.originalText
        if (!isSameRewriteTarget) {
            aiTextRewriteJob?.cancel()
        }
        _uiState.update {
            it.copy(
                activeSheet = ReadBookSheet.AiTextRewrite,
                aiTextRewrite = if (isSameRewriteTarget) {
                    it.aiTextRewrite.copy(
                        chapterTitle = request.chapterTitle,
                        selectedPresetId = selectedPresetId,
                        presets = presets.toImmutableList(),
                    )
                } else {
                    AiTextRewriteUiState(
                        bookUrl = request.bookUrl,
                        chapterIndex = request.chapterIndex,
                        chapterTitle = request.chapterTitle,
                        originalText = request.originalText,
                        selectedPresetId = selectedPresetId,
                        presets = presets.toImmutableList(),
                    )
                },
            )
        }
        loadAiRewriteHistory(request, selectLatest = !isSameRewriteTarget)
    }

    private fun selectAiRewritePreset(presetId: String) {
        _uiState.update {
            it.copy(
                aiTextRewrite = it.aiTextRewrite.copy(
                    selectedPresetId = presetId,
                    errorMessage = null,
                )
            )
        }
    }

    private fun setAiRewriteTemporaryInstruction(instruction: String) {
        _uiState.update {
            it.copy(
                aiTextRewrite = it.aiTextRewrite.copy(
                    temporaryInstruction = instruction,
                    errorMessage = null,
                )
            )
        }
    }

    private fun selectAiRewriteHistory(artifactId: String) {
        val historyItem = _uiState.value.aiTextRewrite.history
            .firstOrNull { it.artifactId == artifactId }
            ?: return
        _uiState.update {
            it.copy(
                aiTextRewrite = it.aiTextRewrite.copy(
                    rewrittenText = historyItem.text,
                    reasoningText = "",
                    thinkingDuration = 0,
                    errorMessage = null,
                )
            )
        }
    }

    private fun loadAiRewriteHistory(
        request: PendingAiTextRewriteRequest,
        selectLatest: Boolean,
    ) {
        viewModelScope.launch {
            val history = withContext(IO) {
                aiArtifactGateway.getArtifactsByContentHash(
                    bookUrl = request.bookUrl,
                    chapterIndex = request.chapterIndex,
                    taskType = AiTaskType.REWRITE_TEXT,
                    contentHash = request.sourceContentHash,
                ).mapNotNull { artifact ->
                    val text = artifact.output?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    AiRewriteHistoryUi(
                        artifactId = artifact.id,
                        text = text,
                        timeText = formatAiRewriteHistoryTime(artifact.updatedAt),
                    )
                }
            }
            val latest = history.firstOrNull()
            _uiState.update { state ->
                if (
                    state.aiTextRewrite.bookUrl != request.bookUrl ||
                    state.aiTextRewrite.chapterIndex != request.chapterIndex ||
                    state.aiTextRewrite.originalText != request.originalText
                ) {
                    state
                } else {
                    state.copy(
                        aiTextRewrite = state.aiTextRewrite.copy(
                            history = history.toImmutableList(),
                            rewrittenText = if (
                                selectLatest &&
                                !state.aiTextRewrite.isLoading &&
                                state.aiTextRewrite.rewrittenText.isBlank() &&
                                latest != null
                            ) {
                                latest.text
                            } else {
                                state.aiTextRewrite.rewrittenText
                            },
                        )
                    )
                }
            }
        }
    }

    private fun formatAiRewriteHistoryTime(timestamp: Long): String {
        val date = Date(timestamp)
        val dateText = android.text.format.DateFormat.getDateFormat(context).format(date)
        val timeText = android.text.format.DateFormat.getTimeFormat(context).format(date)
        return "$dateText $timeText"
    }

    private fun buildAiRewriteSourceContentHash(text: String): String {
        return MD5Utils.md5Encode(normalizeAiReplacementText(text))
    }

    private fun generateSelectedAiTextRewrite() {
        val state = _uiState.value.aiTextRewrite
        val preset = state.presets.firstOrNull { it.id == state.selectedPresetId }
        if (preset == null) {
            _uiState.update {
                it.copy(
                    aiTextRewrite = it.aiTextRewrite.copy(
                        errorMessage = context.getString(R.string.ai_rewrite_no_preset)
                    )
                )
            }
            return
        }
        val request = pendingAiTextRewriteRequest ?: return
        generateAiTextRewrite(
            request = request,
            preset = preset,
            temporaryInstruction = state.temporaryInstruction,
        )
    }

    private fun retryAiTextRewrite() {
        _uiState.update {
            it.copy(
                aiTextRewrite = it.aiTextRewrite.copy(
                    isLoading = false,
                    rewrittenText = "",
                    reasoningText = "",
                    thinkingDuration = 0,
                    referenceCount = 0,
                    errorMessage = null,
                )
            )
        }
        generateSelectedAiTextRewrite()
    }

    private fun generateAiTextRewrite(
        request: PendingAiTextRewriteRequest,
        preset: AiRewritePresetUi,
        temporaryInstruction: String,
    ) {
        aiTextRewriteJob?.cancel()
        _uiState.update {
            it.copy(
                aiTextRewrite = it.aiTextRewrite.copy(
                    isLoading = true,
                    rewrittenText = "",
                    reasoningText = "",
                    thinkingDuration = 0,
                    referenceCount = 0,
                    errorMessage = null,
                )
            )
        }
        aiTextRewriteJob = viewModelScope.launch {
            try {
                val referenceContext = buildAiRewriteReferenceContext(request)
                val aiRequest = AiTextFactoryUseCase.Request(
                        bookUrl = request.bookUrl,
                        chapterIndex = request.chapterIndex,
                        chapterTitle = request.chapterTitle,
                        inputText = request.originalText,
                        taskType = AiTaskType.REWRITE_TEXT,
                        userInstruction = buildAiRewriteInstruction(
                            preset.instruction,
                            temporaryInstruction,
                        ),
                        referenceText = referenceContext.text,
                        skipCache = true,
                        artifactContentHash = request.sourceContentHash,
                    )
                val taskId = withContext(IO) { aiTextFactoryUseCase.start(aiRequest) }
                aiTextFactoryUseCase.observeTaskById(taskId).collect { task ->
                    val snapshot = task ?: return@collect
                    if (!isCurrentAiTextRewrite(request)) return@collect
                    val rewrite = _uiState.value.aiTextRewrite
                    when (snapshot.status) {
                        AiArtifact.STATUS_RUNNING -> _uiState.update {
                            it.copy(
                                aiTextRewrite = rewrite.copy(
                                    isLoading = true,
                                    rewrittenText = snapshot.output.orEmpty(),
                                    reasoningText = snapshot.reasoning,
                                    referenceCount = referenceContext.count,
                                    errorMessage = null,
                                )
                            )
                        }

                        AiArtifact.STATUS_SUCCESS -> {
                            _uiState.update {
                                it.copy(
                                    aiTextRewrite = rewrite.copy(
                                        isLoading = false,
                                        rewrittenText = snapshot.output.orEmpty(),
                                        reasoningText = snapshot.reasoning,
                                        referenceCount = referenceContext.count,
                                        errorMessage = null,
                                    )
                                )
                            }
                            loadAiRewriteHistory(request, selectLatest = false)
                        }

                        AiArtifact.STATUS_FAILED -> _uiState.update {
                            it.copy(
                                aiTextRewrite = rewrite.copy(
                                    isLoading = false,
                                    errorMessage = snapshot.errorMessage
                                        ?: context.getString(R.string.load_failed),
                                )
                            )
                        }

                        else -> Unit
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrentAiTextRewrite(request)) {
                    _uiState.update {
                        it.copy(
                            aiTextRewrite = it.aiTextRewrite.copy(
                                isLoading = false,
                                errorMessage = aiErrorMessage(error),
                            )
                        )
                    }
                }
            }
        }
    }

    private fun confirmAiTextRewrite() {
        val rewriteState = _uiState.value.aiTextRewrite
        val book = ReadBook.book ?: return
        if (rewriteState.isLoading || rewriteState.isApplying || rewriteState.errorMessage != null) return
        if (book.bookUrl != rewriteState.bookUrl ||
            ReadBook.durChapterIndex != rewriteState.chapterIndex
        ) {
            _uiState.update {
                it.copy(
                    aiTextRewrite = it.aiTextRewrite.copy(
                        errorMessage = context.getString(R.string.ai_chapter_changed)
                    )
                )
            }
            return
        }
        val pattern = normalizeAiReplacementText(rewriteState.originalText)
        val replacement = normalizeAiReplacementText(rewriteState.rewrittenText)
        if (pattern.isBlank()) {
            _uiState.update {
                it.copy(
                    aiTextRewrite = it.aiTextRewrite.copy(
                        errorMessage = context.getString(R.string.ai_text_clean_empty_selection)
                    )
                )
            }
            return
        }
        if (replacement.isBlank()) {
            _uiState.update {
                it.copy(
                    aiTextRewrite = it.aiTextRewrite.copy(
                        errorMessage = context.getString(R.string.ai_rewrite_empty_result)
                    )
                )
            }
            return
        }
        if (pattern == replacement) {
            _uiState.update {
                it.copy(
                    aiTextRewrite = it.aiTextRewrite.copy(
                        errorMessage = context.getString(R.string.ai_text_clean_no_change)
                    )
                )
            }
            return
        }

        _uiState.update {
            it.copy(aiTextRewrite = it.aiTextRewrite.copy(isApplying = true))
        }
        viewModelScope.launch {
            try {
                saveBookContentProcessUseCase.saveReplacement(
                    bookUrl = rewriteState.bookUrl,
                    chapterIndex = rewriteState.chapterIndex,
                    chapterPosition = pendingAiTextRewriteRequest?.chapterPosition ?: 0,
                    selectedText = pattern,
                    contextBefore = pendingAiTextRewriteRequest?.contextBefore.orEmpty(),
                    contextAfter = pendingAiTextRewriteRequest?.contextAfter.orEmpty(),
                    replacementText = replacement,
                    kind = BookContentProcess.KIND_AI_REWRITE,
                ).getOrThrow()
                reloadCurrentChapterAfterContentProcessChanged(
                    bookUrl = rewriteState.bookUrl,
                    chapterIndex = rewriteState.chapterIndex,
                )
                pendingAiTextRewriteRequest = null
                _uiState.update {
                    it.copy(
                        activeSheet = null,
                        aiTextRewrite = AiTextRewriteUiState(),
                    )
                }
                _effects.tryEmit(
                    ReadBookEffect.ShowToast(
                        context.getString(R.string.ai_text_rewrite_saved)
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        aiTextRewrite = it.aiTextRewrite.copy(
                            isApplying = false,
                            errorMessage = error.localizedMessage
                                ?: context.getString(R.string.error),
                        )
                    )
                }
            }
        }
    }

    private fun openAiRewritePresetConfig() {
        val presets = loadAiRewritePresets()
        _uiState.update {
            it.copy(
                activeSheet = ReadBookSheet.AiRewritePresetConfig,
                aiRewritePresetConfig = it.aiRewritePresetConfig.copy(
                    editing = false,
                    presets = presets.toImmutableList(),
                    errorMessage = null,
                ),
            )
        }
    }

    private fun closeAiRewritePresetConfig() {
        val nextSheet = if (pendingAiTextRewriteRequest != null) {
            ReadBookSheet.AiTextRewrite
        } else {
            null
        }
        _uiState.update {
            it.copy(
                activeSheet = nextSheet,
                aiRewritePresetConfig = AiRewritePresetConfigUiState(),
            )
        }
    }

    private fun startAddAiRewritePreset() {
        _uiState.update {
            it.copy(
                aiRewritePresetConfig = it.aiRewritePresetConfig.copy(
                    editing = true,
                    editingPresetId = null,
                    editingName = "",
                    editingInstruction = "",
                    errorMessage = null,
                )
            )
        }
    }

    private fun startEditAiRewritePreset(preset: AiRewritePresetUi) {
        _uiState.update {
            it.copy(
                aiRewritePresetConfig = it.aiRewritePresetConfig.copy(
                    editing = true,
                    editingPresetId = preset.id,
                    editingName = preset.name,
                    editingInstruction = preset.instruction,
                    errorMessage = null,
                )
            )
        }
    }

    private fun saveAiRewritePreset() {
        val config = _uiState.value.aiRewritePresetConfig
        val name = config.editingName.trim()
        val instruction = config.editingInstruction.trim()
        if (name.isBlank() || instruction.isBlank()) {
            _uiState.update {
                it.copy(
                    aiRewritePresetConfig = it.aiRewritePresetConfig.copy(
                        errorMessage = context.getString(R.string.ai_rewrite_preset_empty)
                    )
                )
            }
            return
        }
        val editingId = config.editingPresetId
        val savedPresets = if (editingId == null) {
            config.presets + AiRewritePresetUi(
                id = UUID.randomUUID().toString(),
                name = name,
                instruction = instruction,
            )
        } else {
            config.presets.map { preset ->
                if (preset.id == editingId) {
                    preset.copy(name = name, instruction = instruction)
                } else {
                    preset
                }
            }
        }
        saveAiRewritePresets(savedPresets)
        syncAiRewritePresets(savedPresets)
        clearAiRewritePresetDraft()
    }

    private fun clearAiRewritePresetDraft() {
        _uiState.update {
            it.copy(
                aiRewritePresetConfig = it.aiRewritePresetConfig.copy(
                    editing = false,
                    editingPresetId = null,
                    editingName = "",
                    editingInstruction = "",
                    errorMessage = null,
                )
            )
        }
    }

    private fun deleteAiRewritePreset() {
        val deletePreset = _uiState.value.aiRewritePresetConfig.deletePreset ?: return
        val savedPresets = _uiState.value.aiRewritePresetConfig.presets
            .filterNot { it.id == deletePreset.id }
        aiPromptPresetGateway.deletePresetSync(deletePreset.id)
        syncAiRewritePresets(savedPresets)
        _uiState.update {
            it.copy(
                aiRewritePresetConfig = it.aiRewritePresetConfig.copy(deletePreset = null)
            )
        }
    }

    private fun syncAiRewritePresets(presets: List<AiRewritePresetUi>) {
        val selected = _uiState.value.aiTextRewrite.selectedPresetId
            .takeIf { id -> presets.any { it.id == id } }
            ?: presets.firstOrNull()?.id.orEmpty()
        _uiState.update {
            it.copy(
                aiTextRewrite = it.aiTextRewrite.copy(
                    presets = presets.toImmutableList(),
                    selectedPresetId = selected,
                    rewrittenText = "",
                    reasoningText = "",
                    thinkingDuration = 0,
                    referenceCount = 0,
                    errorMessage = null,
                ),
                aiRewritePresetConfig = it.aiRewritePresetConfig.copy(
                    presets = presets.toImmutableList(),
                ),
            )
        }
    }

    private fun loadAiRewritePresets(): List<AiRewritePresetUi> {
        if (aiPromptPresetGateway.countByTaskTypeSync(AiTaskType.REWRITE_TEXT) == 0) {
            aiPromptPresetGateway.savePresetsSync(
                defaultAiRewritePresets().mapIndexed { index, preset ->
                    preset.toAiPromptPreset(index)
                }
            )
        }
        return aiPromptPresetGateway.getEnabledByTaskType(AiTaskType.REWRITE_TEXT)
            .map { it.toAiRewritePresetUi() }
    }

    private fun saveAiRewritePresets(presets: List<AiRewritePresetUi>) {
        aiPromptPresetGateway.savePresetsSync(
            presets.mapIndexed { index, preset ->
                preset.toAiPromptPreset(index)
            }
        )
    }

    private fun defaultAiRewritePresets(): List<AiRewritePresetUi> {
        return listOf(
            AiRewritePresetUi(
                id = "default_polish",
                name = context.getString(R.string.ai_rewrite_preset_polish_name),
                instruction = context.getString(R.string.ai_rewrite_preset_polish_instruction),
            ),
            AiRewritePresetUi(
                id = "default_concise",
                name = context.getString(R.string.ai_rewrite_preset_concise_name),
                instruction = context.getString(R.string.ai_rewrite_preset_concise_instruction),
            ),
            AiRewritePresetUi(
                id = "default_dialogue",
                name = context.getString(R.string.ai_rewrite_preset_dialogue_name),
                instruction = context.getString(R.string.ai_rewrite_preset_dialogue_instruction),
            ),
        )
    }

    private fun AiRewritePresetUi.toAiPromptPreset(sortNumber: Int): AiPromptPreset {
        val now = System.currentTimeMillis()
        return AiPromptPreset(
            id = id,
            taskType = AiTaskType.REWRITE_TEXT,
            name = name,
            instruction = instruction,
            builtIn = id.startsWith("default_"),
            sortNumber = sortNumber,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun AiPromptPreset.toAiRewritePresetUi(): AiRewritePresetUi {
        return AiRewritePresetUi(
            id = id,
            name = name,
            instruction = instruction,
        )
    }

    private fun buildAiRewriteInstruction(
        presetInstruction: String,
        temporaryInstruction: String,
    ): String {
        val temporary = temporaryInstruction.trim()
        if (temporary.isBlank()) return presetInstruction
        return buildString {
            append(presetInstruction)
            append("\n\nTemporary instruction for this rewrite only:\n")
            append(temporary)
        }
    }

    private suspend fun buildAiRewriteReferenceContext(
        request: PendingAiTextRewriteRequest,
    ): AiRewriteReferenceContext = withContext(IO) {
        val book = ReadBook.book
            ?.takeIf { it.bookUrl == request.bookUrl }
            ?: return@withContext AiRewriteReferenceContext()
        val terms = extractAiRewriteReferenceTerms(request.originalText)
        if (terms.isEmpty()) return@withContext AiRewriteReferenceContext()

        val chapters = appDb.bookChapterDao.getChapterList(request.bookUrl)
            .asSequence()
            .filter { it.index != request.chapterIndex }
            .sortedWith(
                compareBy<BookChapter> { kotlin.math.abs(it.index - request.chapterIndex) }
                    .thenBy { it.index }
            )
            .take(AI_REWRITE_REFERENCE_SCAN_CHAPTERS)
            .toList()

        val excerpts = mutableListOf<String>()
        for (chapter in chapters) {
            coroutineContext.ensureActive()
            val content = BookHelp.getContent(book, chapter) ?: continue
            val term = terms.firstOrNull { term ->
                chapter.title.contains(term) || content.contains(term)
            } ?: continue
            val excerpt = extractAiRewriteReferenceExcerpt(content, term)
            if (excerpt.isBlank()) continue
            excerpts += buildString {
                append("Chapter ")
                append(chapter.index + 1)
                if (chapter.title.isNotBlank()) {
                    append(": ")
                    append(chapter.title)
                }
                append("\nKeyword: ")
                append(term)
                append("\n")
                append(excerpt)
            }
            if (excerpts.size >= AI_REWRITE_REFERENCE_MAX_EXCERPTS) break
        }

        if (excerpts.isEmpty()) {
            AiRewriteReferenceContext()
        } else {
            AiRewriteReferenceContext(
                text = excerpts.joinToString("\n\n---\n\n"),
                count = excerpts.size,
            )
        }
    }

    private fun extractAiRewriteReferenceTerms(text: String): List<String> {
        val stopWords = setOf(
            "自己", "他们", "她们", "你们", "我们", "这个", "那个", "什么", "只是", "没有",
            "不是", "已经", "知道", "起来", "一下", "心里", "眼前", "声音", "时候", "突然",
            "微微", "终于", "如果", "因为", "所以", "但是", "然后", "似乎", "仿佛", "开始",
        )
        val counts = linkedMapOf<String, Int>()
        fun addTerm(term: String) {
            val normalized = term.trim()
            if (normalized.length < 2 || normalized in stopWords) return
            counts[normalized] = (counts[normalized] ?: 0) + 1
        }

        Regex("""([\u4e00-\u9fa5]{2,4})(?:说|问|道|喊|叫|笑|答|叹|想|看|望|皱眉|点头|摇头)""")
            .findAll(text)
            .forEach { match -> addTerm(match.groupValues[1]) }
        Regex("""\b[A-Z][A-Za-z]{2,}\b""")
            .findAll(text)
            .forEach { match -> addTerm(match.value) }

        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
            .take(6)
    }

    private fun extractAiRewriteReferenceExcerpt(content: String, term: String): String {
        val paragraphs = content.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val paragraphIndex = paragraphs.indexOfFirst { it.contains(term) }
        if (paragraphIndex >= 0) {
            val start = (paragraphIndex - 1).coerceAtLeast(0)
            val end = (paragraphIndex + 2).coerceAtMost(paragraphs.size)
            return trimAiRewriteReferenceExcerpt(
                paragraphs.subList(start, end).joinToString("\n"),
                term,
            )
        }
        return trimAiRewriteReferenceExcerpt(content, term)
    }

    private fun trimAiRewriteReferenceExcerpt(text: String, term: String): String {
        if (text.length <= AI_REWRITE_REFERENCE_EXCERPT_CHARS) return text.trim()
        val index = text.indexOf(term).takeIf { it >= 0 } ?: 0
        val start = (index - AI_REWRITE_REFERENCE_EXCERPT_CHARS / 2).coerceAtLeast(0)
        val end = (start + AI_REWRITE_REFERENCE_EXCERPT_CHARS).coerceAtMost(text.length)
        return text.substring(start, end).trim()
    }

    private fun isCurrentAiTextClean(request: PendingAiTextCleanRequest): Boolean {
        val state = _uiState.value
        return state.activeSheet is ReadBookSheet.AiTextClean &&
                state.aiTextClean.bookUrl == request.bookUrl &&
                state.aiTextClean.chapterIndex == request.chapterIndex &&
                state.aiTextClean.originalText == request.originalText
    }

    private fun isCurrentAiTextRewrite(request: PendingAiTextRewriteRequest): Boolean {
        val state = _uiState.value
        return state.aiTextRewrite.bookUrl == request.bookUrl &&
                state.aiTextRewrite.chapterIndex == request.chapterIndex &&
                state.aiTextRewrite.originalText == request.originalText
    }

    private fun buildSelectionContext(
        content: String,
        selectedText: String,
        approximatePosition: Int,
    ): Pair<String, String> {
        if (content.isBlank()) return "" to ""
        val start = findClosestOccurrence(content, selectedText, approximatePosition)
        if (start < 0) return "" to ""
        val end = start + selectedText.length
        return content.substring((start - AI_TEXT_CONTEXT_CHARS).coerceAtLeast(0), start) to
                content.substring(end, (end + AI_TEXT_CONTEXT_CHARS).coerceAtMost(content.length))
    }

    private fun findClosestOccurrence(
        content: String,
        selectedText: String,
        approximatePosition: Int,
    ): Int {
        var match = content.indexOf(selectedText)
        if (match < 0) return -1
        var closest = match
        var closestDistance = kotlin.math.abs(match - approximatePosition)
        while (match >= 0) {
            val distance = kotlin.math.abs(match - approximatePosition)
            if (distance < closestDistance) {
                closest = match
                closestDistance = distance
            }
            match = content.indexOf(selectedText, match + 1)
        }
        return closest
    }

    private fun normalizeAiReplacementText(text: String): String {
        val indent = ReadBookConfig.paragraphIndent
        return text.lines()
            .joinToString("\n") { line -> line.removePrefix(indent).trim() }
            .trim()
    }

    private fun aiErrorMessage(error: Throwable): String {
        return when {
            error.message?.contains("No AI model configured", ignoreCase = true) == true ->
                context.getString(R.string.ai_model_not_configured)

            else -> error.localizedMessage ?: context.getString(R.string.error)
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

    private fun openContentEdit() {
        pendingContentEditCursorOffset = currentContentEditOffset()
        pendingContentEditAnchor = currentContentEditAnchor()
        _uiState.update { it.copy(activeSheet = ReadBookSheet.ContentEdit) }
    }

    private fun currentContentEditPage(): TextPage? {
        return ReadBook.curTextChapter?.getPage(ReadBook.durPageIndex)
    }

    private fun currentContentEditOffset(): Int {
        val page = currentContentEditPage()
        return page?.lines
            ?.firstOrNull { !it.isTitle && it.text.isNotBlank() }
            ?.chapterPosition
            ?: page?.lines
                ?.firstOrNull { !it.isTitle }
                ?.chapterPosition
            ?: ReadBook.durChapterPos
    }

    private fun currentContentEditAnchor(): String? {
        return currentContentEditPage()
            ?.lines
            ?.firstOrNull { !it.isTitle && it.text.isNotBlank() }
            ?.text
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun resolveContentEditCursorOffset(text: String): Int {
        if (text.isEmpty()) {
            clearPendingContentEditLocation()
            return 0
        }
        val preferred = (pendingContentEditCursorOffset ?: currentContentEditOffset())
            .coerceIn(0, text.length)
        val anchor = pendingContentEditAnchor ?: currentContentEditAnchor()
        clearPendingContentEditLocation()
        if (anchor.isNullOrBlank()) {
            return preferred
        }
        val startIndex = (preferred - 200).coerceAtLeast(0)
        val nearIndex = text.indexOf(anchor, startIndex = startIndex)
        if (nearIndex >= 0) {
            return nearIndex
        }
        val anyIndex = text.indexOf(anchor)
        return if (anyIndex >= 0) anyIndex else preferred
    }

    private fun clearPendingContentEditLocation() {
        pendingContentEditCursorOffset = null
        pendingContentEditAnchor = null
    }

    private fun loadContentEdit() {
        _uiState.update { it.copy(contentEditLoading = true, contentEditText = "") }
        execute {
            val book = ReadBook.book ?: return@execute
            val chapter = appDb.bookChapterDao
                .getChapter(book.bookUrl, ReadBook.durChapterIndex)
                ?: return@execute
            val title = chapter.getDisplayTitle(
                chineseConverterType = readSettingsRepository.currentSettings.chineseConverterType
            )
            val contentProcessor = ContentProcessor.get(book.name, book.origin)
            val rawContent = BookHelp.getContent(book, chapter) ?: return@execute
            val text = contentProcessor.getContent(book, chapter, rawContent, includeTitle = false)
                .toString()
            val cursorOffset = resolveContentEditCursorOffset(text)
            _uiState.update {
                it.copy(
                    contentEditText = text,
                    contentEditTitle = title,
                    contentEditCursorOffset = cursorOffset,
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
            val cursorOffset = resolveContentEditCursorOffset(text)
            _uiState.update {
                it.copy(
                    contentEditText = text,
                    contentEditCursorOffset = cursorOffset,
                    contentEditLoading = false,
                )
            }
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
        searchResult: SearchResult,
        query: String = _uiState.value.searchContentQuery,
    ): Array<Int> {
        val pages = textChapter.pages
        val content = textChapter.getContent()
        if (pages.isEmpty()) return arrayOf(-1, 0, 0, 0, 0, 0)

        val match = findSearchResultMatch(content, searchResult, query)
            ?: return arrayOf(-1, 0, 0, 0, 0, 0)
        val contentPosition = match.first
        val queryLength = match.second
        if (contentPosition < 0 || queryLength <= 0) {
            return arrayOf(-1, 0, 0, 0, 0, 0)
        }

        val start = findSearchTextPoint(pages, contentPosition, preferPreviousLine = false)
            ?: return arrayOf(-1, 0, 0, 0, 0, 0)
        val end = findSearchTextPoint(
            pages,
            contentPosition + queryLength - 1,
            preferPreviousLine = true
        ) ?: start
        return arrayOf(
            start.pageIndex,
            start.lineIndex,
            start.charIndex,
            end.pageIndex - start.pageIndex,
            end.lineIndex,
            end.charIndex
        )
    }

    private fun findSearchResultMatch(
        content: String,
        searchResult: SearchResult,
        query: String,
    ): Pair<Int, Int>? {
        if (query.isEmpty()) return null
        val directLength = if (searchResult.matchLength > 0) searchResult.matchLength else query.length
        val directIndex = searchResult.queryIndexInChapter
        if (directIndex >= 0 && directIndex + directLength <= content.length) {
            val directMatch = if (searchResult.isRegex) {
                runCatching {
                    Regex(query).matches(content.substring(directIndex, directIndex + directLength))
                }.getOrDefault(false)
            } else {
                content.regionMatches(
                    directIndex,
                    query,
                    0,
                    query.length,
                    ignoreCase = false
                )
            }
            if (directMatch) {
                return directIndex to directLength
            }
        }
        if (searchResult.isRegex) {
            return runCatching {
                Regex(query).findAll(content)
                    .drop(searchResult.resultCountWithinChapter)
                    .firstOrNull()
                    ?.let { it.range.first to it.value.length }
            }.getOrNull()
        }

        var count = 0
        var index = content.indexOf(query)
        while (count != searchResult.resultCountWithinChapter && index >= 0) {
            index = content.indexOf(query, index + query.length)
            count += 1
        }
        return index.takeIf { it >= 0 }?.let { it to query.length }
    }

    private fun findSearchTextPoint(
        pages: List<TextPage>,
        contentPosition: Int,
        preferPreviousLine: Boolean,
    ): SearchTextPoint? {
        var fallback: SearchTextPoint? = null
        pages.forEachIndexed { pageIndex, page ->
            page.lines.forEachIndexed { lineIndex, line ->
                if (line.columns.isEmpty()) return@forEachIndexed
                val lineStart = line.chapterPosition
                val lineEndExclusive = lineStart + line.charSize
                if (contentPosition in lineStart until lineEndExclusive) {
                    return SearchTextPoint(
                        pageIndex = pageIndex,
                        lineIndex = lineIndex,
                        charIndex = (contentPosition - lineStart).coerceIn(0, line.columns.lastIndex)
                    )
                }
                if (preferPreviousLine && line.isParagraphEnd && contentPosition == lineEndExclusive) {
                    return SearchTextPoint(
                        pageIndex = pageIndex,
                        lineIndex = lineIndex,
                        charIndex = line.columns.lastIndex
                    )
                }
                if (contentPosition >= lineEndExclusive) {
                    fallback = SearchTextPoint(
                        pageIndex = pageIndex,
                        lineIndex = lineIndex,
                        charIndex = line.columns.lastIndex
                    )
                }
            }
        }
        return fallback
    }

    /**
     * Compute the search result position and emit [ReadBookEffect.NavigateToSearchResult]
     * so the Controller can navigate and highlight.
     */
    private fun navigateToSearchResult(result: SearchResult) {
        val query = _uiState.value.searchContentQuery
        if (query.isEmpty()) return
        val chapterIndex = result.chapterIndex
        val textChapter = ReadBook.curTextChapter
        if (textChapter != null && textChapter.chapter.index == chapterIndex) {
            val pos = searchResultPositions(textChapter, result, query)
            val lineIndex = pos[1]
            val charIndex = pos[2]
            val endRelativePage = pos[3]
            val endLineIndex = pos[4]
            val endCharIndex = pos[5]
            _effects.tryEmit(
                ReadBookEffect.NavigateToSearchResult(
                    result = result,
                    chapterIndex = chapterIndex,
                    pageIndex = pos[0],
                    lineIndex = lineIndex,
                    startCharIndex = charIndex,
                    endRelativePage = endRelativePage,
                    endLineIndex = endLineIndex,
                    endCharIndex = endCharIndex,
                )
            )
        } else {
            // Chapter not loaded — emit with -1 so the Controller knows to open the chapter first
            _effects.tryEmit(
                ReadBookEffect.NavigateToSearchResult(
                    result = result,
                    chapterIndex = chapterIndex,
                    pageIndex = -1,
                    lineIndex = 0,
                    startCharIndex = 0,
                    endRelativePage = 0,
                    endLineIndex = 0,
                    endCharIndex = 0,
                )
            )
        }
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
            _effects.tryEmit(ReadBookEffect.ShowToast("保存图片失败: ${it.localizedMessage}"))
        }.onSuccess {
            _effects.tryEmit(ReadBookEffect.ShowToast("已保存到相册"))
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

    private fun loadContentProcesses() {
        val book = ReadBook.book ?: return
        val chapterIndex = ReadBook.durChapterIndex
        _uiState.update {
            it.copy(
                contentProcessConfig = it.contentProcessConfig.copy(
                    isLoading = true,
                    errorMessage = null,
                )
            )
        }
        viewModelScope.launch(IO) {
            runCatching {
                bookContentProcessGateway.getForChapter(book.bookUrl, chapterIndex)
                    .mapNotNull { it.toContentProcessItemUi() }
                    .toImmutableList()
            }.onSuccess { items ->
                _uiState.update {
                    it.copy(
                        contentProcessConfig = it.contentProcessConfig.copy(
                            isLoading = false,
                            items = items,
                            errorMessage = null,
                        )
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        contentProcessConfig = it.contentProcessConfig.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: context.getString(R.string.error),
                        )
                    )
                }
            }
        }
    }

    private fun toggleContentProcess(id: String, enabled: Boolean) {
        viewModelScope.launch(IO) {
            runCatching {
                bookContentProcessGateway.setEnabled(id, enabled)
            }.onSuccess {
                reloadCurrentChapterAfterContentProcessChanged()
                loadContentProcesses()
            }.onFailure { error ->
                _effects.tryEmit(
                    ReadBookEffect.ShowToast(error.localizedMessage ?: context.getString(R.string.error))
                )
            }
        }
    }

    private fun deletePendingContentProcess() {
        val item = _uiState.value.contentProcessConfig.deleteItem ?: return
        viewModelScope.launch(IO) {
            runCatching {
                bookContentProcessGateway.delete(item.id)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        contentProcessConfig = it.contentProcessConfig.copy(deleteItem = null)
                    )
                }
                reloadCurrentChapterAfterContentProcessChanged()
                loadContentProcesses()
            }.onFailure { error ->
                _effects.tryEmit(
                    ReadBookEffect.ShowToast(error.localizedMessage ?: context.getString(R.string.error))
                )
            }
        }
    }

    private fun reloadCurrentChapterAfterContentProcessChanged(
        bookUrl: String? = null,
        chapterIndex: Int = ReadBook.durChapterIndex,
    ) {
        val book = ReadBook.book ?: return
        if (bookUrl != null && book.bookUrl != bookUrl) return
        if (ReadBook.durChapterIndex != chapterIndex) return
        ReadBook.clearTextChapter()
        for (index in chapterIndex - 1..chapterIndex + 1) {
            ReadBook.removeLoading(index)
        }
        ReadBook.loadContent(resetPageOffset = false)
    }

    private fun BookContentProcess.toContentProcessItemUi(): ContentProcessItemUi? {
        val anchor = GSON.fromJsonObject<TextProcessAnchor>(anchorJson).getOrNull()
            ?: return null
        val action = GSON.fromJsonObject<TextProcessAction>(actionJson).getOrNull()
            ?: return null
        return ContentProcessItemUi(
            id = id,
            kind = kind,
            actionType = action.type,
            enabled = enabled && status == BookContentProcess.STATUS_ACTIVE,
            chapterIndex = chapterIndex ?: anchor.chapterIndex,
            selectedText = anchor.selectedText,
            replacementText = action.replacement ?: action.text.orEmpty(),
            createdAt = createdAt,
        )
    }

    private fun changeReplaceRule(enabled: Boolean) {
        ReadBook.book?.let {
            it.setUseReplaceRule(enabled)
            ReadBook.saveRead()
            _uiState.update { state ->
                state.copy(useReplaceRule = enabled, replaceRuleEnabled = enabled)
            }
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
                readAloudTtsTimer = if (
                    route == ReadBookMenuRoute.ReadAloud && BaseReadAloudService.isRun
                ) {
                    BaseReadAloudService.timeMinute.coerceAtLeast(0)
                } else {
                    it.readAloudTtsTimer
                },
            )
        }
    }

    private fun openDefaultReadAloudInterface() {
        if (
            _uiState.value.defaultReadAloudInterface ==
            ReadAloudSettingsRepository.DEFAULT_INTERFACE_PLAYER
        ) {
            _uiState.update {
                it.copy(
                    menuState = ReadBookMenuState(),
                    activeSheet = ReadBookSheet.ReadAloudPlayer
                )
            }
        } else {
            openReadMenuRoute(ReadBookMenuRoute.ReadAloud)
        }
    }

    @Suppress("LongMethod")
    private fun handleConfigUpdate(update: ConfigUpdate) {
        update.toReadStyleMutation()?.let(readBookStyleConfigRepository::updateCurrentStyle)
        when (update) {
            // --- Text style ---
            is ConfigUpdate.TextSize,
            is ConfigUpdate.LetterSpacing,
            is ConfigUpdate.LineSpacing,
            is ConfigUpdate.ParagraphSpacing,
            is ConfigUpdate.ParagraphIndent,
            is ConfigUpdate.TextItalic,
            is ConfigUpdate.TextBold,
            is ConfigUpdate.TextColor,
            is ConfigUpdate.TextAccentColor -> Unit

            // --- Title style ---
            is ConfigUpdate.TitleMode,
            is ConfigUpdate.TitleBold,
            is ConfigUpdate.TitleSegScaling,
            is ConfigUpdate.TitleLineSpacingExtra,
            is ConfigUpdate.TitleLineSpacingSub,
            is ConfigUpdate.TitleSize,
            is ConfigUpdate.TitleTopSpacing,
            is ConfigUpdate.TitleBottomSpacing,
            is ConfigUpdate.TitleColor,
            is ConfigUpdate.TitleColorNight,
            is ConfigUpdate.TitleFont,
            is ConfigUpdate.TitleSegType,
            is ConfigUpdate.TitleSegDistance,
            is ConfigUpdate.TitleSegFlag -> Unit

            // --- Header / footer tips ---
            is ConfigUpdate.HeaderMode,
            is ConfigUpdate.FooterMode,
            is ConfigUpdate.TipHeaderLeft,
            is ConfigUpdate.TipHeaderMiddle,
            is ConfigUpdate.TipHeaderRight,
            is ConfigUpdate.TipFooterLeft,
            is ConfigUpdate.TipFooterMiddle,
            is ConfigUpdate.TipFooterRight,
            is ConfigUpdate.CustomTipHeaderLeft,
            is ConfigUpdate.CustomTipHeaderMiddle,
            is ConfigUpdate.CustomTipHeaderRight,
            is ConfigUpdate.CustomTipFooterLeft,
            is ConfigUpdate.CustomTipFooterMiddle,
            is ConfigUpdate.CustomTipFooterRight,
            is ConfigUpdate.HeaderFont,
            is ConfigUpdate.HeaderFontSize,
            is ConfigUpdate.TipHeaderColor,
            is ConfigUpdate.TipHeaderColorNight,
            is ConfigUpdate.TipFooterColor,
            is ConfigUpdate.TipFooterColorNight,
            is ConfigUpdate.TipDividerColor -> Unit

            // --- Layout / style ---
            is ConfigUpdate.StyleSelect -> {
                viewModelScope.launch {
                    readSettingsRepository.setStyleSelect(ReadSessionState.isComic, update.index)
                }
            }
            is ConfigUpdate.ShareLayout -> {
                viewModelScope.launch {
                    readSettingsRepository.setShareLayout(update.value)
                }
            }
            is ConfigUpdate.PageAnim -> Unit

            // --- Menu appearance ---
            is ConfigUpdate.MenuBgColor -> {
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuBgColor(update.color)
                }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }
            is ConfigUpdate.MenuAccentColor -> {
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuAccentColor(update.color)
                }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }
            is ConfigUpdate.MenuContainerColor -> {
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuContainerColor(update.color)
                }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }
            is ConfigUpdate.MenuBgColorNight -> {
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuBgColorNight(update.color)
                }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }
            is ConfigUpdate.MenuAccentColorNight -> {
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuAccentColorNight(update.color)
                }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }
            is ConfigUpdate.MenuContainerColorNight -> {
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuContainerColorNight(update.color)
                }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }
            is ConfigUpdate.MenuTextColor -> {
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuTextColor(update.color)
                }
                _uiState.update {
                    it.copy(menuConfig = it.menuConfig.copy(readMenuTextColor = update.color))
                }
            }
            is ConfigUpdate.MenuTextColorNight -> {
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuTextColorNight(update.color)
                }
                _uiState.update {
                    it.copy(menuConfig = it.menuConfig.copy(readMenuTextColorNight = update.color))
                }
            }
            is ConfigUpdate.MenuColorMode -> {
                val value = update.value.coerceIn(0, 1)
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuColorMode(value)
                }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }
            is ConfigUpdate.ReadBarStyle -> {
                val value = update.value.coerceIn(0, 2)
                viewModelScope.launch {
                    readSettingsRepository.setReadBarStyle(value)
                }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }

            // --- Menu bar border ---
            is ConfigUpdate.BorderWidth -> {
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuBorderWidth(update.value)
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(readMenuBorderWidth = update.value)) }
            }
            is ConfigUpdate.BorderColor -> {
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuBorderColor(update.color)
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(readMenuBorderColor = update.color)) }
            }
            is ConfigUpdate.BorderColorNight -> {
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuBorderColorNight(update.color)
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(readMenuBorderColorNight = update.color)) }
            }

            // --- Shadow ---
            is ConfigUpdate.TextShadow,
            is ConfigUpdate.ShadowRadius,
            is ConfigUpdate.ShadowDx,
            is ConfigUpdate.ShadowDy,
            is ConfigUpdate.ShadowColor -> Unit

            // --- Underline ---
            is ConfigUpdate.Underline,
            is ConfigUpdate.DottedLine,
            is ConfigUpdate.UnderlineExtend,
            is ConfigUpdate.UnderlineHeight,
            is ConfigUpdate.UnderlinePadding,
            is ConfigUpdate.DottedBase,
            is ConfigUpdate.DottedRatio,
            is ConfigUpdate.UnderlineColor -> Unit

            // --- Body padding ---
            is ConfigUpdate.PaddingTop,
            is ConfigUpdate.PaddingBottom,
            is ConfigUpdate.PaddingLeft,
            is ConfigUpdate.PaddingRight -> Unit

            // --- Header padding ---
            is ConfigUpdate.HeaderPaddingTop,
            is ConfigUpdate.HeaderPaddingBottom,
            is ConfigUpdate.HeaderPaddingLeft,
            is ConfigUpdate.HeaderPaddingRight,
            is ConfigUpdate.ShowHeaderLine -> Unit

            // --- Footer padding ---
            is ConfigUpdate.FooterPaddingTop,
            is ConfigUpdate.FooterPaddingBottom,
            is ConfigUpdate.FooterPaddingLeft,
            is ConfigUpdate.FooterPaddingRight,
            is ConfigUpdate.ShowFooterLine -> Unit

            // --- Background / display ---
            is ConfigUpdate.BgStr,
            is ConfigUpdate.BgStrNight,
            is ConfigUpdate.BgStrEInk,
            is ConfigUpdate.BgType,
            is ConfigUpdate.BgTypeNight,
            is ConfigUpdate.BgTypeEInk,
            is ConfigUpdate.BgAlpha,
            is ConfigUpdate.StatusIconDark,
            is ConfigUpdate.StyleName -> Unit
            is ConfigUpdate.MenuIconShowText -> {
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuIconShowText(update.value)
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(readMenuIconShowText = update.value)) }
            }
            is ConfigUpdate.MenuIconStyle -> {
                val value = update.value.coerceIn(0, 2)
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuIconStyle(value)
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(readMenuIconStyle = value)) }
            }
            is ConfigUpdate.TitleBarIconStyle -> {
                val value = update.value.coerceIn(0, 2)
                viewModelScope.launch {
                    readSettingsRepository.setTitleBarIconStyle(value)
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(titleBarIconStyle = value)) }
            }
            is ConfigUpdate.MenuIconItemsPerRow -> {
                val value = update.value.coerceIn(2, 8)
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuIconItemsPerRow(value)
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(readMenuIconItemsPerRow = value)) }
            }
            is ConfigUpdate.MenuIconRowCount -> {
                val value = update.value.coerceIn(1, 2)
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuIconRowCount(value)
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(readMenuIconRowCount = value)) }
            }
            is ConfigUpdate.MenuBottomCornerRadius -> {
                val value = update.value.coerceIn(0, 32)
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuBottomCornerRadius(value)
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(readMenuBottomCornerRadius = value)) }
            }
            is ConfigUpdate.FloatingBottomBar -> {
                val needsBlurFallback = !update.value &&
                        _uiState.value.menuConfig.readMenuBottomBarBlurMode ==
                        ReadMenuBlurMode.LiquidGlass
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuFloatingBottomBar(update.value)
                    if (needsBlurFallback) {
                        readSettingsRepository.setReadMenuBottomBarBlurMode(ReadMenuBlurMode.Haze)
                    }
                }
                _uiState.update {
                    it.copy(
                        menuConfig = it.menuConfig.copy(
                            readMenuFloatingBottomBar = update.value,
                            readMenuBottomBarBlurMode = if (needsBlurFallback) ReadMenuBlurMode.Haze
                            else it.menuConfig.readMenuBottomBarBlurMode,
                        )
                    )
                }
            }
            is ConfigUpdate.ShowMenuIcon -> {
                viewModelScope.launch {
                    readSettingsRepository.setShowMenuIcon(update.value)
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(showMenuIcon = update.value)) }
            }
            is ConfigUpdate.MenuTopBarBlurMode -> {
                val mode = update.value.coerceIn(0, 2).let {
                    if (it == ReadMenuBlurMode.LiquidGlass) ReadMenuBlurMode.Haze else it
                }
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuTopBarBlurMode(mode)
                }
                _uiState.update {
                    it.copy(menuConfig = it.menuConfig.copy(readMenuTopBarBlurMode = mode))
                }
            }

            is ConfigUpdate.MenuBottomBarBlurMode -> {
                val mode = update.value.coerceIn(0, 2)
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuBottomBarBlurMode(mode)
                }
                _uiState.update {
                    it.copy(menuConfig = it.menuConfig.copy(readMenuBottomBarBlurMode = mode))
                }
            }

            is ConfigUpdate.MenuTopBarLiquidGlassButtons -> {
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuTopBarLiquidGlassButtons(update.value)
                }
                _uiState.update {
                    it.copy(menuConfig = it.menuConfig.copy(readMenuTopBarLiquidGlassButtons = update.value))
                }
            }

            is ConfigUpdate.MenuTopBarTitleCapsule -> {
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuTopBarTitleCapsule(update.value)
                }
                _uiState.update {
                    it.copy(menuConfig = it.menuConfig.copy(readMenuTopBarTitleCapsule = update.value))
                }
            }

            is ConfigUpdate.MenuBottomBarLiquidGlassButtons -> {
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuBottomBarLiquidGlassButtons(update.value)
                }
                _uiState.update {
                    it.copy(menuConfig = it.menuConfig.copy(readMenuBottomBarLiquidGlassButtons = update.value))
                }
            }

            is ConfigUpdate.MenuFloatingIconLiquidGlass -> {
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuFloatingIconLiquidGlass(update.value)
                }
                _uiState.update {
                    it.copy(menuConfig = it.menuConfig.copy(readMenuFloatingIconLiquidGlass = update.value))
                }
            }

            is ConfigUpdate.MenuTopBarBlurSelection -> {
                val mode = update.mode.coerceIn(0, 2).let {
                    if (it == ReadMenuBlurMode.LiquidGlass) ReadMenuBlurMode.Haze else it
                }
                val style = update.style.coerceIn(0, 1)
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
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuBottomBarBlurStyle(style)
                }
                _uiState.update {
                    it.copy(menuConfig = it.menuConfig.copy(readMenuBottomBarBlurStyle = style))
                }
            }
            is ConfigUpdate.MenuBlurRadius -> {
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuBlurRadius(update.value)
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(readMenuBlurRadius = update.value)) }
            }
            is ConfigUpdate.MenuBlurAlpha -> {
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuBlurAlpha(update.value)
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(readMenuBlurAlpha = update.value)) }
            }
            is ConfigUpdate.MenuBlurColor -> {
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuBlurColor(update.color)
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(readMenuBlurColor = update.color)) }
            }
            is ConfigUpdate.MenuBlurColorNight -> {
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuBlurColorNight(update.color)
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(readMenuBlurColorNight = update.color)) }
            }
            is ConfigUpdate.MenuPaletteStyle -> {
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuPaletteStyle(update.value)
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(readMenuPaletteStyle = update.value)) }
            }
            is ConfigUpdate.MenuLensRadius -> {
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
                viewModelScope.launch {
                    readSettingsRepository.setTitleBarCustomIcons(
                        ReadBookConfig.encodeReadMenuCustomIcons(icons)
                    )
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(titleBarCustomIcons = icons.toImmutableMap())) }
            }
            is ConfigUpdate.TitleBarIconPosition -> {
                viewModelScope.launch {
                    readSettingsRepository.setTitleBarIconPosition(update.value)
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(titleBarIconPosition = update.value)) }
            }
            is ConfigUpdate.ShowTitleBarIcons -> {
                viewModelScope.launch {
                    readSettingsRepository.setShowTitleBarIcons(update.value)
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(showTitleBarIcons = update.value)) }
            }
            is ConfigUpdate.TitleBarCompact -> {
                viewModelScope.launch {
                    readSettingsRepository.update { it.copy(titleBarCompact = update.value) }
                }
                _uiState.update { it.copy(menuConfig = it.menuConfig.copy(titleBarCompact = update.value)) }
            }

            // --- System UI (also persists to DataStore) ---
            is ConfigUpdate.HideStatusBar -> {
                viewModelScope.launch {
                    readSettingsRepository.setHideStatusBar(update.value)
                }
            }
            is ConfigUpdate.HideNavigationBar -> {
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
            is ConfigUpdate.ReadBodyToLh -> {
                viewModelScope.launch {
                    readSettingsRepository.setReadBodyToLh(update.value)
                }
            }
            is ConfigUpdate.DefaultSourceChangeAll -> {
                viewModelScope.launch {
                    readSettingsRepository.setDefaultSourceChangeAll(update.value)
                }
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
                viewModelScope.launch {
                    readSettingsRepository.setUseZhLayout(update.value)
                }
            }
            is ConfigUpdate.ShowBrightnessView -> {
                viewModelScope.launch {
                    readSettingsRepository.setShowBrightnessView(update.value)
                }
                _uiState.update {
                    it.copy(menuConfig = it.menuConfig.copy(showBrightnessView = update.value))
                }
                postEvent(PreferKey.showBrightnessView, "")
            }

            is ConfigUpdate.BrightnessVwPos -> {
                viewModelScope.launch {
                    readSettingsRepository.setBrightnessVwPos(update.value)
                }
                _uiState.update {
                    it.copy(menuConfig = it.menuConfig.copy(brightnessVwPos = update.value))
                }
            }

            is ConfigUpdate.BrightnessAuto -> {
                _uiState.update {
                    it.copy(menuConfig = it.menuConfig.copy(brightnessAuto = update.value))
                }
                viewModelScope.launch {
                    readSettingsRepository.setBrightnessAuto(update.value)
                }
                _effects.tryEmit(
                    ReadBookEffect.ToggleBrightnessAuto(
                        update.value,
                        _uiState.value.menuConfig.readBrightness
                    )
                )
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
                _uiState.update {
                    it.copy(menuConfig = it.menuConfig.copy(readSliderMode = update.value))
                }
            }
            is ConfigUpdate.DoubleHorizontalPage -> {
                viewModelScope.launch {
                    readSettingsRepository.setDoubleHorizontalPage(update.value)
                }
            }
            is ConfigUpdate.ProgressBarBehavior -> {
                viewModelScope.launch {
                    readSettingsRepository.setProgressBarBehavior(update.value)
                }
                _uiState.update { it.copy(styleConfig = buildStyleConfig()) }
            }
            is ConfigUpdate.MouseWheelPage -> {
                viewModelScope.launch {
                    readSettingsRepository.setMouseWheelPage(update.value)
                }
            }
            is ConfigUpdate.VolumeKeyPage -> {
                viewModelScope.launch {
                    readSettingsRepository.setVolumeKeyPage(update.value)
                }
            }
            is ConfigUpdate.VolumeKeyPageOnPlay -> {
                viewModelScope.launch {
                    readSettingsRepository.setVolumeKeyPageOnPlay(update.value)
                }
            }
            is ConfigUpdate.KeyPageOnLongPress -> {
                viewModelScope.launch {
                    readSettingsRepository.setKeyPageOnLongPress(update.value)
                }
            }
            is ConfigUpdate.SliderVibrator -> {
                viewModelScope.launch {
                    readSettingsRepository.setSliderVibrator(update.value)
                }
            }
            is ConfigUpdate.SelectVibrator -> {
                viewModelScope.launch {
                    readSettingsRepository.setSelectVibrator(update.value)
                }
            }
            is ConfigUpdate.AutoChangeSource -> {
                viewModelScope.launch {
                    readSettingsRepository.setAutoChangeSource(update.value)
                }
            }
            is ConfigUpdate.AutoSuggestDayNight -> {
                if (update.value) {
                    hasDismissedDarkReminder = false
                    hasDismissedLightReminder = false
                }
                viewModelScope.launch {
                    readSettingsRepository.setAutoSuggestDayNight(update.value)
                }
            }
            is ConfigUpdate.SelectText -> {
                viewModelScope.launch {
                    readSettingsRepository.setSelectText(update.value)
                }
                _effects.tryEmit(ReadBookEffect.UpTextSelectAble(update.value))
            }
            is ConfigUpdate.NoAnimScrollPage -> {
                viewModelScope.launch {
                    readSettingsRepository.setNoAnimScrollPage(update.value)
                }
                _effects.tryEmit(ReadBookEffect.UpPageAnim(upRecorder = false))
            }
            is ConfigUpdate.OptimizeRender -> {
                viewModelScope.launch {
                    readSettingsRepository.setOptimizeRender(update.value)
                }
            }
            is ConfigUpdate.ClickImgWay -> {
                viewModelScope.launch {
                    readSettingsRepository.setClickImgWay(update.value)
                }
            }
            is ConfigUpdate.DisableReturnKey -> {
                viewModelScope.launch {
                    readSettingsRepository.setDisableReturnKey(update.value)
                }
            }
            is ConfigUpdate.ExpandTextMenu -> {
                viewModelScope.launch {
                    readSettingsRepository.setExpandTextMenu(update.value)
                }
            }
            is ConfigUpdate.ShowSelectMenuIcon -> {
                viewModelScope.launch {
                    readSettingsRepository.setShowSelectMenuIcon(update.value)
                }
            }
            is ConfigUpdate.ShowReadTitleAddition -> {
                viewModelScope.launch {
                    readSettingsRepository.setShowReadTitleAddition(update.value)
                }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }

            // --- Auto read ---
            is ConfigUpdate.AutoReadSpeed -> {
                viewModelScope.launch {
                    readSettingsRepository.setAutoReadSpeed(update.value)
                }
            }

            // --- Chinese converter ---
            is ConfigUpdate.ChineseConverterType -> {
                viewModelScope.launch {
                    readSettingsRepository.setChineseConverterType(update.value)
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

    private fun ConfigUpdate.toReadStyleMutation(): ReadStyleMutation? = when (this) {
        is ConfigUpdate.TextSize -> intMutation(ReadStyleIntKey.TextSize, value)
        is ConfigUpdate.LetterSpacing -> floatMutation(ReadStyleFloatKey.LetterSpacing, value)
        is ConfigUpdate.LineSpacing -> intMutation(ReadStyleIntKey.LineSpacing, value)
        is ConfigUpdate.ParagraphSpacing -> intMutation(ReadStyleIntKey.ParagraphSpacing, value)
        is ConfigUpdate.ParagraphIndent -> stringMutation(ReadStyleStringKey.ParagraphIndent, value)
        is ConfigUpdate.TextItalic -> booleanMutation(ReadStyleBooleanKey.TextItalic, value)
        is ConfigUpdate.TextBold -> intMutation(ReadStyleIntKey.TextBold, value)
        is ConfigUpdate.TextColor -> colorMutation(ReadStyleColorKey.Text, color)
        is ConfigUpdate.TextAccentColor -> colorMutation(ReadStyleColorKey.TextAccent, color)
        is ConfigUpdate.TitleMode -> intMutation(ReadStyleIntKey.TitleMode, value)
        is ConfigUpdate.TitleBold -> intMutation(ReadStyleIntKey.TitleBold, value)
        is ConfigUpdate.TitleSegScaling -> floatMutation(ReadStyleFloatKey.TitleSegScaling, value)
        is ConfigUpdate.TitleLineSpacingExtra ->
            intMutation(ReadStyleIntKey.TitleLineSpacingExtra, value)
        is ConfigUpdate.TitleLineSpacingSub ->
            intMutation(ReadStyleIntKey.TitleLineSpacingSub, value)
        is ConfigUpdate.TitleSize -> intMutation(ReadStyleIntKey.TitleSize, value)
        is ConfigUpdate.TitleTopSpacing -> intMutation(ReadStyleIntKey.TitleTopSpacing, value)
        is ConfigUpdate.TitleBottomSpacing ->
            intMutation(ReadStyleIntKey.TitleBottomSpacing, value)
        is ConfigUpdate.TitleColor -> colorMutation(ReadStyleColorKey.Title, color)
        is ConfigUpdate.TitleColorNight -> colorMutation(ReadStyleColorKey.TitleNight, color)
        is ConfigUpdate.TitleFont -> stringMutation(ReadStyleStringKey.TitleFont, path)
        is ConfigUpdate.TitleSegType -> intMutation(ReadStyleIntKey.TitleSegType, value)
        is ConfigUpdate.TitleSegDistance -> intMutation(ReadStyleIntKey.TitleSegDistance, value)
        is ConfigUpdate.TitleSegFlag -> stringMutation(ReadStyleStringKey.TitleSegFlag, value)
        is ConfigUpdate.HeaderMode -> intMutation(ReadStyleIntKey.HeaderMode, value)
        is ConfigUpdate.FooterMode -> intMutation(ReadStyleIntKey.FooterMode, value)
        is ConfigUpdate.TipHeaderLeft -> intMutation(ReadStyleIntKey.TipHeaderLeft, value)
        is ConfigUpdate.TipHeaderMiddle -> intMutation(ReadStyleIntKey.TipHeaderMiddle, value)
        is ConfigUpdate.TipHeaderRight -> intMutation(ReadStyleIntKey.TipHeaderRight, value)
        is ConfigUpdate.TipFooterLeft -> intMutation(ReadStyleIntKey.TipFooterLeft, value)
        is ConfigUpdate.TipFooterMiddle -> intMutation(ReadStyleIntKey.TipFooterMiddle, value)
        is ConfigUpdate.TipFooterRight -> intMutation(ReadStyleIntKey.TipFooterRight, value)
        is ConfigUpdate.HeaderFont -> stringMutation(ReadStyleStringKey.HeaderFont, path)
        is ConfigUpdate.CustomTipHeaderLeft ->
            stringMutation(ReadStyleStringKey.CustomTipHeaderLeft, value)
        is ConfigUpdate.CustomTipHeaderMiddle ->
            stringMutation(ReadStyleStringKey.CustomTipHeaderMiddle, value)
        is ConfigUpdate.CustomTipHeaderRight ->
            stringMutation(ReadStyleStringKey.CustomTipHeaderRight, value)
        is ConfigUpdate.CustomTipFooterLeft ->
            stringMutation(ReadStyleStringKey.CustomTipFooterLeft, value)
        is ConfigUpdate.CustomTipFooterMiddle ->
            stringMutation(ReadStyleStringKey.CustomTipFooterMiddle, value)
        is ConfigUpdate.CustomTipFooterRight ->
            stringMutation(ReadStyleStringKey.CustomTipFooterRight, value)
        is ConfigUpdate.HeaderFontSize -> intMutation(ReadStyleIntKey.HeaderFontSize, value)
        is ConfigUpdate.TipHeaderColor -> colorMutation(ReadStyleColorKey.TipHeader, color)
        is ConfigUpdate.TipHeaderColorNight ->
            colorMutation(ReadStyleColorKey.TipHeaderNight, color)
        is ConfigUpdate.TipFooterColor -> colorMutation(ReadStyleColorKey.TipFooter, color)
        is ConfigUpdate.TipFooterColorNight ->
            colorMutation(ReadStyleColorKey.TipFooterNight, color)
        is ConfigUpdate.TipDividerColor -> colorMutation(ReadStyleColorKey.TipDivider, color)
        is ConfigUpdate.PageAnim -> intMutation(ReadStyleIntKey.PageAnim, value)
        is ConfigUpdate.TextShadow -> booleanMutation(ReadStyleBooleanKey.TextShadow, value)
        is ConfigUpdate.ShadowRadius -> floatMutation(ReadStyleFloatKey.ShadowRadius, value)
        is ConfigUpdate.ShadowDx -> floatMutation(ReadStyleFloatKey.ShadowDx, value)
        is ConfigUpdate.ShadowDy -> floatMutation(ReadStyleFloatKey.ShadowDy, value)
        is ConfigUpdate.ShadowColor -> colorMutation(ReadStyleColorKey.Shadow, color)
        is ConfigUpdate.Underline -> booleanMutation(ReadStyleBooleanKey.Underline, value)
        is ConfigUpdate.DottedLine -> booleanMutation(ReadStyleBooleanKey.DottedLine, value)
        is ConfigUpdate.UnderlineExtend ->
            booleanMutation(ReadStyleBooleanKey.UnderlineExtend, value)
        is ConfigUpdate.UnderlineHeight -> intMutation(ReadStyleIntKey.UnderlineHeight, value)
        is ConfigUpdate.UnderlinePadding -> intMutation(ReadStyleIntKey.UnderlinePadding, value)
        is ConfigUpdate.DottedBase -> floatMutation(ReadStyleFloatKey.DottedBase, value)
        is ConfigUpdate.DottedRatio -> floatMutation(ReadStyleFloatKey.DottedRatio, value)
        is ConfigUpdate.UnderlineColor -> colorMutation(ReadStyleColorKey.Underline, color)
        is ConfigUpdate.PaddingTop -> intMutation(ReadStyleIntKey.PaddingTop, value)
        is ConfigUpdate.PaddingBottom -> intMutation(ReadStyleIntKey.PaddingBottom, value)
        is ConfigUpdate.PaddingLeft -> intMutation(ReadStyleIntKey.PaddingLeft, value)
        is ConfigUpdate.PaddingRight -> intMutation(ReadStyleIntKey.PaddingRight, value)
        is ConfigUpdate.HeaderPaddingTop -> intMutation(ReadStyleIntKey.HeaderPaddingTop, value)
        is ConfigUpdate.HeaderPaddingBottom ->
            intMutation(ReadStyleIntKey.HeaderPaddingBottom, value)
        is ConfigUpdate.HeaderPaddingLeft -> intMutation(ReadStyleIntKey.HeaderPaddingLeft, value)
        is ConfigUpdate.HeaderPaddingRight ->
            intMutation(ReadStyleIntKey.HeaderPaddingRight, value)
        is ConfigUpdate.ShowHeaderLine ->
            booleanMutation(ReadStyleBooleanKey.ShowHeaderLine, value)
        is ConfigUpdate.FooterPaddingTop -> intMutation(ReadStyleIntKey.FooterPaddingTop, value)
        is ConfigUpdate.FooterPaddingBottom ->
            intMutation(ReadStyleIntKey.FooterPaddingBottom, value)
        is ConfigUpdate.FooterPaddingLeft -> intMutation(ReadStyleIntKey.FooterPaddingLeft, value)
        is ConfigUpdate.FooterPaddingRight ->
            intMutation(ReadStyleIntKey.FooterPaddingRight, value)
        is ConfigUpdate.ShowFooterLine ->
            booleanMutation(ReadStyleBooleanKey.ShowFooterLine, value)
        is ConfigUpdate.BgStr -> stringMutation(ReadStyleStringKey.BgStr, value)
        is ConfigUpdate.BgStrNight -> stringMutation(ReadStyleStringKey.BgStrNight, value)
        is ConfigUpdate.BgStrEInk -> stringMutation(ReadStyleStringKey.BgStrEInk, value)
        is ConfigUpdate.BgType -> intMutation(ReadStyleIntKey.BgType, value)
        is ConfigUpdate.BgTypeNight -> intMutation(ReadStyleIntKey.BgTypeNight, value)
        is ConfigUpdate.BgTypeEInk -> intMutation(ReadStyleIntKey.BgTypeEInk, value)
        is ConfigUpdate.BgAlpha -> intMutation(ReadStyleIntKey.BgAlpha, value)
        is ConfigUpdate.StatusIconDark ->
            booleanMutation(ReadStyleBooleanKey.StatusIconDark, value)
        is ConfigUpdate.StyleName -> stringMutation(ReadStyleStringKey.StyleName, value)
        else -> null
    }

    private fun intMutation(key: ReadStyleIntKey, value: Int) =
        ReadStyleMutation.IntValue(key, value)

    private fun floatMutation(key: ReadStyleFloatKey, value: Float) =
        ReadStyleMutation.FloatValue(key, value)

    private fun booleanMutation(key: ReadStyleBooleanKey, value: Boolean) =
        ReadStyleMutation.BooleanValue(key, value)

    private fun stringMutation(key: ReadStyleStringKey, value: String) =
        ReadStyleMutation.StringValue(key, value)

    private fun colorMutation(key: ReadStyleColorKey, value: Int) =
        ReadStyleMutation.ColorValue(key, value)

    private fun moveHighlightRule(from: Int, to: Int) {
        val rules = _uiState.value.highlightRuleConfig.rules
        if (from !in rules.indices || to !in rules.indices) return
        val reordered = rules.toMutableList().apply {
            add(to, removeAt(from))
        }
        _uiState.update {
            it.copy(
                highlightRuleConfig = it.highlightRuleConfig.copy(
                    rules = reordered.toImmutableList()
                )
            )
        }
    }

    private fun importHighlightRuleSource(text: String) {
        _uiState.update {
            it.copy(
                highlightRuleConfig = it.highlightRuleConfig.copy(
                    importState = BaseImportUiState.Loading
                )
            )
        }
        execute {
            val importedRules = importHighlightRuleSourceAwait(text.trim())
                .map(highlightRuleRepository::sanitizeRule)
            if (importedRules.isEmpty()) {
                throw NoStackTraceException(context.getString(R.string.wrong_format))
            }
            val oldRules = highlightRuleRepository.load(ReadBookConfig.durConfig.name)
                .associateBy { it.id }
            BaseImportUiState.Success(
                source = text,
                items = importedRules.map { rule ->
                    val oldRule = oldRules[rule.id]
                    val status = when {
                        oldRule == null -> ImportStatus.New
                        oldRule != rule -> ImportStatus.Update
                        else -> ImportStatus.Existing
                    }
                    ImportItemWrapper(
                        data = rule,
                        oldData = oldRule,
                        status = status,
                        isSelected = status != ImportStatus.Existing,
                    )
                }
            )
        }.onSuccess { importState ->
            _uiState.update {
                it.copy(
                    highlightRuleConfig = it.highlightRuleConfig.copy(
                        importState = importState
                    )
                )
            }
        }.onError {
            AppLog.put("导入高亮规则失败\n${it.localizedMessage}", it, true)
            _uiState.update { state ->
                state.copy(
                    highlightRuleConfig = state.highlightRuleConfig.copy(
                        importState = BaseImportUiState.Error(
                            it.localizedMessage ?: context.getString(R.string.wrong_format)
                        )
                    )
                )
            }
        }
    }

    private suspend fun importHighlightRuleSourceAwait(text: String): List<HighlightRule> {
        return when {
            text.isJsonArray() -> GSON.fromJsonArray<HighlightRule>(text).getOrThrow()
            text.isJsonObject() -> listOf(
                GSON.fromJsonObject<HighlightRule>(text).getOrThrow()
            )
            text.isAbsUrl() -> {
                val body = okHttpClient.newCallResponseBody {
                    if (text.endsWith("#requestWithoutUA")) {
                        url(text.substringBeforeLast("#requestWithoutUA"))
                        header(AppConst.UA_NAME, "null")
                    } else {
                        url(text)
                    }
                }.decompressed().text()
                importHighlightRuleSourceAwait(body)
            }
            else -> throw NoStackTraceException(context.getString(R.string.wrong_format))
        }
    }

    private fun importHighlightRuleFile(uri: Uri) {
        execute<String?> {
            context.contentResolver.openInputStream(uri)?.use {
                it.reader().readText()
            }
        }.onSuccess { text ->
            if (text.isNullOrBlank()) {
                _uiState.update { state ->
                    state.copy(
                        highlightRuleConfig = state.highlightRuleConfig.copy(
                            importState = BaseImportUiState.Error(
                                context.getString(R.string.wrong_format)
                            )
                        )
                    )
                }
            } else {
                importHighlightRuleSource(text)
            }
        }.onError {
            _uiState.update { state ->
                state.copy(
                    highlightRuleConfig = state.highlightRuleConfig.copy(
                        importState = BaseImportUiState.Error(
                            it.localizedMessage ?: context.getString(R.string.wrong_format)
                        )
                    )
                )
            }
        }
    }

    private fun cancelHighlightRuleImport() {
        _uiState.update {
            it.copy(
                highlightRuleConfig = it.highlightRuleConfig.copy(
                    importState = BaseImportUiState.Idle
                )
            )
        }
    }

    private fun toggleHighlightRuleImportSelection(index: Int) {
        val importState = _uiState.value.highlightRuleConfig.importState
            as? BaseImportUiState.Success<HighlightRule> ?: return
        if (index !in importState.items.indices) return
        val items = importState.items.toMutableList()
        val item = items[index]
        items[index] = item.copy(isSelected = !item.isSelected)
        _uiState.update {
            it.copy(
                highlightRuleConfig = it.highlightRuleConfig.copy(
                    importState = importState.copy(items = items)
                )
            )
        }
    }

    private fun toggleHighlightRuleImportAll(isSelected: Boolean) {
        val importState = _uiState.value.highlightRuleConfig.importState
            as? BaseImportUiState.Success<HighlightRule> ?: return
        _uiState.update {
            it.copy(
                highlightRuleConfig = it.highlightRuleConfig.copy(
                    importState = importState.copy(
                        items = importState.items.map { item ->
                            item.copy(isSelected = isSelected)
                        }
                    )
                )
            )
        }
    }

    private fun updateHighlightRuleImportItem(index: Int, rule: HighlightRule) {
        val importState = _uiState.value.highlightRuleConfig.importState
            as? BaseImportUiState.Success<HighlightRule> ?: return
        if (index !in importState.items.indices) return
        val items = importState.items.toMutableList()
        items[index] = items[index].copy(data = rule)
        _uiState.update {
            it.copy(
                highlightRuleConfig = it.highlightRuleConfig.copy(
                    importState = importState.copy(
                        items = items,
                        version = importState.version + 1,
                    )
                )
            )
        }
    }

    private fun saveImportedHighlightRules() {
        val state = _uiState.value.highlightRuleConfig
        val importState = state.importState
            as? BaseImportUiState.Success<HighlightRule> ?: return
        val importedRules = importState.items
            .filter { it.isSelected }
            .map { highlightRuleRepository.sanitizeRule(it.data) }
        if (importedRules.isEmpty()) return
        val importedById = importedRules.associateBy { it.id }
        val mergedRules = state.rules.map { importedById[it.id] ?: it } +
                importedRules.filter { imported -> state.rules.none { it.id == imported.id } }
        saveHighlightRules(mergedRules)
        cancelHighlightRuleImport()
    }

    private fun exportHighlightRules(uri: Uri) {
        val rules = _uiState.value.highlightRuleConfig.rules
        execute {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.bufferedWriter().use { writer ->
                    writer.write(GSON.toJson(rules))
                }
            } ?: throw NoStackTraceException(context.getString(R.string.error))
        }.onSuccess {
            _effects.tryEmit(ReadBookEffect.ShowToast(context.getString(R.string.export_success)))
        }.onError {
            _effects.tryEmit(
                ReadBookEffect.ShowToast(
                    it.localizedMessage ?: context.getString(R.string.error)
                )
            )
        }
    }

    private fun exportHighlightRulesAsUrl() {
        val rules = _uiState.value.highlightRuleConfig.rules
        execute {
            uploadRepository.upload(
                fileName = HighlightRuleRepository.backupFileName,
                file = GSON.toJson(rules),
                contentType = "application/json",
            )
        }.onSuccess { url ->
            context.sendToClip(url)
            _effects.tryEmit(ReadBookEffect.ShowToast(context.getString(R.string.copy_url)))
        }
    }

    private fun loadHighlightRules() {
        val configName = ReadBookConfig.durConfig.name
        _uiState.update {
            it.copy(
                highlightRuleConfig = it.highlightRuleConfig.copy(
                    rules = highlightRuleRepository.load(configName).toImmutableList(),
                    editingRule = null,
                    showNewRule = false,
                    deleteRule = null,
                    importState = BaseImportUiState.Idle,
                ),
            )
        }
    }

    private fun saveHighlightRules(rules: List<HighlightRule>) {
        val configName = ReadBookConfig.durConfig.name
        val sanitizedRules = rules.map(highlightRuleRepository::sanitizeRule)
        highlightRuleRepository.save(configName, sanitizedRules)
        TextChapterLayout.invalidateRegexCache()
        _uiState.update {
            it.copy(
                highlightRuleConfig = it.highlightRuleConfig.copy(
                    rules = highlightRuleRepository.load(configName).toImmutableList(),
                    editingRule = null,
                    showNewRule = false,
                    deleteRule = null,
                ),
            )
        }
        _effects.tryEmit(
            ReadBookEffect.UpdateReadViewConfig(
                setOf(ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.ReloadContent)
            )
        )
    }

    private fun deletePendingHighlightRule() {
        val rule = _uiState.value.highlightRuleConfig.deleteRule ?: return
        val configName = ReadBookConfig.durConfig.name
        highlightRuleRepository.delete(rule)
        TextChapterLayout.invalidateRegexCache()
        _uiState.update {
            it.copy(
                highlightRuleConfig = it.highlightRuleConfig.copy(
                    rules = highlightRuleRepository.load(configName).toImmutableList(),
                    deleteRule = null,
                ),
            )
        }
        _effects.tryEmit(
            ReadBookEffect.UpdateReadViewConfig(
                setOf(ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.ReloadContent)
            )
        )
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
        readBookStyleConfigRepository.updateCurrentStyle(
            stringMutation(ReadStyleStringKey.TextFont, path)
        )
        _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(
            setOf(ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.ReloadContent, ConfigUpdateAction.UpdateStyle)
        ))
    }

    private fun selectTitleFont(path: String) {
        readBookStyleConfigRepository.updateCurrentStyle(
            stringMutation(ReadStyleStringKey.TitleFont, path)
        )
        _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(
            setOf(ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.ReloadContent, ConfigUpdateAction.UpdateStyle)
        ))
    }

    private fun toggleDayNight() {
        lastSwitchDayNightReminderTime = System.currentTimeMillis()
        hasDismissedDarkReminder = false
        hasDismissedLightReminder = false
        val nextMode = if (isNightTheme()) "1" else "2"
        viewModelScope.launch {
            appShellSettingsGateway.update { it.copy(themeMode = nextMode) }
        }
        _uiState.update {
            val newActiveReminder = if (it.activeReminder?.type is ReminderType.DayNightReminder) {
                null
            } else {
                it.activeReminder
            }
            it.copy(
                activeReminder = newActiveReminder,
                styleConfig = buildStyleConfig()
            )
        }
        reminderQueue.removeAll { it.type is ReminderType.DayNightReminder }
        _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(
            setOf(
                ConfigUpdateAction.UpdateBackground,
                ConfigUpdateAction.UpdateStyle,
                ConfigUpdateAction.UpdateContent,
                ConfigUpdateAction.InvalidateTextPage,
                ConfigUpdateAction.SubmitRenderTask,
                ConfigUpdateAction.UpdateSystemUi
            )
        ))
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
                    setOf(
                        ConfigUpdateAction.UpdateBackground,
                        ConfigUpdateAction.UpdateStyle,
                        ConfigUpdateAction.ReloadContent,
                        ConfigUpdateAction.UpdatePageAnim
                    )
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
        when (dialogId) {
            ReadBookColorPickerIds.SHADOW_COLOR -> {
                readBookStyleConfigRepository.updateCurrentStyle(
                    colorMutation(ReadStyleColorKey.Shadow, color)
                )
                _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(
                    setOf(ConfigUpdateAction.UpdateStyle, ConfigUpdateAction.UpdateContent, ConfigUpdateAction.InvalidateTextPage, ConfigUpdateAction.SubmitRenderTask)
                ))
            }

            ReadBookColorPickerIds.TEXT_COLOR -> {
                readBookStyleConfigRepository.updateCurrentStyle(
                    colorMutation(ReadStyleColorKey.Text, color)
                )
                _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(
                    setOf(ConfigUpdateAction.UpdateStyle, ConfigUpdateAction.UpdateContent, ConfigUpdateAction.InvalidateTextPage, ConfigUpdateAction.SubmitRenderTask)
                ))
                if (readSettingsRepository.currentSettings.readBarStyleFollowPage) {
                    postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                }
            }

            ReadBookColorPickerIds.TEXT_ACCENT_COLOR -> {
                readBookStyleConfigRepository.updateCurrentStyle(
                    colorMutation(ReadStyleColorKey.TextAccent, color)
                )
                _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(
                    setOf(ConfigUpdateAction.UpdateStyle, ConfigUpdateAction.UpdateContent, ConfigUpdateAction.InvalidateTextPage, ConfigUpdateAction.SubmitRenderTask)
                ))
                if (readSettingsRepository.currentSettings.readBarStyleFollowPage) {
                    postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                }
            }

            ReadBookColorPickerIds.BG_COLOR -> {
                readBookStyleConfigRepository.updateCurrentStyle(
                    ReadStyleMutation.Background(0, "#${color.hexString}")
                )
                _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(
                    setOf(ConfigUpdateAction.UpdateBackground)
                ))
                if (readSettingsRepository.currentSettings.readBarStyleFollowPage) {
                    postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                }
            }

            ReadBookColorPickerIds.TIP_HEADER_COLOR -> {
                readBookStyleConfigRepository.updateCurrentStyle(
                    colorMutation(ReadStyleColorKey.TipHeader, color)
                )
                postEvent(EventBus.TIP_COLOR, "")
                _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(
                    setOf(ConfigUpdateAction.UpdateStyle)
                ))
            }

            ReadBookColorPickerIds.TIP_FOOTER_COLOR -> {
                readBookStyleConfigRepository.updateCurrentStyle(
                    colorMutation(ReadStyleColorKey.TipFooter, color)
                )
                postEvent(EventBus.TIP_COLOR, "")
                _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(
                    setOf(ConfigUpdateAction.UpdateStyle)
                ))
            }

            ReadBookColorPickerIds.TIP_DIVIDER_COLOR -> {
                readBookStyleConfigRepository.updateCurrentStyle(
                    colorMutation(ReadStyleColorKey.TipDivider, color)
                )
                postEvent(EventBus.TIP_COLOR, "")
                _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(
                    setOf(ConfigUpdateAction.UpdateStyle)
                ))
            }

            ReadBookColorPickerIds.TITLE_COLOR -> {
                readBookStyleConfigRepository.updateCurrentStyle(
                    colorMutation(ReadStyleColorKey.Title, color)
                )
                _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(
                    setOf(ConfigUpdateAction.UpdateChapterStyle, ConfigUpdateAction.ReloadContent)
                ))
            }

            ReadBookColorPickerIds.MENU_BG_COLOR -> {
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuBgColor(color)
                }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }

            ReadBookColorPickerIds.MENU_ACCENT_COLOR -> {
                viewModelScope.launch {
                    readSettingsRepository.setReadMenuAccentColor(color)
                }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }

            ReadBookColorPickerIds.UNDERLINE_COLOR -> {
                readBookStyleConfigRepository.updateCurrentStyle(
                    colorMutation(ReadStyleColorKey.Underline, color)
                )
                _effects.tryEmit(ReadBookEffect.UpdateReadViewConfig(
                    setOf(ConfigUpdateAction.UpdateStyle, ConfigUpdateAction.UpdateContent, ConfigUpdateAction.InvalidateTextPage, ConfigUpdateAction.SubmitRenderTask)
                ))
            }
        }
    }

    private fun toggleTranslation() {
        val book = ReadBook.book ?: return
        val enabled = !book.getTranslationMode()
        book.setTranslationMode(enabled)
        book.save()
        _uiState.update { it.copy(translationMode = enabled) }
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

    private fun openChapterUrl() {
        if (ReadBook.isLocalBook) return
        val book = ReadBook.book ?: return
        val chapter = ReadBook.curTextChapter?.chapter
            ?: appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
            ?: return
        val url = chapter.getAbsoluteURL()
        if (url.isBlank()) return
        viewModelScope.launch {
            val useBrowser = localPreferencesRepository
                .getPreference(LocalPreferencesKeys.READ_URL_IN_BROWSER, false)
                .first()
            if (useBrowser) {
                context.openUrl(url.substringBefore(",{"))
            } else {
                val bookSource = ReadBook.bookSource
                _effects.tryEmit(
                    ReadBookEffect.OpenWebView(
                        title = chapter.title,
                        url = url,
                        sourceOrigin = bookSource?.bookSourceUrl,
                        sourceName = bookSource?.bookSourceName,
                        sourceType = bookSource?.getSourceType(),
                    )
                )
            }
        }
    }

    private fun runSourceCustomButton(longClick: Boolean) {
        val source = ReadBook.bookSource?.takeIf { it.customButton } ?: return
        val book = ReadBook.book ?: return
        val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
        _effects.tryEmit(
            ReadBookEffect.RunSourceCustomButton(
                event = if (longClick) {
                    SourceCallBack.LONG_CLICK_CUSTOM_BUTTON
                } else {
                    SourceCallBack.CLICK_CUSTOM_BUTTON
                },
                source = source,
                book = book,
                chapter = chapter,
            )
        )
    }

    private fun toggleReadUrlInBrowser() {
        viewModelScope.launch {
            val current = localPreferencesRepository
                .getPreference(LocalPreferencesKeys.READ_URL_IN_BROWSER, false)
                .first()
            val newValue = !current
            localPreferencesRepository.updatePreference(
                LocalPreferencesKeys.READ_URL_IN_BROWSER, newValue
            )
            _effects.tryEmit(
                ReadBookEffect.ShowToast(
                    context.getString(
                        if (newValue) R.string.open_by_browser else R.string.open_by_webview
                    )
                )
            )
        }
    }

    private fun showPayDialog() {
        val book = ReadBook.book ?: return
        if (book.isLocal) return
        val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
        if (chapter == null) {
            _effects.tryEmit(ReadBookEffect.ShowToast(context.getString(R.string.no_chapter)))
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
        viewModelScope.launch {
            otherSettingsGateway.update { it.copy(defaultBookTreeUri = uri.toString()) }
        }
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
                searchMenuVisible = false,
                activeDialog = if (ReadBook.lastBookProgress != null) {
                    ReadBookDialog.RestoreLastBookProgress
                } else {
                    it.activeDialog
                }
            )
        }
        _effects.tryEmit(ReadBookEffect.ExitSearch)
    }

    private fun navigateSearchResultByOffset(offset: Int) {
        val state = _uiState.value
        val currentIndex = state.searchResultIndex.coerceSearchResultIndex(
            state.searchResultList.size
        )
        val targetIndex = currentIndex + offset
        val result = state.searchResultList.getOrNull(targetIndex) ?: return
        ReadBook.saveCurrentBookProgress()
        _uiState.update { it.copy(searchResultIndex = targetIndex) }
        navigateToSearchResult(result)
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
            _effects.tryEmit(ReadBookEffect.ShowToast("添加书籍失败"))
        }
    }

    private var lastSwitchDayNightReminderTime: Long = 0L
    private val reminderQueue = ArrayDeque<ReminderUiState>()
    private var hasDismissedDarkReminder = false
    private var hasDismissedLightReminder = false

    fun isDayNightSwitchCoolingDown(): Boolean {
        return System.currentTimeMillis() - lastSwitchDayNightReminderTime < REMINDER_COOLDOWN_MS
    }

    private fun showReminder(reminder: ReminderUiState) {
        if (_uiState.value.activeReminder == null && reminderQueue.isEmpty()) {
            _uiState.update { it.copy(activeReminder = reminder) }
        } else {
            reminderQueue.addLast(reminder)
        }
    }

    private fun isReadBgLight(colorInt: Int): Boolean {
        // io.legado.app.utils.ColorUtils.isColorLight 判断条件是 >= 0.5
        // 实际很多肉眼觉得亮的颜色会被判断为false，例如 0xFFC5B098
        return AndroidColorUtils.calculateLuminance(colorInt) >= LIGHT_LUMINANCE_THRESHOLD
    }

    private fun checkSwitchDayNight(lux: Float) {
        if (
            !readSettingsRepository.currentSettings.autoSuggestDayNight ||
            isDayNightSwitchCoolingDown()
        ) return
        val isNight = isNightTheme()
        val styleConfig = _uiState.value.styleConfig
        if (!isNight && lux <= DARK_LUX_THRESHOLD) {
            if (hasDismissedDarkReminder) return
            val bgType = styleConfig.bgType
            val isLightBg = if (bgType == 0) {
                val colorInt = runCatching { styleConfig.bgStr.toColorInt() }.getOrDefault(0xFFEEEEEE.toInt())
                isReadBgLight(colorInt)
            } else {
                val meanColor = ReadSessionState.backgroundMeanColor
                if (meanColor != 0) isReadBgLight(meanColor) else true
            }
            if (isLightBg) {
                lastSwitchDayNightReminderTime = System.currentTimeMillis()
                showReminder(
                    ReminderUiState(
                        message = context.getString(R.string.switch_to_dark_mode_tip),
                        actionText = context.getString(R.string.switch_action),
                        actionIntent = ReadBookIntent.ToggleDayNight,
                        type = ReminderType.DayNightReminder(targetIsNight = true),
                    )
                )
            }
        } else if (isNight && lux >= BRIGHT_LUX_THRESHOLD) {
            if (hasDismissedLightReminder) return
            val bgTypeNight = styleConfig.bgTypeNight
            val isDarkBg = if (bgTypeNight == 0) {
                val colorInt = runCatching { styleConfig.bgStrNight.toColorInt() }.getOrDefault(0xFF000000.toInt())
                !isReadBgLight(colorInt)
            } else {
                val meanColor = ReadSessionState.backgroundMeanColor
                if (meanColor != 0) !isReadBgLight(meanColor) else true
            }
            if (isDarkBg) {
                lastSwitchDayNightReminderTime = System.currentTimeMillis()
                showReminder(
                    ReminderUiState(
                        message = context.getString(R.string.switch_to_light_mode_tip),
                        actionText = context.getString(R.string.switch_action),
                        actionIntent = ReadBookIntent.ToggleDayNight,
                        type = ReminderType.DayNightReminder(targetIsNight = false),
                    )
                )
            }
        }
    }

    private fun dismissReminder() {
        val currentReminder = _uiState.value.activeReminder
        if (currentReminder != null) {
            when (val type = currentReminder.type) {
                is ReminderType.DayNightReminder -> {
                    if (type.targetIsNight) {
                        hasDismissedDarkReminder = true
                    } else {
                        hasDismissedLightReminder = true
                    }
                }
                else -> {}
            }
        }
        _uiState.update { it.copy(activeReminder = null) }
        if (reminderQueue.isNotEmpty()) {
            viewModelScope.launch {
                //延迟一下，让上一个提醒的动画结束
                delay(500.milliseconds)
                if (_uiState.value.activeReminder == null && reminderQueue.isNotEmpty()) {
                    val next = reminderQueue.removeFirst()
                    _uiState.update { it.copy(activeReminder = next) }
                }
            }
        }
    }
}

internal fun readStyleExportFileName(styleName: String): String {
    val safeName = styleName
        .trim()
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .trim('.')
        .ifBlank { "readConfig" }
    return "$safeName.zip"
}

private const val TITLE_BAR_ICON_PREFS = "title_bar_icons"
private const val TITLE_BAR_ICON_KEY = "icons"
private const val TOOL_BUTTON_PREFS = "tool_button_config"
private const val TOOL_BUTTON_KEY = "tool_buttons"
private const val AI_TEXT_CONTEXT_CHARS = 1000
private const val AI_REWRITE_REFERENCE_SCAN_CHAPTERS = 80
private const val AI_REWRITE_REFERENCE_MAX_EXCERPTS = 6
private const val AI_REWRITE_REFERENCE_EXCERPT_CHARS = 600
private val DEFAULT_ENABLED_BUTTON_IDS = setOf(
    "search",
    "auto_page",
    "catalog",
    "read_aloud",
    "setting",
)

private const val DARK_LUX_THRESHOLD = 8f
private const val BRIGHT_LUX_THRESHOLD = 100f
private const val LIGHT_LUMINANCE_THRESHOLD = 0.35
private const val REMINDER_COOLDOWN_MS = 10 * 60 * 1000L

private data class SearchTextPoint(
    val pageIndex: Int,
    val lineIndex: Int,
    val charIndex: Int,
)

private data class PendingAiTextCleanRequest(
    val bookUrl: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val chapterPosition: Int,
    val originalText: String,
    val contextBefore: String,
    val contextAfter: String,
)

private data class PendingAiTextRewriteRequest(
    val bookUrl: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val chapterPosition: Int,
    val originalText: String,
    val sourceContentHash: String,
    val contextBefore: String,
    val contextAfter: String,
)

private data class AiRewriteReferenceContext(
    val text: String = "",
    val count: Int = 0,
)

private fun Int.coerceSearchResultIndex(resultSize: Int): Int {
    return if (resultSize <= 0) 0 else coerceIn(0, resultSize - 1)
}

private fun String.isHttpTtsImportUri(): Boolean {
    val uri = runCatching { Uri.parse(this) }.getOrNull() ?: return false
    return uri.scheme in setOf("legado", "yuedu")
            && uri.host == "import"
            && uri.path.equals("/httpTTS", ignoreCase = true)
}
