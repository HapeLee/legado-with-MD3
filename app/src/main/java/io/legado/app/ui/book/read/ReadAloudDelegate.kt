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
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.PlaybackTimer
import io.legado.app.domain.model.readaloud.ReadAloudSessionStatus
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.domain.model.readaloud.VoiceCatalogEntry
import io.legado.app.domain.model.settings.ReadAloudSettings
import io.legado.app.domain.usecase.SyncReadAloudVoicesUseCase
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadAloudSessionStore
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.utils.TTSCacheUtils
import io.legado.app.utils.postEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 朗读域（R2.2 续批）。
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
                        showReadAloudCapsule = prefs.showReadAloudCapsule,
                        capsuleAutoCollapse = prefs.capsuleAutoCollapse,
                        readAloudCapsuleOffsetX = prefs.capsuleOffsetX,
                        readAloudCapsuleOffsetY = prefs.capsuleOffsetY,
                        readAloudMediaButtonPerNext = prefs.mediaButtonPerNext,
                        readAloudByPage = prefs.readAloudByPage,
                        readAloudSystemMediaCompat =
                            prefs.systemMediaControlCompatibilityChange,
                        readAloudAndroidMediaControl = prefs.androidMediaControlEnabled,
                        readAloudStreamAudio = prefs.streamReadAloudAudio,
                        readAloudTtsFollowSys = prefs.ttsFollowSys,
                        readAloudTtsSpeechRate = prefs.ttsSpeechRate,
                        readAloudTtsTimer = prefs.ttsTimer,
                        readAloudFinishCurrentChapterAfterTimer =
                            prefs.finishCurrentChapterAfterTimer,
                        speechAnalysisMode = prefs.speechAnalysisMode,
                        useMultiSpeaker = prefs.useMultiSpeaker,
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

    /** 媒体键/胶囊触发的默认朗读界面：按设置决定开播放器弹层还是经典控制面板。 */
    fun openDefaultInterface() {
        if (
            host.uiState.defaultReadAloudInterface ==
            ReadAloudSettingsRepository.DEFAULT_INTERFACE_PLAYER
        ) {
            host.updateState {
                it.copy(
                    menuState = ReadBookMenuState(),
                    activeSheet = ReadBookSheet.ReadAloudPlayer,
                )
            }
        } else {
            host.openReadMenuRoute(ReadBookMenuRoute.ReadAloud)
        }
    }

    fun openPlayer() {
        host.updateState {
            it.copy(menuState = ReadBookMenuState(), activeSheet = ReadBookSheet.ReadAloudPlayer)
        }
    }

    fun openClassicControls() {
        host.updateState { it.copy(activeSheet = null) }
        host.openReadMenuRoute(ReadBookMenuRoute.ReadAloud)
    }

    fun openConfigSheet() {
        host.updateState { it.copy(activeSheet = ReadBookSheet.ReadAloudConfig) }
        scope.launch { syncConfiguredTtsVoices() }
    }

    fun openTtsEnginesAndVoices() {
        host.updateState { it.copy(activeSheet = null) }
        host.emitEffect(ReadBookEffect.OpenTtsEnginesAndVoices)
    }

    fun openTtsCache() {
        host.updateState { it.copy(activeSheet = null) }
        host.emitEffect(ReadBookEffect.OpenTtsCache)
    }

    fun openBookVoiceCasting() {
        ReadBook.book?.bookUrl?.let { bookUrl ->
            host.updateState { it.copy(activeSheet = null) }
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
        updateSettings { it.copy(ttsTimer = timer) }
        host.updateState { it.copy(readAloudTtsTimer = timer) }
    }

    fun setFinishCurrentChapterAfterTimer(value: Boolean) {
        updateSettings { it.copy(finishCurrentChapterAfterTimer = value) }
        host.updateState { it.copy(readAloudFinishCurrentChapterAfterTimer = value) }
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
     * 多角色朗读开关。正在朗读时必须重启朗读服务才能换掉合成管线，
     * 重启前记住页内位置，等服务真的回到 Idle 再重放，避免新旧管线叠音。
     */
    fun setUseMultiSpeaker(value: Boolean) {
        scope.launch {
            val shouldRestart = BaseReadAloudService.isRun
            val resumePlaying = shouldRestart && !BaseReadAloudService.pause
            val chapter = ReadBook.curTextChapter
            val chapterPosition = readAloudSessionStore.state.value.playback.chapterPosition
            readAloudSettingsRepository.update { it.copy(useMultiSpeaker = value) }
            host.updateState { it.copy(useMultiSpeaker = value) }
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

    private inline fun updateSettings(
        crossinline transform: (ReadAloudSettings) -> ReadAloudSettings,
    ) {
        scope.launch {
            readAloudSettingsRepository.update { transform(it) }
        }
    }
}
