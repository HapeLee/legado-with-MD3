package io.legado.app.ui.book.readaloud.player

import android.app.Application
import androidx.lifecycle.Observer
import com.jeremyliao.liveeventbus.LiveEventBus
import io.legado.app.constant.EventBus
import io.legado.app.data.repository.BookRepository
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.model.PlaybackTimer
import io.legado.app.domain.model.readaloud.ReadAloudSessionStatus
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadAloudSessionStore
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.read.ReadConfigUpdateBus
import io.legado.app.ui.config.readConfig.ReadConfig
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlin.math.roundToInt

/** Compatibility boundary between the Compose player and the legacy reader/service state. */
class ReadAloudPlayerCoordinator(
    private val application: Application,
    private val sessionStore: ReadAloudSessionStore,
    private val readAloudSettingsGateway: ReadAloudSettingsGateway,
    private val bookRepository: BookRepository,
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
    private val bookState =
        merge(bookChanges, refreshRequests, configChanges).map { snapshotBook() }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val bookWithChapters = bookState.flatMapLatest { book ->
        if (book.bookUrl.isBlank()) {
            flowOf(book to persistentListOf<ReadAloudChapterSourceState>())
        } else {
            bookRepository.flowChapters(book.bookUrl).map { chapters ->
                val sourceChapters = chapters.map { chapter ->
                    ReadAloudChapterSourceState(
                        index = chapter.index,
                        title = chapter.title,
                        isVolume = chapter.isVolume,
                        tocLevel = chapter.tocLevel,
                    )
                }.toImmutableList()
                book to sourceChapters
            }
        }
    }

    val state: Flow<ReadAloudPlayerSourceState> = combine(
        sessionStore.state,
        bookWithChapters,
        readAloudSettingsGateway.settings,
    ) { session, (book, chapters), settings ->
        val playback = session.playback
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
            speed = ReadConfig.ttsSpeechRate,
            timerMinutes = session.timerMinutes,
            finishCurrentChapterAfterTimer = settings.finishCurrentChapterAfterTimer,
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
            speed = ReadConfig.ttsSpeechRate,
            timerMinutes = session.timerMinutes,
            finishCurrentChapterAfterTimer =
                readAloudSettingsGateway.currentSettings.finishCurrentChapterAfterTimer,
        )
    }

    fun refresh() {
        refreshRequests.tryEmit(Unit)
    }

    private fun snapshotBook(): BookState {
        val book = ReadBook.book
        val chapter = ReadBook.curTextChapter
        return BookState(
            bookUrl = book?.bookUrl.orEmpty(),
            bookName = book?.name.orEmpty(),
            author = book?.author.orEmpty(),
            coverPath = book?.getDisplayCover(),
            sourceOrigin = book?.origin,
            chapterIndex = chapter?.position ?: -1,
            chapterTitle = chapter?.title.orEmpty(),
            chapterText = chapter?.getContent().orEmpty(),
            textLines = chapter?.paragraphs.orEmpty().mapNotNull { paragraph ->
                paragraph.text.replace(Regex("[袮祢꧁]"), " ").trim()
                    .takeIf(String::isNotEmpty)?.let {
                    ReadAloudTextLineUi(it, paragraph.chapterPosition)
                }
            }.toImmutableList(),
        )
    }

    fun togglePause() {
        when {
            !BaseReadAloudService.isRun -> ReadBook.readAloud()
            BaseReadAloudService.pause -> ReadAloud.resume(application)
            else -> ReadAloud.pause(application)
        }
    }

    fun previousParagraph() = ReadAloud.prevParagraph(application)
    fun nextParagraph() = ReadAloud.nextParagraph(application)
    fun previousChapter() = ReadBook.moveToPrevChapter(true, false)
    fun nextChapter() = ReadBook.moveToNextChapter(true)
    fun selectChapter(index: Int) = ReadBook.openChapter(index, durChapterPos = 0)

    suspend fun setSpeed(value: Int) {
        readAloudSettingsGateway.update { it.copy(ttsSpeechRate = coerceReadAloudSpeed(value)) }
        ReadAloud.upTtsSpeechRate(application)
    }

    suspend fun setTimer(minutes: Int) {
        val timer = PlaybackTimer.normalize(minutes)
        readAloudSettingsGateway.update { it.copy(ttsTimer = timer) }
        ReadAloud.setTimer(application, timer)
    }

    suspend fun setFinishCurrentChapterAfterTimer(value: Boolean) {
        readAloudSettingsGateway.update { it.copy(finishCurrentChapterAfterTimer = value) }
    }

    fun seekTo(chapterPosition: Int, chapterLength: Int) {
        val chapter = ReadBook.curTextChapter ?: return
        val position = chapterPosition.coerceIn(0, chapterLength)
        val pageIndex = chapter.getPageIndexByCharIndex(position)
        if (pageIndex < 0) return
        val startPos = position - chapter.getReadLength(pageIndex)
        ReadAloud.play(application, play = true, pageIndex = pageIndex, startPos = startPos)
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
    val finishCurrentChapterAfterTimer: Boolean,
)

/** 语速调节范围，与经典朗读控制（ReadAloudScreen 的 valueRange = 0f..80f）保持一致。 */
internal const val READ_ALOUD_SPEED_MIN = 0
internal const val READ_ALOUD_SPEED_MAX = 80

internal fun coerceReadAloudSpeed(value: Int): Int =
    value.coerceIn(READ_ALOUD_SPEED_MIN, READ_ALOUD_SPEED_MAX)

/** 语速显示文本，如 20 -> "2.0"、15 -> "1.5"，与播放器 valueLabel 格式化一致。 */
internal fun formatReadAloudSpeedLabel(speed: Int): String {
    val display = speed / 10f
    return if (display == display.roundToInt().toFloat()) {
        "${display.roundToInt()}.0"
    } else {
        String.format("%.1f", display)
    }
}
