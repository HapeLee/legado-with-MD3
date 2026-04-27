package io.legado.app.ui.book.cache.manage

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.model.BookChapterCacheInfo
import io.legado.app.domain.usecase.CacheBookChaptersUseCase
import io.legado.app.domain.usecase.ClearBookCacheUseCase
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isNotShelf
import io.legado.app.model.CacheBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

data class BookCacheManageUiState(
    val isLoading: Boolean = true,
    val shelfBooks: List<BookCacheBookItem> = emptyList(),
    val notShelfBooks: List<BookCacheBookItem> = emptyList(),
    val expandedBookUrls: Set<String> = emptySet(),
    val chaptersByBookUrl: Map<String, List<BookCacheChapterItem>> = emptyMap(),
    val downloadSummary: String = "",
    val version: Long = 0,
)

data class BookCacheBookItem(
    val bookUrl: String,
    val name: String,
    val author: String,
    val totalCount: Int,
    val cachedCount: Int,
    val waitingCount: Int,
    val downloadingCount: Int,
    val errorCount: Int,
    val isNotShelf: Boolean,
) {
    val progress: Float get() = if (totalCount == 0) 0f else cachedCount.toFloat() / totalCount
    val isDownloading: Boolean get() = waitingCount > 0 || downloadingCount > 0
}

data class BookCacheChapterItem(
    val chapterUrl: String,
    val title: String,
    val index: Int,
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
    data class StartBookDownload(val bookUrl: String) : BookCacheManageIntent
    data class StopBookDownload(val bookUrl: String) : BookCacheManageIntent
    data class ToggleBookExpanded(val bookUrl: String) : BookCacheManageIntent
    data class DeleteBookCache(val bookUrl: String) : BookCacheManageIntent
    data class DownloadChapter(val bookUrl: String, val chapterIndex: Int) : BookCacheManageIntent
    data class StopChapterDownload(val bookUrl: String, val chapterIndex: Int) : BookCacheManageIntent
    data class DeleteChapterCache(
        val bookUrl: String,
        val chapterUrl: String,
        val chapterTitle: String,
        val chapterIndex: Int,
    ) : BookCacheManageIntent
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
    private var fullReloadJob: Job? = null
    private val bookReloadJobs = hashMapOf<String, Job>()

    fun onIntent(intent: BookCacheManageIntent) {
        when (intent) {
            BookCacheManageIntent.Initialize -> initialize()
            BookCacheManageIntent.Refresh -> reloadAll(forceDatabase = true)
            BookCacheManageIntent.StartAllDownloads -> startAllDownloads()
            BookCacheManageIntent.StopAllDownloads -> stopAllDownloads()
            is BookCacheManageIntent.StartBookDownload -> startBookDownload(intent.bookUrl)
            is BookCacheManageIntent.StopBookDownload -> stopBookDownload(intent.bookUrl)
            is BookCacheManageIntent.ToggleBookExpanded -> toggleBookExpanded(intent.bookUrl)
            is BookCacheManageIntent.DeleteBookCache -> deleteBookCache(intent.bookUrl)
            is BookCacheManageIntent.DownloadChapter -> downloadChapter(
                intent.bookUrl,
                intent.chapterIndex
            )
            is BookCacheManageIntent.StopChapterDownload -> stopChapterDownload(
                intent.bookUrl,
                intent.chapterIndex,
            )
            is BookCacheManageIntent.DeleteChapterCache -> deleteChapterCache(
                intent.bookUrl,
                intent.chapterUrl,
                intent.chapterTitle,
                intent.chapterIndex,
            )
        }
    }

    private fun initialize() {
        if (observeJob != null) return
        observeJob = viewModelScope.launch {
            bookDao.flowAll().collect { books ->
                reloadAll(books = books, forceDatabase = false)
            }
        }
        viewModelScope.launch {
            CacheBook.cacheSuccessFlow.collect { chapter ->
                scheduleBookReload(chapter.bookUrl)
            }
        }
        viewModelScope.launch {
            CacheBook.downloadingIndicesFlow.collect { state ->
                scheduleBookReload(state.first)
            }
        }
        viewModelScope.launch {
            CacheBook.queueChangedFlow.collect { bookUrl ->
                scheduleBookReload(bookUrl)
            }
        }
        viewModelScope.launch {
            CacheBook.downloadErrorFlow.collect { state ->
                scheduleBookReload(state.first)
            }
        }
        viewModelScope.launch {
            CacheBook.downloadSummaryFlow.collect {
                _uiState.update {
                    it.copy(
                        downloadSummary = buildDownloadSummary(it.shelfBooks + it.notShelfBooks),
                        version = it.version + 1,
                    )
                }
            }
        }
    }

    private fun reloadAll(
        books: List<Book>? = null,
        forceDatabase: Boolean = false,
    ) {
        fullReloadJob?.cancel()
        bookReloadJobs.values.forEach { it.cancel() }
        bookReloadJobs.clear()
        fullReloadJob = viewModelScope.launch {
            val expandedBookUrls = uiState.value.expandedBookUrls
            val result = withContext(Dispatchers.IO) {
                val sourceBooks = if (forceDatabase) bookDao.all else books ?: bookDao.all
                val items = sortItems(
                    sourceBooks
                        .filterNot { it.isLocal || it.isAudio }
                        .mapNotNull { book -> buildBookItem(book) }
                        .filter { item ->
                            item.cachedCount > 0 || item.isDownloading || item.errorCount > 0
                        }
                )
                val booksByUrl = items.associateBy { it.bookUrl }
                val retainedExpandedBookUrls = expandedBookUrls.filterTo(linkedSetOf()) {
                    booksByUrl.containsKey(it)
                }
                val chaptersByBookUrl = retainedExpandedBookUrls.associateWith { bookUrl ->
                    buildChapterItems(bookUrl)
                }
                LoadedCacheState(
                    items = items,
                    expandedBookUrls = retainedExpandedBookUrls,
                    chaptersByBookUrl = chaptersByBookUrl,
                )
            }
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    shelfBooks = result.items.filterNot { item -> item.isNotShelf },
                    notShelfBooks = result.items.filter { item -> item.isNotShelf },
                    expandedBookUrls = result.expandedBookUrls,
                    chaptersByBookUrl = result.chaptersByBookUrl,
                    downloadSummary = buildDownloadSummary(result.items),
                    version = state.version + 1,
                )
            }
        }
    }

    private fun scheduleBookReload(bookUrl: String, debounceMillis: Long = 80) {
        if (bookUrl.isBlank()) return
        bookReloadJobs.remove(bookUrl)?.cancel()
        bookReloadJobs[bookUrl] = viewModelScope.launch {
            if (debounceMillis > 0) {
                delay(debounceMillis)
            }
            reloadBook(bookUrl)
            if (bookReloadJobs[bookUrl] == currentCoroutineContext()[Job]) {
                bookReloadJobs.remove(bookUrl)
            }
        }
    }

    private suspend fun reloadBook(bookUrl: String) {
        val expanded = uiState.value.expandedBookUrls.contains(bookUrl)
        val result = withContext(Dispatchers.IO) {
            val book = bookDao.getBook(bookUrl)
            val item = book
                ?.takeUnless { it.isLocal || it.isAudio }
                ?.let { buildBookItem(it) }
                ?.takeIf {
                    it.cachedCount > 0 || it.isDownloading || it.errorCount > 0
                }
            val chapters = if (expanded && item != null) {
                buildChapterItems(item.bookUrl)
            } else {
                null
            }
            LoadedBookState(item, chapters)
        }
        _uiState.update { state ->
            val combinedBooks = (state.shelfBooks + state.notShelfBooks)
                .filterNot { it.bookUrl == bookUrl }
                .let { items ->
                    result.item?.let { items + it } ?: items
                }
            val sortedBooks = sortItems(combinedBooks)
            val expandedBookUrls = if (result.item == null) {
                state.expandedBookUrls - bookUrl
            } else {
                state.expandedBookUrls
            }
            val chaptersByBookUrl = when {
                result.item == null -> state.chaptersByBookUrl - bookUrl
                result.chapters != null -> state.chaptersByBookUrl + (bookUrl to result.chapters)
                else -> state.chaptersByBookUrl
            }
            state.copy(
                shelfBooks = sortedBooks.filterNot { item -> item.isNotShelf },
                notShelfBooks = sortedBooks.filter { item -> item.isNotShelf },
                expandedBookUrls = expandedBookUrls,
                chaptersByBookUrl = chaptersByBookUrl,
                downloadSummary = buildDownloadSummary(sortedBooks),
                version = state.version + 1,
            )
        }
    }

    private fun buildBookItem(book: Book): BookCacheBookItem? {
        val cacheFiles = BookHelp.getChapterFiles(book)
        val model = CacheBook.cacheBookMap[book.bookUrl]
        val waitingIndices = model?.waitingIndices().orEmpty()
        val downloadingIndices = model?.downloadingIndices().orEmpty()
        val errorIndices = errorIndices(book.bookUrl)
        val totalCount = bookChapterDao.getChapterCount(book.bookUrl)
        val cachedFileCount = cacheFiles.count { it.endsWith(".nb") }
        val cachedCount = min(cachedFileCount + bookChapterDao.getVolumeCount(book.bookUrl), totalCount)
        if (totalCount == 0 && cacheFiles.isEmpty() && model == null && !book.isNotShelf) {
            return null
        }
        return BookCacheBookItem(
            bookUrl = book.bookUrl,
            name = book.name,
            author = book.getRealAuthor(),
            totalCount = totalCount,
            cachedCount = cachedCount,
            waitingCount = waitingIndices.size,
            downloadingCount = downloadingIndices.size,
            errorCount = errorIndices.size,
            isNotShelf = book.isNotShelf,
        )
    }

    private fun buildChapterItems(bookUrl: String): List<BookCacheChapterItem> {
        val book = bookDao.getBook(bookUrl) ?: return emptyList()
        val chapters = bookChapterDao.getChapterCacheInfoList(bookUrl)
        val cacheFiles = BookHelp.getChapterFiles(book)
        val model = CacheBook.cacheBookMap[bookUrl]
        val waitingIndices = model?.waitingIndices().orEmpty()
        val downloadingIndices = model?.downloadingIndices().orEmpty()
        val errorIndices = errorIndices(bookUrl)
        return chapters.map { chapter ->
            BookCacheChapterItem(
                chapterUrl = chapter.url,
                title = chapter.title,
                index = chapter.index,
                isCached = cacheFiles.contains(chapter.getFileName()) || chapter.isVolume,
                isWaiting = waitingIndices.contains(chapter.index),
                isDownloading = downloadingIndices.contains(chapter.index),
                isError = errorIndices.contains(chapter.index),
            )
        }
    }

    private fun errorIndices(bookUrl: String): Set<Int> {
        return CacheBook.errorIndices(bookUrl)
    }

    private fun toggleBookExpanded(bookUrl: String) {
        val shouldExpand = !_uiState.value.expandedBookUrls.contains(bookUrl)
        _uiState.update { state ->
            if (shouldExpand) {
                state.copy(
                    expandedBookUrls = state.expandedBookUrls + bookUrl,
                    version = state.version + 1,
                )
            } else {
                state.copy(
                    expandedBookUrls = state.expandedBookUrls - bookUrl,
                    chaptersByBookUrl = state.chaptersByBookUrl - bookUrl,
                    version = state.version + 1,
                )
            }
        }
        if (shouldExpand) {
            loadBookChapters(bookUrl)
        }
    }

    private fun loadBookChapters(bookUrl: String) {
        viewModelScope.launch {
            val chapters = withContext(Dispatchers.IO) {
                buildChapterItems(bookUrl)
            }
            _uiState.update { state ->
                if (!state.expandedBookUrls.contains(bookUrl)) {
                    state
                } else {
                    state.copy(
                        chaptersByBookUrl = state.chaptersByBookUrl + (bookUrl to chapters),
                        version = state.version + 1,
                    )
                }
            }
        }
    }

    private fun stopAllDownloads() {
        CacheBook.stop(context)
        reloadAll(forceDatabase = true)
    }

    private fun stopBookDownload(bookUrl: String) {
        CacheBook.removeBook(bookUrl)
        scheduleBookReload(bookUrl, debounceMillis = 0)
    }

    private fun startAllDownloads() {
        val items = uiState.value.shelfBooks + uiState.value.notShelfBooks
        execute {
            items.sumOf { item ->
                val chapterIndices = downloadableChapterIndices(item.bookUrl)
                cacheBookChaptersUseCase.execute(item.bookUrl, chapterIndices)
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
            reloadAll(forceDatabase = true)
        }
    }

    private fun startBookDownload(bookUrl: String) {
        execute {
            cacheBookChaptersUseCase.execute(bookUrl, downloadableChapterIndices(bookUrl))
        }.onSuccess { count ->
            if (count > 0) {
                _effects.tryEmit(BookCacheManageEffect.ShowMessage("已加入缓存队列: $count 章"))
            } else {
                _effects.tryEmit(BookCacheManageEffect.ShowMessage("没有可缓存的章节"))
            }
        }.onError {
            _effects.tryEmit(BookCacheManageEffect.ShowMessage("加入缓存队列失败\n${it.localizedMessage}"))
        }.onFinally {
            scheduleBookReload(bookUrl, debounceMillis = 0)
        }
    }

    private fun downloadableChapterIndices(bookUrl: String): List<Int> {
        val book = bookDao.getBook(bookUrl) ?: return emptyList()
        val cacheFiles = BookHelp.getChapterFiles(book)
        val model = CacheBook.cacheBookMap[bookUrl]
        val waitingIndices = model?.waitingIndices().orEmpty()
        val downloadingIndices = model?.downloadingIndices().orEmpty()
        return bookChapterDao.getChapterCacheInfoList(bookUrl)
            .asSequence()
            .filterNot { chapter ->
                chapter.isVolume ||
                    cacheFiles.contains(chapter.getFileName()) ||
                    waitingIndices.contains(chapter.index) ||
                    downloadingIndices.contains(chapter.index)
            }
            .map { it.index }
            .toList()
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
            scheduleBookReload(bookUrl, debounceMillis = 0)
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
            scheduleBookReload(bookUrl, debounceMillis = 0)
        }
    }

    private fun stopChapterDownload(bookUrl: String, chapterIndex: Int) {
        if (CacheBook.removeChapter(bookUrl, chapterIndex)) {
            scheduleBookReload(bookUrl, debounceMillis = 0)
        }
    }

    private fun deleteChapterCache(
        bookUrl: String,
        chapterUrl: String,
        chapterTitle: String,
        chapterIndex: Int,
    ) {
        val book = bookDao.getBook(bookUrl) ?: return
        val chapter = BookChapter(
            url = chapterUrl,
            title = chapterTitle,
            bookUrl = bookUrl,
            index = chapterIndex,
        )
        execute {
            BookHelp.delContent(book, chapter)
        }.onSuccess {
            _effects.tryEmit(BookCacheManageEffect.ShowMessage("章节缓存已删除"))
        }.onError {
            _effects.tryEmit(BookCacheManageEffect.ShowMessage("删除章节缓存失败\n${it.localizedMessage}"))
        }.onFinally {
            scheduleBookReload(bookUrl, debounceMillis = 0)
        }
    }

    private fun sortItems(items: List<BookCacheBookItem>): List<BookCacheBookItem> {
        return items.sortedWith(compareByDescending<BookCacheBookItem> { it.isDownloading }
            .thenByDescending { it.cachedCount }
            .thenBy { it.name })
    }

    private fun buildDownloadSummary(items: List<BookCacheBookItem>): String {
        if (items.isEmpty()) return ""
        val downloadingCount = items.sumOf { it.downloadingCount }
        val waitingCount = items.sumOf { it.waitingCount }
        val errorCount = items.sumOf { it.errorCount }
        val cachedCount = items.sumOf { it.cachedCount }
        return "正在下载:$downloadingCount | 等待中:$waitingCount | 失败:$errorCount | 成功:$cachedCount"
    }

    private data class LoadedCacheState(
        val items: List<BookCacheBookItem>,
        val expandedBookUrls: Set<String>,
        val chaptersByBookUrl: Map<String, List<BookCacheChapterItem>>,
    )

    private data class LoadedBookState(
        val item: BookCacheBookItem?,
        val chapters: List<BookCacheChapterItem>?,
    )
}

private fun BookChapterCacheInfo.getFileName(): String {
    return BookChapter(
        url = url,
        title = title,
        isVolume = isVolume,
        index = index,
    ).getFileName()
}
