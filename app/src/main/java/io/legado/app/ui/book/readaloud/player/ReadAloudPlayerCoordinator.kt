package io.legado.app.ui.book.readaloud.player

import android.app.Application
import androidx.lifecycle.Observer
import com.jeremyliao.liveeventbus.LiveEventBus
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.HttpTtsRepository
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.gateway.ReadAloudVoiceGateway
import io.legado.app.domain.model.PlaybackTimer
import io.legado.app.domain.model.readaloud.BookVoiceBinding
import io.legado.app.domain.model.readaloud.ContentSplitPolicies
import io.legado.app.domain.model.readaloud.ReadAloudEngineSelection
import io.legado.app.domain.model.readaloud.ReadAloudSessionStatus
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.domain.model.readaloud.VoiceCatalogEntry
import io.legado.app.domain.model.settings.ReadAloudContentSplitMode
import io.legado.app.domain.model.settings.ReadAloudTimerMode
import io.legado.app.domain.usecase.SyncReadAloudVoicesUseCase
import io.legado.app.feature.reader.core.readaloud.ReaderReadAloudChapter
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadAloudSessionStore
import io.legado.app.model.ReadBook
import io.legado.app.model.reader.ReaderChapterInput
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.read.ReadConfigUpdateBus
import io.legado.app.utils.GSON
import io.legado.app.utils.TTSCacheUtils
import io.legado.app.utils.fromJsonObject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Compatibility boundary between the Compose player and the legacy reader/service state. */
class ReadAloudPlayerCoordinator(
    private val application: Application,
    private val sessionStore: ReadAloudSessionStore,
    private val readAloudSettingsGateway: ReadAloudSettingsGateway,
    private val bookRepository: BookRepository,
    private val voiceGateway: ReadAloudVoiceGateway,
    private val httpTtsRepository: HttpTtsRepository,
    private val syncReadAloudVoicesUseCase: SyncReadAloudVoicesUseCase,
) {
    private val refreshRequests = MutableSharedFlow<Unit>(replay = 1)
    private val bookChanges = callbackFlow {
        val observer = Observer<Any> { trySend(Unit) }
        EVENT_KEYS.forEach { LiveEventBus.get<Any>(it).observeForever(observer) }
        trySend(Unit)
        awaitClose {
            EVENT_KEYS.forEach { LiveEventBus.get<Any>(it).removeObserver(observer) }
        }
    }
    private val configChanges = ReadConfigUpdateBus.events.map { }

    /**
     * `TTS_PROGRESS` 会按词回调（见 `BaseReadAloudService.upTtsProgress`），但绝大多数事件并不会
     * 改变快照内容。这里去重，避免每次进度事件都让下游 `flatMapLatest` 重新订阅 Room 章节流、
     * 重建目录列表实例（对照 `AudioPlayCoordinator.chapters` 的同名处理）。
     */
    private val bookState = merge(bookChanges, refreshRequests, configChanges,
        ReadAloud.bookSession.state.map { })
        .map { snapshotBook() }
        .distinctUntilChanged()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val existing = voiceGateway.getVoices()
                if (existing.isEmpty()) {
                    val httpSources = httpTtsRepository.getAll()
                    val entries = buildList {
                        add(
                            VoiceCatalogEntry(
                                engineType = ReadAloudVoice.ENGINE_SYSTEM,
                                engineId = "",
                                displayName = application.getString(R.string.system_tts),
                            )
                        )
                        httpSources.forEach { http ->
                            add(
                                VoiceCatalogEntry(
                                    engineType = ReadAloudVoice.ENGINE_HTTP,
                                    engineId = http.id.toString(),
                                    displayName = http.name,
                                    sourceRevision = http.lastUpdateTime,
                                )
                            )
                        }
                    }
                    syncReadAloudVoicesUseCase(
                        entries = entries,
                        managedSources = setOf(ReadAloudVoice.MANAGED_BY_CONFIGURED_TTS),
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val bookWithChapters = bookState.flatMapLatest { book ->
        if (book.bookUrl.isBlank()) {
            flowOf(
                Triple(
                    book,
                    persistentListOf<ReadAloudChapterSourceState>(),
                    emptyList<BookVoiceBinding>()
                )
            )
        } else {
            combine(
                bookRepository.flowChapters(book.bookUrl),
                voiceGateway.observeBindings(book.bookUrl),
            ) { chapters, bindings ->
                val sourceChapters = chapters.map { chapter ->
                    ReadAloudChapterSourceState(
                        index = chapter.index,
                        title = chapter.title,
                        isVolume = chapter.isVolume,
                        tocLevel = chapter.tocLevel,
                    )
                }.toImmutableList()
                Triple(book, sourceChapters, bindings)
            }
        }
    }

    val state: Flow<ReadAloudPlayerSourceState> = combine(
        sessionStore.state,
        bookWithChapters,
        readAloudSettingsGateway.settings,
        voiceGateway.observeVoices(),
    ) { session, (book, chapters, bindings), settings, voicesList ->
        val playback = session.playback
        val currentEngine = (ReadAloud.bookSession.book ?: ReadBook.book)?.getTtsEngine()
            ?: settings.ttsEngine
        val voiceItems = mapVoiceItems(voicesList, bindings, currentEngine)
        ReadAloudPlayerSourceState(
            bookUrl = book.bookUrl,
            bookName = book.bookName,
            author = book.author,
            coverPath = book.coverPath,
            sourceOrigin = book.sourceOrigin,
            chapterIndex = book.chapterIndex,
            chapterTitle = book.chapterTitle,
            chapters = chapters,
            chapterText = book.chapterText,
            textLines = book.textLines,
            chapterPosition = playback.chapterPosition,
            chapterLength = playback.chapterLength.coerceAtLeast(1),
            playbackText = playback.text,
            engineName = playback.engineName,
            speakerName = playback.characterName.ifBlank { playback.roleType.storageValue },
            isPaused = session.status != ReadAloudSessionStatus.Playing,
            speed = readAloudSettingsGateway.currentSettings.ttsSpeechRate,
            timerMinutes = session.timerMinutes,
            timerMode = settings.timerMode,
            timerChapters = if (session.timerChapters > 0 || session.status != ReadAloudSessionStatus.Idle) {
                session.timerChapters
            } else {
                settings.timerChapters
            },
            finishCurrentChapterAfterTimer = settings.finishCurrentChapterAfterTimer,
            voices = voiceItems,
        )
    }

    fun snapshot(): ReadAloudPlayerSourceState {
        val book = snapshotBook()
        val session = sessionStore.state.value
        val playback = session.playback
        return ReadAloudPlayerSourceState(
            bookUrl = book.bookUrl,
            bookName = book.bookName,
            author = book.author,
            coverPath = book.coverPath,
            sourceOrigin = book.sourceOrigin,
            chapterIndex = book.chapterIndex,
            chapterTitle = book.chapterTitle,
            chapters = persistentListOf(),
            chapterText = book.chapterText,
            textLines = book.textLines,
            chapterPosition = playback.chapterPosition,
            chapterLength = playback.chapterLength.coerceAtLeast(1),
            playbackText = playback.text,
            engineName = playback.engineName,
            speakerName = playback.characterName.ifBlank { playback.roleType.storageValue },
            isPaused = session.status != ReadAloudSessionStatus.Playing,
            speed = readAloudSettingsGateway.currentSettings.ttsSpeechRate,
            timerMinutes = session.timerMinutes,
            timerMode = readAloudSettingsGateway.currentSettings.timerMode,
            timerChapters = if (session.timerChapters > 0 || session.status != ReadAloudSessionStatus.Idle) {
                session.timerChapters
            } else {
                readAloudSettingsGateway.currentSettings.timerChapters
            },
            finishCurrentChapterAfterTimer =
                readAloudSettingsGateway.currentSettings.finishCurrentChapterAfterTimer,
            voices = persistentListOf(),
        )
    }

    fun refresh() {
        refreshRequests.tryEmit(Unit)
    }

    private var chapterSnapshotKey: ChapterSnapshotKey? = null
    private var chapterSnapshotCache: ChapterSnapshot? = null

    private fun snapshotBook(): BookState {
        val book = ReadAloud.bookSession.book ?: ReadBook.book
        val input = if (ReadAloud.bookSession.book != null) ReadAloud.bookSession.input
            else ReadBook.readerChapterInputWindow.current
        val chapter = input?.let(::chapterSnapshot)
        return BookState(
            bookUrl = book?.bookUrl.orEmpty(),
            bookName = book?.name.orEmpty(),
            author = book?.author.orEmpty(),
            coverPath = book?.getDisplayCover(),
            sourceOrigin = book?.origin,
            chapterIndex = chapter?.chapterIndex ?: -1,
            chapterTitle = chapter?.title.orEmpty(),
            chapterText = input?.source?.semanticContent.orEmpty(),
            textLines = chapter?.textLines ?: persistentListOf(),
        )
    }

    /**
     * 章节切分与文本行派生。
     *
     * 整章切分（[ReaderReadAloudChapter.create]）和随后的逐段字符清洗都跑在主线程上，而
     * `snapshotBook()` 会被每个 TTS 进度事件（逐词回调）触发，所以这里按真正影响结果的输入
     * 记忆化：只有章节内容、标题、分页或划分方式变化时才重建。
     */
    private fun chapterSnapshot(input: ReaderChapterInput): ChapterSnapshot {
        val settings = readAloudSettingsGateway.currentSettings
        val key = ChapterSnapshotKey(
            chapterIndex = input.chapter.index,
            title = input.displayTitle,
            semanticContent = input.source.semanticContent,
            pageStarts = if (ReadAloud.bookSession.owns(input.book.bookUrl)) {
                ReadAloud.bookSession.pagination?.pageStarts.orEmpty()
            } else ReadBook.readerPagination(input.chapter.index)?.pageStarts.orEmpty(),
            // 与朗读服务同口径：「默认」在多角色关闭时落到整段，否则听书页展示的
            // 文本行会与服务实际播放的单元粒度不一致。划分只认「多角色朗读」这一个开关，
            // 「多角色分配」是正文胶囊的显示开关，不改朗读单元粒度。
            contentSplitMode = ContentSplitPolicies.resolve(
                mode = ReadAloudContentSplitMode.fromStorage(settings.contentSplitMode),
                useMultiSpeaker = settings.useMultiSpeaker,
            ),
        )
        val cached = chapterSnapshotCache
        if (cached != null && chapterSnapshotKey == key) return cached
        val chapter = ReaderReadAloudChapter.create(
            chapterIndex = key.chapterIndex,
            title = key.title,
            semanticContent = key.semanticContent,
            pageStarts = key.pageStarts,
            contentSplitMode = key.contentSplitMode,
        )
        val snapshot = ChapterSnapshot(
            chapterIndex = chapter.chapterIndex,
            title = chapter.title,
            textLines = chapter.paragraphs.mapNotNull { paragraph ->
                paragraph.text.replace(Regex("[袮祢꧁\uFFFC]"), " ").trim()
                    .takeIf(String::isNotEmpty)?.let {
                    ReadAloudTextLineUi(it, paragraph.chapterPosition)
                }
            }.toImmutableList(),
        )
        chapterSnapshotKey = key
        chapterSnapshotCache = snapshot
        return snapshot
    }

    fun togglePause() {
        when {
            !BaseReadAloudService.isRun -> if (ReadAloud.bookSession.book != null) {
                ReadAloud.play(application, chapterPosition = ReadAloud.bookSession.state.value.chapterPosition,
                    fromReader = false)
            } else ReadBook.readAloud()
            BaseReadAloudService.pause -> ReadAloud.resume(application)
            else -> ReadAloud.pause(application)
        }
    }

    /** 悬浮胶囊与播放界面的停止入口共用。 */
    fun stop() = ReadAloud.stop(application)

    fun previousParagraph() = ReadAloud.prevParagraph(application)
    fun nextParagraph() = ReadAloud.nextParagraph(application)
    fun previousChapter() = ReadAloud.previousChapter(application)
    fun nextChapter() = ReadAloud.nextChapter(application)
    fun selectChapter(index: Int) = ReadAloud.selectChapter(application, index)

    suspend fun clearTtsCache() = withContext(Dispatchers.IO) {
        TTSCacheUtils.clearTtsCache()
    }

    suspend fun setSpeed(value: Int) {
        readAloudSettingsGateway.update { it.copy(ttsSpeechRate = coerceReadAloudSpeed(value)) }
        ReadAloud.upTtsSpeechRate(application)
    }

    suspend fun setTimer(minutes: Int) {
        val timer = PlaybackTimer.normalize(minutes)
        readAloudSettingsGateway.update {
            it.copy(
                ttsTimer = timer,
                timerMode = ReadAloudTimerMode.Minute.storageValue,
                // 两种模式互斥：切到分钟模式时清掉章节配额，避免两个倒计时同时生效
                timerChapters = 0,
            )
        }
        ReadAloud.setTimer(application, timer)
    }

    /** 切换定时模式；切过去的模式若没设过值，等于关闭定时。 */
    suspend fun setTimerMode(mode: ReadAloudTimerMode) {
        readAloudSettingsGateway.update {
            it.copy(
                timerMode = mode.storageValue,
                ttsTimer = if (mode == ReadAloudTimerMode.Chapter) 0 else it.ttsTimer,
                timerChapters = if (mode == ReadAloudTimerMode.Minute) 0 else it.timerChapters,
            )
        }
        val settings = readAloudSettingsGateway.currentSettings
        ReadAloud.setTimer(
            application,
            if (mode == ReadAloudTimerMode.Minute) settings.ttsTimer else 0,
        )
        ReadAloud.setTimerChapters(
            application,
            if (mode == ReadAloudTimerMode.Chapter) settings.timerChapters else 0,
        )
    }

    /** 章节定时剩余章数；0 关闭。 */
    suspend fun setTimerChapters(chapters: Int) {
        val quota = PlaybackTimer.normalizeChapters(chapters)
        readAloudSettingsGateway.update {
            it.copy(
                timerChapters = quota,
                timerMode = ReadAloudTimerMode.Chapter.storageValue,
                ttsTimer = 0,
            )
        }
        ReadAloud.setTimerChapters(application, quota)
    }

    /** 分钟定时到点后是否读完本章再停。 */
    suspend fun setFinishCurrentChapterAfterTimer(value: Boolean) {
        readAloudSettingsGateway.update { it.copy(finishCurrentChapterAfterTimer = value) }
    }

    fun seekTo(chapterPosition: Int, chapterLength: Int) {
        val position = chapterPosition.coerceIn(0, chapterLength)
        ReadAloud.play(application, play = true, chapterPosition = position, fromReader = false)
    }

    suspend fun selectVoice(voiceId: String) {
        val voice = voiceGateway.getVoice(voiceId) ?: return
        val engineValue = when (voice.engineType) {
            ReadAloudVoice.ENGINE_HTTP -> voice.engineId
            ReadAloudVoice.ENGINE_SYSTEM -> if (voice.engineId.isBlank()) null else {
                GSON.toJson(SelectItem(voice.displayName, voice.engineId))
            }

            ReadAloudVoice.ENGINE_CLOUD -> GSON.toJson(
                ReadAloudEngineSelection(
                    engineType = voice.engineType,
                    engineId = voice.engineId,
                    speakerId = voice.speakerId,
                    displayName = voice.displayName,
                )
            )

            else -> return
        }

        val bookUrl = (ReadAloud.bookSession.book ?: ReadBook.book)?.bookUrl.orEmpty()
        if (bookUrl.isNotBlank()) {
            withContext(Dispatchers.IO) {
                bookRepository.getBook(bookUrl)?.let { book ->
                    book.setTtsEngine(engineValue)
                    bookRepository.update(book)
                }
            }
            ReadAloud.bookSession.book?.takeIf { it.bookUrl == bookUrl }?.setTtsEngine(engineValue)
            ReadBook.book?.takeIf { it.bookUrl == bookUrl }?.setTtsEngine(engineValue)
            voiceGateway.upsertBinding(
                BookVoiceBinding(
                    bookUrl = bookUrl,
                    subjectType = BookVoiceBinding.SUBJECT_NARRATOR,
                    subjectId = BookVoiceBinding.SUBJECT_NARRATOR,
                    voiceId = voice.id,
                    locked = true,
                    source = BookVoiceBinding.SOURCE_USER,
                    confidence = 1f,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
        readAloudSettingsGateway.update { it.copy(ttsEngine = engineValue) }

        val wasRunning = BaseReadAloudService.isRun && !BaseReadAloudService.pause
        val currentPos = ReadAloud.bookSession.state.value.chapterPosition.takeIf { it > 0 }
            ?: (snapshotBook().chapterIndex.takeIf { it >= 0 }?.let { ReadBook.durChapterPos } ?: 0)
        ReadAloud.upReadAloudClass()
        if (wasRunning) {
            ReadAloud.play(
                application,
                play = true,
                chapterPosition = currentPos,
                fromReader = false
            )
        }
        refresh()
    }

    private fun mapVoiceItems(
        voicesList: List<ReadAloudVoice>,
        bindings: List<BookVoiceBinding>,
        currentEngine: String?,
    ): ImmutableList<ReadAloudVoiceItemUi> {
        val narratorBinding = bindings.firstOrNull {
            it.subjectType == BookVoiceBinding.SUBJECT_NARRATOR &&
                    it.subjectId == BookVoiceBinding.SUBJECT_NARRATOR
        }
        // 引擎级条目（speakerId 为空）保存了引擎自己的显示名：HTTP 源导入的音色行只有
        // 类型标签，用它才能在音色列表和引擎分组里显示源名称。
        val engineDisplayNames = voicesList
            .filter { it.speakerId.isBlank() && it.displayName.isNotBlank() }
            .associate { voice -> (voice.engineType to voice.engineId) to voice.displayName }
        return voicesList.filter { it.enabled && it.available && !it.excluded }.map { voice ->
            ReadAloudVoiceItemUi(
                id = voice.id,
                name = voice.displayName.ifBlank { voice.speakerId },
                engineType = voice.engineType,
                engineId = voice.engineId,
                engineName = engineDisplayNames[voice.engineType to voice.engineId]
                    ?: engineTypeLabel(voice.engineType),
                isSelected = isReadAloudVoiceSelected(
                    voice = voice,
                    narratorVoiceId = narratorBinding?.voiceId,
                    currentEngine = currentEngine,
                ),
                isEngineDefault = voice.speakerId.isBlank(),
            )
        }.toImmutableList()
    }

    private fun engineTypeLabel(engineType: String): String = when (engineType) {
        ReadAloudVoice.ENGINE_SYSTEM -> application.getString(R.string.system_tts)
        ReadAloudVoice.ENGINE_HTTP -> "HTTP TTS"
        ReadAloudVoice.ENGINE_CLOUD -> "云端 TTS"
        else -> engineType
    }

    private companion object {
        val EVENT_KEYS = listOf(
            EventBus.UPDATE_READ_ACTION_BAR,
            EventBus.SOURCE_CHANGED,
            EventBus.ALOUD_STATE,
            EventBus.TTS_PROGRESS,
        )
    }

    private data class BookState(
        val bookUrl: String,
        val bookName: String,
        val author: String,
        val coverPath: String?,
        val sourceOrigin: String?,
        val chapterIndex: Int,
        val chapterTitle: String,
        val chapterText: String,
        val textLines: ImmutableList<ReadAloudTextLineUi>,
    )

    /** 影响章节切分结果的输入；任一项变化都需要重建 [ChapterSnapshot]。 */
    private data class ChapterSnapshotKey(
        val chapterIndex: Int,
        val title: String,
        val semanticContent: String,
        val pageStarts: List<Int>,
        val contentSplitMode: ReadAloudContentSplitMode,
    )

    /** 听书页展示需要的章节派生结果。 */
    private data class ChapterSnapshot(
        val chapterIndex: Int,
        val title: String,
        val textLines: ImmutableList<ReadAloudTextLineUi>,
    )
}

data class ReadAloudChapterSourceState(
    val index: Int,
    val title: String,
    val isVolume: Boolean,
    val tocLevel: Int,
)

data class ReadAloudPlayerSourceState(
    val bookUrl: String,
    val bookName: String,
    val author: String,
    val coverPath: String?,
    val sourceOrigin: String?,
    val chapterIndex: Int,
    val chapterTitle: String,
    val chapters: ImmutableList<ReadAloudChapterSourceState>,
    val chapterText: String,
    val textLines: ImmutableList<ReadAloudTextLineUi>,
    val chapterPosition: Int,
    val chapterLength: Int,
    val playbackText: String,
    val engineName: String,
    val speakerName: String,
    val isPaused: Boolean,
    val speed: Int,
    val timerMinutes: Int,
    val timerMode: String,
    val timerChapters: Int,
    val finishCurrentChapterAfterTimer: Boolean,
    val voices: ImmutableList<ReadAloudVoiceItemUi> = persistentListOf(),
)

/** 语速调节范围，与经典朗读控制（ReadAloudScreen 的 valueRange = 0f..80f）保持一致。 */
internal const val READ_ALOUD_SPEED_MIN = 0
internal const val READ_ALOUD_SPEED_MAX = 80

internal fun coerceReadAloudSpeed(value: Int): Int =
    value.coerceIn(READ_ALOUD_SPEED_MIN, READ_ALOUD_SPEED_MAX)

/** 语速显示文本，如 5 -> "1.0"、10 -> "1.5"、15 -> "2.0"，与 TTS 实际播放倍率 (speed + 5) / 10 一致。 */
internal fun formatReadAloudSpeedLabel(speed: Int): String {
    val display = (speed + 5) / 10f
    return if (display == display.roundToInt().toFloat()) {
        "${display.roundToInt()}.0"
    } else {
        String.format(java.util.Locale.ROOT, "%.1f", display)
    }
}

/**
 * 一条音色是否代表当前选择。
 *
 * - [narratorVoiceId] 非空时以人物绑定为准，这是唯一能精确到 speaker 的信息。
 * - HTTP TTS 的 `ttsEngine` 只保存引擎 id，无法区分引擎内的具体 speaker。没有绑定时
 *   只让引擎级条目命中，否则同引擎下的所有音色会一起显示为已选。
 * - 云端音色按 speaker 精确匹配；引擎级条目（speaker 为空）不再连带命中全部音色。
 */
internal fun isReadAloudVoiceSelected(
    voice: ReadAloudVoice,
    narratorVoiceId: String?,
    currentEngine: String?,
): Boolean = when {
    narratorVoiceId != null -> narratorVoiceId == voice.id
    voice.engineType == ReadAloudVoice.ENGINE_HTTP ->
        voice.speakerId.isBlank() && currentEngine == voice.engineId

    voice.engineType == ReadAloudVoice.ENGINE_CLOUD ->
        GSON.fromJsonObject<ReadAloudEngineSelection>(currentEngine).getOrNull()?.let {
            it.engineType == voice.engineType && it.engineId == voice.engineId &&
                    it.speakerId == voice.speakerId
        } == true

    voice.engineType == ReadAloudVoice.ENGINE_SYSTEM ->
        if (voice.engineId.isBlank()) currentEngine == null
        else GSON.fromJsonObject<SelectItem<String>>(currentEngine).getOrNull()?.value ==
                voice.engineId

    else -> false
}
