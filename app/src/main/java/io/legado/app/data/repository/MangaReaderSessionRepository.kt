package io.legado.app.data.repository

import android.app.Application
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.Book.ReadConfig
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookProgress
import io.legado.app.domain.gateway.BackupSettingsGateway
import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isLocalModified
import io.legado.app.help.book.removeType
import io.legado.app.help.book.simulatedTotalChapterNum
import io.legado.app.help.source.getSourceType
import io.legado.app.help.storage.Backup
import io.legado.app.model.ReadManga
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setChapter
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.manga.MangaContent
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.ImageSaveUtils
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isTrue
import io.legado.app.utils.mapParallelSafe
import io.legado.app.utils.postEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class MangaReaderLaunchRequest(
    val bookUrl: String?,
    val inBookshelf: Boolean,
    val chapterChanged: Boolean,
)

sealed interface MangaPaymentResult {
    data class OpenUrl(
        val url: String,
        val sourceOrigin: String?,
        val sourceName: String?,
        val sourceType: Int?,
    ) : MangaPaymentResult

    data object Refreshed : MangaPaymentResult
}

sealed interface MangaReaderSessionEvent {
    data class LoadFailed(val message: String) : MangaReaderSessionEvent
    data class ConfirmProgress(val progress: BookProgress) : MangaReaderSessionEvent
    data object Loading : MangaReaderSessionEvent
    data object LoadStarted : MangaReaderSessionEvent
    data class Message(val message: String) : MangaReaderSessionEvent
}

data class MangaReaderSessionState(
    val content: MangaContent,
    val bookName: String,
    val bookAuthor: String,
    val bookUrl: String,
    val chapterName: String,
    val chapterUrl: String?,
    val sourceName: String,
    val sourceUrl: String?,
    val sourceType: Int?,
    val chapterIndex: Int,
    val chapterCount: Int,
)

/** Data/session boundary around the legacy ReadManga engine. */
class MangaReaderSessionRepository(
    private val application: Application,
    private val database: AppDatabase,
    private val getReadingProgressUseCase: io.legado.app.domain.usecase.GetReadingProgressUseCase,
    private val imageLoader: ImageLoader,
    private val backupSettingsGateway: BackupSettingsGateway,
    private val downloadCacheSettingsGateway: DownloadCacheSettingsGateway,
    private val otherSettingsGateway: OtherSettingsGateway,
    private val readSettingsGateway: ReadSettingsGateway,
) {
    private val _events = MutableSharedFlow<MangaReaderSessionEvent>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()
    private val _sessionState = MutableStateFlow<MangaReaderSessionState?>(null)
    val sessionState = _sessionState.asStateFlow()

    private val engineCallback = object : ReadManga.Callback {
        override fun upContent() {
            publishSessionState()
        }

        override fun loadFail(msg: String) {
            _events.tryEmit(MangaReaderSessionEvent.LoadFailed(msg))
        }

        override fun sureNewProgress(progress: BookProgress) {
            _events.tryEmit(MangaReaderSessionEvent.ConfirmProgress(progress))
        }

        override fun showLoading() {
            _events.tryEmit(MangaReaderSessionEvent.Loading)
        }

        override fun startLoad() {
            _events.tryEmit(MangaReaderSessionEvent.LoadStarted)
        }
    }

    fun openSession() = ReadManga.register(engineCallback)

    fun closeSession() = ReadManga.unregister(engineCallback)

    fun currentSessionState(): MangaReaderSessionState = MangaReaderSessionState(
        content = ReadManga.mangaContents.copy(items = ReadManga.mangaContents.items.toList()),
        bookName = ReadManga.book?.name.orEmpty(),
        bookAuthor = ReadManga.book?.author.orEmpty(),
        bookUrl = ReadManga.book?.bookUrl.orEmpty(),
        chapterName = ReadManga.curMangaChapter?.chapter?.title
            ?: ReadManga.book?.durChapterTitle.orEmpty(),
        chapterUrl = ReadManga.curMangaChapter?.chapter?.url,
        sourceName = ReadManga.bookSource?.bookSourceName.orEmpty(),
        sourceUrl = ReadManga.bookSource?.bookSourceUrl,
        sourceType = ReadManga.bookSource?.getSourceType(),
        chapterIndex = ReadManga.durChapterIndex,
        chapterCount = ReadManga.simulatedChapterSize,
    )

    private fun publishSessionState() {
        _sessionState.value = currentSessionState()
    }

    fun currentBook(): Book? = ReadManga.book

    fun currentSourceOrigin(): String? = ReadManga.book?.origin

    fun currentChapterIndex(): Int = ReadManga.durChapterIndex

    fun inBookshelf(): Boolean = ReadManga.inBookshelf

    fun hasBook(): Boolean = ReadManga.book != null

    fun moveToPreviousChapter(toFirst: Boolean = false) = ReadManga.moveToPrevChapter(toFirst)

    fun moveToNextChapter(toFirst: Boolean = false) = ReadManga.moveToNextChapter(toFirst)

    fun reloadContent() = ReadManga.loadContent()

    fun reloadOrUpdateContent() = ReadManga.loadOrUpContent()

    fun applyProgress(progress: BookProgress) = ReadManga.setProgress(progress)

    fun setVisiblePage(chapterIndex: Int, pageIndex: Int) {
        if (ReadManga.durChapterIndex == chapterIndex) {
            ReadManga.durChapterPos = pageIndex
            ReadManga.curPageChanged()
        }
    }

    fun effectiveScrollMode(defaultValue: Int): Int =
        ReadManga.book?.readConfig?.mangaScrollMode ?: defaultValue

    fun effectiveSidePadding(defaultValue: Int): Int =
        ReadManga.book?.readConfig?.webtoonSidePaddingDp ?: defaultValue

    suspend fun initialize(request: MangaReaderLaunchRequest): BookProgress? = withContext(Dispatchers.IO) {
        ReadManga.inBookshelf = request.inBookshelf
        ReadManga.chapterChanged = request.chapterChanged
        val book = request.bookUrl?.takeIf(String::isNotEmpty)?.let(database.bookDao::getBook)
            ?: database.bookDao.lastReadBook
            ?: ReadManga.book
            ?: throw NoStackTraceException(application.getString(R.string.no_book))
        val progress = initializeBook(book)
        ReadManga.saveRead()
        progress
    }

    private suspend fun initializeBook(book: Book): BookProgress? {
        val sameBook = ReadManga.book?.bookUrl == book.bookUrl
        if (sameBook) ReadManga.upData(book) else ReadManga.resetData(book)

        if (!book.isLocal && book.tocUrl.isEmpty() && !loadBookInfo(book)) {
            throw NoStackTraceException(application.getString(R.string.manga_reader_details_failed))
        }
        if (book.isLocal && !localBookExists(book)) {
            throw NoStackTraceException(application.getString(R.string.no_book))
        }
        if ((ReadManga.chapterSize == 0 || book.isLocalModified()) && !loadChapterListAwait(book)) {
            throw NoStackTraceException(application.getString(R.string.error_load_toc))
        }

        if (sameBook) ReadManga.loadOrUpContent() else ReadManga.loadContent()

        val progress = when {
            ReadManga.chapterChanged -> {
                ReadManga.chapterChanged = false
                null
            }
            !sameBook && backupSettingsGateway.currentSettings.syncBookProgressPlus -> {
                ReadManga.syncProgress(
                    newProgressAction = { progress ->
                        ReadManga.mCallback?.sureNewProgress(progress)
                    }
                )
                null
            }
            !sameBook -> syncBookProgress(book)
            else -> null
        }

        if (!book.isLocal && ReadManga.bookSource == null &&
            readSettingsGateway.currentSettings.autoChangeSource
        ) {
            autoChangeSource(book.name, book.author)
        }
        return progress
    }

    suspend fun refreshBookSource() = withContext(Dispatchers.IO) {
        ReadManga.book?.let { book ->
            ReadManga.bookSource = database.bookSourceDao.getBookSource(book.origin)
        }
    }

    suspend fun disableSource() = withContext(Dispatchers.IO) {
        ReadManga.bookSource?.let { source ->
            source.enabled = false
            database.bookSourceDao.update(source)
        }
    }

    suspend fun loadChapterList(book: Book): Boolean = withContext(Dispatchers.IO) {
        loadChapterListAwait(book)
    }

    private suspend fun loadChapterListAwait(book: Book): Boolean {
        val source = ReadManga.bookSource ?: return true
        val oldBook = book.copy()
        return WebBook.getChapterListAwait(source, book, true).fold(
            onSuccess = { chapters ->
                if (oldBook.bookUrl == book.bookUrl) database.bookDao.update(book)
                else {
                    database.bookDao.replace(oldBook, book)
                    BookHelp.updateCacheFolder(oldBook, book)
                }
                database.bookChapterDao.delByBook(oldBook.bookUrl)
                database.bookChapterDao.insert(*chapters.toTypedArray())
                ReadManga.onChapterListUpdated(book)
                true
            },
            onFailure = {
                ReadManga.mCallback?.loadFail(application.getString(R.string.error_load_toc))
                false
            },
        )
    }

    private suspend fun loadBookInfo(book: Book): Boolean {
        val source = ReadManga.bookSource ?: return true
        return runCatching {
            WebBook.getBookInfoAwait(source, book, canReName = false)
        }.fold(
            onSuccess = { true },
            onFailure = {
                ReadManga.mCallback?.loadFail(
                    application.getString(R.string.manga_reader_details_error, it.localizedMessage)
                )
                false
            },
        )
    }

    private suspend fun autoChangeSource(name: String, author: String) {
        val sources = database.bookSourceDao.allTextEnabledPart
        flow {
            sources.forEach { source -> source.getBookSource()?.let { emit(it) } }
        }.mapParallelSafe(
            downloadCacheSettingsGateway.currentSettings.threadCount
        ) { source ->
            val book = WebBook.preciseSearchAwait(source, name, author).getOrThrow()
            if (book.tocUrl.isEmpty()) WebBook.getBookInfoAwait(source, book)
            val toc = WebBook.getChapterListAwait(source, book).getOrThrow()
            val chapter = toc.getOrElse(book.durChapterIndex) { toc.last() }
            val nextChapter = toc.getOrElse(chapter.index) { toc.first() }
            WebBook.getContentAwait(source, book, chapter, nextChapter.url)
            book to toc
        }.take(1).onEmpty {
            throw NoStackTraceException(application.getString(R.string.manga_reader_no_source))
        }.catch {
            AppLog.put("Automatic source change failed\n${it.localizedMessage}", it)
            _events.tryEmit(
                MangaReaderSessionEvent.Message(
                    it.localizedMessage ?: application.getString(R.string.manga_reader_auto_source_failed)
                )
            )
        }.collect { (book, toc) -> changeSource(book, toc) }
    }

    private suspend fun syncBookProgress(book: Book): BookProgress? {
        if (!backupSettingsGateway.currentSettings.syncBookProgress) return null
        val progress = getReadingProgressUseCase.execute(book.name, book.author)?.let {
            BookProgress(
                name = it.name,
                author = it.author,
                durChapterIndex = it.durChapterIndex,
                durChapterPos = it.durChapterPos,
                durChapterTime = it.durChapterTime,
                durChapterTitle = it.durChapterTitle,
            )
        } ?: return null
        return if (progress.durChapterIndex < book.durChapterIndex ||
            progress.durChapterIndex == book.durChapterIndex && progress.durChapterPos < book.durChapterPos
        ) {
            progress
        } else {
            if (progress.durChapterIndex < book.simulatedTotalChapterNum()) ReadManga.setProgress(progress)
            null
        }
    }

    suspend fun changeSource(book: Book, toc: List<BookChapter>) = withContext(Dispatchers.IO) {
        ReadManga.book?.migrateTo(
            newBook = book,
            toc = toc,
            defaultReplaceEnabled = otherSettingsGateway.currentSettings.replaceEnableDefault,
            chineseConverterType = readSettingsGateway.currentSettings.chineseConverterType,
        )
        book.removeType(BookType.updateError)
        ReadManga.book?.delete()
        database.bookDao.insert(book)
        database.bookChapterDao.insert(*toc.toTypedArray())
        ReadManga.resetData(book)
        ReadManga.loadContent()
        postEvent(EventBus.SOURCE_CHANGED, book.bookUrl)
    }

    suspend fun openChapter(index: Int, pageIndex: Int = 0) = withContext(Dispatchers.IO) {
        val currentBook = ReadManga.book ?: return@withContext
        database.bookDao.getBook(currentBook.bookUrl)?.let(ReadManga::upData)
        if (index !in 0 until ReadManga.simulatedChapterSize) return@withContext
        ReadManga.durChapterIndex = index
        ReadManga.durChapterPos = pageIndex.coerceAtLeast(0)
        ReadManga.clearMangaChapter()
        ReadManga.showLoading()
        ReadManga.loadContent()
        ReadManga.saveRead(pageChanged = true)
    }

    suspend fun removeCurrentTemporaryBook() = withContext(Dispatchers.IO) {
        val book = ReadManga.book
        val url = book?.bookUrl
        book?.delete()
        if (ReadManga.book?.bookUrl == url) ReadManga.book = null
    }

    suspend fun addCurrentBookToShelf() = withContext(Dispatchers.IO) {
        val book = ReadManga.book ?: throw NoStackTraceException(application.getString(R.string.no_book))
        persistOnShelf(book, database.bookChapterDao.getChapterList(book.bookUrl))
        ReadManga.inBookshelf = true
    }

    suspend fun addToShelf(book: Book, toc: List<BookChapter>) = withContext(Dispatchers.IO) {
        persistOnShelf(book, toc)
    }

    private suspend fun persistOnShelf(book: Book, toc: List<BookChapter>) {
        book.removeType(BookType.notShelf)
        if (book.order == 0) book.order = database.bookDao.minOrder - 1
        database.bookDao.insert(book)
        database.bookChapterDao.insert(*toc.toTypedArray())
    }

    suspend fun updateReadConfig(update: ReadConfig.() -> Unit) = withContext(Dispatchers.IO) {
        val book = ReadManga.book ?: return@withContext
        book.readConfig = (book.readConfig ?: ReadConfig()).apply(update)
        database.bookDao.update(book)
    }

    suspend fun refreshCurrentChapter() = withContext(Dispatchers.IO) {
        val book = ReadManga.book ?: return@withContext
        database.bookChapterDao.getChapter(book.bookUrl, ReadManga.durChapterIndex)?.let { chapter ->
            BookHelp.delContent(book, chapter)
            openChapter(ReadManga.durChapterIndex, ReadManga.durChapterPos)
        }
    }

    suspend fun saveImage(url: String, folderName: String = "Legado"): Boolean =
        withContext(Dispatchers.IO) {
            val result = imageLoader.execute(
                ImageRequest.Builder(application).data(url).allowHardware(false).build()
            )
            val bitmap = requireNotNull(result.image).toBitmap()
            val bytes = ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)
                output.toByteArray()
            }
            ImageSaveUtils.saveImageToGallery(application, bytes, folderName = folderName)
        }

    suspend fun payCurrentChapter(): MangaPaymentResult = withContext(Dispatchers.IO) {
        val book = ReadManga.book ?: throw NoStackTraceException(application.getString(R.string.no_book))
        if (book.isLocal) throw NoStackTraceException("local book")
        val chapter = database.bookChapterDao.getChapter(book.bookUrl, ReadManga.durChapterIndex)
            ?: throw NoStackTraceException("no chapter")
        val source = ReadManga.bookSource ?: throw NoStackTraceException("no book source")
        val payAction = source.getContentRule().payAction
        if (payAction.isNullOrBlank()) throw NoStackTraceException("no pay action")
        val analyzeRule = AnalyzeRule(book, source).apply {
            setCoroutineContext(currentCoroutineContext())
            setBaseUrl(chapter.url)
            setChapter(chapter)
        }
        val result = analyzeRule.evalJS(payAction).toString()
        when {
            result.isAbsUrl() -> MangaPaymentResult.OpenUrl(
                result,
                source.bookSourceUrl,
                source.bookSourceName,
                source.getSourceType(),
            )
            result.isTrue() -> {
                ReadManga.curMangaChapter = null
                BookHelp.delContent(book, chapter)
                loadChapterListAwait(book)
                MangaPaymentResult.Refreshed
            }
            else -> throw NoStackTraceException("pay action returned $result")
        }
    }

    fun resumeSession() {
        ReadManga.initReadTime()
        ReadManga.startAutoSaveSession()
    }

    fun syncProgressOnNetworkAvailable() {
        if (backupSettingsGateway.currentSettings.syncBookProgressPlus) ReadManga.syncProgress()
    }

    fun pauseSession() {
        val shouldPersist = ReadManga.inBookshelf
        if (shouldPersist) {
            ReadManga.saveRead()
        }
        ReadManga.stopAutoSaveSession()
        ReadManga.commitReadSession()
        ReadManga.cancelPreDownloadTask()
        if (shouldPersist && !io.legado.app.BuildConfig.DEBUG) {
            if (backupSettingsGateway.currentSettings.syncBookProgressPlus) ReadManga.syncProgress()
            else ReadManga.uploadProgress()
            Backup.autoBack(application)
        }
    }

    private fun localBookExists(book: Book): Boolean = runCatching {
        LocalBook.getBookInputStream(book)
    }.isSuccess

}
