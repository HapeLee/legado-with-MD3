package io.legado.app.ui.book.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.legado.app.R
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
import kotlinx.coroutines.Job
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

    private val _effects = MutableSharedFlow<VideoPlayerEffect>(
        extraBufferCapacity = 16,
    )
    val effects = _effects.asSharedFlow()

    private val exoPlayerLazy = lazy {
        ExoPlayerHelper.createHttpExoPlayer(appCtx).apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) pausedByLifecycle = false
                    _uiState.update { it.copy(isPlaying = isPlaying) }
                }

                override fun onPlayerError(error: PlaybackException) {
                    saveProgress()
                    _uiState.update { it.copy(error = error.message, isLoading = false) }
                    _effects.tryEmit(
                        VideoPlayerEffect.ShowToast(
                            error.message ?: appCtx.getString(R.string.video_play_failed)
                        )
                    )
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
    private var loadJob: Job? = null

    fun init(bookUrl: String) {
        if (!shouldInitialize(initializedBookUrl, bookUrl)) return
        initializedBookUrl = bookUrl
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val loaded = bookRepository.getBook(bookUrl) ?: run {
                failLoad(appCtx.getString(R.string.book_not_found))
                return@launch
            }
            val source = bookSourceRepository.getBookSource(loaded.origin) ?: run {
                failLoad(appCtx.getString(R.string.source_not_found))
                return@launch
            }
            book = loaded
            bookSourceUrl = loaded.origin
            _uiState.update {
                it.copy(
                    bookName = loaded.name,
                    bookAuthor = loaded.author,
                )
            }
            // AddToBookshelfUseCase 只插书，书架直开视频书时没有详情和目录，需在这里补齐。
            if (loaded.tocUrl.isEmpty()) {
                try {
                    WebBook.getBookInfoAwait(source, loaded)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failLoad(e.message ?: appCtx.getString(R.string.error_get_book_info))
                    return@launch
                }
            }
            var chapterCount = bookRepository.getChapterCount(loaded.bookUrl)
            if (chapterCount == 0) {
                try {
                    val oldBook = loaded.copy()
                    val chapters = WebBook.getChapterListAwait(source, loaded).getOrThrow()
                    if (oldBook.bookUrl == loaded.bookUrl) {
                        bookRepository.update(loaded)
                    } else {
                        bookRepository.replace(oldBook, loaded)
                    }
                    bookRepository.deleteChaptersByBook(loaded.bookUrl)
                    bookRepository.insertChapters(*chapters.toTypedArray())
                    chapterCount = chapters.size
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failLoad(e.message ?: appCtx.getString(R.string.error_load_toc))
                    return@launch
                }
            }
            chapterIndex = if (chapterCount > 0) {
                loaded.durChapterIndex.coerceIn(0, chapterCount - 1)
            } else {
                0
            }
            _uiState.update { it.copy(chapterCount = chapterCount) }
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
            is VideoPlayerIntent.ToggleFullscreen -> {
                _uiState.update { it.copy(isFullscreen = !it.isFullscreen) }
            }
            is VideoPlayerIntent.Retry -> {
                // init 中途失败（详情/目录没拿到、章节计数为 0）时重跑 init；
                // 正文加载失败只重试当前章节。
                val url = initializedBookUrl
                if (url != null && _uiState.value.chapterCount == 0) {
                    initializedBookUrl = null
                    init(url)
                } else {
                    loadChapter(chapterIndex, book?.durChapterPos?.toLong() ?: 0L)
                }
            }
        }
    }

    private fun loadChapter(index: Int, positionMs: Long) {
        val currentBook = book ?: return
        val sourceUrl = bookSourceUrl ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            saveProgress()
            _uiState.update { it.copy(isLoading = true, error = null) }
            val chapter = bookRepository.getChapter(currentBook.bookUrl, index) ?: run {
                failLoad(appCtx.getString(R.string.chapter_not_found))
                return@launch
            }
            val source = bookSourceRepository.getBookSource(sourceUrl) ?: run {
                failLoad(appCtx.getString(R.string.source_not_found))
                return@launch
            }
            try {
                val content = WebBook.getContentAwait(source, currentBook, chapter)
                if (content.isBlank()) {
                    failLoad(appCtx.getString(R.string.video_link_not_found))
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
                failLoad(e.message ?: appCtx.getString(R.string.load_failed))
            }
        }
    }

    private fun failLoad(message: String) {
        _uiState.update { it.copy(isLoading = false, error = message) }
        _effects.tryEmit(VideoPlayerEffect.ShowToast(message))
    }

    private fun saveProgress() {
        val mediaItemCount = if (exoPlayerLazy.isInitialized()) exoPlayer.mediaItemCount else 0
        if (!shouldSaveProgress(mediaItemCount)) return
        val currentBook = book ?: return
        val index = chapterIndex
        if (index < 0) return
        val position = exoPlayer.currentPosition.coerceAtLeast(0L)
        val chapterTitle = _uiState.value.chapterTitle.takeIf { it.isNotBlank() }
        currentBook.durChapterPos = position.toInt()
        videoProgressScope.launch {
            bookRepository.upVideoPlayProgress(
                bookUrl = currentBook.bookUrl,
                index = index,
                position = position.toInt(),
                chapterTitle = chapterTitle,
                time = System.currentTimeMillis(),
            )
        }
    }

    override fun onCleared() {
        saveProgress()
        if (exoPlayerLazy.isInitialized()) exoPlayer.release()
        super.onCleared()
    }
}
