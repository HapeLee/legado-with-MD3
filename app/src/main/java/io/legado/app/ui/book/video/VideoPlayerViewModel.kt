package io.legado.app.ui.book.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.legado.app.data.entities.Book
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.BookSourceRepository
import io.legado.app.help.exoplayer.ExoPlayerHelper
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.analyzeRule.AnalyzeUrl.Companion.getMediaItem
import io.legado.app.model.webBook.WebBook
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import splitties.init.appCtx

internal fun clampSeekPosition(positionMs: Long, durationMs: Long): Long {
    return if (durationMs > 0) positionMs.coerceIn(0L, durationMs) else positionMs.coerceAtLeast(0L)
}

internal fun previousChapterIndex(index: Int): Int = (index - 1).coerceAtLeast(0)

internal fun nextChapterIndex(index: Int, chapterCount: Int): Int {
    return if (chapterCount <= 0) index else (index + 1).coerceAtMost(chapterCount - 1)
}

internal fun shouldSaveProgress(mediaItemCount: Int): Boolean = mediaItemCount > 0

internal fun shouldInitialize(loadedBookUrl: String?, requestedBookUrl: String): Boolean =
    loadedBookUrl != requestedBookUrl

/** 进程级作用域：ViewModel 清除后仍需落库，不能使用 viewModelScope。 */
private val videoProgressScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class VideoPlayerViewModel(
    private val bookRepository: BookRepository,
    private val bookSourceRepository: BookSourceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<VideoPlayerEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private val exoPlayerLazy = lazy {
        ExoPlayerHelper.createHttpExoPlayer(appCtx).apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) pausedByLifecycle = false
                    _uiState.update { it.copy(isPlaying = isPlaying) }
                }

                override fun onPlayerError(error: PlaybackException) {
                    _uiState.update { it.copy(error = error.message, isLoading = false) }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    _uiState.update {
                        it.copy(
                            isLoading = playbackState == Player.STATE_BUFFERING,
                            duration = duration.coerceAtLeast(0L),
                            bufferedPosition = bufferedPosition.coerceAtLeast(0L),
                        )
                    }
                }
            })
        }
    }

    val exoPlayer: ExoPlayer by exoPlayerLazy

    private var book: Book? = null
    private var bookSourceUrl: String? = null
    private var chapterIndex: Int = 0
    private var initializedBookUrl: String? = null
    private var pausedByLifecycle = false

    fun init(bookUrl: String) {
        if (!shouldInitialize(initializedBookUrl, bookUrl)) return
        initializedBookUrl = bookUrl
        viewModelScope.launch {
            val loaded = bookRepository.getBook(bookUrl) ?: run {
                _effects.tryEmit(VideoPlayerEffect.ShowToast("书籍不存在"))
                return@launch
            }
            book = loaded
            bookSourceUrl = loaded.origin
            chapterIndex = loaded.durChapterIndex
            _uiState.update {
                it.copy(
                    bookName = loaded.name,
                    bookAuthor = loaded.author,
                    chapterCount = bookRepository.getChapterCount(bookUrl),
                )
            }
            loadChapter(chapterIndex, loaded.durChapterPos.toLong())
        }
    }

    fun onIntent(intent: VideoPlayerIntent) {
        when (intent) {
            is VideoPlayerIntent.PlayPause -> {
                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
            }
            is VideoPlayerIntent.Pause -> {
                pausedByLifecycle = true
                exoPlayer.pause()
            }
            is VideoPlayerIntent.SeekTo -> {
                exoPlayer.seekTo(clampSeekPosition(intent.positionMs, exoPlayer.duration))
            }
            is VideoPlayerIntent.PreviousChapter -> {
                val target = previousChapterIndex(chapterIndex)
                if (target != chapterIndex) loadChapter(target, 0L)
            }
            is VideoPlayerIntent.NextChapter -> {
                val target = nextChapterIndex(chapterIndex, _uiState.value.chapterCount)
                if (target != chapterIndex) loadChapter(target, 0L)
            }
            is VideoPlayerIntent.Retry -> loadChapter(chapterIndex, 0L)
        }
    }

    private fun loadChapter(index: Int, positionMs: Long) {
        val currentBook = book ?: return
        val sourceUrl = bookSourceUrl ?: return
        viewModelScope.launch {
            saveProgress()
            _uiState.update { it.copy(isLoading = true, error = null) }
            val chapter = bookRepository.getChapter(currentBook.bookUrl, index) ?: run {
                _uiState.update { it.copy(isLoading = false) }
                _effects.tryEmit(VideoPlayerEffect.ShowToast("章节不存在"))
                return@launch
            }
            val source = bookSourceRepository.getBookSource(sourceUrl) ?: run {
                _uiState.update { it.copy(isLoading = false) }
                _effects.tryEmit(VideoPlayerEffect.ShowToast("书源不存在"))
                return@launch
            }
            try {
                val content = WebBook.getContentAwait(source, currentBook, chapter)
                if (content.isBlank()) {
                    _uiState.update { it.copy(isLoading = false) }
                    _effects.tryEmit(VideoPlayerEffect.ShowToast("未获取到视频链接"))
                } else {
                    chapterIndex = index
                    _uiState.update { it.copy(chapterTitle = chapter.title, chapterIndex = index) }
                    val mediaItem: MediaItem = AnalyzeUrl(
                        content,
                        source = source,
                        ruleData = currentBook,
                        chapter = chapter,
                        coroutineContext = kotlin.coroutines.EmptyCoroutineContext,
                    ).getMediaItem()
                    exoPlayer.setMediaItem(mediaItem, positionMs)
                    exoPlayer.prepare()
                    if (!pausedByLifecycle) exoPlayer.play()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
                _effects.tryEmit(VideoPlayerEffect.ShowToast(e.message ?: "加载失败"))
            }
        }
    }

    private fun saveProgress() {
        val mediaItemCount = if (exoPlayerLazy.isInitialized()) exoPlayer.mediaItemCount else 0
        if (!shouldSaveProgress(mediaItemCount)) return
        val currentBook = book ?: return
        val index = chapterIndex
        if (index < 0) return
        val position = exoPlayer.currentPosition.coerceAtLeast(0L)
        videoProgressScope.launch {
            bookRepository.upVideoPlayProgress(
                bookUrl = currentBook.bookUrl,
                index = index,
                position = position.toInt(),
                time = System.currentTimeMillis(),
            )
        }
    }

    override fun onCleared() {
        saveProgress()
        exoPlayer.release()
        super.onCleared()
    }
}
