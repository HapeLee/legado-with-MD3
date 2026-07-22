package io.legado.app.ui.book.readaloud.player

import android.app.Application
import androidx.lifecycle.Observer
import com.jeremyliao.liveeventbus.LiveEventBus
import io.legado.app.constant.EventBus
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.model.readaloud.ReadAloudSessionStatus
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadAloudSessionStore
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.config.readConfig.ReadConfig
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/** Compatibility boundary between the Compose player and the legacy reader/service state. */
class ReadAloudPlayerCoordinator(
    private val application: Application,
    private val sessionStore: ReadAloudSessionStore,
    private val readAloudSettingsGateway: ReadAloudSettingsGateway,
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
    private val bookState = merge(bookChanges, refreshRequests).map { snapshotBook() }

    val state: Flow<ReadAloudPlayerSourceState> = combine(
        sessionStore.state,
        bookState,
    ) { session, book ->
        val playback = session.playback
        ReadAloudPlayerSourceState(
            bookUrl = book.bookUrl,
            bookName = book.bookName,
            author = book.author,
            coverPath = book.coverPath,
            sourceOrigin = book.sourceOrigin,
            chapterIndex = book.chapterIndex,
            chapterTitle = book.chapterTitle,
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
                paragraph.text.replace(Regex("[袮꧁]"), " ").trim()
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

    suspend fun setSpeed(value: Int) {
        readAloudSettingsGateway.update { it.copy(ttsSpeechRate = value.coerceIn(0, 80)) }
        ReadAloud.upTtsSpeechRate(application)
    }

    fun setTimer(minutes: Int) = ReadAloud.setTimer(application, minutes)

    fun seekTo(chapterPosition: Int, chapterLength: Int) {
        val chapter = ReadBook.curTextChapter ?: return
        val position = chapterPosition.coerceIn(0, chapterLength)
        val pageIndex = chapter.getPageIndexByCharIndex(position)
        val startPos = position - chapter.getReadLength(pageIndex)
        ReadAloud.play(application, play = true, pageIndex = pageIndex, startPos = startPos)
    }

    private companion object {
        val EVENT_KEYS = listOf(
            EventBus.UP_CONFIG,
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

data class ReadAloudPlayerSourceState(
    val bookUrl: String,
    val bookName: String,
    val author: String,
    val coverPath: String?,
    val sourceOrigin: String?,
    val chapterIndex: Int,
    val chapterTitle: String,
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
)
