package io.legado.app.ui.book.manage

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.dao.BookGroupDao
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.constant.BookType
import io.legado.app.domain.usecase.BatchChangeSourceCandidate
import io.legado.app.domain.usecase.BatchChangeSourcePreviewItem
import io.legado.app.domain.usecase.BatchChangeSourcePreviewStatus
import io.legado.app.domain.usecase.BatchCacheDownloadUseCase
import io.legado.app.domain.usecase.CacheBookChaptersUseCase
import io.legado.app.domain.usecase.ChangeBookSourceUseCase
import io.legado.app.domain.usecase.ChangeSourceMigrationOptions
import io.legado.app.domain.usecase.ClearBookCacheUseCase
import io.legado.app.domain.usecase.DeleteBooksUseCase
import io.legado.app.domain.usecase.UpdateBooksGroupUseCase
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.removeType
import io.legado.app.model.CacheBook
import io.legado.app.help.config.LocalConfig
import io.legado.app.service.ExportBookService
import io.legado.app.ui.config.bookshelfConfig.BookshelfConfig
import io.legado.app.ui.config.bookshelfConfig.BookshelfManageScreenConfig
import io.legado.app.ui.config.otherConfig.OtherConfig
import io.legado.app.ui.main.bookshelf.toLightBook
import io.legado.app.utils.cnCompare
import io.legado.app.utils.move
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import java.util.concurrent.ConcurrentHashMap

data class BookshelfManageScreenExportConfig(
    val exportUseReplace: Boolean = true,
    val enableCustomExport: Boolean = false,
    val exportNoChapterName: Boolean = false,
    val exportToWebDav: Boolean = false,
    val exportPictureFile: Boolean = false,
    val parallelExportBook: Boolean = false,
    val exportType: Int = 0,
    val exportCharset: String = "UTF-8",
    val bookExportFileName: String? = null,
    val episodeExportFileName: String = ""
)

data class BookshelfManageScreenUiState(
    val groupId: Long = -1,
    val groupName: String? = null,
    val groupList: List<BookGroup> = emptyList(),
    val books: List<Book> = emptyList(),
    val bookSort: Int = BookshelfConfig.bookshelfSort,
    val bookSortOrder: Int = BookshelfConfig.bookshelfSortOrder,
    val isDownloadRunning: Boolean = false,
    val isChangingSource: Boolean = false,
    val changeSourceProgress: String? = null,
    val changeSourceMessage: String? = null,
    val changeSourceError: String? = null,
    val batchChangePreviewItems: List<BatchChangeSourcePreviewItem> = emptyList(),
    val batchChangeOptions: ChangeSourceMigrationOptions = ChangeSourceMigrationOptions(),
    val cacheVersion: Long = 0,
    val deleteBookOriginal: Boolean = LocalConfig.deleteBookOriginal,
    val exportConfig: BookshelfManageScreenExportConfig = BookshelfManageScreenExportConfig()
)

sealed interface BookshelfManageScreenIntent {
    data class Initialize(val groupId: Long) : BookshelfManageScreenIntent
    data class ChangeGroup(val groupId: Long) : BookshelfManageScreenIntent
    data class StartDownloadForVisibleBooks(
        val books: List<Book>,
        val downloadAllChapters: Boolean
    ) : BookshelfManageScreenIntent
    data object StopDownload : BookshelfManageScreenIntent
    data class ToggleBookDownload(val book: Book) : BookshelfManageScreenIntent
    data class DeleteBookDownload(val bookUrl: String) : BookshelfManageScreenIntent
    data class ClearBookCache(val book: Book) : BookshelfManageScreenIntent
    data class MoveBooksToGroup(val bookUrls: Set<String>, val groupId: Long) : BookshelfManageScreenIntent
    data class DeleteBooks(val bookUrls: Set<String>, val deleteOriginal: Boolean) : BookshelfManageScreenIntent
    data class ClearCachesForBooks(val bookUrls: Set<String>) : BookshelfManageScreenIntent
    data class MoveBookOrder(val fromIndex: Int, val toIndex: Int) : BookshelfManageScreenIntent
    data class DownloadBooks(val bookUrls: Set<String>, val downloadAllChapters: Boolean) : BookshelfManageScreenIntent
    data class ChangeBookSource(
        val oldBookUrl: String,
        val source: BookSource,
        val book: Book,
        val chapters: List<BookChapter>,
        val options: ChangeSourceMigrationOptions,
    ) : BookshelfManageScreenIntent
    data class BatchChangeBookSource(
        val bookUrls: Set<String>,
        val sources: List<BookSource>,
        val options: ChangeSourceMigrationOptions,
    ) : BookshelfManageScreenIntent
    data class MigratePreviewItem(val oldBookUrl: String) : BookshelfManageScreenIntent
    data class SkipPreviewItem(val oldBookUrl: String) : BookshelfManageScreenIntent
    data class SelectPreviewCandidate(val oldBookUrl: String, val candidateIndex: Int) : BookshelfManageScreenIntent
    data class UpdatePreviewItem(
        val oldBookUrl: String,
        val source: BookSource,
        val book: Book,
        val chapterCount: Int,
    ) : BookshelfManageScreenIntent
    data class AddPreviewItemToShelf(val oldBookUrl: String) : BookshelfManageScreenIntent
    data class OpenBookInfoPreview(val book: Book, val inBookshelf: Boolean) : BookshelfManageScreenIntent
    data object MigrateAllPreviewItems : BookshelfManageScreenIntent
    data object DismissChangeSourceStatus : BookshelfManageScreenIntent
    data object DismissBatchChangePreview : BookshelfManageScreenIntent
    data class SetExportUseReplace(val enabled: Boolean) : BookshelfManageScreenIntent
    data class SetEnableCustomExport(val enabled: Boolean) : BookshelfManageScreenIntent
    data class SetExportNoChapterName(val enabled: Boolean) : BookshelfManageScreenIntent
    data class SetExportToWebDav(val enabled: Boolean) : BookshelfManageScreenIntent
    data class SetExportPictureFile(val enabled: Boolean) : BookshelfManageScreenIntent
    data class SetParallelExportBook(val enabled: Boolean) : BookshelfManageScreenIntent
    data class SetExportType(val type: Int) : BookshelfManageScreenIntent
    data class SetExportCharset(val charset: String) : BookshelfManageScreenIntent
    data class SetBookExportFileName(val fileName: String?) : BookshelfManageScreenIntent
    data class SetEpisodeExportFileName(val fileName: String) : BookshelfManageScreenIntent
}

sealed interface BookshelfManageScreenEffect {
    data class NotifyBookChanged(val bookUrl: String) : BookshelfManageScreenEffect
    data class ShowMessage(val message: String) : BookshelfManageScreenEffect
    data class OpenBookInfo(val bookUrl: String, val name: String, val author: String) : BookshelfManageScreenEffect
}

class BookshelfManageScreenViewModel(
    application: Application,
    private val bookDao: BookDao,
    private val bookGroupDao: BookGroupDao,
    private val bookChapterDao: BookChapterDao,
    val bookshelfManageScreenConfig: BookshelfManageScreenConfig,
    private val batchCacheDownloadUseCase: BatchCacheDownloadUseCase,
    private val cacheBookChaptersUseCase: CacheBookChaptersUseCase,
    private val changeBookSourceUseCase: ChangeBookSourceUseCase,
    private val clearBookCacheUseCase: ClearBookCacheUseCase,
    private val deleteBooksUseCase: DeleteBooksUseCase,
    private val updateBooksGroupUseCase: UpdateBooksGroupUseCase
) : BaseViewModel(application) {

    private companion object {
        const val DOWNLOAD_STATUS_REFRESH_INTERVAL_MILLIS = 2_000L
    }

    private val _uiState = MutableStateFlow(BookshelfManageScreenUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<BookshelfManageScreenEffect>(extraBufferCapacity = 32)
    val effects = _effects.asSharedFlow()

    private val cacheCounts = ConcurrentHashMap<String, Int>()
    private val pendingDownloadBookUrls = ConcurrentHashMap.newKeySet<String>()
    private val downloadFailureMessages = ConcurrentHashMap<String, String>()
    private var booksJob: Job? = null
    private var groupsJob: Job? = null
    private var cacheLoadJob: Job? = null
    private var observersStarted = false
    private val pendingDownloadStatusBookUrls = ConcurrentHashMap.newKeySet<String>()
    private val pendingCacheCountRefreshBookUrls = ConcurrentHashMap.newKeySet<String>()
    @Volatile
    private var pendingDownloadRunningRefresh = false

    fun dispatch(intent: BookshelfManageScreenIntent) {
        when (intent) {
            is BookshelfManageScreenIntent.Initialize -> initialize(intent.groupId)
            is BookshelfManageScreenIntent.ChangeGroup -> changeGroup(intent.groupId)
            is BookshelfManageScreenIntent.StartDownloadForVisibleBooks -> startDownloadForVisibleBooks(
                intent.books,
                intent.downloadAllChapters
            )

            BookshelfManageScreenIntent.StopDownload -> CacheBook.stop(context)
            is BookshelfManageScreenIntent.ToggleBookDownload -> toggleBookDownload(intent.book)
            is BookshelfManageScreenIntent.DeleteBookDownload -> CacheBook.remove(context, intent.bookUrl)
            is BookshelfManageScreenIntent.ClearBookCache -> clearCacheForBook(intent.book)
            is BookshelfManageScreenIntent.MoveBooksToGroup -> moveBooksToGroup(intent.bookUrls, intent.groupId)
            is BookshelfManageScreenIntent.DeleteBooks -> deleteBooks(intent.bookUrls, intent.deleteOriginal)
            is BookshelfManageScreenIntent.ClearCachesForBooks -> clearCachesForBooks(intent.bookUrls)
            is BookshelfManageScreenIntent.MoveBookOrder -> moveBookOrder(intent.fromIndex, intent.toIndex)
            is BookshelfManageScreenIntent.DownloadBooks -> downloadBooks(intent.bookUrls, intent.downloadAllChapters)
            is BookshelfManageScreenIntent.ChangeBookSource -> changeBookSource(
                intent.oldBookUrl,
                intent.source,
                intent.book,
                intent.chapters,
                intent.options
            )

            is BookshelfManageScreenIntent.BatchChangeBookSource -> batchChangeBookSource(
                intent.bookUrls,
                intent.sources,
                intent.options
            )

            is BookshelfManageScreenIntent.MigratePreviewItem -> migratePreviewItem(intent.oldBookUrl)
            is BookshelfManageScreenIntent.SkipPreviewItem -> skipPreviewItem(intent.oldBookUrl)
            is BookshelfManageScreenIntent.SelectPreviewCandidate -> selectPreviewCandidate(
                intent.oldBookUrl,
                intent.candidateIndex
            )

            is BookshelfManageScreenIntent.UpdatePreviewItem -> updatePreviewItem(
                intent.oldBookUrl,
                intent.source,
                intent.book,
                intent.chapterCount
            )

            is BookshelfManageScreenIntent.AddPreviewItemToShelf -> addPreviewItemToShelf(intent.oldBookUrl)
            is BookshelfManageScreenIntent.OpenBookInfoPreview -> openBookInfoPreview(
                intent.book,
                intent.inBookshelf
            )

            BookshelfManageScreenIntent.MigrateAllPreviewItems -> migrateAllPreviewItems()

            BookshelfManageScreenIntent.DismissChangeSourceStatus -> {
                _uiState.update {
                    it.copy(
                        changeSourceProgress = null,
                        changeSourceMessage = null,
                        changeSourceError = null,
                    )
                }
            }

            BookshelfManageScreenIntent.DismissBatchChangePreview -> {
                _uiState.update { it.copy(batchChangePreviewItems = emptyList()) }
            }

            is BookshelfManageScreenIntent.SetExportUseReplace -> {
                bookshelfManageScreenConfig.exportUseReplace = intent.enabled
                syncExportConfig()
                val msg = if (intent.enabled) "替换净化功能已开启" else "替换净化功能已关闭"
                _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage(msg))
            }

            is BookshelfManageScreenIntent.SetEnableCustomExport -> {
                bookshelfManageScreenConfig.enableCustomExport = intent.enabled
                syncExportConfig()
            }

            is BookshelfManageScreenIntent.SetExportNoChapterName -> {
                bookshelfManageScreenConfig.exportNoChapterName = intent.enabled
                syncExportConfig()
            }

            is BookshelfManageScreenIntent.SetExportToWebDav -> {
                bookshelfManageScreenConfig.exportToWebDav = intent.enabled
                syncExportConfig()
            }

            is BookshelfManageScreenIntent.SetExportPictureFile -> {
                bookshelfManageScreenConfig.exportPictureFile = intent.enabled
                syncExportConfig()
            }

            is BookshelfManageScreenIntent.SetParallelExportBook -> {
                bookshelfManageScreenConfig.parallelExportBook = intent.enabled
                syncExportConfig()
            }

            is BookshelfManageScreenIntent.SetExportType -> {
                bookshelfManageScreenConfig.exportType = intent.type
                syncExportConfig()
            }

            is BookshelfManageScreenIntent.SetExportCharset -> {
                bookshelfManageScreenConfig.exportCharset = intent.charset
                syncExportConfig()
            }

            is BookshelfManageScreenIntent.SetBookExportFileName -> {
                bookshelfManageScreenConfig.bookExportFileName = intent.fileName
                syncExportConfig()
            }

            is BookshelfManageScreenIntent.SetEpisodeExportFileName -> {
                bookshelfManageScreenConfig.episodeExportFileName = intent.fileName
                syncExportConfig()
            }
        }
    }

    fun getCacheCount(bookUrl: String): Int? = cacheCounts[bookUrl]

    fun isBookDownloading(bookUrl: String): Boolean {
        if (CacheBook.pendingAdmissionFlow.value[bookUrl].orZero() > 0) return true
        val bookState = CacheBook.downloadStateFlow.value.books[bookUrl] ?: return false
        return bookState.waitingCount > 0 || bookState.runningIndices.isNotEmpty()
    }

    fun getDownloadFailureMessage(bookUrl: String): String? {
        return downloadFailureMessages[bookUrl]
    }

    fun isBookPreparingDownload(bookUrl: String): Boolean {
        return pendingDownloadBookUrls.contains(bookUrl)
    }

    private fun initialize(groupId: Long) {
        _uiState.update { it.copy(groupId = groupId) }
        syncExportConfig()
        observeGroups()
        observeBooks(groupId)
        observeDownloadAndExportChanges()
        refreshGroupName(groupId)
    }

    private fun changeGroup(groupId: Long) {
        _uiState.update { it.copy(groupId = groupId) }
        observeBooks(groupId)
        refreshGroupName(groupId)
    }

    private fun observeGroups() {
        groupsJob?.cancel()
        groupsJob = viewModelScope.launch {
            bookGroupDao.flowAll().collect { groups ->
                _uiState.update { it.copy(groupList = groups) }
            }
        }
    }

    private fun observeBooks(groupId: Long) {
        booksJob?.cancel()
        booksJob = viewModelScope.launch {
            bookDao.flowBookShelfByGroup(groupId).map { books ->
                val booksDownload = books.filter { !it.isAudio }.map { it.toLightBook() }
                val bookSort = bookshelfManageScreenConfig.getBookSortByGroupId(groupId)
                val isDescending = bookshelfManageScreenConfig.bookshelfSortOrder == 1
                bookSort to when (bookSort) {
                    1 -> if (isDescending) booksDownload.sortedByDescending { it.latestChapterTime }
                    else booksDownload.sortedBy { it.latestChapterTime }

                    2 -> if (isDescending) {
                        booksDownload.sortedWith { o1, o2 -> o2.name.cnCompare(o1.name) }
                    } else {
                        booksDownload.sortedWith { o1, o2 -> o1.name.cnCompare(o2.name) }
                    }

                    3 -> if (isDescending) booksDownload.sortedByDescending { it.order }
                    else booksDownload.sortedBy { it.order }

                    4 -> if (isDescending) booksDownload.sortedByDescending {
                        max(it.latestChapterTime, it.durChapterTime)
                    } else booksDownload.sortedBy {
                        max(it.latestChapterTime, it.durChapterTime)
                    }

                    else -> if (isDescending) booksDownload.sortedByDescending { it.durChapterTime }
                    else booksDownload.sortedBy { it.durChapterTime }
                }
            }.collect { (bookSort, books) ->
                _uiState.update {
                    it.copy(
                        books = books,
                        bookSort = bookSort,
                        bookSortOrder = bookshelfManageScreenConfig.bookshelfSortOrder
                    )
                }
                loadCacheCounts(books)
            }
        }
    }

    private fun observeDownloadAndExportChanges() {
        if (observersStarted) return
        observersStarted = true
        viewModelScope.launch {
            CacheBook.cacheSuccessFlow.collect { chapter ->
                scheduleCacheCountRefresh(chapter.bookUrl)
            }
        }
        viewModelScope.launch {
            CacheBook.downloadStateFlow.collect { downloadState ->
                val successfulBookUrls = downloadState.books
                    .filterValues {
                        it.successCount > 0 &&
                                it.failedIndices.isEmpty() &&
                                it.failureMessage == null
                    }
                    .keys
                val failureMsgs = downloadState.books.mapNotNull { (bookUrl, bookState) ->
                    val message = bookState.failureMessage ?: if (bookState.failedIndices.isNotEmpty()) {
                        "${bookState.failedIndices.size} 章"
                    } else {
                        null
                    }
                    message?.let { bookUrl to it }
                }.toMap()
                pendingDownloadBookUrls.removeAll(downloadState.books.keys)
                successfulBookUrls.forEach { downloadFailureMessages.remove(it) }
                downloadFailureMessages.putAll(failureMsgs)
                _uiState.update {
                    it.copy(isDownloadRunning = downloadState.isRunning || CacheBook.pendingAdmissionFlow.value.isNotEmpty())
                }
                downloadState.books.keys.forEach { bookUrl ->
                    scheduleDownloadStatusRefresh(bookUrl)
                }
                scheduleDownloadStatusRefresh()
            }
        }
        viewModelScope.launch {
            CacheBook.pendingAdmissionFlow.collect { pending ->
                pending.keys.forEach { bookUrl ->
                    scheduleDownloadStatusRefresh(bookUrl)
                }
                scheduleDownloadStatusRefresh()
            }
        }
        viewModelScope.launch {
            CacheBook.queueChangedFlow.collect { bookUrl ->
                scheduleDownloadStatusRefresh(bookUrl)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(DOWNLOAD_STATUS_REFRESH_INTERVAL_MILLIS)
                flushDownloadStatusRefresh()
            }
        }
        viewModelScope.launch {
            ExportBookService.exportBookUpdateFlow.collect { bookUrl ->
                emitBookChanged(bookUrl)
            }
        }
    }

    private fun syncExportConfig() {
        _uiState.update {
            it.copy(
                exportConfig = BookshelfManageScreenExportConfig(
                    exportUseReplace = bookshelfManageScreenConfig.exportUseReplace,
                    enableCustomExport = bookshelfManageScreenConfig.enableCustomExport,
                    exportNoChapterName = bookshelfManageScreenConfig.exportNoChapterName,
                    exportToWebDav = bookshelfManageScreenConfig.exportToWebDav,
                    exportPictureFile = bookshelfManageScreenConfig.exportPictureFile,
                    parallelExportBook = bookshelfManageScreenConfig.parallelExportBook,
                    exportType = bookshelfManageScreenConfig.exportType,
                    exportCharset = bookshelfManageScreenConfig.exportCharset,
                    bookExportFileName = bookshelfManageScreenConfig.bookExportFileName,
                    episodeExportFileName = bookshelfManageScreenConfig.episodeExportFileName
                )
            )
        }
    }

    private fun syncDownloadRunning() {
        _uiState.update {
            it.copy(isDownloadRunning = CacheBook.isRun || CacheBook.pendingAdmissionFlow.value.isNotEmpty())
        }
    }

    private fun refreshGroupName(groupId: Long) {
        execute {
            val title = bookGroupDao.getByID(groupId)?.groupName
            title ?: context.getString(io.legado.app.R.string.no_group)
        }.onSuccess { groupName ->
            _uiState.update { it.copy(groupName = groupName) }
        }
    }

    private fun loadCacheCounts(books: List<Book>) {
        cacheLoadJob?.cancel()
        cacheLoadJob = viewModelScope.launch(Dispatchers.IO) {
            val visibleBookUrls = books.mapTo(hashSetOf()) { it.bookUrl }
            cacheCounts.keys.toList().forEach { bookUrl ->
                if (!visibleBookUrls.contains(bookUrl)) {
                    cacheCounts.remove(bookUrl)
                }
            }
            val changedBookUrls = linkedSetOf<String>()
            books.forEach { book ->
                if (!book.isLocal && !cacheCounts.containsKey(book.bookUrl)) {
                    cacheCounts[book.bookUrl] = calculateCacheCount(book)
                    changedBookUrls.add(book.bookUrl)
                }
                ensureActive()
            }
            if (changedBookUrls.isNotEmpty()) {
                _uiState.update { it.copy(cacheVersion = it.cacheVersion + 1) }
            }
        }
    }

    private fun scheduleCacheCountRefresh(bookUrl: String) {
        if (bookUrl.isNotBlank()) {
            pendingCacheCountRefreshBookUrls.add(bookUrl)
        }
        pendingDownloadRunningRefresh = true
    }

    private fun scheduleDownloadStatusRefresh(bookUrl: String = "") {
        if (bookUrl.isNotBlank()) {
            pendingDownloadStatusBookUrls.add(bookUrl)
        }
        pendingDownloadRunningRefresh = true
    }

    private suspend fun flushDownloadStatusRefresh() {
        val cacheRefreshBookUrls = pendingCacheCountRefreshBookUrls.toList()
        cacheRefreshBookUrls.forEach { pendingCacheCountRefreshBookUrls.remove(it) }
        val statusBookUrls = pendingDownloadStatusBookUrls.toList()
        statusBookUrls.forEach { pendingDownloadStatusBookUrls.remove(it) }
        val shouldSyncDownloadRunning = pendingDownloadRunningRefresh ||
            cacheRefreshBookUrls.isNotEmpty() ||
            statusBookUrls.isNotEmpty()
        pendingDownloadRunningRefresh = false
        val changedBookUrls = linkedSetOf<String>()
        val visibleBookUrls = uiState.value.books.mapTo(hashSetOf()) { it.bookUrl }
        cacheRefreshBookUrls.forEach { bookUrl ->
            if (visibleBookUrls.contains(bookUrl)) {
                bookDao.getBook(bookUrl)?.let { book ->
                    cacheCounts[bookUrl] = calculateCacheCount(book)
                    changedBookUrls.add(bookUrl)
                }
            }
        }
        statusBookUrls.forEach { bookUrl ->
            if (visibleBookUrls.contains(bookUrl)) {
                changedBookUrls.add(bookUrl)
            }
        }
        if (shouldSyncDownloadRunning) {
            syncDownloadRunning()
        }
        emitBooksChanged(changedBookUrls)
    }

    private fun calculateCacheCount(book: Book): Int {
        val cacheNames = BookHelp.getChapterFiles(book)
        if (cacheNames.isEmpty()) return 0
        val totalCount = bookChapterDao.getChapterCount(book.bookUrl)
        val cachedFileCount = cacheNames.count { it.endsWith(".nb") }
        return min(cachedFileCount + bookChapterDao.getVolumeCount(book.bookUrl), totalCount)
    }

    private fun Int?.orZero(): Int = this ?: 0

    private fun startDownloadForVisibleBooks(books: List<Book>, downloadAllChapters: Boolean) {
        val bookUrls = books.mapTo(hashSetOf()) { it.bookUrl }
        bookUrls.forEach { downloadFailureMessages.remove(it) }
        execute {
            batchCacheDownloadUseCase.execute(
                bookUrls = bookUrls,
                downloadAllChapters = downloadAllChapters,
                skipAudioBooks = true
            )
        }.onFinally {
            syncDownloadRunning()
        }
    }

    private fun toggleBookDownload(book: Book) {
        if (book.isLocal) return
        if (isBookDownloading(book.bookUrl) || pendingDownloadBookUrls.contains(book.bookUrl)) {
            CacheBook.remove(context, book.bookUrl)
            pendingDownloadBookUrls.remove(book.bookUrl)
            emitBookChanged(book.bookUrl)
            syncDownloadRunning()
        } else {
            pendingDownloadBookUrls.add(book.bookUrl)
            downloadFailureMessages.remove(book.bookUrl)
            emitBookChanged(book.bookUrl)
            execute {
                cacheBookChaptersUseCase.executeRange(book.bookUrl, 0, book.lastChapterIndex)
            }.onSuccess { count ->
                if (count <= 0) {
                    pendingDownloadBookUrls.remove(book.bookUrl)
                    downloadFailureMessages[book.bookUrl] = "没有可缓存的章节"
                    emitBookChanged(book.bookUrl)
                    _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("没有可缓存的章节"))
                }
            }.onError { error ->
                pendingDownloadBookUrls.remove(book.bookUrl)
                downloadFailureMessages[book.bookUrl] = error.localizedMessage ?: "未知错误"
                emitBookChanged(book.bookUrl)
                _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("缓存失败\n${error.localizedMessage}"))
            }.onFinally {
                syncDownloadRunning()
            }
        }
    }

    private fun moveBooksToGroup(bookUrls: Set<String>, groupId: Long) {
        if (bookUrls.isEmpty()) return
        val safeGroupId = groupId.coerceAtLeast(0L)
        execute {
            updateBooksGroupUseCase.replaceGroup(bookUrls, safeGroupId)
        }.onError {
            _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("移动分组失败\n${it.localizedMessage}"))
        }
    }

    private fun deleteBooks(bookUrls: Set<String>, deleteOriginal: Boolean) {
        if (bookUrls.isEmpty()) return
        execute {
            LocalConfig.deleteBookOriginal = deleteOriginal
            deleteBooksUseCase.execute(bookUrls, deleteOriginal)
        }.onSuccess { deletedBookUrls ->
            _uiState.update { it.copy(deleteBookOriginal = deleteOriginal) }
            deletedBookUrls.forEach { cacheCounts.remove(it) }
            _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("删除成功"))
        }.onError {
            _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("删除失败\n${it.localizedMessage}"))
        }
    }

    private fun clearCachesForBooks(bookUrls: Set<String>) {
        if (bookUrls.isEmpty()) return
        execute {
            clearBookCacheUseCase.execute(bookUrls)
        }.onSuccess { clearedBookUrls ->
            clearedBookUrls.forEach { bookUrl ->
                cacheCounts[bookUrl] = 0
                emitBookChanged(bookUrl)
            }
            _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("缓存已清理"))
        }.onError {
            _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("清理缓存失败\n${it.localizedMessage}"))
        }
    }

    private fun moveBookOrder(fromIndex: Int, toIndex: Int) {
        val books = uiState.value.books
        if (fromIndex !in books.indices || toIndex !in books.indices || fromIndex == toIndex) {
            return
        }
        val reorderedBooks = books.toMutableList().apply { move(fromIndex, toIndex) }
        val isDescending = uiState.value.bookSortOrder == 1
        val maxOrder = reorderedBooks.size
        reorderedBooks.forEachIndexed { index, book ->
            book.order = if (isDescending) maxOrder - index else index + 1
        }
        _uiState.update {
            it.copy(
                books = reorderedBooks,
                cacheVersion = it.cacheVersion + 1
            )
        }
        execute {
            bookDao.update(*reorderedBooks.toTypedArray())
        }.onError {
            _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("排序保存失败\n${it.localizedMessage}"))
        }
    }

    private fun downloadBooks(bookUrls: Set<String>, downloadAllChapters: Boolean) {
        if (bookUrls.isEmpty()) return
        bookUrls.forEach { downloadFailureMessages.remove(it) }
        execute {
            batchCacheDownloadUseCase.execute(
                bookUrls = bookUrls,
                downloadAllChapters = downloadAllChapters,
                skipAudioBooks = true
            )
        }.onSuccess { count ->
            if (count > 0) {
                _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("已加入缓存队列: $count 本"))
            } else {
                _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("没有可缓存的书籍"))
            }
            syncDownloadRunning()
        }.onError {
            _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("批量缓存失败\n${it.localizedMessage}"))
        }
    }

    private fun changeBookSource(
        oldBookUrl: String,
        source: BookSource,
        book: Book,
        chapters: List<BookChapter>,
        options: ChangeSourceMigrationOptions,
    ) {
        execute {
            val oldBook = bookDao.getBook(oldBookUrl) ?: return@execute null
            changeBookSourceUseCase.changeTo(oldBook, book, chapters, options)
        }.onSuccess { result ->
            result ?: return@onSuccess
            cacheCounts.remove(result.oldBookUrl)
            cacheCounts[result.book.bookUrl] = 0
            emitBookChanged(result.book.bookUrl)
            _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("换源完成"))
        }.onError {
            _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("换源失败\n${it.localizedMessage}"))
        }
    }

    private fun batchChangeBookSource(
        bookUrls: Set<String>,
        sources: List<BookSource>,
        options: ChangeSourceMigrationOptions,
    ) {
        if (bookUrls.isEmpty()) {
            _uiState.update { it.copy(changeSourceError = "未选择书籍") }
            return
        }
        if (sources.isEmpty()) {
            _uiState.update { it.copy(changeSourceError = "未选择书源") }
            return
        }
        execute {
            val concurrency = OtherConfig.threadCount.coerceAtLeast(1)
            _uiState.update {
                it.copy(
                    isChangingSource = true,
                    changeSourceProgress = "0 / ${bookUrls.size}",
                    changeSourceMessage = "开始查找：${bookUrls.size} 本，${sources.size} 个书源，并发 $concurrency",
                    changeSourceError = null,
                    batchChangeOptions = options,
                    batchChangePreviewItems = emptyList()
                )
            }
            val books = bookUrls.mapNotNull { bookDao.getBook(it) }
            changeBookSourceUseCase.prepareBatchChange(
                books = books,
                sources = sources,
                concurrency = concurrency,
            ) { current, total, bookName ->
                _uiState.update {
                    it.copy(changeSourceProgress = "$current / $total  $bookName")
                }
            }
        }.onSuccess { previewItems ->
            _uiState.update {
                it.copy(
                    batchChangePreviewItems = previewItems,
                    isChangingSource = false,
                    changeSourceProgress = null
                )
            }
            val matchedCount = previewItems.count { it.canMigrate }
            val skippedCount = previewItems.count {
                it.status == BatchChangeSourcePreviewStatus.Skipped
            }
            val notFoundCount = previewItems.size - matchedCount - skippedCount
            _uiState.update {
                it.copy(
                    changeSourceMessage = "查找完成：可迁移 $matchedCount 本，未找到 $notFoundCount 本，跳过 $skippedCount 本",
                    changeSourceError = null
                )
            }
        }.onError {
            val progress = uiState.value.changeSourceProgress.orEmpty()
            _uiState.update { state ->
                state.copy(
                    changeSourceError = "批量换源查找失败${if (progress.isBlank()) "" else "\n进度：$progress"}\n${it.localizedMessage}"
                )
            }
        }.onFinally {
            _uiState.update {
                it.copy(
                    isChangingSource = false,
                    changeSourceProgress = null
                )
            }
        }
    }

    private fun migratePreviewItem(oldBookUrl: String) {
        val item = uiState.value.batchChangePreviewItems.firstOrNull {
            it.oldBook.bookUrl == oldBookUrl
        } ?: return
        val candidate = item.selectedCandidate ?: return
        execute {
            val oldBook = bookDao.getBook(oldBookUrl) ?: item.oldBook
            val chapters = changeBookSourceUseCase.loadCandidateChapters(
                candidate.source,
                candidate.book
            ) ?: error("获取目录失败")
            changeBookSourceUseCase.changeTo(
                oldBook = oldBook,
                newBook = candidate.book,
                chapters = chapters,
                options = uiState.value.batchChangeOptions,
            )
        }.onSuccess { result ->
            cacheCounts.remove(result.oldBookUrl)
            cacheCounts[result.book.bookUrl] = 0
            removePreviewItem(oldBookUrl)
            emitBookChanged(result.book.bookUrl)
            _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("迁移完成"))
        }.onError {
            _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("迁移失败\n${it.localizedMessage}"))
        }
    }

    private fun skipPreviewItem(oldBookUrl: String) {
        _uiState.update { state ->
            state.copy(
                batchChangePreviewItems = state.batchChangePreviewItems.map { item ->
                    if (item.oldBook.bookUrl == oldBookUrl) {
                        item.copy(status = BatchChangeSourcePreviewStatus.Skipped)
                    } else {
                        item
                    }
                }
            )
        }
    }

    private fun selectPreviewCandidate(oldBookUrl: String, candidateIndex: Int) {
        _uiState.update { state ->
            state.copy(
                batchChangePreviewItems = state.batchChangePreviewItems.map { item ->
                    if (item.oldBook.bookUrl == oldBookUrl) {
                        item.copy(
                            selectedCandidateIndex = candidateIndex.coerceIn(
                                0,
                                (item.candidates.size - 1).coerceAtLeast(0)
                            ),
                            status = BatchChangeSourcePreviewStatus.Matched
                        )
                    } else {
                        item
                    }
                }
            )
        }
    }

    private fun updatePreviewItem(
        oldBookUrl: String,
        source: BookSource,
        book: Book,
        chapterCount: Int,
    ) {
        _uiState.update { state ->
            state.copy(
                batchChangePreviewItems = state.batchChangePreviewItems.map { item ->
                    if (item.oldBook.bookUrl == oldBookUrl) {
                        book.totalChapterNum = chapterCount
                        item.copy(
                            candidates = listOf(
                                BatchChangeSourceCandidate(
                                    source = source,
                                    book = book,
                                    chapterCount = chapterCount
                                )
                            ) +
                                    item.candidates,
                            selectedCandidateIndex = 0,
                            status = BatchChangeSourcePreviewStatus.Matched
                        )
                    } else {
                        item
                    }
                }
            )
        }
    }

    private fun addPreviewItemToShelf(oldBookUrl: String) {
        val item = uiState.value.batchChangePreviewItems.firstOrNull {
            it.oldBook.bookUrl == oldBookUrl
        } ?: return
        val candidate = item.selectedCandidate ?: return
        execute {
            val chapters = changeBookSourceUseCase.loadCandidateChapters(
                candidate.source,
                candidate.book
            ) ?: error("获取目录失败")
            candidate.book.removeType(BookType.notShelf)
            if (candidate.book.order == 0) {
                candidate.book.order = bookDao.minOrder - 1
            }
            bookDao.insert(candidate.book)
            bookChapterDao.insert(*chapters.toTypedArray())
            candidate.book
        }.onSuccess {
            _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("已添加到书架"))
        }.onError {
            _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("添加书籍失败\n${it.localizedMessage}"))
        }
    }

    private fun openBookInfoPreview(book: Book, inBookshelf: Boolean) {
        execute {
            if (!inBookshelf) {
                appDb.searchBookDao.insert(book.toSearchBook())
            }
            book
        }.onSuccess {
            _effects.tryEmit(BookshelfManageScreenEffect.OpenBookInfo(it.bookUrl, it.name, it.author))
        }
    }

    private fun removePreviewItem(oldBookUrl: String) {
        _uiState.update {
            it.copy(
                batchChangePreviewItems = it.batchChangePreviewItems.filterNot { item ->
                    item.oldBook.bookUrl == oldBookUrl
                },
                cacheVersion = it.cacheVersion + 1
            )
        }
    }

    private fun migrateAllPreviewItems() {
        val items = uiState.value.batchChangePreviewItems.filter { it.canMigrate }
        if (items.isEmpty()) return
        execute {
            _uiState.update {
                it.copy(isChangingSource = true, changeSourceProgress = "0 / ${items.size}")
            }
            items.forEachIndexed { index, item ->
                _uiState.update {
                    it.copy(changeSourceProgress = "${index + 1} / ${items.size}  ${item.oldBook.name}")
                }
                val candidate = item.selectedCandidate ?: return@forEachIndexed
                val oldBook = bookDao.getBook(item.oldBook.bookUrl) ?: item.oldBook
                val chapters = changeBookSourceUseCase.loadCandidateChapters(
                    candidate.source,
                    candidate.book
                ) ?: return@forEachIndexed
                changeBookSourceUseCase.changeTo(
                    oldBook = oldBook,
                    newBook = candidate.book,
                    chapters = chapters,
                    options = uiState.value.batchChangeOptions,
                )
            }
        }.onSuccess {
            cacheCounts.clear()
            _uiState.update { it.copy(batchChangePreviewItems = emptyList()) }
            _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("批量迁移完成"))
        }.onError {
            _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("批量迁移失败\n${it.localizedMessage}"))
        }.onFinally {
            _uiState.update {
                it.copy(
                    isChangingSource = false,
                    changeSourceProgress = null,
                    cacheVersion = it.cacheVersion + 1
                )
            }
        }
    }

    private fun clearCacheForBook(book: Book) {
        execute {
            clearBookCacheUseCase.execute(book.bookUrl)
        }.onSuccess { bookUrl ->
            bookUrl ?: return@onSuccess
            cacheCounts[bookUrl] = 0
            emitBookChanged(bookUrl)
        }
    }

    private fun emitBookChanged(bookUrl: String) {
        _uiState.update { it.copy(cacheVersion = it.cacheVersion + 1) }
        _effects.tryEmit(BookshelfManageScreenEffect.NotifyBookChanged(bookUrl))
    }

    private fun emitBooksChanged(bookUrls: Set<String>) {
        if (bookUrls.isEmpty()) return
        _uiState.update { it.copy(cacheVersion = it.cacheVersion + 1) }
    }

}
