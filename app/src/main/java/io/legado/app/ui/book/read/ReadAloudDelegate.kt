package io.legado.app.ui.book.read

import android.content.Context
import android.speech.tts.TextToSpeech
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.repository.HttpTtsRepository
import io.legado.app.data.repository.ReadAloudSettingsRepository
import io.legado.app.data.repository.ReadSettingsRepository
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.PlaybackTimer
import io.legado.app.domain.model.readaloud.ReadAloudContentSplitSetting
import io.legado.app.domain.model.readaloud.ReadAloudSessionStatus
import io.legado.app.domain.model.readaloud.ReadAloudSplitSymbol
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.domain.model.readaloud.VoiceCatalogEntry
import io.legado.app.domain.model.settings.ReadAloudSettings
import io.legado.app.domain.model.settings.ReadAloudTimerMode
import io.legado.app.domain.usecase.SyncReadAloudVoicesUseCase
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadAloudSessionStore
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.readaloud.ReadAloudPlayerOverlayBus
import io.legado.app.utils.TTSCacheUtils
import io.legado.app.utils.postEvent
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 朗读域。
 *
 * 管朗读设置的读写、四个数值选择弹层、播放传输控制、声音目录同步和 TTS 缓存清理。
 *
 * **无自持状态**：朗读的 20 来个字段散落在 [ReadBookUiState] 里，被 `ReadAloudScreen`、
 * `ReadAloudConfigContent`、`ReadBookScreen`、`ReadBookRouteScreen` 四处直读——
 * 搬出去要同时改这四个 composable 的入参。故与 [ReadConfigUpdateDelegate] /
 * [ReadButtonConfigDelegate] 同形：状态留在 UiState，读写一律经 [Host]。
 */
class ReadAloudDelegate(
    private val context: Context,
    private val scope: CoroutineScope,
    private val host: Host,
    private val readSettingsRepository: ReadSettingsRepository,
    private val readAloudSettingsRepository: ReadAloudSettingsRepository,
    private val readAloudSessionStore: ReadAloudSessionStore,
    private val httpTtsRepository: HttpTtsRepository,
    private val aiProfileGateway: AiProfileGateway,
    private val syncReadAloudVoicesUseCase: SyncReadAloudVoicesUseCase,
) {

    interface Host {
        val uiState: ReadBookUiState

        /** 朗读预下载章节数住在 ReadPreferences，不在 ReadBookUiState。 */
        val preDownloadNum: Int

        /** 系统 TTS 引擎清单（VM 侧 lazy，构造代价高，只取一次）。 */
        val systemTtsEngines: List<TextToSpeech.EngineInfo>

        fun updateState(transform: (ReadBookUiState) -> ReadBookUiState)

        fun emitEffect(effect: ReadBookEffect)

        suspend fun emitEffectAwait(effect: ReadBookEffect)

        fun openReadMenuRoute(route: ReadBookMenuRoute)

        /** 朗读进度（TTS 回调上报的章内偏移），VM 用独立 flow 暴露给胶囊。 */
        fun publishReadAloudProgress(chapterStart: Int)
    }

    /** 订阅朗读设置，投影进 UiState。VM 构造时调一次。 */
    fun collectPreferences() {
        scope.launch {
            readAloudSettingsRepository.preferences.collect { prefs ->
                host.updateState {
                    it.copy(
                        readAloudIgnoreAudioFocus = prefs.ignoreAudioFocus,
                        readAloudPauseOnPhoneCall = prefs.pauseReadAloudWhilePhoneCalls,
                        readAloudWakeLock = prefs.readAloudWakeLock,
                        readAloudKeepOnExit = prefs.keepReadAloudOnExit,
                        showReadAloudCapsule = prefs.showReadAloudCapsule,
                        capsuleAutoCollapse = prefs.capsuleAutoCollapse,
                        readAloudCapsuleOffsetX = prefs.capsuleOffsetX,
                        readAloudCapsuleOffsetY = prefs.capsuleOffsetY,
                        readAloudMediaButtonPerNext = prefs.mediaButtonPerNext,
                        readAloudByPage = prefs.readAloudByPage,
                        readAloudContentSplitMode = prefs.contentSplitMode,
                        readAloudContentSplitSymbols = prefs.contentSplitSymbols.toImmutableSet(),
                        readAloudSystemMediaCompat =
                            prefs.systemMediaControlCompatibilityChange,
                        readAloudAndroidMediaControl = prefs.androidMediaControlEnabled,
                        readAloudStreamAudio = prefs.streamReadAloudAudio,
                        readAloudTtsFollowSys = prefs.ttsFollowSys,
                        readAloudTtsSpeechRate = prefs.ttsSpeechRate,
                        readAloudTtsTimer = prefs.ttsTimer,
                        readAloudFinishCurrentChapterAfterTimer =
                            prefs.finishCurrentChapterAfterTimer,
                        readAloudTimerMode = prefs.timerMode,
                        readAloudTimerChapters = prefs.timerChapters,
                        speechAnalysisMode = prefs.speechAnalysisMode,
                        speechAnalysisReasoningLevel = prefs.speechAnalysisReasoningLevel,
                        useMultiSpeaker = prefs.useMultiSpeaker,
                        multiRoleCast = prefs.multiRoleCast,
                        // 漏填这一项会出现「开关显示关、正文胶囊却还是开」：
                        // 胶囊读的是 ReadConfig.bgmAssign（持久值），开关读的是这里的状态
                        bgmAssign = prefs.bgmAssign,
                        // 总音量存在配乐自己的 prefs 里（与配乐库/段内音量同一处），不在朗读设置模型中
                        bgmVolume = io.legado.app.help.readaloud.cast.BgmPoolStore.volume(),
                        defaultReadAloudInterface = prefs.defaultInterface,
                        preDownloadNum = host.preDownloadNum,
                        audioCacheCleanTime = prefs.audioCacheCleanTime,
                        readAloudParagraphInterval = prefs.ttsParagraphInterval,
                    )
                }
            }
        }
    }

    /**
     * 刷新声音目录。朗读引擎可能在 cloudtts 页被新增/删除，
     * 打开朗读设置弹层和 VM 构造时各同步一次。
     */
    suspend fun syncConfiguredTtsVoices(
        systemTtsLabel: String = context.getString(R.string.system_tts),
        httpTtsList: List<HttpTTS> = httpTtsRepository.getAllSync(),
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
                host.systemTtsEngines.forEach { engine ->
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

    // --- 播放控制 ---

    fun updateProgress(chapterStart: Int) {
        if (BaseReadAloudService.isPlay() && chapterStart > 0) {
            host.publishReadAloudProgress(chapterStart)
        }
    }

    fun stop() {
        ReadAloud.stop(context)
        host.updateState { it.copy(isReadAloudRunning = false, isReadAloudPaused = false) }
    }

    fun prevParagraph() = ReadAloud.prevParagraph(context)

    fun nextParagraph() = ReadAloud.nextParagraph(context)

    /** 朗读面板换章：朗读驱动的章节移动，页面跟随朗读，不视为手动脱离。 */
    fun prevChapter() = BaseReadAloudService.withSpeechNavigation {
        ReadBook.moveToPrevChapter(upContent = true, toLast = false)
    }

    fun nextChapter() = BaseReadAloudService.withSpeechNavigation {
        ReadBook.moveToNextChapter(true)
    }

    /** 回到朗读位置：恢复页面跟随朗读，并跳到朗读所在章节/字符位置。全程不打断当前朗读。 */
    fun backToSpeakingPosition() {
        readAloudSessionStore.restoreReadAloudFollow()
        val speakingChapterIndex = BaseReadAloudService.currentChapterIndex
        val speakingChapterStart = BaseReadAloudService.currentProgress
        if (speakingChapterIndex < 0) return
        // currentProgress 为正在朗读的精确章内位置（onRangeStart 段内偏移上报），整段高亮落在当前段
        val chapterStart = speakingChapterStart.coerceAtLeast(0)
        if (speakingChapterIndex != ReadBook.durChapterIndex) {
            // 跳到朗读位置属于朗读相关的页面移动，不能触发手动脱离
            BaseReadAloudService.withSpeechNavigation {
                ReadBook.openChapter(speakingChapterIndex, chapterStart) {
                    ReadBook.upTextChapterAloudSpan(chapterStart)
                }
            }
        } else {
            ReadBook.syncReadAloudPage(speakingChapterIndex, chapterStart)
            ReadBook.upTextChapterAloudSpan(chapterStart)
        }
    }

    // --- 界面入口 ---

    /** 媒体键/胶囊触发的默认朗读界面：按设置决定开播放器还是经典控制面板。 */
    fun openDefaultInterface() {
        if (
            host.uiState.defaultReadAloudInterface ==
            ReadAloudSettingsRepository.DEFAULT_INTERFACE_PLAYER
        ) {
            openPlayer()
        } else {
            host.openReadMenuRoute(ReadBookMenuRoute.ReadAloud)
        }
    }

    /**
     * 打开听书播放弹层。
     *
     * 播放弹层是 Activity 级全局浮层（[ReadAloudPlayerOverlayBus]），不占导航栈，
     * 所以这里只请求宿主把它拉起来；先把菜单与已有弹层收掉，
     * 关闭播放弹层时不会停在半开的菜单上。
     */
    fun openPlayer() {
        host.updateState { it.copy(menuState = ReadBookMenuState(), activeSheet = null) }
        ReadAloudPlayerOverlayBus.request()
    }

    /** 经典朗读控制面板：阅读菜单里的一页，不遮挡正文区域之外的交互。 */
    fun openClassicControls() {
        host.updateState { it.copy(activeSheet = null) }
        host.openReadMenuRoute(ReadBookMenuRoute.ReadAloud)
    }

    fun openConfigSheet() {
        // 从底栏重新打开算「新一次设置」，回到常规页；被整屏页盖住再回来才停在那一 tab
        host.updateState {
            it.copy(activeSheet = ReadBookSheet.ReadAloudConfig, readAloudConfigTab = 0)
        }
        scope.launch { syncConfiguredTtsVoices() }
    }

    /** 卡片里滑页/点 tab：tab 住在状态里，弹层被拆掉重建才不会退回常规。 */
    fun setConfigTab(tab: Int) {
        host.updateState { it.copy(readAloudConfigTab = tab.coerceIn(0, 1)) }
    }

    /*
     * 下面三个目的地是整屏 NavKey。`activeSheet` 与 `readAloudConfigTab` 分开管：
     * 推送整屏页时不清空 `activeSheet`，返回时弹层自己摊回来、tab 由
     * `readAloudConfigTab` 记住；朗读设置卡片被整屏页盖住时由 ReadBookScreen
     * 按导航栈顶收起，避免窗口级浮层悬在新页面上面。
     */
    fun openTtsEnginesAndVoices() {
        host.emitEffect(ReadBookEffect.OpenTtsEnginesAndVoices)
    }

    fun openTtsCache() {
        host.emitEffect(ReadBookEffect.OpenTtsCache)
    }

    fun openBookVoiceCasting() {
        ReadBook.book?.bookUrl?.let { bookUrl ->
            host.emitEffect(ReadBookEffect.OpenBookVoiceCasting(bookUrl))
        }
    }

    fun openSystemTtsSettings() {
        host.emitEffect(ReadBookEffect.OpenSystemTtsSettings)
    }

    fun clearTtsCache() {
        TTSCacheUtils.clearTtsCache()
        host.emitEffect(
            ReadBookEffect.TtsCacheCleared(context.getString(R.string.clear_cache_success))
        )
    }

    // --- 四个数值选择弹层 ---

    fun openPreDownloadNumPicker() {
        host.updateState {
            it.copy(
                preDownloadNum = host.preDownloadNum,
                activeSheet = ReadBookSheet.PreDownloadConfig,
            )
        }
    }

    fun openPreSynthesisConcurrencyPicker() {
        host.updateState {
            it.copy(
                preSynthesisConcurrency =
                    readAloudSettingsRepository.currentSettings.ttsPreSynthesisConcurrency,
                activeSheet = ReadBookSheet.PreSynthesisConcurrencyConfig,
            )
        }
    }

    fun openParagraphIntervalPicker() {
        host.updateState {
            it.copy(
                readAloudParagraphInterval =
                    readAloudSettingsRepository.currentSettings.ttsParagraphInterval,
                activeSheet = ReadBookSheet.ParagraphIntervalConfig,
            )
        }
    }

    fun openCacheCleanTimePicker() {
        host.updateState {
            it.copy(
                audioCacheCleanTime = readAloudSettingsRepository.currentSettings.audioCacheCleanTime,
                activeSheet = ReadBookSheet.AudioCacheCleanConfig,
            )
        }
    }

    fun applyPreDownloadNum(value: Int) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            readSettingsRepository.setPreDownloadNum(value)
        }
        host.updateState {
            it.copy(preDownloadNum = value, activeSheet = ReadBookSheet.ReadAloudConfig)
        }
    }

    fun applyPreSynthesisConcurrency(value: Int) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            readAloudSettingsRepository.update {
                it.copy(ttsPreSynthesisConcurrency = value.coerceIn(1, 8))
            }
        }
        host.updateState {
            it.copy(preSynthesisConcurrency = value, activeSheet = ReadBookSheet.ReadAloudConfig)
        }
    }

    fun applyAudioCacheCleanTime(value: Int) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            readAloudSettingsRepository.update { it.copy(audioCacheCleanTime = value) }
        }
        host.updateState {
            it.copy(audioCacheCleanTime = value, activeSheet = ReadBookSheet.ReadAloudConfig)
        }
    }

    fun applyParagraphInterval(value: Int) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            readAloudSettingsRepository.update { it.copy(ttsParagraphInterval = value) }
        }
        host.updateState { it.copy(readAloudParagraphInterval = value) }
    }

    // --- 开关类设置 ---

    fun setIgnoreAudioFocus(value: Boolean) = updateSettings { it.copy(ignoreAudioFocus = value) }

    fun setPauseOnPhoneCall(value: Boolean) =
        updateSettings { it.copy(pauseReadAloudWhilePhoneCalls = value) }

    fun setWakeLock(value: Boolean) = updateSettings { it.copy(readAloudWakeLock = value) }

    fun setKeepOnExit(value: Boolean) = updateSettings { it.copy(keepReadAloudOnExit = value) }

    fun setShowCapsule(value: Boolean) = updateSettings { it.copy(showReadAloudCapsule = value) }

    fun setCapsuleAutoCollapse(value: Boolean) =
        updateSettings { it.copy(capsuleAutoCollapse = value) }

    fun setMediaButtonPerNext(value: Boolean) = updateSettings { it.copy(mediaButtonPerNext = value) }

    fun setSystemMediaCompat(value: Boolean) =
        updateSettings { it.copy(systemMediaControlCompatibilityChange = value) }

    fun setAndroidMediaControl(value: Boolean) =
        updateSettings { it.copy(androidMediaControlEnabled = value) }

    fun setByPage(value: Boolean) {
        updateSettings { it.copy(readAloudByPage = value) }
        if (value) postEvent(EventBus.MEDIA_BUTTON, false)
    }

    /**
     * 应用内容划分方式（含标点集合）。[value] 是
     * [ReadAloudContentSplitSetting.encode] 的 `方式|标点` 编码。
     *
     * 一个意图同时承载两者：划分方式决定是否展示标点多选，两者始终一起提交，
     * 拆成两套意图只会让 ReadBookViewModel 多长一条 when 分支。
     */
    fun setContentSplitMode(value: String) {
        val (mode, symbols) = ReadAloudContentSplitSetting.decode(value)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            readAloudSettingsRepository.setContentSplit(
                mode = mode,
                symbols = ReadAloudSplitSymbol.storageValues(symbols),
            )
        }
        host.updateState {
            it.copy(
                readAloudContentSplitMode = mode.storageValue,
                readAloudContentSplitSymbols = symbols.map(Char::toString).toImmutableSet(),
            )
        }
    }

    fun setStreamAudio(value: Boolean) {
        updateSettings { it.copy(streamReadAloudAudio = value) }
        if (value) postEvent(EventBus.MEDIA_BUTTON, false)
    }

    fun resetCapsulePosition() {
        host.updateState { it.copy(readAloudCapsuleOffsetX = 0f, readAloudCapsuleOffsetY = 0f) }
        updateSettings { it.copy(capsuleOffsetX = 0f, capsuleOffsetY = 0f) }
    }

    fun setCapsulePosition(x: Float, y: Float) {
        host.updateState { it.copy(readAloudCapsuleOffsetX = x, readAloudCapsuleOffsetY = y) }
        updateSettings { it.copy(capsuleOffsetX = x, capsuleOffsetY = y) }
    }

    fun setTtsFollowSys(value: Boolean) {
        updateSettings { it.copy(ttsFollowSys = value) }
        host.updateState { it.copy(readAloudTtsFollowSys = value) }
    }

    fun setTtsTimer(value: Int) {
        val timer = PlaybackTimer.normalize(value)
        ReadAloud.setTimer(context, timer)
        // 两种定时互斥：设分钟定时即切到分钟模式并清掉章节配额
        updateSettings {
            it.copy(
                ttsTimer = timer,
                timerMode = ReadAloudTimerMode.Minute.storageValue,
                timerChapters = 0,
            )
        }
        ReadAloud.setTimerChapters(context, 0)
        host.updateState {
            it.copy(
                readAloudTtsTimer = timer,
                readAloudTimerMode = ReadAloudTimerMode.Minute.storageValue,
                readAloudTimerChapters = 0,
            )
        }
    }

    fun setFinishCurrentChapterAfterTimer(value: Boolean) {
        updateSettings { it.copy(finishCurrentChapterAfterTimer = value) }
        host.updateState { it.copy(readAloudFinishCurrentChapterAfterTimer = value) }
    }

    fun setTimerMode(mode: ReadAloudTimerMode) {
        val prefs = readAloudSettingsRepository.currentSettings
        val minutes = if (mode == ReadAloudTimerMode.Minute) prefs.ttsTimer else 0
        val chapters = if (mode == ReadAloudTimerMode.Chapter) prefs.timerChapters else 0
        updateSettings {
            it.copy(
                timerMode = mode.storageValue,
                ttsTimer = minutes,
                timerChapters = chapters,
            )
        }
        ReadAloud.setTimer(context, minutes)
        ReadAloud.setTimerChapters(context, chapters)
        host.updateState {
            it.copy(
                readAloudTimerMode = mode.storageValue,
                readAloudTtsTimer = minutes,
                readAloudTimerChapters = chapters,
            )
        }
    }

    fun setTimerChapters(value: Int) {
        val chapters = PlaybackTimer.normalizeChapters(value)
        ReadAloud.setTimerChapters(context, chapters)
        updateSettings {
            it.copy(
                timerChapters = chapters,
                timerMode = ReadAloudTimerMode.Chapter.storageValue,
                ttsTimer = 0,
            )
        }
        // 切到章节模式要同时停掉正在跑的分钟倒计时
        ReadAloud.setTimer(context, 0)
        host.updateState {
            it.copy(
                readAloudTimerChapters = chapters,
                readAloudTimerMode = ReadAloudTimerMode.Chapter.storageValue,
                readAloudTtsTimer = 0,
            )
        }
    }

    fun setTtsSpeechRate(value: Int) {
        scope.launch {
            readAloudSettingsRepository.update { it.copy(ttsSpeechRate = value.coerceIn(0, 80)) }
            ReadAloud.upTtsSpeechRate(context)
        }
        host.updateState { it.copy(readAloudTtsSpeechRate = value) }
    }

    fun setDefaultInterface(value: String) {
        updateSettings {
            it.copy(
                defaultInterface = value.takeIf { candidate ->
                    candidate in ReadAloudSettingsRepository.AVAILABLE_INTERFACES
                } ?: ReadAloudSettingsRepository.DEFAULT_INTERFACE_CLASSIC
            )
        }
        host.updateState { it.copy(defaultReadAloudInterface = value) }
    }

    /** 非规则模式要求已配置 AI 模型，否则拒绝切换并提示。 */
    fun setSpeechAnalysisMode(value: String) {
        scope.launch {
            if (value != "rule") {
                val configured = aiProfileGateway.getTaskPreset(AiTaskType.ANALYZE_SPEECH)
                    ?: aiProfileGateway.getTaskPreset(AiTaskType.CHAT)
                if (configured == null) {
                    host.emitEffectAwait(
                        ReadBookEffect.ShowToast(
                            context.getString(R.string.speech_analysis_ai_model_required)
                        )
                    )
                    return@launch
                }
            }
            readAloudSettingsRepository.update { it.copy(speechAnalysisMode = value) }
            host.updateState { it.copy(speechAnalysisMode = value) }
        }
    }

    /**
     * 朗读分析的推理级别。关闭思考模式是 AI 朗读分析的默认值：默认思考的模型（智谱 GLM 等）
     * 只把内容放在 reasoning_content 里，分析会直接失败。
     */
    fun setSpeechAnalysisReasoningLevel(value: String) {
        val level = AiReasoningLevel.fromStorage(value, AiReasoningLevel.OFF)
        updateSettings { it.copy(speechAnalysisReasoningLevel = level.storageValue) }
        host.updateState { it.copy(speechAnalysisReasoningLevel = level.storageValue) }
    }

    /**
     * 多角色朗读开关（底栏「多角色朗读」）。
     *
     * 它同时决定三件事：有没有音色计划（旁白/角色音全在那份计划里）、正文按哪种粒度切分、
     * 正文里的角色标记要不要注入，所以正在朗读时必须换掉朗读服务——不重启就是开关只改了
     * 胶囊：开着不换音色，关着又停在配音页选的旁白音上回不去默认引擎。
     * 重排当前章让胶囊即时出现/消失，重启前记住页内位置，等旧服务真的 Idle 再重放，避免叠音。
     */
    fun setMultiRoleCast(value: Boolean) {
        scope.launch {
            val shouldRestart = BaseReadAloudService.isRun
            val resumePlaying = shouldRestart && !BaseReadAloudService.pause
            val chapterPosition = readAloudSessionStore.state.value.playback.chapterPosition
            readAloudSettingsRepository.update { it.copy(multiRoleCast = value) }
            host.updateState { it.copy(multiRoleCast = value) }
            // 设置落库后重排当前章：注入/摘掉角色标记，胶囊才会跟着变
            ReadBook.clearTextChapter()
            ReadBook.loadContent(
                resetPageOffset = false,
                preserveReadAloudPosition = readsDurChapterAloud(),
            )
            if (shouldRestart) {
                restartReadAloudWithNewPipeline(resumePlaying, chapterPosition)
            }
        }
    }

    /**
     * 背景音乐分配开关：只影响正文段首的配乐胶囊渲染与朗读时的配乐轨，
     * 同样不需要重启朗读服务；切换后重排当前章让胶囊即时出现/消失。
     */
    fun setBgmAssign(value: Boolean) {
        host.updateState { it.copy(bgmAssign = value) }
        scope.launch {
            readAloudSettingsRepository.update { it.copy(bgmAssign = value) }
            ReadBook.clearTextChapter()
            ReadBook.loadContent(
                resetPageOffset = false,
                preserveReadAloudPosition = readsDurChapterAloud(),
            )
        }
    }

    /**
     * 朗读服务正在读的就是这一章 —— 重排时把朗读位置交给 `ReadAloud.syncLayout()` 同步，
     * 而不是让它按当前页重新起播（不保位的重载会把读过半章的朗读拽回章首）。
     */
    private fun readsDurChapterAloud(): Boolean =
        BaseReadAloudService.isRun &&
            BaseReadAloudService.currentChapterIndex == ReadBook.durChapterIndex

    /**
     * 背景音乐总音量：只写 prefs + 刷状态，不重排当前章（音量不在胶囊文字里）。
     *
     * 朗读中的配乐轨靠 BgmPoolStore.volumeVersion 发现改动，拖滑杆时即时跟着变。
     */
    fun setBgmVolume(value: Float) {
        val volume = value.coerceIn(0f, 1f)
        host.updateState { it.copy(bgmVolume = volume) }
        io.legado.app.help.readaloud.cast.BgmPoolStore.setVolume(volume)
    }

    /**
     * 原版「多说话人」开关。与上面的多角色朗读一样要换掉合成管线：
     * 正在朗读时重启服务，重启前记住页内位置，等服务真的回到 Idle 再重放，避免新旧管线叠音。
     */
    fun setUseMultiSpeaker(value: Boolean) {
        scope.launch {
            val shouldRestart = BaseReadAloudService.isRun
            val resumePlaying = shouldRestart && !BaseReadAloudService.pause
            val chapterPosition = readAloudSessionStore.state.value.playback.chapterPosition
            readAloudSettingsRepository.update { it.copy(useMultiSpeaker = value) }
            host.updateState { it.copy(useMultiSpeaker = value) }
            if (shouldRestart) {
                restartReadAloudWithNewPipeline(resumePlaying, chapterPosition)
            }
        }
    }

    /**
     * 换掉朗读管线后重启朗读服务。
     *
     * 先 stop 并等旧服务真的回到 Idle 再重放，否则新旧两条管线会同时出声（叠音）。
     * [chapterPosition] 是重启前记住的页内位置，让用户听回来刚才那句而不是章首。
     */
    private suspend fun restartReadAloudWithNewPipeline(resumePlaying: Boolean, chapterPosition: Int) {
        if (ReadBook.readerChapterInputWindow.current == null) return
        ReadAloud.stop(context)
        val stopped = withTimeoutOrNull(2_000) {
            readAloudSessionStore.state.first {
                it.status == ReadAloudSessionStatus.Idle
            }
        }
        if (stopped == null) return
        ReadAloud.refreshReadAloudClass()
        ReadAloud.play(
            context = context,
            play = resumePlaying,
            chapterPosition = chapterPosition.coerceAtLeast(0),
        )
    }

    private inline fun updateSettings(
        crossinline transform: (ReadAloudSettings) -> ReadAloudSettings,
    ) {
        scope.launch {
            readAloudSettingsRepository.update { transform(it) }
        }
    }
}
