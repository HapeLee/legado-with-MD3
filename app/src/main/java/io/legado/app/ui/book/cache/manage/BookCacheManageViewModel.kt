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
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

data class BookCacheManageUiState(
    val isLoading: Boolean = true,
    val shelfBooks: List<BookCacheBookItem> = emptyList(),
    val notShelfBooks: List<BookCacheBookItem> = emptyList(),
    val expandedBookUrls: Set<String> = emptySet(),
    val chaptersByBookUrl: Map<String, List<BookCacheChapterItem>> = emptyMap(),
    val downloadSummary: String = "",
    val hasPausedDownloads: Boolean = false,
    val version: Long = 0,
)

data class BookCacheBookItem(
    val bookUrl: String,
    val name: String,
    val author: String,
    val totalCount: Int,
    val cachedCount: Int,
    val cachedFileCount: Int,
    val waitingCount: Int,
    val downloadingCount: Int,
    val pausedCount: Int,
    val errorCount: Int,
    val isNotShelf: Boolean,
) {
    val progress: Float get() = if (totalCount == 0) 0f else cachedCount.toFloat() / totalCount
    val hasActiveDownload: Boolean get() = waitingCount > 0 || downloadingCount > 0
    val isPaused: Boolean get() = pausedCount > 0
    val hasDownloadTask: Boolean get() = hasActiveDownload || isPaused
    val isDownloading: Boolean get() = hasActiveDownload
}

data class BookCacheChapterItem(
    val chapterUrl: String,
    val title: String,
    val index: Int,
    val isCached: Boolean,
    val isWaiting: Boolean,
    val isDownloading: Boolean,
    val isPaused: Boolean,
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

    private companion object {
        const val DOWNLOAD_BATCH_SIZE = 512
        const val DOWNLOAD_STATUS_REFRESH_INTERVAL_MILLIS = 2_000L
    }

    private val _uiState = MutableStateFlow(BookCacheManageUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<BookCacheManageEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var observeJob: Job? = null
    private var fullReloadJob: Job? = null
    private val bookReloadJobs = hashMapOf<String, Job>()
    private val pendingDownloadRefreshBookUrls = ConcurrentHashMap.newKeySet<String>()
    @Volatile
    private var pendingDownloadSummaryRefresh = false

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
                scheduleDownloadStatusRefresh(chapter.bookUrl)
            }
        }
        viewModelScope.launch {
            CacheBook.downloadStateFlow.collect { state ->
                state.books.keys.forEach { scheduleDownloadStatusRefresh(it) }
                pendingDownloadSummaryRefresh = true
            }
        }
        viewModelScope.launch {
            CacheBook.pendingAdmissionFlow.collect { pending ->
                pending.keys.forEach { scheduleDownloadStatusRefresh(it) }
                pendingDownloadSummaryRefresh = true
            }
        }
        viewModelScope.launch {
            CacheBook.queueChangedFlow.collect { bookUrl ->
                scheduleDownloadStatusRefresh(bookUrl)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(DOWNLOAD_STATUS_REFRESH_INTERVAL_MILLIS)
                flushDownloadStatusRefresh()
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
                        .filter(::shouldShowItem)
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
                    hasPausedDownloads = CacheBook.hasPausedDownloads,
                    version = state.version + 1,
                )
            }
        }
    }

    private fun scheduleDownloadStatusRefresh(bookUrl: String) {
        if (bookUrl.isNotBlank()) {
            pendingDownloadRefreshBookUrls.add(bookUrl)
        }
        pendingDownloadSummaryRefresh = true
    }

    private suspend fun flushDownloadStatusRefresh() {
        val bookUrls = pendingDownloadRefreshBookUrls.toList()
        bookUrls.forEach { pendingDownloadRefreshBookUrls.remove(it) }
        val shouldRefreshSummary = pendingDownloadSummaryRefresh || bookUrls.isNotEmpty()
        pendingDownloadSummaryRefresh = false
        bookUrls.forEach { bookUrl ->
            reloadBook(bookUrl)
        }
        if (shouldRefreshSummary) {
            _uiState.update {
                it.copy(
                    downloadSummary = buildDownloadSummary(it.shelfBooks + it.notShelfBooks),
                    hasPausedDownloads = CacheBook.hasPausedDownloads,
                    version = it.version + 1,
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
                ?.takeIf(::shouldShowItem)
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
                hasPausedDownloads = CacheBook.hasPausedDownloads,
                version = state.version + 1,
            )
        }
    }

    private fun buildBookItem(book: Book): BookCacheBookItem? {
        val cacheFiles = BookHelp.getChapterFiles(book)
        val bookState = CacheBook.downloadStateFlow.value.books[book.bookUrl]
        val model = CacheBook.cacheBookMap[book.bookUrl]
        val rawWaitingCount = bookState?.waitingCount.orZero() +
                CacheBook.pendingAdmissionFlow.value[book.bookUrl].orZero()
        val rawDownloadingCount = bookState?.runningIndices?.size.orZero()
        val isBookPaused = model?.isPaused() == true ||
                (CacheBook.hasPausedDownloads && CacheBook.pendingAdmissionFlow.value.containsKey(book.bookUrl))
        val pausedCount = if (isBookPaused) {
            rawWaitingCount + rawDownloadingCount + bookState?.pausedIndices?.size.orZero()
        } else {
            bookState?.pausedIndices?.size.orZero()
        }
        val waitingCount = if (isBookPaused) 0 else rawWaitingCount
        val downloadingCount = if (isBookPaused) 0 else rawDownloadingCount
        val errorIndices = errorIndices(book.bookUrl)
        val totalCount = bookChapterDao.getChapterCount(book.bookUrl)
        val cachedFileCount = cacheFiles.count { it.endsWith(".nb") }
        val cachedCount = min(cachedFileCount + bookChapterDao.getVolumeCount(book.bookUrl), totalCount)
        if (totalCount == 0 && cacheFiles.isEmpty() && waitingCount == 0 && downloadingCount == 0 && pausedCount == 0 && !book.isNotShelf) {
            return null
        }
        return BookCacheBookItem(
            bookUrl = book.bookUrl,
            name = book.name,
            author = book.getRealAuthor(),
            totalCount = totalCount,
            cachedCount = cachedCount,
            cachedFileCount = cachedFileCount,
            waitingCount = waitingCount,
            downloadingCount = downloadingCount,
            pausedCount = pausedCount,
            errorCount = errorIndices.size,
            isNotShelf = book.isNotShelf,
        )
    }

    private fun shouldShowItem(item: BookCacheBookItem): Boolean {
        return item.cachedFileCount > 0 || item.hasDownloadTask || item.errorCount > 0
    }

    private fun buildChapterItems(bookUrl: String): List<BookCacheChapterItem> {
        val book = bookDao.getBook(bookUrl) ?: return emptyList()
        val chapters = bookChapterDao.getChapterCacheInfoList(bookUrl)
        val cacheFiles = BookHelp.getChapterFiles(book)
        val model = CacheBook.cacheBookMap[bookUrl]
        val errorIndices = errorIndices(bookUrl)
        return chapters.map { chapter ->
            val isPaused = model?.isPaused(chapter.index) == true
            BookCacheChapterItem(
                chapterUrl = chapter.url,
                title = chapter.title,
                index = chapter.index,
                isCached = cacheFiles.contains(chapter.getFileName()) || chapter.isVolume,
                isWaiting = !isPaused && model?.isWaiting(chapter.index) == true,
                isDownloading = !isPaused && model?.isDownloading(chapter.index) == true,
                isPaused = isPaused,
                isError = errorIndices.contains(chapter.index),
            )
        }
    }

    private fun errorIndices(bookUrl: String): Set<Int> {
        return CacheBook.errorIndices(bookUrl)
    }

    private fun Int?.orZero(): Int = this ?: 0

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
        execute {
            CacheBook.pause(context)
        }.onFinally {
            reloadAll(forceDatabase = true)
        }
    }

    private fun stopBookDownload(bookUrl: String) {
        execute {
            CacheBook.pauseBook(context, bookUrl)
        }.onFinally {
            scheduleBookReload(bookUrl, debounceMillis = 0)
        }
    }

    private fun startAllDownloads() {
        val items = uiState.value.shelfBooks + uiState.value.notShelfBooks
        execute {
            if (CacheBook.resume(context)) {
                return@execute null
            }
            var count = 0
            items.forEach { item ->
                downloadableChapterIndexBatches(item.bookUrl).forEach { chapterIndices ->
                    count += cacheBookChaptersUseCase.execute(item.bookUrl, chapterIndices)
                }
                currentCoroutineContext().ensureActive()
            }
            count
        }.onSuccess { countOrNull ->
            val count = countOrNull ?: return@onSuccess
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
            if (CacheBook.resumeBook(context, bookUrl)) {
                return@execute null
            }
            var count = 0
            downloadableChapterIndexBatches(bookUrl).forEach { chapterIndices ->
                count += cacheBookChaptersUseCase.execute(bookUrl, chapterIndices)
            }
            count
        }.onSuccess { countOrNull ->
            val count = countOrNull ?: return@onSuccess
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

    private fun downloadableChapterIndexBatches(
        bookUrl: String,
        batchSize: Int = DOWNLOAD_BATCH_SIZE,
    ): Sequence<List<Int>> = sequence {
        val book = bookDao.getBook(bookUrl) ?: return@sequence
        val cacheFiles = BookHelp.getChapterFiles(book)
        val model = CacheBook.cacheBookMap[bookUrl]
        var batch = ArrayList<Int>(batchSize)
        for (chapter in bookChapterDao.getChapterCacheInfoList(bookUrl)) {
            if (
                chapter.isVolume ||
                    cacheFiles.contains(chapter.getFileName()) ||
                    model?.isPaused(chapter.index) == true ||
                    model?.isWaiting(chapter.index) == true ||
                    model?.isDownloading(chapter.index) == true
            ) {
                continue
            }
            batch.add(chapter.index)
            if (batch.size == batchSize) {
                yield(batch)
                batch = ArrayList(batchSize)
            }
        }
        if (batch.isNotEmpty()) {
            yield(batch)
        }
    }

    private fun deleteBookCache(bookUrl: String) {
        execute {
            CacheBook.removeAwait(context, bookUrl)
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
            if (CacheBook.resumeChapter(context, bookUrl, chapterIndex)) {
                return@execute false
            }
            cacheBookChaptersUseCase.execute(bookUrl, listOf(chapterIndex))
            true
        }.onSuccess { enqueued ->
            if (!enqueued) return@onSuccess
            _effects.tryEmit(BookCacheManageEffect.ShowMessage("章节已加入缓存队列"))
        }.onError {
            _effects.tryEmit(BookCacheManageEffect.ShowMessage("章节缓存失败\n${it.localizedMessage}"))
        }.onFinally {
            scheduleBookReload(bookUrl, debounceMillis = 0)
        }
    }

    private fun stopChapterDownload(bookUrl: String, chapterIndex: Int) {
        execute {
            CacheBook.pauseChapter(bookUrl, chapterIndex)
        }.onSuccess {
            scheduleBookReload(bookUrl, debounceMillis = 0)
        }
    }

    private fun deleteChapterCache(
        bookUrl: String,
        chapterUrl: String,
        chapterTitle: String,
        chapterIndex: Int,
    ) {
        execute {
            val book = bookDao.getBook(bookUrl) ?: return@execute false
            val chapter = BookChapter(
                url = chapterUrl,
                title = chapterTitle,
                bookUrl = bookUrl,
                index = chapterIndex,
            )
            BookHelp.delContent(book, chapter)
            true
        }.onSuccess { deleted ->
            if (!deleted) return@onSuccess
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
        val pausedCount = items.sumOf { it.pausedCount }
        val errorCount = items.sumOf { it.errorCount }
        val cachedCount = items.sumOf { it.cachedCount }
        return "下载中:$downloadingCount | 等待:$waitingCount | 暂停:$pausedCount | 失败:$errorCount | 已缓存:$cachedCount"
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
