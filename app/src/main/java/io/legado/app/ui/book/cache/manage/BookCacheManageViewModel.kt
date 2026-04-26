package io.legado.app.ui.book.cache.manage

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.domain.usecase.CacheBookChaptersUseCase
import io.legado.app.domain.usecase.ClearBookCacheUseCase
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isNotShelf
import io.legado.app.model.CacheBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BookCacheManageUiState(
    val isLoading: Boolean = true,
    val shelfBooks: List<BookCacheBookItem> = emptyList(),
    val notShelfBooks: List<BookCacheBookItem> = emptyList(),
    val downloadSummary: String = CacheBook.downloadSummary,
    val version: Long = 0,
)

data class BookCacheBookItem(
    val book: Book,
    val chapters: List<BookCacheChapterItem>,
    val cachedCount: Int,
    val waitingCount: Int,
    val downloadingCount: Int,
    val errorCount: Int,
    val isNotShelf: Boolean,
) {
    val totalCount: Int get() = chapters.size
    val progress: Float get() = if (totalCount == 0) 0f else cachedCount.toFloat() / totalCount
    val isDownloading: Boolean get() = waitingCount > 0 || downloadingCount > 0
}

data class BookCacheChapterItem(
    val chapter: BookChapter,
    val isCached: Boolean,
    val isWaiting: Boolean,
    val isDownloading: Boolean,
    val isError: Boolean,
)

sealed interface BookCacheManageIntent {
    data object Initialize : BookCacheManageIntent
    data object Refresh : BookCacheManageIntent
    data object StartAllDownloads : BookCacheManageIntent
    data object StopAllDownloads : BookCacheManageIntent
    data class DeleteBookCache(val bookUrl: String) : BookCacheManageIntent
    data class DownloadChapter(val bookUrl: String, val chapterIndex: Int) : BookCacheManageIntent
    data class DeleteChapterCache(val bookUrl: String, val chapterUrl: String) : BookCacheManageIntent
}

sealed interface BookCacheManageEffect {
    data class ShowMessage(val message: String) : BookCacheManageEffect
}

class BookCacheManageViewModel(
    application: Application,
    private val bookDao: BookDao,
    private val bookChapterDao: BookChapterDao,
    private val cacheBookChaptersUseCase: CacheBookChaptersUseCase,
    private val clearBookCacheUseCase: ClearBookCacheUseCase,
) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(BookCacheManageUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<BookCacheManageEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var observeJob: Job? = null
    private val chapterCache = hashMapOf<String, List<BookChapter>>()

    fun onIntent(intent: BookCacheManageIntent) {
        when (intent) {
            BookCacheManageIntent.Initialize -> initialize()
            BookCacheManageIntent.Refresh -> reload()
            BookCacheManageIntent.StartAllDownloads -> startAllDownloads()
            BookCacheManageIntent.StopAllDownloads -> stopAllDownloads()
            is BookCacheManageIntent.DeleteBookCache -> deleteBookCache(intent.bookUrl)
            is BookCacheManageIntent.DownloadChapter -> downloadChapter(
                intent.bookUrl,
                intent.chapterIndex
            )
            is BookCacheManageIntent.DeleteChapterCache -> deleteChapterCache(
                intent.bookUrl,
                intent.chapterUrl
            )
        }
    }

    private fun initialize() {
        if (observeJob != null) return
        observeJob = viewModelScope.launch {
            bookDao.flowAll().collect {
                reload(it)
            }
        }
        viewModelScope.launch {
            CacheBook.cacheSuccessFlow.collect {
                reload()
            }
        }
        viewModelScope.launch {
            CacheBook.downloadingIndicesFlow.collect {
                reload()
            }
        }
        viewModelScope.launch {
            CacheBook.downloadErrorFlow.collect {
                reload()
            }
        }
        viewModelScope.launch {
            CacheBook.downloadSummaryFlow.collect { summary ->
                _uiState.update {
                    it.copy(downloadSummary = summary, version = it.version + 1)
                }
                reload()
            }
        }
    }

    private fun reload(books: List<Book>? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val sourceBooks = books ?: bookDao.all
            val items = sourceBooks
                .filterNot { it.isLocal || it.isAudio }
                .mapNotNull { book -> buildBookItem(book) }
                .filter { item ->
                    item.cachedCount > 0 || item.isDownloading || item.errorCount > 0
                }
                .sortedWith(compareByDescending<BookCacheBookItem> { it.isDownloading }
                    .thenByDescending { it.cachedCount }
                    .thenBy { it.book.name })
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        shelfBooks = items.filterNot { item -> item.isNotShelf },
                        notShelfBooks = items.filter { item -> item.isNotShelf },
                        downloadSummary = CacheBook.downloadSummary,
                        version = it.version + 1,
                    )
                }
            }
        }
    }

    private fun buildBookItem(book: Book): BookCacheBookItem? {
        val chapters = chapterCache.getOrPut(book.bookUrl) {
            bookChapterDao.getChapterList(book.bookUrl)
        }
        val cacheFiles = BookHelp.getChapterFiles(book)
        val model = CacheBook.cacheBookMap[book.bookUrl]
        val waitingIndices = model?.waitingIndices().orEmpty()
        val downloadingIndices = model?.downloadingIndices().orEmpty()
        val errorIndices = CacheBook.downloadErrorFlow.value
            .takeIf { it.first == book.bookUrl }
            ?.second
            .orEmpty()
        if (chapters.isEmpty() && cacheFiles.isEmpty() && model == null && !book.isNotShelf) {
            return null
        }
        val chapterItems = chapters.map { chapter ->
            BookCacheChapterItem(
                chapter = chapter,
                isCached = cacheFiles.contains(chapter.getFileName()) || chapter.isVolume,
                isWaiting = waitingIndices.contains(chapter.index),
                isDownloading = downloadingIndices.contains(chapter.index),
                isError = errorIndices.contains(chapter.index),
            )
        }
        return BookCacheBookItem(
            book = book,
            chapters = chapterItems,
            cachedCount = chapterItems.count { it.isCached },
            waitingCount = waitingIndices.size,
            downloadingCount = downloadingIndices.size,
            errorCount = errorIndices.size,
            isNotShelf = book.isNotShelf,
        )
    }

    private fun stopAllDownloads() {
        CacheBook.stop(context)
        reload()
    }

    private fun startAllDownloads() {
        val items = uiState.value.shelfBooks + uiState.value.notShelfBooks
        execute {
            items.sumOf { item ->
                val chapterIndices = item.chapters
                    .filterNot { it.isCached || it.isWaiting || it.isDownloading }
                    .map { it.chapter.index }
                cacheBookChaptersUseCase.execute(item.book.bookUrl, chapterIndices)
            }
        }.onSuccess { count ->
            if (count > 0) {
                _effects.tryEmit(BookCacheManageEffect.ShowMessage("已加入缓存队列: $count 章"))
            } else {
                _effects.tryEmit(BookCacheManageEffect.ShowMessage("没有可缓存的章节"))
            }
        }.onError {
            _effects.tryEmit(BookCacheManageEffect.ShowMessage("加入缓存队列失败\n${it.localizedMessage}"))
        }.onFinally {
            reload()
        }
    }

    private fun deleteBookCache(bookUrl: String) {
        CacheBook.remove(context, bookUrl)
        execute {
            clearBookCacheUseCase.execute(bookUrl)
        }.onSuccess {
            _effects.tryEmit(BookCacheManageEffect.ShowMessage("缓存已删除"))
        }.onError {
            _effects.tryEmit(BookCacheManageEffect.ShowMessage("删除缓存失败\n${it.localizedMessage}"))
        }.onFinally {
            reload()
        }
    }

    private fun downloadChapter(bookUrl: String, chapterIndex: Int) {
        execute {
            cacheBookChaptersUseCase.execute(bookUrl, listOf(chapterIndex))
        }.onSuccess {
            _effects.tryEmit(BookCacheManageEffect.ShowMessage("章节已加入缓存队列"))
        }.onError {
            _effects.tryEmit(BookCacheManageEffect.ShowMessage("章节缓存失败\n${it.localizedMessage}"))
        }.onFinally {
            reload()
        }
    }

    private fun deleteChapterCache(bookUrl: String, chapterUrl: String) {
        val book = bookDao.getBook(bookUrl) ?: return
        val chapter = chapterCache[bookUrl]?.firstOrNull { it.url == chapterUrl } ?: return
        execute {
            BookHelp.delContent(book, chapter)
        }.onSuccess {
            _effects.tryEmit(BookCacheManageEffect.ShowMessage("章节缓存已删除"))
        }.onError {
            _effects.tryEmit(BookCacheManageEffect.ShowMessage("删除章节缓存失败\n${it.localizedMessage}"))
        }.onFinally {
            reload()
        }
    }
}
