package io.legado.app.ui.book.cache

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.dao.BookGroupDao
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookGroup
import io.legado.app.domain.usecase.BatchCacheDownloadUseCase
import io.legado.app.domain.usecase.CacheBookChaptersUseCase
import io.legado.app.domain.usecase.ClearBookCacheUseCase
import io.legado.app.domain.usecase.DeleteBooksUseCase
import io.legado.app.domain.usecase.UpdateBooksGroupUseCase
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isLocal
import io.legado.app.model.CacheBook
import io.legado.app.help.config.LocalConfig
import io.legado.app.service.ExportBookService
import io.legado.app.ui.config.cacheConfig.CacheConfig
import io.legado.app.ui.main.bookshelf.toLightBook
import io.legado.app.utils.cnCompare
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max

data class CacheExportConfig(
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

data class CacheUiState(
    val groupId: Long = -1,
    val groupName: String? = null,
    val groupList: List<BookGroup> = emptyList(),
    val books: List<Book> = emptyList(),
    val isDownloadRunning: Boolean = false,
    val cacheVersion: Long = 0,
    val deleteBookOriginal: Boolean = LocalConfig.deleteBookOriginal,
    val exportConfig: CacheExportConfig = CacheExportConfig()
)

sealed interface CacheIntent {
    data class Initialize(val groupId: Long) : CacheIntent
    data class ChangeGroup(val groupId: Long) : CacheIntent
    data class StartDownloadForVisibleBooks(
        val books: List<Book>,
        val downloadAllChapters: Boolean
    ) : CacheIntent
    data object StopDownload : CacheIntent
    data class ToggleBookDownload(val book: Book) : CacheIntent
    data class DeleteBookDownload(val bookUrl: String) : CacheIntent
    data class ClearBookCache(val book: Book) : CacheIntent
    data class MoveBooksToGroup(val bookUrls: Set<String>, val groupId: Long) : CacheIntent
    data class DeleteBooks(val bookUrls: Set<String>, val deleteOriginal: Boolean) : CacheIntent
    data class ClearCachesForBooks(val bookUrls: Set<String>) : CacheIntent
    data class DownloadBooks(val bookUrls: Set<String>, val downloadAllChapters: Boolean) : CacheIntent
    data class SetExportUseReplace(val enabled: Boolean) : CacheIntent
    data class SetEnableCustomExport(val enabled: Boolean) : CacheIntent
    data class SetExportNoChapterName(val enabled: Boolean) : CacheIntent
    data class SetExportToWebDav(val enabled: Boolean) : CacheIntent
    data class SetExportPictureFile(val enabled: Boolean) : CacheIntent
    data class SetParallelExportBook(val enabled: Boolean) : CacheIntent
    data class SetExportType(val type: Int) : CacheIntent
    data class SetExportCharset(val charset: String) : CacheIntent
    data class SetBookExportFileName(val fileName: String?) : CacheIntent
    data class SetEpisodeExportFileName(val fileName: String) : CacheIntent
}

sealed interface CacheEffect {
    data class NotifyBookChanged(val bookUrl: String) : CacheEffect
    data class ShowMessage(val message: String) : CacheEffect
}

class CacheViewModel(
    application: Application,
    private val bookDao: BookDao,
    private val bookGroupDao: BookGroupDao,
    private val bookChapterDao: BookChapterDao,
    val cacheConfig: CacheConfig,
    private val batchCacheDownloadUseCase: BatchCacheDownloadUseCase,
    private val cacheBookChaptersUseCase: CacheBookChaptersUseCase,
    private val clearBookCacheUseCase: ClearBookCacheUseCase,
    private val deleteBooksUseCase: DeleteBooksUseCase,
    private val updateBooksGroupUseCase: UpdateBooksGroupUseCase
) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(CacheUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<CacheEffect>(extraBufferCapacity = 32)
    val effects = _effects.asSharedFlow()

    private val cacheChapters = hashMapOf<String, HashSet<String>>()
    private var booksJob: Job? = null
    private var groupsJob: Job? = null
    private var observersStarted = false

    fun dispatch(intent: CacheIntent) {
        when (intent) {
            is CacheIntent.Initialize -> initialize(intent.groupId)
            is CacheIntent.ChangeGroup -> changeGroup(intent.groupId)
            is CacheIntent.StartDownloadForVisibleBooks -> startDownloadForVisibleBooks(
                intent.books,
                intent.downloadAllChapters
            )

            CacheIntent.StopDownload -> CacheBook.stop(context)
            is CacheIntent.ToggleBookDownload -> toggleBookDownload(intent.book)
            is CacheIntent.DeleteBookDownload -> CacheBook.remove(context, intent.bookUrl)
            is CacheIntent.ClearBookCache -> clearCacheForBook(intent.book)
            is CacheIntent.MoveBooksToGroup -> moveBooksToGroup(intent.bookUrls, intent.groupId)
            is CacheIntent.DeleteBooks -> deleteBooks(intent.bookUrls, intent.deleteOriginal)
            is CacheIntent.ClearCachesForBooks -> clearCachesForBooks(intent.bookUrls)
            is CacheIntent.DownloadBooks -> downloadBooks(intent.bookUrls, intent.downloadAllChapters)
            is CacheIntent.SetExportUseReplace -> {
                cacheConfig.exportUseReplace = intent.enabled
                syncExportConfig()
                val msg = if (intent.enabled) "替换净化功能已开启" else "替换净化功能已关闭"
                _effects.tryEmit(CacheEffect.ShowMessage(msg))
            }

            is CacheIntent.SetEnableCustomExport -> {
                cacheConfig.enableCustomExport = intent.enabled
                syncExportConfig()
            }

            is CacheIntent.SetExportNoChapterName -> {
                cacheConfig.exportNoChapterName = intent.enabled
                syncExportConfig()
            }

            is CacheIntent.SetExportToWebDav -> {
                cacheConfig.exportToWebDav = intent.enabled
                syncExportConfig()
            }

            is CacheIntent.SetExportPictureFile -> {
                cacheConfig.exportPictureFile = intent.enabled
                syncExportConfig()
            }

            is CacheIntent.SetParallelExportBook -> {
                cacheConfig.parallelExportBook = intent.enabled
                syncExportConfig()
            }

            is CacheIntent.SetExportType -> {
                cacheConfig.exportType = intent.type
                syncExportConfig()
            }

            is CacheIntent.SetExportCharset -> {
                cacheConfig.exportCharset = intent.charset
                syncExportConfig()
            }

            is CacheIntent.SetBookExportFileName -> {
                cacheConfig.bookExportFileName = intent.fileName
                syncExportConfig()
            }

            is CacheIntent.SetEpisodeExportFileName -> {
                cacheConfig.episodeExportFileName = intent.fileName
                syncExportConfig()
            }
        }
    }

    fun getCacheChapters(bookUrl: String): Set<String>? = cacheChapters[bookUrl]

    fun getAllCacheChapters(): Map<String, Set<String>> = cacheChapters

    fun isBookDownloading(bookUrl: String): Boolean {
        return CacheBook.cacheBookMap[bookUrl]?.isStop() == false
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
                when (cacheConfig.getBookSortByGroupId(groupId)) {
                    1 -> booksDownload.sortedByDescending { it.latestChapterTime }
                    2 -> booksDownload.sortedWith { o1, o2 -> o1.name.cnCompare(o2.name) }
                    3 -> booksDownload.sortedBy { it.order }
                    4 -> booksDownload.sortedByDescending {
                        max(it.latestChapterTime, it.durChapterTime)
                    }

                    else -> booksDownload.sortedByDescending { it.durChapterTime }
                }
            }.collect { books ->
                _uiState.update { it.copy(books = books) }
                loadCacheFiles(books)
            }
        }
    }

    private fun observeDownloadAndExportChanges() {
        if (observersStarted) return
        observersStarted = true
        viewModelScope.launch {
            CacheBook.cacheSuccessFlow.collect { chapter ->
                onChapterCached(chapter)
            }
        }
        viewModelScope.launch {
            CacheBook.downloadingIndicesFlow.collect { (bookUrl, _) ->
                syncDownloadRunning()
                if (bookUrl.isNotEmpty()) {
                    emitBookChanged(bookUrl)
                }
            }
        }
        viewModelScope.launch {
            CacheBook.downloadErrorFlow.collect { (bookUrl, _) ->
                syncDownloadRunning()
                if (bookUrl.isNotEmpty()) {
                    emitBookChanged(bookUrl)
                }
            }
        }
        viewModelScope.launch {
            CacheBook.downloadSummaryFlow.collect {
                syncDownloadRunning()
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
                exportConfig = CacheExportConfig(
                    exportUseReplace = cacheConfig.exportUseReplace,
                    enableCustomExport = cacheConfig.enableCustomExport,
                    exportNoChapterName = cacheConfig.exportNoChapterName,
                    exportToWebDav = cacheConfig.exportToWebDav,
                    exportPictureFile = cacheConfig.exportPictureFile,
                    parallelExportBook = cacheConfig.parallelExportBook,
                    exportType = cacheConfig.exportType,
                    exportCharset = cacheConfig.exportCharset,
                    bookExportFileName = cacheConfig.bookExportFileName,
                    episodeExportFileName = cacheConfig.episodeExportFileName
                )
            )
        }
    }

    private fun syncDownloadRunning() {
        _uiState.update { it.copy(isDownloadRunning = CacheBook.isRun) }
    }

    private fun refreshGroupName(groupId: Long) {
        execute {
            val title = bookGroupDao.getByID(groupId)?.groupName
            title ?: context.getString(io.legado.app.R.string.no_group)
        }.onSuccess { groupName ->
            _uiState.update { it.copy(groupName = groupName) }
        }
    }

    private fun loadCacheFiles(books: List<Book>) {
        execute {
            books.forEach { book ->
                if (!book.isLocal && !cacheChapters.contains(book.bookUrl)) {
                    val chapterCaches = hashSetOf<String>()
                    val cacheNames = BookHelp.getChapterFiles(book)
                    if (cacheNames.isNotEmpty()) {
                        bookChapterDao.getChapterList(book.bookUrl).also {
                            book.totalChapterNum = it.size
                        }.forEach { chapter ->
                            if (cacheNames.contains(chapter.getFileName()) || chapter.isVolume) {
                                chapterCaches.add(chapter.url)
                            }
                        }
                    }
                    cacheChapters[book.bookUrl] = chapterCaches
                    emitBookChanged(book.bookUrl)
                }
                ensureActive()
            }
        }
    }

    private fun onChapterCached(chapter: BookChapter) {
        val bookUrl = chapter.bookUrl
        val chapterSet = cacheChapters.getOrPut(bookUrl) { hashSetOf() }
        chapterSet.add(chapter.url)
        emitBookChanged(bookUrl)
    }

    private fun startDownloadForVisibleBooks(books: List<Book>, downloadAllChapters: Boolean) {
        execute {
            batchCacheDownloadUseCase.execute(
                bookUrls = books.map { it.bookUrl }.toSet(),
                downloadAllChapters = downloadAllChapters,
                skipAudioBooks = true
            )
        }.onFinally {
            syncDownloadRunning()
        }
    }

    private fun toggleBookDownload(book: Book) {
        if (book.isLocal) return
        if (isBookDownloading(book.bookUrl)) {
            CacheBook.remove(context, book.bookUrl)
            syncDownloadRunning()
        } else {
            execute {
                cacheBookChaptersUseCase.execute(book.bookUrl, 0..book.lastChapterIndex)
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
            _effects.tryEmit(CacheEffect.ShowMessage("移动分组失败\n${it.localizedMessage}"))
        }
    }

    private fun deleteBooks(bookUrls: Set<String>, deleteOriginal: Boolean) {
        if (bookUrls.isEmpty()) return
        execute {
            LocalConfig.deleteBookOriginal = deleteOriginal
            deleteBooksUseCase.execute(bookUrls, deleteOriginal)
        }.onSuccess { deletedBookUrls ->
            _uiState.update { it.copy(deleteBookOriginal = deleteOriginal) }
            deletedBookUrls.forEach { cacheChapters.remove(it) }
            _effects.tryEmit(CacheEffect.ShowMessage("删除成功"))
        }.onError {
            _effects.tryEmit(CacheEffect.ShowMessage("删除失败\n${it.localizedMessage}"))
        }
    }

    private fun clearCachesForBooks(bookUrls: Set<String>) {
        if (bookUrls.isEmpty()) return
        execute {
            clearBookCacheUseCase.execute(bookUrls)
        }.onSuccess { clearedBookUrls ->
            clearedBookUrls.forEach { bookUrl ->
                cacheChapters[bookUrl] = hashSetOf()
                emitBookChanged(bookUrl)
            }
            _effects.tryEmit(CacheEffect.ShowMessage("缓存已清理"))
        }.onError {
            _effects.tryEmit(CacheEffect.ShowMessage("清理缓存失败\n${it.localizedMessage}"))
        }
    }

    private fun downloadBooks(bookUrls: Set<String>, downloadAllChapters: Boolean) {
        if (bookUrls.isEmpty()) return
        execute {
            batchCacheDownloadUseCase.execute(
                bookUrls = bookUrls,
                downloadAllChapters = downloadAllChapters,
                skipAudioBooks = true
            )
        }.onSuccess { count ->
            if (count > 0) {
                _effects.tryEmit(CacheEffect.ShowMessage("已加入缓存队列: $count 本"))
            } else {
                _effects.tryEmit(CacheEffect.ShowMessage("没有可缓存的书籍"))
            }
            syncDownloadRunning()
        }.onError {
            _effects.tryEmit(CacheEffect.ShowMessage("批量缓存失败\n${it.localizedMessage}"))
        }
    }

    private fun clearCacheForBook(book: Book) {
        execute {
            clearBookCacheUseCase.execute(book.bookUrl)
        }.onSuccess { bookUrl ->
            bookUrl ?: return@onSuccess
            cacheChapters[bookUrl] = hashSetOf()
            emitBookChanged(bookUrl)
        }
    }

    private fun emitBookChanged(bookUrl: String) {
        _uiState.update { it.copy(cacheVersion = it.cacheVersion + 1) }
        _effects.tryEmit(CacheEffect.NotifyBookChanged(bookUrl))
    }

}
