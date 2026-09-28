package io.legado.app.service.playback

import android.app.Application
import androidx.lifecycle.Observer
import com.jeremyliao.liveeventbus.LiveEventBus
import io.legado.app.constant.EventBus
import io.legado.app.constant.Status
import io.legado.app.domain.gateway.PlaybackCapsuleGateway
import io.legado.app.domain.model.PlaybackCapsuleSource
import io.legado.app.domain.model.PlaybackCapsuleState
import io.legado.app.domain.model.readaloud.ReadAloudSessionStatus
import io.legado.app.domain.model.selectPlaybackCapsule
import io.legado.app.model.AudioPlay
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadAloudSessionStore
import io.legado.app.model.ReadBook
import io.legado.app.service.AudioPlayService
import io.legado.app.service.BaseReadAloudService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** 既有两种播放引擎的 Android 适配器，仅投影胶囊状态，不初始化播放器或加载书籍。 */
class PlaybackCapsuleGatewayAdapter(
    private val application: Application,
    private val sessionStore: ReadAloudSessionStore,
) : PlaybackCapsuleGateway {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val availability =
        MutableStateFlow(BaseReadAloudService.isRun to AudioPlayService.isRun)
    private val preparedAudioBookUrl = MutableStateFlow<String?>(null)

    // 服务建立时记录归属；模型换书后晚到的旧服务销毁不能清掉新书准备会话。
    private var serviceAudioBookUrl = AudioPlay.book?.bookUrl.takeIf { AudioPlayService.isRun }
    private val events = callbackFlow {
        val observer = Observer<Any> { trySend(Unit) }
        val keys = listOf(
            EventBus.AUDIO_STATE, EventBus.AUDIO_PROGRESS, EventBus.AUDIO_SIZE,
            EventBus.AUDIO_SUB_TITLE, EventBus.ALOUD_STATE, EventBus.TTS_PROGRESS
        )
        keys.forEach { LiveEventBus.get<Any>(it).observeForever(observer) }
        trySend(Unit)
        awaitClose { keys.forEach { LiveEventBus.get<Any>(it).removeObserver(observer) } }
    }

    override val state =
        combine(sessionStore.state, availability, preparedAudioBookUrl, events) { _, _, _, _ ->
            readAloudSnapshot() to audioBookSnapshot()
        }.runningFold(PlaybackCapsuleState()) { previous, (readAloud, audioBook) ->
            val selected = selectPlaybackCapsule(readAloud, audioBook, previous.source)
            // source 归空立即触发退场，保留最后一张封面供淡出，避免中途变成占位图。
            if (selected.source == null) previous.copy(source = null, isPaused = true) else selected
        }.stateIn(
            scope, SharingStarted.WhileSubscribed(5000),
            selectPlaybackCapsule(readAloudSnapshot(), audioBookSnapshot(), null)
        )

    override fun setSessionAvailable(source: PlaybackCapsuleSource, available: Boolean) {
        if (source == PlaybackCapsuleSource.AudioBook) {
            if (available) serviceAudioBookUrl = AudioPlay.book?.bookUrl
            else {
                serviceAudioBookUrl?.let(::clearPreparedAudioBook)
                serviceAudioBookUrl = null
            }
        }
        availability.update {
            when (source) {
                PlaybackCapsuleSource.ReadAloud -> available to it.second
                PlaybackCapsuleSource.AudioBook -> it.first to available
            }
        }
    }

    override fun prepareAudioBook(bookUrl: String) {
        preparedAudioBookUrl.value = bookUrl
    }

    override fun clearPreparedAudioBook(bookUrl: String) {
        preparedAudioBookUrl.compareAndSet(bookUrl, null)
    }

    private fun readAloudSnapshot(): PlaybackCapsuleState {
        if (!availability.value.first) return PlaybackCapsuleState()
        val book = ReadBook.book ?: return PlaybackCapsuleState()
        val session = sessionStore.state.value
        return PlaybackCapsuleState(
            PlaybackCapsuleSource.ReadAloud, book.bookUrl, book.name,
            book.author, book.getDisplayCover(), book.origin,
            session.status != ReadAloudSessionStatus.Playing,
            (session.playback.chapterPosition.toFloat() / session.playback.chapterLength.coerceAtLeast(
                1
            )).coerceIn(0f, 1f),
            chapterTitle = session.playback.chapterTitle,
            chapterIndex = session.playback.chapterIndex
        )
    }

    private fun audioBookSnapshot(): PlaybackCapsuleState {
        val book = AudioPlay.book ?: return PlaybackCapsuleState()
        if (!availability.value.second && preparedAudioBookUrl.value != book.bookUrl) return PlaybackCapsuleState()
        return PlaybackCapsuleState(
            PlaybackCapsuleSource.AudioBook,
            book.bookUrl,
            book.name,
            book.author,
            book.getDisplayCover(),
            book.origin,
            !availability.value.second || AudioPlay.status != Status.PLAY,
            (AudioPlay.durChapterPos.toFloat() / AudioPlay.durAudioSize.coerceAtLeast(1)).coerceIn(
                0f,
                1f
            ),
            inBookshelf = AudioPlay.inBookshelf,
            chapterTitle = AudioPlay.durChapter?.takeIf {
                it.bookUrl == book.bookUrl && it.index == AudioPlay.durChapterIndex
            }?.title.orEmpty(),
            chapterIndex = AudioPlay.durChapterIndex
        )
    }

    override fun togglePause(source: PlaybackCapsuleSource) {
        if (!ownsSelectedSession(source)) return
        when (source) {
            PlaybackCapsuleSource.ReadAloud -> if (BaseReadAloudService.pause) ReadAloud.resume(
                application
            ) else ReadAloud.pause(application)

            PlaybackCapsuleSource.AudioBook -> when (if (AudioPlayService.isRun) AudioPlay.status else Status.STOP) {
                Status.PLAY -> AudioPlay.pause(application)
                Status.PAUSE -> AudioPlay.resume(application)
                else -> AudioPlay.loadOrUpPlayUrl()
            }
        }
    }

    override fun stop(source: PlaybackCapsuleSource) {
        if (!ownsSelectedSession(source)) return
        when (source) {
            PlaybackCapsuleSource.ReadAloud -> ReadAloud.stop(application)
            PlaybackCapsuleSource.AudioBook -> {
                AudioPlay.book?.bookUrl?.let(::clearPreparedAudioBook)
                AudioPlay.stop()
            }
        }
    }

    /** 拦截来自旧胶囊的点击，已准备的有声书可以恢复，停止且没有准备会话时不再接受动作。 */
    private fun ownsSelectedSession(source: PlaybackCapsuleSource): Boolean =
        state.value.source == source &&
                when (source) {
                    PlaybackCapsuleSource.ReadAloud -> availability.value.first && BaseReadAloudService.isRun
                    PlaybackCapsuleSource.AudioBook -> (availability.value.second && AudioPlayService.isRun) ||
                            (AudioPlay.book?.bookUrl != null && preparedAudioBookUrl.value == AudioPlay.book?.bookUrl)
                }
}
