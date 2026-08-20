package io.legado.app.ui.book.audio

import android.app.Application
import androidx.lifecycle.Observer
import com.jeremyliao.liveeventbus.LiveEventBus
import io.legado.app.constant.EventBus
import io.legado.app.constant.Status
import io.legado.app.data.repository.BookRepository
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.help.book.getBookSource
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.AudioPlay
import io.legado.app.service.AudioPlayService
import io.legado.app.ui.widget.components.player.PlayerChapterUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * 有声书播放器与旧版 [AudioPlay] 模型 / [AudioPlayService] 之间的兼容边界：
 * 监听 LiveEventBus 播放事件驱动重新快照，对外暴露稳定的状态流与播放动作。
 */
class AudioPlayCoordinator(
    private val application: Application,
    private val bookRepository: BookRepository,
    private val otherSettingsGateway: OtherSettingsGateway,
    private val readAloudSettingsGateway: ReadAloudSettingsGateway,
) {
    private val refreshRequests = MutableSharedFlow<Unit>(replay = 1)
    private val loading = MutableStateFlow(false)

    private val liveEvents = callbackFlow {
        val observer = Observer<Any> { value ->
            // AUDIO_SPEED 事件回写最新倍速（服务不持久化倍速，由模型进程内记忆）
            if (value is Float) AudioPlay.speed = value
            trySend(Unit)
        }
        EVENT_KEYS.forEach { LiveEventBus.get<Any>(it).observeForever(observer) }
        trySend(Unit)
        awaitClose {
            EVENT_KEYS.forEach { LiveEventBus.get<Any>(it).removeObserver(observer) }
        }
    }
    private val bookState = merge(liveEvents, refreshRequests).map { snapshotBook() }

    /**
     * 章节列表：仅当书籍切换（bookUrl 变化）或目录数据变化时才重建，
     * 避免每次播放事件（每秒一次的 AUDIO_PROGRESS 等）都重新订阅 Room 流、
     * 重建列表实例，导致目录页 LaunchedEffect 反复触发滚动。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val chapters: Flow<ImmutableList<PlayerChapterUi>> =
        bookState.map { it.bookUrl }
            .distinctUntilChanged()
            .flatMapLatest { bookUrl ->
                if (bookUrl.isBlank()) {
                    flowOf(persistentListOf())
                } else {
                    bookRepository.flowChapters(bookUrl).map { chapterList ->
                        chapterList.map { chapter ->
                            PlayerChapterUi(
                                index = chapter.index,
                                title = chapter.title,
                                isVolume = chapter.isVolume,
                                tocLevel = chapter.tocLevel,
                            )
                        }.toImmutableList()
                    }
                }
            }

    val state: Flow<AudioPlaySourceState> = combine(
        bookState,
        chapters,
        loading,
        otherSettingsGateway.settings,
        readAloudSettingsGateway.settings,
    ) { book, chapterList, isLoading, other, aloud ->
        AudioPlaySourceState(
            bookUrl = book.bookUrl,
            bookName = book.bookName,
            author = book.author,
            coverPath = book.coverPath,
            sourceOrigin = book.sourceOrigin,
            chapterIndex = book.chapterIndex,
            chapterTitle = book.chapterTitle,
            chapters = chapterList,
            status = book.status,
            isLoading = isLoading,
            position = book.position,
            duration = book.duration,
            speed = AudioPlay.speed,
            timerMinutes = AudioPlayService.timeMinute,
            playMode = AudioPlay.playMode,
            canLogin = book.canLogin,
            wakeLockEnabled = other.audioPlayUseWakeLock,
            mediaControlEnabled = aloud.systemMediaControlCompatibilityChange,
            canPrevious = book.canPrevious,
            canNext = book.canNext,
            openCredits = book.openCredits,
            closeCredits = book.closeCredits,
            audioGain = book.audioGain,
        )
    }

    fun snapshot(): AudioPlaySourceState {
        val book = snapshotBook()
        return AudioPlaySourceState(
            bookUrl = book.bookUrl,
            bookName = book.bookName,
            author = book.author,
            coverPath = book.coverPath,
            sourceOrigin = book.sourceOrigin,
            chapterIndex = book.chapterIndex,
            chapterTitle = book.chapterTitle,
            chapters = persistentListOf(),
            status = book.status,
            isLoading = loading.value,
            position = book.position,
            duration = book.duration,
            speed = AudioPlay.speed,
            timerMinutes = AudioPlayService.timeMinute,
            playMode = AudioPlay.playMode,
            canLogin = book.canLogin,
            wakeLockEnabled = otherSettingsGateway.currentSettings.audioPlayUseWakeLock,
            mediaControlEnabled =
                readAloudSettingsGateway.currentSettings.systemMediaControlCompatibilityChange,
            canPrevious = book.canPrevious,
            canNext = book.canNext,
            openCredits = book.openCredits,
            closeCredits = book.closeCredits,
            audioGain = book.audioGain,
        )
    }

    fun refresh() {
        refreshRequests.tryEmit(Unit)
    }

    fun setLoading(value: Boolean) {
        loading.value = value
    }

    fun togglePlay() {
        when {
            AudioPlay.status == Status.PLAY -> AudioPlay.pause(application)
            AudioPlay.status == Status.PAUSE -> AudioPlay.resume(application)
            else -> AudioPlay.loadOrUpPlayUrl()
        }
    }

    fun stop() = AudioPlay.stop()

    fun previous() = AudioPlay.prev()

    fun next() = AudioPlay.next()

    fun skipTo(index: Int) = AudioPlay.skipTo(index)

    fun seekTo(positionMs: Int) = AudioPlay.adjustProgress(positionMs)

    fun changePlayMode() = AudioPlay.changePlayMode()

    fun setSpeed(value: Float) {
        AudioPlay.speed = value
        AudioPlay.adjustSpeed(value)
    }

    fun setTimer(minutes: Int) = AudioPlay.setTimer(minutes)

    fun setOpenCredits(seconds: Int) {
        AudioPlay.book?.let {
            it.setOpenCredits(seconds.coerceIn(0, 180))
            // save() 内含同步 DAO 调用，放到后台线程执行，避免主线程写库
            Coroutine.async { it.save() }
        }
        refresh()
    }

    fun setCloseCredits(seconds: Int) {
        AudioPlay.book?.let {
            it.setCloseCredits(seconds.coerceIn(0, 180))
            Coroutine.async { it.save() }
        }
        refresh()
    }

    fun setAudioGain(gainMb: Int) {
        AudioPlay.adjustGain(gainMb)
        refresh()
    }

    fun refreshBookSource() {
        AudioPlay.book?.let { AudioPlay.bookSource = it.getBookSource() }
        refresh()
    }

    private fun snapshotBook(): AudioPlayBookState {
        val book = AudioPlay.book
        val chapter = AudioPlay.durChapter
        return AudioPlayBookState(
            bookUrl = book?.bookUrl.orEmpty(),
            bookName = book?.name.orEmpty(),
            author = book?.author.orEmpty(),
            coverPath = book?.getDisplayCover(),
            sourceOrigin = book?.origin,
            chapterIndex = AudioPlay.durChapterIndex,
            chapterTitle = chapter?.title.orEmpty(),
            status = AudioPlay.status,
            position = AudioPlay.durChapterPos,
            duration = AudioPlay.durAudioSize,
            canLogin = !AudioPlay.bookSource?.loginUrl.isNullOrBlank(),
            canPrevious = AudioPlay.durChapterIndex > 0,
            canNext = AudioPlay.durChapterIndex < AudioPlay.simulatedChapterSize - 1,
            openCredits = book?.getOpenCredits() ?: 0,
            closeCredits = book?.getCloseCredits() ?: 0,
            audioGain = book?.getAudioGain() ?: 0,
        )
    }

    private companion object {
        val EVENT_KEYS = listOf(
            EventBus.AUDIO_STATE,
            EventBus.AUDIO_SUB_TITLE,
            EventBus.AUDIO_SIZE,
            EventBus.AUDIO_PROGRESS,
            EventBus.AUDIO_SPEED,
            EventBus.AUDIO_DS,
            EventBus.PLAY_MODE_CHANGED,
        )
    }
}

data class AudioPlaySourceState(
    val bookUrl: String,
    val bookName: String,
    val author: String,
    val coverPath: String?,
    val sourceOrigin: String?,
    val chapterIndex: Int,
    val chapterTitle: String,
    val chapters: ImmutableList<PlayerChapterUi>,
    val status: Int,
    val isLoading: Boolean,
    val position: Int,
    val duration: Int,
    val speed: Float,
    val timerMinutes: Int,
    val playMode: AudioPlay.PlayMode,
    val canLogin: Boolean,
    val wakeLockEnabled: Boolean,
    val mediaControlEnabled: Boolean,
    val canPrevious: Boolean,
    val canNext: Boolean,
    val openCredits: Int,
    val closeCredits: Int,
    val audioGain: Int,
)

private data class AudioPlayBookState(
    val bookUrl: String,
    val bookName: String,
    val author: String,
    val coverPath: String?,
    val sourceOrigin: String?,
    val chapterIndex: Int,
    val chapterTitle: String,
    val status: Int,
    val position: Int,
    val duration: Int,
    val canLogin: Boolean,
    val canPrevious: Boolean,
    val canNext: Boolean,
    val openCredits: Int,
    val closeCredits: Int,
    val audioGain: Int,
)
