package io.legado.app.ui.book.read

import android.app.Application
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.lifecycle.viewModelScope
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookProgress
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.BookSourceRepository
import io.legado.app.data.repository.BookmarkRepository
import io.legado.app.data.repository.HighlightRuleRepository
import io.legado.app.data.repository.HttpTtsRepository
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
import io.legado.app.domain.gateway.ReadStyleGateway
import io.legado.app.domain.gateway.ThemeSettingsGateway
import io.legado.app.domain.model.readaloud.ReadAloudSessionStatus
import io.legado.app.domain.usecase.AiTextFactoryUseCase
import io.legado.app.domain.usecase.ChangeBookSourceUseCase
import io.legado.app.domain.usecase.CleanSelectedTextUseCase
import io.legado.app.domain.usecase.GenerateChapterSummaryUseCase
import io.legado.app.domain.usecase.GetReadingProgressUseCase
import io.legado.app.domain.usecase.RelocateMarkingTargetUseCase
import io.legado.app.domain.usecase.SaveBookContentProcessUseCase
import io.legado.app.domain.usecase.SaveMarkingUseCase
import io.legado.app.domain.usecase.SyncReadAloudVoicesUseCase
import io.legado.app.domain.usecase.UploadReadingProgressUseCase
import io.legado.app.domain.usecase.VerifyBookmarkTargetUseCase
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.isEpub
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isLocalTxt
import io.legado.app.help.book.isMobi
import io.legado.app.help.book.removeType
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.source.getSourceType
import io.legado.app.model.ImageProvider
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadAloudSessionStore
import io.legado.app.model.ReadBook
import io.legado.app.model.ReaderSession
import io.legado.app.model.ReaderSessionEvent
import io.legado.app.model.SourceCallBack
import io.legado.app.model.activeReadAloudProgress
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setChapter
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.model.translation.TranslationChapterKey
import io.legado.app.model.translation.TranslationChapterStatus
import io.legado.app.model.translation.TranslationManager
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.sheet.ReaderBookSheetTab
import io.legado.app.ui.book.searchContent.SearchResult
import io.legado.app.utils.ImageSaveUtils
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isTrue
import io.legado.app.utils.openUrl
import io.legado.app.utils.toStringArray
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    private val saveMarkingUseCase: SaveMarkingUseCase,
    private val verifyBookmarkTargetUseCase: VerifyBookmarkTargetUseCase,
    private val relocateMarkingTargetUseCase: RelocateMarkingTargetUseCase,
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
    private val themeSettingsGateway: ThemeSettingsGateway,
    private val httpTtsRepository: HttpTtsRepository,
    private val bookSourceRepository: BookSourceRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val bookRepository: BookRepository,
    private val readerSession: ReaderSession,
) : BaseViewModel(application) {

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

    /**
     * 书籍信息/目录有两种呈现：`useNewTocSheet` 开着时在阅读页内开 Sheet，
     * 否则按老路子发 Effect 开新页。两个入口的判断完全一致，合在一处，
     * 免得以后只改一边。
     */
    private fun openBookNavigation(
        tab: ReaderBookSheetTab,
        fallbackEffect: (Book) -> ReadBookEffect,
    ) {
        val book = ReadBook.book ?: return
        closeReadMenu()
        if (readSettingsRepository.currentSettings.useNewTocSheet) {
            _uiState.update { it.copy(activeSheet = ReadBookSheet.BookNavigation(tab)) }
        } else {
            _effects.tryEmit(fallbackEffect(book))
        }
    }

    private fun closeReadMenu() {
        _uiState.update { it.copy(menuState = ReadBookMenuState()) }
    }

    /**
     * 直接打开书籍详情（跳过阅读信息 Sheet，长按标题胶囊时使用）。
     */
    private fun openBookInfoDirect() {
        val book = ReadBook.book ?: return
        closeReadMenu()
        _effects.tryEmit(ReadBookEffect.OpenBookInfo(book.name, book.author, book.bookUrl))
    }

    // --- 正文处理域 ---

    private val contentProcessDelegate = ReadContentProcessDelegate(
        context = context,
        scope = viewModelScope,
        host = object : ReadContentProcessDelegate.Host {
            override fun showToast(message: String) {
                _effects.tryEmit(ReadBookEffect.ShowToast(message))
            }
        },
        bookContentProcessGateway = bookContentProcessGateway,
    )

    val contentProcessState = contentProcessDelegate.uiState

    // --- 划线/高亮笔记域：一次「选中 → 配置样式/备注 → 保存」的临时会话 ---

    private val markingDelegate = MarkingDelegate(
        scope = viewModelScope,
        context = context,
        highlightRuleRepository = highlightRuleRepository,
        saveMarkingUseCase = saveMarkingUseCase,
        host = object : MarkingDelegate.Host {
            override fun reloadCurrentChapter() {
                contentProcessDelegate.reloadCurrentChapter()
            }

            override fun dismissMarkingSheet() {
                restoreMarkingReturnSheet()
            }

            override fun showToast(message: String) {
                _effects.tryEmit(ReadBookEffect.ShowToast(message))
            }
        },
    )

    val markingState = markingDelegate.uiState

    /**
     * 划线笔记编辑可能从目录 Sheet 进入：保存/删除/取消后应回到原 sheet（目录），
     * 而不是被丢回阅读页。从划词菜单新建时无原 sheet，回 null。
     */
    private var markingReturnSheet: ReadBookSheet? = null

    private fun restoreMarkingReturnSheet() {
        val returnSheet = markingReturnSheet
        markingReturnSheet = null
        _uiState.update { it.copy(activeSheet = returnSheet) }
    }

    // --- 跳转校验域：书签/笔记跳转前比对源与章节标题 ---

    private val bookmarkNavigateDelegate = ReadBookmarkNavigateDelegate(
        scope = viewModelScope,
        bookRepository = bookRepository,
        verifyUseCase = verifyBookmarkTargetUseCase,
        relocateMarkingTargetUseCase = relocateMarkingTargetUseCase,
        host = object : ReadBookmarkNavigateDelegate.Host {
            override val pendingTarget: PendingBookmarkTarget?
                get() = _uiState.value.pendingBookmarkTarget

            override fun jumpToChapter(chapterIndex: Int, chapterPos: Int) {
                onIntent(ReadBookIntent.DismissSheet)
                onIntent(ReadBookIntent.OpenChapterResult(chapterIndex, chapterPos))
            }

            override fun setPendingTarget(pending: PendingBookmarkTarget?) {
                _uiState.update { it.copy(pendingBookmarkTarget = pending) }
            }
        },
    )

    // --- AI 域（摘要 / 净化 / 重写 / 预设）---

    private val aiHost = object : ReadAiDelegate.Host {
        override val activeSheet: ReadBookSheet? get() = _uiState.value.activeSheet

        override val chapterName: String get() = _uiState.value.chapterName

        override fun setActiveSheet(sheet: ReadBookSheet?) {
            _uiState.update { it.copy(activeSheet = sheet) }
        }

        override fun closeReadMenu() {
            this@ReadBookViewModel.closeReadMenu()
        }

        override fun showToast(message: String) {
            _effects.tryEmit(ReadBookEffect.ShowToast(message))
        }

        override fun reloadChapterAfterContentProcessChanged(
            bookUrl: String,
            chapterIndex: Int,
        ) {
            contentProcessDelegate.reloadCurrentChapter(bookUrl, chapterIndex)
        }

        override suspend fun findChapter(bookUrl: String, chapterIndex: Int): BookChapter? =
            bookRepository.getChapter(bookUrl, chapterIndex)

        override suspend fun listChapters(bookUrl: String): List<BookChapter> =
            bookRepository.getChapters(bookUrl)
    }

    private val aiDelegate = ReadAiDelegate(
        context = context,
        scope = viewModelScope,
        host = aiHost,
        generateChapterSummaryUseCase = generateChapterSummaryUseCase,
        cleanSelectedTextUseCase = cleanSelectedTextUseCase,
        aiTextFactoryUseCase = aiTextFactoryUseCase,
        saveBookContentProcessUseCase = saveBookContentProcessUseCase,
        aiArtifactGateway = aiArtifactGateway,
        aiPromptPresetGateway = aiPromptPresetGateway,
    )

    val aiState = aiDelegate.uiState

    // --- 高亮规则域 ---

    private val highlightRuleDelegate = ReadHighlightRuleDelegate(
        context = context,
        scope = viewModelScope,
        host = object : ReadHighlightRuleDelegate.Host {
            override fun showToast(message: String) {
                _effects.tryEmit(ReadBookEffect.ShowToast(message))
            }

            override fun notifyRulesChanged() {
                _effects.tryEmit(
                    ReadBookEffect.UpdateReadViewConfig(
                        setOf(
                            ConfigUpdateAction.UpdateChapterStyle,
                            ConfigUpdateAction.ReloadContent,
                        )
                    )
                )
            }
        },
        highlightRuleRepository = highlightRuleRepository,
        uploadRepository = uploadRepository,
    )

    val highlightRuleState = highlightRuleDelegate.uiState

    // --- 正文编辑域 ---

    private val contentEditDelegate = ReadContentEditDelegate(
        scope = viewModelScope,
        host = object : ReadContentEditDelegate.Host {
            override fun setActiveSheet(sheet: ReadBookSheet?) {
                _uiState.update { it.copy(activeSheet = sheet) }
            }

            override suspend fun findChapter(bookUrl: String, chapterIndex: Int): BookChapter? =
                bookRepository.getChapter(bookUrl, chapterIndex)
        },
        readSettingsRepository = readSettingsRepository,
    )

    val contentEditState = contentEditDelegate.uiState

    // --- 配置更新分发（无自持状态，menuConfig 仍在 ReadBookUiState）---

    private val configUpdateDelegate: ReadConfigUpdateDelegate = ReadConfigUpdateDelegate(
        scope = viewModelScope,
        host = object : ReadConfigUpdateDelegate.Host {
            override val menuConfig: ReadMenuConfig get() = _uiState.value.menuConfig

            override fun updateMenuConfig(transform: (ReadMenuConfig) -> ReadMenuConfig) {
                _uiState.update { it.copy(menuConfig = transform(it.menuConfig)) }
            }

            override fun refreshConfigSnapshots() {
                _uiState.update {
                    it.copy(
                        styleConfig = buildStyleConfig(),
                        sheetConfig = buildSheetConfig(),
                    )
                }
            }

            override fun emitEffect(effect: ReadBookEffect) {
                _effects.tryEmit(effect)
            }

            override fun resetDayNightReminderDismissal() {
                styleDelegate.resetDayNightReminderDismissal()
            }
        },
        readSettingsRepository = readSettingsRepository,
        readBookStyleConfigRepository = readBookStyleConfigRepository,
    )

    // --- 书签域（无自持状态，草稿随 ReadBookSheet.Bookmark 走）---

    private val bookmarkDelegate = ReadBookmarkDelegate(
        scope = viewModelScope,
        host = object : ReadBookmarkDelegate.Host {
            override fun setActiveSheet(sheet: ReadBookSheet?) {
                _uiState.update { it.copy(menuState = ReadBookMenuState(), activeSheet = sheet) }
            }

            override fun emitEffect(effect: ReadBookEffect) {
                _effects.tryEmit(effect)
            }
        },
        bookmarkRepository = bookmarkRepository,
        bookKey = _uiState.map { it.book?.let { book -> book.name to book.author } },
    )

    // --- 开书 / 目录 / 换源 / 进度同步域（无自持状态，isInitFinish 仍在 UiState）---

    private val loadDelegate: ReadBookLoadDelegate = ReadBookLoadDelegate(
        context = context,
        scope = viewModelScope,
        host = object : ReadBookLoadDelegate.Host {
            override var justInitData: Boolean
                get() = this@ReadBookViewModel.justInitData
                set(value) {
                    this@ReadBookViewModel.justInitData = value
                }

            override fun setInitFinish() {
                _uiState.update { it.copy(isInitFinish = true) }
            }

            override fun emitEffect(effect: ReadBookEffect) {
                _effects.tryEmit(effect)
            }

            override fun sureNewProgress(progress: BookProgress) {
                this@ReadBookViewModel.sureNewProgress(progress)
            }

            override fun requestBooksDirPicker(reloadChapterList: Boolean) {
                this@ReadBookViewModel.requestBooksDirPicker(reloadChapterList)
            }

            override suspend fun syncReadPreferencesSnapshot() {
                this@ReadBookViewModel.syncReadPreferencesSnapshot()
            }

            override fun openChapter(index: Int, durChapterPos: Int) {
                this@ReadBookViewModel.openChapter(index, durChapterPos)
            }
        },
        bookRepository = bookRepository,
        bookSourceRepository = bookSourceRepository,
        readSettingsRepository = readSettingsRepository,
        backupSettingsGateway = backupSettingsGateway,
        changeSourceSettingsGateway = changeSourceSettingsGateway,
        downloadCacheSettingsGateway = downloadCacheSettingsGateway,
        changeBookSourceUseCase = changeBookSourceUseCase,
        getReadingProgressUseCase = getReadingProgressUseCase,
        uploadReadingProgressUseCase = uploadReadingProgressUseCase,
    )

    // --- 阅读样式域（无自持状态，styleConfig / activeReminder / eyeProtection 仍在 UiState）---

    private val styleDelegate: ReadStyleDelegate = ReadStyleDelegate(
        context = context,
        scope = viewModelScope,
        host = object : ReadStyleDelegate.Host {
            override val uiState: ReadBookUiState get() = _uiState.value

            override val isNightTheme: Boolean get() = isNightTheme()

            override fun updateState(transform: (ReadBookUiState) -> ReadBookUiState) {
                _uiState.update(transform)
            }

            override fun emitEffect(effect: ReadBookEffect) {
                _effects.tryEmit(effect)
            }

            override fun applyConfigUpdate(update: ConfigUpdate) {
                configUpdateDelegate.handle(update)
            }
        },
        readSettingsRepository = readSettingsRepository,
        readStyleGateway = readBookStyleConfigRepository,
        appShellSettingsGateway = appShellSettingsGateway,
        themeSettingsGateway = themeSettingsGateway,
    )

    /** 自定义书签角标（拷贝落盘 + 刷新角标），独立于样式域的小委托。 */
    private val bookmarkBadgeDelegate = BookmarkBadgeDelegate(
        scope = viewModelScope,
        context = context,
        readSettingsRepository = readSettingsRepository,
        emitEffect = _effects::tryEmit,
    )

    /** 日夜切换冷却期内不再弹提醒；光线传感器回调在 RouteScreen 里先问这个再发 intent。 */
    fun isDayNightSwitchCoolingDown(): Boolean = styleDelegate.isDayNightSwitchCoolingDown()

    // --- 朗读域（无自持状态，朗读设置字段仍在 ReadBookUiState）---

    private val readAloudDelegate = ReadAloudDelegate(
        context = context,
        scope = viewModelScope,
        host = object : ReadAloudDelegate.Host {
            override val uiState: ReadBookUiState get() = _uiState.value

            override val preDownloadNum: Int get() = _readPreferences.value.preDownloadNum

            override val systemTtsEngines: List<TextToSpeech.EngineInfo> get() = sysEngines

            override fun updateState(transform: (ReadBookUiState) -> ReadBookUiState) {
                _uiState.update(transform)
            }

            override fun emitEffect(effect: ReadBookEffect) {
                _effects.tryEmit(effect)
            }

            override suspend fun emitEffectAwait(effect: ReadBookEffect) {
                _effects.emit(effect)
            }

            override fun openReadMenuRoute(route: ReadBookMenuRoute) {
                this@ReadBookViewModel.openReadMenuRoute(route)
            }

            override fun publishReadAloudProgress(chapterStart: Int) {
                _readAloudProgress.value = chapterStart
            }
        },
        readSettingsRepository = readSettingsRepository,
        readAloudSettingsRepository = readAloudSettingsRepository,
        readAloudSessionStore = readAloudSessionStore,
        httpTtsRepository = httpTtsRepository,
        aiProfileGateway = aiProfileGateway,
        syncReadAloudVoicesUseCase = syncReadAloudVoicesUseCase,
    )

    // --- 菜单按钮配置域（无自持状态，按钮列表仍在 menuConfig）---

    private val buttonConfigDelegate = ReadButtonConfigDelegate(
        context = context,
        scope = viewModelScope,
        readSettingsRepository = readSettingsRepository,
        host = object : ReadButtonConfigDelegate.Host {
            override fun updateMenuConfig(transform: (ReadMenuConfig) -> ReadMenuConfig) {
                _uiState.update { it.copy(menuConfig = transform(it.menuConfig)) }
            }

            override fun applyConfigUpdate(update: ConfigUpdate) {
                configUpdateDelegate.handle(update)
            }
        },
    )

    // --- 净化规则域（无自持状态，规则列表仍在 uiState.allReplaceRules）---

    private val replaceRuleDelegate = ReadReplaceRuleDelegate(
        scope = viewModelScope,
        replaceRuleRepository = replaceRuleRepository,
        host = object : ReadReplaceRuleDelegate.Host {
            override fun updateAllReplaceRules(rules: List<ReplaceRuleItemUi>) {
                _uiState.update { it.copy(allReplaceRules = rules.toImmutableList()) }
            }

            override fun updateUseReplaceRule(enabled: Boolean) {
                _uiState.update {
                    it.copy(useReplaceRule = enabled, replaceRuleEnabled = enabled)
                }
            }
        },
    )

    private val sysEngines: List<TextToSpeech.EngineInfo> by lazy {
        val tts = TextToSpeech(context, null)
        val engines = tts.engines
        tts.shutdown()
        engines
    }

    private val _readPreferences = MutableStateFlow(ReadPreferences())
    val readPreferences = _readPreferences.asStateFlow()

    private var pendingBooksDirReloadChapterList: Boolean = false
    private var translationStatusJob: Job? = null
    private var observedTranslationKey: TranslationChapterKey? = null

    val isInitFinish: Boolean get() = _uiState.value.isInitFinish

    private var cachedChapterBookUrl: String? = null

    /**
     * ReadView 在 Compose 组合期就构造并画第一帧, 早于 initData 完成,
     * 那时 isInitFinish 还是 false, 于是先闪一帧 "加载数据中".
     * 记下本路由要打开的书, 供 [isCachedChapterUsable] 在绘制时判定.
     */
    fun prepareCachedChapterFallback(bookUrl: String?, chapterChanged: Boolean) {
        cachedChapterBookUrl = bookUrl?.takeUnless { it.isEmpty() || chapterChanged }
    }

    /**
     * ReadBook 里已经有本书可直接用的章节(重新进入, 或导航期间预加载排好的).
     * 在绘制时判定而不是组合期一次性判定: 预加载的发布可能比组合晚几十毫秒.
     */
    fun isCachedChapterUsable(): Boolean {
        val bookUrl = cachedChapterBookUrl ?: return false
        if (ReadBook.book?.bookUrl != bookUrl) return false
        if (ReadBook.msg != null) return false
        val chapter = ReadBook.curTextChapter ?: return false
        if (!chapter.isLayoutSizeMatch()) return false
        // 长章节在导航窗口内排不完整章, 只要当前页已经排出来就够画第一帧了
        return chapter.isCompleted ||
                (chapter.isLayoutRunning && chapter.getPage(ReadBook.durPageIndex) != null)
    }

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
        // 订阅必须早于 attach()：会话事件用 tryEmit 投递，没有订阅者会被丢弃。
        // viewModelScope 是 Main.immediate，VM 在主线程构造，故 launch 会同步跑到
        // collect 的挂起点——本行返回时订阅者已注册。
        collectReaderSessionEvents()
        readerSession.attach()
        buttonConfigDelegate.refresh()
        collectReadPreferences()
        styleDelegate.collectEyeProtectionSettings()
        readAloudDelegate.collectPreferences()
        collectEventBus()
        collectReaderSession()
        collectReadStyle()
        bookmarkDelegate.start()
        replaceRuleDelegate.start()
        execute { readAloudDelegate.syncConfiguredTtsVoices() }
    }

    /**
     * 消费 [ReadStyleGateway.state]（Track E · E2）：排版配置的唯一变更通知。
     *
     * 排版底座 `ReadBookConfig.Config` 是可变全局、无 flow，此前 UiState 里的
     * [ReadBookStyleConfig] / [ReadSheetConfigUiState] 只能靠各写入站点手工重建——
     * 13 处重建 `styleConfig`、**只有 1 处**重建 `sheetConfig`（`syncFromReadBook`），
     * 于是编辑排版后重开弹层显示的是旧值。
     *
     * 改由 gateway 的 `publishState()` 统一驱动后，「新增写入路径忘了重建快照」这个
     * 失效类别不再存在：写入必经 gateway，gateway 必发 state，这里必然重建两份快照。
     *
     * R1.1 收敛后全 VM 只允许三处重建触发，删任何一处前先确认其路径已被其余覆盖：
     * 1. 本 collector——一切经 gateway 的排版写入（编辑/预设/删除/导入，repository 必 publishState）；
     * 2. [collectEventBus] 的 [ReadConfigUpdateBus] collector——不经 gateway 的全局变更
     *    （日夜切换等，revision 不递增，gateway flow 不会发射）；
     * 3. [handleConfigUpdate] 尾部 `styleMutation == null` 分支——只写 DataStore 的更新。
     * `syncFromReadBook` 不再重建（曾经的每翻页兜底会掩盖漏发问题）。
     */
    private fun collectReadStyle() {
        viewModelScope.launch {
            readBookStyleConfigRepository.state.collect {
                _uiState.update { state ->
                    state.copy(
                        styleConfig = buildStyleConfig(),
                        sheetConfig = buildSheetConfig(),
                    )
                }
            }
        }
    }

    /**
     * 消费 [ReaderSession.state]（Track A A5）：会话快照在任意受控 mutator 完成后发射，
     * 据此驱动 UiState 刷新。与遗留 CallBack 刷新路径叠加、幂等（相同结果 StateFlow 不再发），
     * 收集在 mutator 返回之后异步触发，故 syncFromReadBook 读到的 ReadBook 字段已是最终态。
     */
    private fun collectReaderSession() {
        viewModelScope.launch {
            readerSession.state.collect {
                _uiState.update { state -> syncFromReadBook(state) }
            }
        }
    }

    /**
     * 消费 [ReaderSession.events]（R2.3）：遗留 [ReadBook.CallBack] 的四个回调。
     *
     * VM 不再实现 `ReadBook.CallBack`——`ReadBook.callBack` 现在指向本 VM 持有的
     * [LegacyReaderSession]。回调体原样搬过来，只是从「在 ReadBook 的调用线程上同步执行」
     * 变成「在主线程上晚一个派发执行」。
     */
    private fun collectReaderSessionEvents() {
        viewModelScope.launch {
            readerSession.events.collect { event ->
                when (event) {
                    is ReaderSessionEvent.StateInvalidated -> {
                        _uiState.update { syncFromReadBook(it) }
                    }

                    is ReaderSessionEvent.ChapterListRequested -> loadChapterList(event.book)

                    is ReaderSessionEvent.BookChanged -> {
                        _uiState.update { syncFromReadBook(it) }
                        if (!ReadBook.inBookshelf) {
                            removeFromBookshelf { _effects.tryEmit(ReadBookEffect.Finish) }
                        }
                    }

                    is ReaderSessionEvent.NewProgressAvailable ->
                        sureNewProgress(event.progress)
                }
            }
        }
    }

    // --- MVI Intent Dispatcher ---

    fun onIntent(intent: ReadBookIntent) {
        when (intent) {
            is ReadBookIntent.InitData -> {
                initData(intent.request)
                justInitData = true
            }
            is ReadBookIntent.InitReadBookConfig -> viewModelScope.launch {
                initReadBookConfig(intent.request)
            }
            is ReadBookIntent.CheckSwitchDayNight -> styleDelegate.checkSwitchDayNight(intent.lux)
            is ReadBookIntent.DismissReminder -> styleDelegate.dismissReminder()
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
                    _effects.tryEmit(
                        ReadBookEffect.OpenSearch(
                            word = intent.word,
                            bookUrl = bookUrl,
                            autoFocus = intent.autoFocus,
                        )
                    )
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
                    readAloudDelegate.openDefaultInterface()
                }
                _effects.tryEmit(ReadBookEffect.ToggleReadAloud)
            }

            is ReadBookIntent.ToggleAutoPage -> _effects.tryEmit(ReadBookEffect.ToggleAutoPage)
            is ReadBookIntent.StopAutoPage -> _effects.tryEmit(ReadBookEffect.StopAutoPage)
            is ReadBookIntent.RefreshCurrentChapter -> refreshCurrentChapter()
            is ReadBookIntent.RefreshAllChapters -> refreshAllChapters()
            is ReadBookIntent.RefreshContentAfter -> refreshContentAfter()
            is ReadBookIntent.ChangeReplaceRule ->
                replaceRuleDelegate.changeUseReplaceRule(intent.enabled)
            is ReadBookIntent.SetReplaceRuleEnabled ->
                replaceRuleDelegate.setEnabled(intent.id, intent.enabled)
            is ReadBookIntent.MoveReplaceRule ->
                replaceRuleDelegate.move(intent.draggedId, intent.anchorId, intent.afterAnchor)
            is ReadBookIntent.DisableEffectiveReplace -> replaceRuleDelegate.disable(intent.rule)
            ReadBookIntent.DisableChineseConverter -> {
                configUpdateDelegate.handle(ConfigUpdate.ChineseConverterType(0))
            }
            ReadBookIntent.DisableReSegment -> {
                ReadBook.book?.setReSegment(false)
                ReadBook.loadContent(false)
                _uiState.update { it.copy(reSegment = false) }
            }
            is ReadBookIntent.ToggleTranslation -> toggleTranslation()
            is ReadBookIntent.OpenChapterSummary -> aiDelegate.openChapterSummary()
            is ReadBookIntent.OpenAiCurrentChapterRewrite -> aiDelegate.openAiCurrentChapterRewrite()
            is ReadBookIntent.RetryChapterSummary -> aiDelegate.retryChapterSummary()
            is ReadBookIntent.SetChapterSummaryReasoningLevel ->
                aiDelegate.setChapterSummaryReasoningLevel(intent.level)
            is ReadBookIntent.LoadContentProcesses -> contentProcessDelegate.load()
            is ReadBookIntent.ToggleContentProcess ->
                contentProcessDelegate.toggle(intent.id, intent.enabled)
            is ReadBookIntent.RequestDeleteContentProcess ->
                contentProcessDelegate.requestDelete(intent.item)
            is ReadBookIntent.ConfirmDeleteContentProcess -> contentProcessDelegate.confirmDelete()
            is ReadBookIntent.DismissDeleteContentProcess -> contentProcessDelegate.dismissDelete()
            is ReadBookIntent.SelectAiRewritePreset -> aiDelegate.selectAiRewritePreset(intent.presetId)
            is ReadBookIntent.SetAiRewriteTemporaryInstruction ->
                aiDelegate.setAiRewriteTemporaryInstruction(intent.instruction)
            is ReadBookIntent.SelectAiRewriteHistory ->
                aiDelegate.selectAiRewriteHistory(intent.artifactId)
            is ReadBookIntent.GenerateAiTextRewrite -> aiDelegate.generateSelectedAiTextRewrite()
            is ReadBookIntent.RetryAiTextRewrite -> aiDelegate.retryAiTextRewrite()
            is ReadBookIntent.SetAiTextRewriteReasoningLevel ->
                aiDelegate.setAiTextRewriteReasoningLevel(intent.level)
            is ReadBookIntent.ConfirmAiTextRewrite -> aiDelegate.confirmAiTextRewrite()
            is ReadBookIntent.OpenAiRewritePresetConfig -> aiDelegate.openAiRewritePresetConfig()
            is ReadBookIntent.CloseAiRewritePresetConfig -> aiDelegate.closeAiRewritePresetConfig()
            is ReadBookIntent.AddAiRewritePreset -> aiDelegate.startAddAiRewritePreset()
            is ReadBookIntent.EditAiRewritePreset -> aiDelegate.startEditAiRewritePreset(intent.preset)
            is ReadBookIntent.SetAiRewritePresetName ->
                aiDelegate.setAiRewritePresetName(intent.name)
            is ReadBookIntent.SetAiRewritePresetInstruction ->
                aiDelegate.setAiRewritePresetInstruction(intent.instruction)
            is ReadBookIntent.SaveAiRewritePreset -> aiDelegate.saveAiRewritePreset()
            is ReadBookIntent.CancelAiRewritePresetEdit -> aiDelegate.clearAiRewritePresetDraft()
            is ReadBookIntent.RequestDeleteAiRewritePreset ->
                aiDelegate.requestDeleteAiRewritePreset(intent.preset)
            is ReadBookIntent.ConfirmDeleteAiRewritePreset -> aiDelegate.deleteAiRewritePreset()
            is ReadBookIntent.DismissDeleteAiRewritePreset ->
                aiDelegate.dismissDeleteAiRewritePreset()
            is ReadBookIntent.ChangeSourceBook -> changeTo(intent.book)
            is ReadBookIntent.ChangeSource -> changeTo(intent.book, intent.toc)
            is ReadBookIntent.AddSourceAsNewBook -> addToBookshelf(intent.book, intent.toc)
            is ReadBookIntent.OpenChapterResult -> openChapter(intent.index, intent.chapterPos)
            is ReadBookIntent.SourceEditResult -> upBookSource()
            is ReadBookIntent.ReplaceRuleResult -> replaceRuleDelegate.rulesChanged()
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
            is ReadBookIntent.AddBookmark -> bookmarkDelegate.addForCurrentPage()
            is ReadBookIntent.ToggleBookmark -> bookmarkDelegate.toggleForCurrentPage()
            is ReadBookIntent.SaveBookmark -> bookmarkDelegate.save(intent.bookmark)
            is ReadBookIntent.DeleteBookmark -> bookmarkDelegate.delete(intent.bookmark)
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
                    highlightRuleDelegate.load()
                    _uiState.update { it.copy(activeSheet = intent.sheet) }
                } else if (
                    intent.sheet is ReadBookSheet.ContentProcesses ||
                    intent.sheet is ReadBookSheet.TextProcessing
                ) {
                    _uiState.update { it.copy(activeSheet = intent.sheet) }
                    contentProcessDelegate.load()
                } else if (intent.sheet is ReadBookSheet.AiRewritePresetConfig) {
                    aiDelegate.openAiRewritePresetConfig()
                } else {
                    _uiState.update { it.copy(activeSheet = intent.sheet) }
                }
            }
            is ReadBookIntent.DismissSheet -> {
                aiDelegate.onSheetDismissed(_uiState.value.activeSheet)
                when (_uiState.value.activeSheet) {
                    is ReadBookSheet.HighlightRuleConfig -> highlightRuleDelegate.onSheetDismissed()
                    is ReadBookSheet.Marking -> markingDelegate.onSheetDismissed()
                    is ReadBookSheet.ContentEdit -> contentEditDelegate.onSheetDismissed()
                    is ReadBookSheet.ContentProcesses,
                    is ReadBookSheet.TextProcessing -> contentProcessDelegate.onSheetDismissed()
                    else -> Unit
                }
                _uiState.update { it.copy(activeSheet = null) }
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
            is ReadBookIntent.OpenBookInfo -> openBookNavigation(ReaderBookSheetTab.Information) {
                ReadBookEffect.OpenBookInfo(it.name, it.author, it.bookUrl)
            }
            is ReadBookIntent.OpenBookInfoDirect -> openBookInfoDirect()

            is ReadBookIntent.OpenChapterList -> openBookNavigation(ReaderBookSheetTab.Toc) {
                ReadBookEffect.OpenChapterList(it.bookUrl)
            }
            is ReadBookIntent.OpenChapterUrl -> openChapterUrl()
            is ReadBookIntent.SourceCustomButton -> runSourceCustomButton(intent.longClick)
            is ReadBookIntent.ToggleReadUrlInBrowser -> toggleReadUrlInBrowser()
            is ReadBookIntent.OpenContentEdit -> contentEditDelegate.open()
            is ReadBookIntent.LoadContentEdit -> contentEditDelegate.load()
            is ReadBookIntent.SaveContentEdit ->
                contentEditDelegate.save(intent.content, intent.saveToSource)
            is ReadBookIntent.ResetContentEdit -> contentEditDelegate.reset()
            is ReadBookIntent.SetContentEditText -> contentEditDelegate.setText(intent.text)
            is ReadBookIntent.SetContentEditSaveToSource ->
                contentEditDelegate.setSaveToSource(intent.value)
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
                    replaceRuleDelegate.changeUseReplaceRule(enabled)
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
            is ReadBookIntent.UpdateConfig -> {
                configUpdateDelegate.handle(intent.update)
            }
            is ReadBookIntent.AddHighlightRule -> highlightRuleDelegate.startAddRule()
            is ReadBookIntent.EditHighlightRule ->
                highlightRuleDelegate.startEditRule(intent.rule)
            is ReadBookIntent.ToggleHighlightRule ->
                highlightRuleDelegate.toggleRule(intent.rule, intent.enabled)
            is ReadBookIntent.SaveHighlightRule -> highlightRuleDelegate.saveRule(intent.rule)
            is ReadBookIntent.DismissHighlightRuleEdit -> highlightRuleDelegate.dismissRuleEdit()
            is ReadBookIntent.RequestDeleteHighlightRule ->
                highlightRuleDelegate.requestDeleteRule(intent.rule)
            is ReadBookIntent.ConfirmDeleteHighlightRule ->
                highlightRuleDelegate.deletePendingRule()
            is ReadBookIntent.DismissDeleteHighlightRule ->
                highlightRuleDelegate.dismissDeleteRule()
            is ReadBookIntent.MoveHighlightRule ->
                highlightRuleDelegate.moveRule(intent.from, intent.to)
            is ReadBookIntent.SaveHighlightRuleOrder -> highlightRuleDelegate.saveRuleOrder()
            is ReadBookIntent.ImportHighlightRuleSource ->
                highlightRuleDelegate.importSource(intent.text)
            is ReadBookIntent.OpenHighlightRuleImportPicker -> {
                _effects.tryEmit(ReadBookEffect.OpenHighlightRuleImportPicker)
            }
            is ReadBookIntent.HighlightRuleImportFileSelected ->
                highlightRuleDelegate.importFile(intent.uri)
            is ReadBookIntent.CancelHighlightRuleImport -> highlightRuleDelegate.cancelImport()
            is ReadBookIntent.ToggleHighlightRuleImportSelection ->
                highlightRuleDelegate.toggleImportSelection(intent.index)
            is ReadBookIntent.ToggleHighlightRuleImportAll ->
                highlightRuleDelegate.toggleImportAll(intent.isSelected)
            is ReadBookIntent.UpdateHighlightRuleImportItem ->
                highlightRuleDelegate.updateImportItem(intent.index, intent.rule)
            is ReadBookIntent.SaveImportedHighlightRules -> highlightRuleDelegate.saveImported()
            is ReadBookIntent.ExportHighlightRules -> {
                _effects.tryEmit(ReadBookEffect.OpenHighlightRuleExportPicker)
            }
            is ReadBookIntent.ExportHighlightRulesAsUrl -> highlightRuleDelegate.exportAsUrl()
            is ReadBookIntent.ExportHighlightRulesToFile ->
                highlightRuleDelegate.exportToFile(intent.uri)
            is ReadBookIntent.SaveMenuCustomIcon ->
                buttonConfigDelegate.saveMenuCustomIcon(intent.id, intent.uri)
            is ReadBookIntent.SaveTitleBarCustomIcon ->
                buttonConfigDelegate.saveTitleBarCustomIcon(intent.id, intent.uri)
            is ReadBookIntent.OpenMenuCustomIconPicker -> {
                _effects.tryEmit(ReadBookEffect.OpenMenuCustomIconPicker(intent.id))
            }
            is ReadBookIntent.OpenTitleBarCustomIconPicker -> {
                _effects.tryEmit(ReadBookEffect.OpenTitleBarCustomIconPicker(intent.id))
            }
            is ReadBookIntent.SaveMenuButtonConfig ->
                buttonConfigDelegate.saveMenuButtons(intent.items)
            is ReadBookIntent.SaveTitleBarButtonConfig ->
                buttonConfigDelegate.saveTitleBarButtons(intent.items)
            is ReadBookIntent.SaveMoreActionsConfig ->
                buttonConfigDelegate.saveMoreActions(intent.items)

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

            is ReadBookIntent.TtsProgress -> readAloudDelegate.updateProgress(intent.chapterStart)
            is ReadBookIntent.ReadAloudAction -> readAloudDelegate.openDefaultInterface()
            is ReadBookIntent.ConfirmAddCurrentBookToBookshelf -> addCurrentBookToBookshelfAndFinish()
            is ReadBookIntent.ExitWithoutAddingCurrentBookToBookshelf -> removeCurrentNotShelfBookAndFinish()

            is ReadBookIntent.ShowReadAloudConfig -> readAloudDelegate.openConfigSheet()
            is ReadBookIntent.OpenPreDownloadNumPicker ->
                readAloudDelegate.openPreDownloadNumPicker()
            is ReadBookIntent.OpenPreSynthesisConcurrencyPicker ->
                readAloudDelegate.openPreSynthesisConcurrencyPicker()
            is ReadBookIntent.OpenParagraphIntervalPicker ->
                readAloudDelegate.openParagraphIntervalPicker()
            is ReadBookIntent.OpenCacheCleanTimePicker ->
                readAloudDelegate.openCacheCleanTimePicker()
            is ReadBookIntent.ApplyPreDownloadNum ->
                readAloudDelegate.applyPreDownloadNum(intent.value)
            is ReadBookIntent.ApplyPreSynthesisConcurrency ->
                readAloudDelegate.applyPreSynthesisConcurrency(intent.value)
            is ReadBookIntent.ApplyAudioCacheCleanTime ->
                readAloudDelegate.applyAudioCacheCleanTime(intent.value)
            is ReadBookIntent.ApplyParagraphInterval ->
                readAloudDelegate.applyParagraphInterval(intent.value)
            is ReadBookIntent.SetReadAloudIgnoreAudioFocus ->
                readAloudDelegate.setIgnoreAudioFocus(intent.value)
            is ReadBookIntent.SetReadAloudPauseOnPhoneCall ->
                readAloudDelegate.setPauseOnPhoneCall(intent.value)
            is ReadBookIntent.SetReadAloudWakeLock -> readAloudDelegate.setWakeLock(intent.value)
            is ReadBookIntent.SetShowReadAloudCapsule ->
                readAloudDelegate.setShowCapsule(intent.value)
            is ReadBookIntent.SetCapsuleAutoCollapse ->
                readAloudDelegate.setCapsuleAutoCollapse(intent.value)
            ReadBookIntent.ResetReadAloudCapsulePosition ->
                readAloudDelegate.resetCapsulePosition()
            is ReadBookIntent.SetReadAloudCapsulePosition ->
                readAloudDelegate.setCapsulePosition(intent.x, intent.y)
            is ReadBookIntent.SetReadAloudMediaButtonPerNext ->
                readAloudDelegate.setMediaButtonPerNext(intent.value)
            is ReadBookIntent.SetReadAloudByPage -> readAloudDelegate.setByPage(intent.value)
            is ReadBookIntent.SetReadAloudSystemMediaCompat ->
                readAloudDelegate.setSystemMediaCompat(intent.value)
            is ReadBookIntent.SetReadAloudStreamAudio ->
                readAloudDelegate.setStreamAudio(intent.value)
            is ReadBookIntent.ReadAloudPrevParagraph -> readAloudDelegate.prevParagraph()
            is ReadBookIntent.ReadAloudTogglePause -> _effects.tryEmit(ReadBookEffect.ToggleReadAloud)
            is ReadBookIntent.ReadAloudStop -> readAloudDelegate.stop()
            is ReadBookIntent.ReadAloudNextParagraph -> readAloudDelegate.nextParagraph()
            is ReadBookIntent.ReadAloudPrevChapter -> readAloudDelegate.prevChapter()
            is ReadBookIntent.ReadAloudNextChapter -> readAloudDelegate.nextChapter()
            is ReadBookIntent.SetReadAloudTtsTimer -> readAloudDelegate.setTtsTimer(intent.value)
            is ReadBookIntent.SetReadAloudTtsFollowSys ->
                readAloudDelegate.setTtsFollowSys(intent.value)
            is ReadBookIntent.SetReadAloudTtsSpeechRate ->
                readAloudDelegate.setTtsSpeechRate(intent.value)
            is ReadBookIntent.SetSpeechAnalysisMode ->
                readAloudDelegate.setSpeechAnalysisMode(intent.value)
            is ReadBookIntent.SetUseMultiSpeaker ->
                readAloudDelegate.setUseMultiSpeaker(intent.value)
            is ReadBookIntent.SetDefaultReadAloudInterface ->
                readAloudDelegate.setDefaultInterface(intent.value)
            is ReadBookIntent.OpenSystemTtsSettings -> readAloudDelegate.openSystemTtsSettings()
            is ReadBookIntent.ClearTtsCache -> readAloudDelegate.clearTtsCache()
            ReadBookIntent.OpenTtsEnginesAndVoices -> readAloudDelegate.openTtsEnginesAndVoices()
            ReadBookIntent.OpenTtsCache -> readAloudDelegate.openTtsCache()
            ReadBookIntent.OpenBookVoiceCasting -> readAloudDelegate.openBookVoiceCasting()
            ReadBookIntent.OpenReadAloudPlayer -> readAloudDelegate.openPlayer()
            ReadBookIntent.OpenClassicReadAloudControls -> readAloudDelegate.openClassicControls()

            is ReadBookIntent.SelectFont -> styleDelegate.selectFont(intent.path)
            is ReadBookIntent.SelectTitleFont -> styleDelegate.selectTitleFont(intent.path)
            is ReadBookIntent.SelectTitleSystemTypeface ->
                styleDelegate.selectTitleSystemTypeface(intent.index)
            is ReadBookIntent.SelectSystemTypeface ->
                styleDelegate.selectSystemTypeface(intent.index)

            is ReadBookIntent.ColorSelected -> styleDelegate.colorSelected(intent.dialogId, intent.color)
            is ReadBookIntent.ShowPageAnimConfig -> {
                _uiState.update { it.copy(activeSheet = ReadBookSheet.PageAnim) }
            }

            is ReadBookIntent.OpenReplaceEditor -> _effects.tryEmit(
                ReadBookEffect.OpenReplaceEditor(
                    intent.id,
                    intent.pattern
                )
            )

            is ReadBookIntent.ReplaceRuleChanged -> replaceRuleDelegate.rulesChanged()
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
            is ReadBookIntent.ReadStyleImageSelected ->
                styleDelegate.applyBackgroundImage(intent.uri)
            is ReadBookIntent.ReadStyleImageSelectedForMode ->
                styleDelegate.applyBackgroundImageForMode(intent.uri, intent.isNight)
            is ReadBookIntent.BookmarkBadgeImageSelected ->
                bookmarkBadgeDelegate.applyBadgeImage(intent.uri)

            is ReadBookIntent.ClearBookmarkBadgeImage ->
                bookmarkBadgeDelegate.clearBadgeImage()
            is ReadBookIntent.ReadStyleConfigImportSelected -> styleDelegate.importConfig(intent.uri)
            is ReadBookIntent.ReadStyleConfigExportSelected -> styleDelegate.exportConfig(intent.uri)
            is ReadBookIntent.SaveReadStyleConfig -> styleDelegate.saveCurrentStyle()
            is ReadBookIntent.AddReadStyleConfig -> styleDelegate.addStyle()
            is ReadBookIntent.DeleteCurrentReadStyleConfig -> styleDelegate.deleteCurrentStyle()
            is ReadBookIntent.ApplyPresetTheme -> styleDelegate.applyPresetTheme(intent.presetIndex)
            is ReadBookIntent.OpenBgTextConfig -> styleDelegate.openBgTextConfig(intent.index)

            is ReadBookIntent.ToggleDayNight -> styleDelegate.toggleDayNight()
            // Text action menu
            is ReadBookIntent.TextActionAloud -> {
                when (readAloudSettingsRepository.currentSettings.contentSelectSpeakMode) {
                    1 -> intent.selectStartPos?.let {
                        _effects.tryEmit(ReadBookEffect.TextActionAloudSelect(it.copy()))
                    } ?: _effects.tryEmit(ReadBookEffect.TextActionSpeak(intent.text))
                    else -> _effects.tryEmit(ReadBookEffect.TextActionSpeak(intent.text))
                }
            }

            is ReadBookIntent.TextActionBookmark -> bookmarkDelegate.openEditor(intent.bookmark)

            is ReadBookIntent.OpenMarking -> {
                // 从划词菜单新建：无原 sheet 可回
                markingReturnSheet = null
                markingDelegate.open(intent.selection)
                _uiState.update { it.copy(activeSheet = ReadBookSheet.Marking) }
            }

            is ReadBookIntent.EditMarking -> {
                // 从目录 Sheet 进入：记住原 sheet，保存/删除/取消后返回
                markingReturnSheet = _uiState.value.activeSheet
                markingDelegate.openForEdit(intent.id)
                _uiState.update { it.copy(activeSheet = ReadBookSheet.Marking) }
            }

            is ReadBookIntent.DismissMarking -> {
                markingDelegate.onSheetDismissed()
                restoreMarkingReturnSheet()
            }

            is ReadBookIntent.SaveMarking -> {
                markingDelegate.save(intent.style, intent.note)
            }

            is ReadBookIntent.DeleteMarking -> {
                markingDelegate.deleteCurrent()
            }

            is ReadBookIntent.NavigateToBookmark ->
                bookmarkNavigateDelegate.navigateToBookmark(intent.bookmark)

            is ReadBookIntent.NavigateToMarking ->
                bookmarkNavigateDelegate.navigateToMarking(intent.marking)

            is ReadBookIntent.ConfirmBookmarkTargetJump ->
                bookmarkNavigateDelegate.confirmJump()

            is ReadBookIntent.CancelBookmarkTargetJump ->
                bookmarkNavigateDelegate.cancelJump()

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
                    _effects.tryEmit(ReadBookEffect.OpenSearch(intent.text, bookUrl))
                }
            }

            is ReadBookIntent.TextActionDict -> {
                _uiState.update { it.copy(activeSheet = ReadBookSheet.Dict(intent.text)) }
            }

            is ReadBookIntent.OpenAiTextClean -> {
                aiDelegate.openAiTextClean(
                    text = intent.text,
                    chapterIndex = intent.chapterIndex,
                    chapterPosition = intent.chapterPosition,
                )
            }

            is ReadBookIntent.RetryAiTextClean -> aiDelegate.retryAiTextClean()
            is ReadBookIntent.SetAiTextCleanReasoningLevel ->
                aiDelegate.setAiTextCleanReasoningLevel(intent.level)
            is ReadBookIntent.ConfirmAiTextClean -> aiDelegate.confirmAiTextClean()

            is ReadBookIntent.OpenAiTextRewrite -> {
                viewModelScope.launch {
                    aiDelegate.openAiTextRewrite(
                        text = intent.text,
                        chapterIndex = intent.chapterIndex,
                        chapterPosition = intent.chapterPosition,
                    )
                }
            }

            is ReadBookIntent.ApplySimulatedReading -> {
                ReadBook.clearTextChapter()
                execute {
                    ReadBook.book?.let { loadDelegate.initBook(it) }
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

            ReadBookIntent.ToggleEyeProtection -> styleDelegate.toggleEyeProtection()
            is ReadBookIntent.EyeProtectionEnabledChanged -> styleDelegate.updateEyeProtection {
                it.copy(eyeProtectionEnabled = intent.value)
            }
            is ReadBookIntent.EyeProtectionIntensityChanged -> styleDelegate.updateEyeProtection {
                it.copy(colorTemperature = intent.value.coerceIn(0, 100))
            }
            is ReadBookIntent.EyeProtectionAutoNightChanged -> styleDelegate.updateEyeProtection {
                it.copy(eyeProtectionAutoNight = intent.value)
            }
            is ReadBookIntent.EyeProtectionScheduleChanged -> styleDelegate.updateEyeProtection {
                it.copy(eyeProtectionSchedule = intent.value)
            }
            is ReadBookIntent.EyeProtectionStartTimeChanged -> styleDelegate.updateEyeProtection {
                it.copy(eyeProtectionStartTime = intent.value)
            }
            is ReadBookIntent.EyeProtectionEndTimeChanged -> styleDelegate.updateEyeProtection {
                it.copy(eyeProtectionEndTime = intent.value)
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
                val chapter = currentChapter() ?: return@launch
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
            val chapter = currentChapter() ?: return@launch
            _uiState.update {
                it.copy(
                    activeSheet = ReadBookSheet.ChangeChapterSource(
                        chapter.index, chapter.title
                    )
                )
            }
        }
    }

    // --- ReadBook 回调（已全部离开本 ViewModel）---
    //
    // Track B2：渲染子集（upContent/upContentAwait/pageChanged/contentLoadFinish/
    // upPageAnim/cancelSelect/onLayoutPageCompleted）下沉到 UI 层渲染控制器
    // （ReadBook.renderCallBack）。
    // R2.3：状态子集（upMenuView/loadChapterList/notifyBookChanged/sureNewProgress）
    // 迁入 LegacyReaderSession，本 VM 改为订阅 collectReaderSessionEvents()。
    // 业务状态刷新另有 collectReaderSession() 反应式收集 ReadBook.snapshot 驱动。
    // 下面两个不再是 override——除了会话事件，VM 自己也在若干处直接调用。

    private fun loadChapterList(book: Book) {
        ReadBook.upMsg(context.getString(R.string.toc_updateing))
        loadDelegate.doLoadChapterList(book)
    }

    private fun sureNewProgress(progress: BookProgress) {
        _uiState.update {
            it.copy(activeDialog = ReadBookDialog.ConfirmRestoreProgress(progress))
        }
    }

    /**
     * 首次内容渲染完成——由渲染控制器（renderCallBack.contentLoadFinish）幂等调用。
     * `isInitFinish` 是纯业务/UI 标志，不属于渲染，故留在 VM。
     */
    fun markInitFinished() {
        if (!_uiState.value.isInitFinish) {
            _uiState.update { it.copy(isInitFinish = true) }
        }
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
            ReadConfigUpdateBus.events.collect { actions ->
                _uiState.update {
                    it.copy(
                        styleConfig = buildStyleConfig(),
                        sheetConfig = buildSheetConfig(),
                    )
                }
                emitEffectWhenSubscribed(ReadBookEffect.UpdateReadViewConfig(actions))
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
                readAloudDelegate.updateProgress(chapterStart)
            }
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
            menuBgColorDay = dur.menuBgColor(isNight = false),
            menuBgColorNight = dur.menuBgColor(isNight = true),
            menuAccentColorDay = dur.menuAccentColor(isNight = false),
            menuAccentColorNight = dur.menuAccentColor(isNight = true),
            configCount = styleState.items.size,
            styleItems = styleState.items.toImmutableList(),
        )
    }

    private fun buildSheetConfig(): ReadSheetConfigUiState = ReadSheetConfigUiState(
        textSize = ReadBookConfig.textSize,
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
        headerFont = ReadBookConfig.headerFont,
        footerFont = ReadBookConfig.footerFont,
        headerFontSize = ReadBookConfig.headerFontSize,
        footerFontSize = ReadBookConfig.footerFontSize,
        applyHeaderStyle = ReadBookConfig.applyHeaderStyle,
        tipDividerColor = ReadBookConfig.tipDividerColor,
        headerMode = ReadBookConfig.headerMode,
        footerMode = ReadBookConfig.footerMode,
        showHeaderLine = ReadBookConfig.showHeaderLine,
        showFooterLine = ReadBookConfig.showFooterLine,
        tipHeaderLeft = ReadBookConfig.tipHeaderLeft,
        tipHeaderMiddle = ReadBookConfig.tipHeaderMiddle,
        tipHeaderRight = ReadBookConfig.tipHeaderRight,
        tipFooterLeft = ReadBookConfig.tipFooterLeft,
        tipFooterMiddle = ReadBookConfig.tipFooterMiddle,
        tipFooterRight = ReadBookConfig.tipFooterRight,
        customTipHeaderLeft = ReadBookConfig.customTipHeaderLeft,
        customTipHeaderMiddle = ReadBookConfig.customTipHeaderMiddle,
        customTipHeaderRight = ReadBookConfig.customTipHeaderRight,
        customTipFooterLeft = ReadBookConfig.customTipFooterLeft,
        customTipFooterMiddle = ReadBookConfig.customTipFooterMiddle,
        customTipFooterRight = ReadBookConfig.customTipFooterRight,
        tipHeaderColor = ReadBookConfig.tipHeaderColor,
        tipHeaderColorNight = ReadBookConfig.tipHeaderColorNight,
        tipFooterColor = ReadBookConfig.tipFooterColor,
        tipFooterColorNight = ReadBookConfig.tipFooterColorNight,
        textFullJustify = readSettingsRepository.currentSettings.textFullJustify,
        textBottomJustify = readSettingsRepository.currentSettings.textBottomJustify,
        configNames = readBookStyleConfigRepository.currentState.items.map { it.name }
            .filter { it.isNotBlank() }
            .toImmutableList(),
    )

    private fun syncFromReadBook(current: ReadBookUiState): ReadBookUiState {
        val book = ReadBook.book
        val textChapter = ReadBook.curTextChapter
        val translationStatus = observeCurrentTranslation(book, ReadBook.durChapterIndex)
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
            translationStatus = translationStatus,
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
                readMenuTopBarMergeButtons = ReadBookConfig.readMenuTopBarMergeButtons,
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
                moreActionItems = buttonConfigDelegate
                    .parseMoreActions(readSettingsRepository.currentSettings.moreActionsConfig)
                    .toImmutableList(),
                showBrightnessView = ReadBookConfig.showBrightnessView,
                brightnessVwPos = ReadBookConfig.brightnessVwPos,
                readBrightness = ReadBookConfig.readBrightness,
                brightnessAuto = ReadBookConfig.brightnessAuto,
                showMenuIcon = ReadBookConfig.showMenuIcon,
                titleBarCompact = ReadBookConfig.titleBarCompact,
            ),
        )
    }

    private fun observeCurrentTranslation(
        book: Book?,
        chapterIndex: Int,
    ): TranslationChapterStatus {
        val key = book
            ?.takeIf { it.getTranslationMode() }
            ?.let { TranslationChapterKey(it.bookUrl, chapterIndex) }
        if (key == observedTranslationKey && translationStatusJob?.isActive == true) {
            return _uiState.value.translationStatus
        }

        translationStatusJob?.cancel()
        val taskFlow = key?.let {
            TranslationManager.getChapterTaskStateFlow(it.bookUrl, it.chapterIndex)
        }
        if (taskFlow == null) {
            observedTranslationKey = null
            return TranslationChapterStatus.Idle
        }

        observedTranslationKey = key
        translationStatusJob = viewModelScope.launch {
            taskFlow.takeWhile { taskState ->
                if (observedTranslationKey == taskState.key) {
                    _uiState.update { state ->
                        state.copy(translationStatus = taskState.status)
                    }
                }
                taskState.status == TranslationChapterStatus.Translating ||
                    taskState.status == TranslationChapterStatus.Thinking
            }.collect {}
            if (observedTranslationKey == key) observedTranslationKey = null
        }
        return taskFlow.value.status
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

    /**
     * 当前会话书籍的当前章节。
     *
     * R2.1：VM 不再直连 Room DAO，书籍/目录读写一律经 [BookRepository]。
     * 原来散在十余处的 `getChapter(book.bookUrl, ReadBook.durChapterIndex)` 收敛到这里。
     */
    private suspend fun currentChapter(): BookChapter? {
        val book = ReadBook.book ?: return null
        return bookRepository.getChapter(book.bookUrl, ReadBook.durChapterIndex)
    }

    // 开书 / 目录 / 换源 / 进度同步已迁入 [ReadBookLoadDelegate]，这里只留外部入口的转发。

    suspend fun initReadBookConfig(request: ReadBookInitRequest) =
        loadDelegate.initReadBookConfig(request)

    fun initData(request: ReadBookInitRequest, success: (() -> Unit)? = null) =
        loadDelegate.initData(request, success)

    fun markJustInitData() {
        justInitData = true
    }

    fun changeTo(book: Book, toc: List<BookChapter>) = loadDelegate.changeTo(book, toc)

    fun changeTo(book: Book) = loadDelegate.changeTo(book)

    fun isReadingProgressSyncConfigured(): Boolean = loadDelegate.isReadingProgressSyncConfigured()

    suspend fun uploadBookProgress(book: Book) = loadDelegate.uploadBookProgress(book)

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
            val toc = bookRepository.getChapters(book.bookUrl)
            book.removeType(BookType.notShelf)
            if (book.order == 0) {
                book.order = bookRepository.getMinOrder() - 1
            }
            bookRepository.insert(book)
            if (toc.isNotEmpty()) {
                bookRepository.insertChapters(*toc.toTypedArray())
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
                ReadBook.bookSource = bookSourceRepository.getBookSource(book.origin)
            }
        }.onSuccess {
            success?.invoke()
        }
    }

    private fun refreshCurrentChapter() {
        execute {
            ReadBook.book?.let { book ->
                currentChapter()?.let { chapter ->
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
                bookRepository.getChapters(
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
            bookRepository.getChapter(book.bookUrl, chapterIndex)
                ?.let { chapter ->
                    BookHelp.saveText(book, chapter, content)
                    ReadBook.loadContent(chapterIndex, resetPageOffset = false)
                }
        }
    }

    fun reverseContent() {
        execute {
            val book = ReadBook.book ?: return@execute
            val chapter = currentChapter() ?: return@execute
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

    /** 纯定位逻辑见 [ReadSearchResultLocator]；这里保留转发入口供 ReadBookController 调用。 */
    fun searchResultPositions(
        textChapter: TextChapter,
        searchResult: SearchResult,
        query: String = _uiState.value.searchContentQuery,
    ): Array<Int> = ReadSearchResultLocator.positions(textChapter, searchResult, query)

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

    @Suppress("LongMethod")
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
            val chapter = currentChapter() ?: return@launch
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
                bookSourceRepository.updateSources(it)
            }
        }
    }

    fun refreshSeekState() {
        _uiState.update { it.copy(
            seekProgress = calculateSeekProgress(),
            seekMax = calculateSeekMax(),
        ) }
    }

    private fun openChapterUrl() {
        if (ReadBook.isLocalBook) return
        viewModelScope.launch {
            val chapter = ReadBook.curTextChapter?.chapter
                ?: currentChapter()
                ?: return@launch
            val url = chapter.getAbsoluteURL()
            if (url.isBlank()) return@launch
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
        viewModelScope.launch {
            val chapter = currentChapter()
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
        viewModelScope.launch {
            val chapter = currentChapter()
            if (chapter == null) {
                _effects.tryEmit(ReadBookEffect.ShowToast(context.getString(R.string.no_chapter)))
                return@launch
            }
            _uiState.update {
                it.copy(activeDialog = ReadBookDialog.ConfirmChapterPay(chapter.title))
            }
        }
    }

    private fun confirmPayAction() {
        val book = ReadBook.book ?: return
        if (book.isLocal) return
        execute {
            val source = ReadBook.bookSource ?: throw NoStackTraceException("no book source")
            val chapter = currentChapter()
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
            loadDelegate.doLoadChapterList(book)
        } else {
            execute { loadDelegate.initBook(book) }
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
        translationStatusJob?.cancel()
        super.onCleared()
        if (BaseReadAloudService.isRun && BaseReadAloudService.pause) {
            ReadAloud.stop(context)
        }
        readerSession.detach()
    }

    fun addToBookshelf(book: Book, toc: List<BookChapter>, success: (() -> Unit)? = null) {
        execute {
            book.removeType(BookType.notShelf)
            if (book.order == 0) {
                book.order = bookRepository.getMinOrder() - 1
            }
            bookRepository.insert(book)
            bookRepository.insertChapters(*toc.toTypedArray())
        }.onSuccess {
            success?.invoke()
        }.onError {
            AppLog.put("添加书籍到书架失败", it)
            _effects.tryEmit(ReadBookEffect.ShowToast("添加书籍失败"))
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

private fun Int.coerceSearchResultIndex(resultSize: Int): Int {
    return if (resultSize <= 0) 0 else coerceIn(0, resultSize - 1)
}

private fun String.isHttpTtsImportUri(): Boolean {
    val uri = runCatching { Uri.parse(this) }.getOrNull() ?: return false
    return uri.scheme in setOf("legado", "yuedu")
            && uri.host == "import"
            && uri.path.equals("/httpTTS", ignoreCase = true)
}
