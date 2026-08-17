package io.legado.app.data.repository.manga

import android.app.Application
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.readRecord.ReadRecordSession
import io.legado.app.data.repository.ReadRecordRepository
import io.legado.app.domain.gateway.BackupSettingsGateway
import io.legado.app.domain.gateway.BookCacheDownloadGateway
import io.legado.app.domain.gateway.MangaReaderDataGateway
import io.legado.app.domain.model.manga.MangaBookPresentation
import io.legado.app.domain.model.manga.MangaBookState
import io.legado.app.domain.model.manga.MangaChapterContent
import io.legado.app.domain.model.manga.MangaProgressState
import io.legado.app.domain.model.manga.OpenedMangaBook
import io.legado.app.domain.usecase.GetReadingProgressUseCase
import io.legado.app.domain.usecase.UploadReadingProgressUseCase
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isLocalModified
import io.legado.app.help.book.readSimulating
import io.legado.app.help.book.simulatedTotalChapterNum
import io.legado.app.help.source.getSourceType
import io.legado.app.help.storage.Backup
import io.legado.app.model.cache.CacheDownloadRequest
import io.legado.app.model.cache.CacheDownloadSource
import io.legado.app.model.cache.ChapterSelection
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.webBook.WebBook
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class MangaReaderDataRepository(
    private val application: Application,
    private val database: AppDatabase,
    private val getReadingProgressUseCase: GetReadingProgressUseCase,
    private val uploadReadingProgressUseCase: UploadReadingProgressUseCase,
    private val backupSettingsGateway: BackupSettingsGateway,
    private val readRecordRepository: ReadRecordRepository,
    private val bookCacheDownloadGateway: BookCacheDownloadGateway,
) : MangaReaderDataGateway {

    private var readingStartedAt: Long? = null
    private val chapterPageLoader = MangaChapterPageLoader(application, database)
    private val localMangaLoader =
        LocalMangaLoader(java.io.File(application.cacheDir, "local-manga"))

    override fun observeBookPresentation(bookUrl: String) =
        database.bookDao.flowGetBook(bookUrl)
            .map { book ->
                MangaBookPresentation(
                    scrollMode = book?.readConfig?.mangaScrollMode,
                    sidePaddingDp = book?.readConfig?.webtoonSidePaddingDp,
                )
            }
            .distinctUntilChanged()

    override suspend fun openBook(
        bookUrl: String?,
        inBookshelf: Boolean,
        chapterChanged: Boolean,
    ): OpenedMangaBook {
        val book = bookUrl?.takeIf(String::isNotEmpty)?.let(database.bookDao::getBook)
            ?: database.bookDao.lastReadBook
            ?: throw NoStackTraceException(application.getString(R.string.no_book))
        if (book.isLocal && !localBookExists(book)) {
            throw NoStackTraceException(application.getString(R.string.no_book))
        }
        val source = database.bookSourceDao.getBookSource(book.origin)
        if (!book.isLocal && book.tocUrl.isEmpty()) {
            val activeSource = source ?: throw NoStackTraceException(
                application.getString(R.string.manga_reader_details_failed),
            )
            val oldBook = book.copy()
            WebBook.getBookInfoAwait(activeSource, book, canReName = false)
            if (oldBook.bookUrl == book.bookUrl) database.bookDao.update(book)
            else {
                database.bookDao.replace(oldBook, book)
                BookHelp.updateCacheFolder(oldBook, book)
            }
        }
        var chapterCount = database.bookChapterDao.getChapterCount(book.bookUrl)
        if (chapterCount == 0 || book.isLocalModified()) {
            if (book.isLocal) {
                val chapters = if (localMangaLoader.supports(book)) {
                    localMangaLoader.chapters(book).also {
                        book.totalChapterNum = it.size
                        book.latestChapterTitle = it.lastOrNull()?.title.orEmpty()
                    }
                } else LocalBook.getChapterList(book)
                database.bookChapterDao.delByBook(book.bookUrl)
                database.bookChapterDao.insert(*chapters.toTypedArray())
                database.bookDao.update(book)
                chapterCount = chapters.size
            } else {
                val activeSource = source ?: throw NoStackTraceException(
                    application.getString(R.string.error_load_toc),
                )
                val oldBook = book.copy()
                val chapters = WebBook.getChapterListAwait(activeSource, book, true).getOrThrow()
                if (oldBook.bookUrl == book.bookUrl) {
                    database.bookDao.update(book)
                } else {
                    database.bookDao.replace(oldBook, book)
                    BookHelp.updateCacheFolder(oldBook, book)
                }
                database.bookChapterDao.delByBook(oldBook.bookUrl)
                database.bookChapterDao.insert(*chapters.toTypedArray())
                chapterCount = chapters.size
            }
        }
        if (chapterCount == 0) chapterCount = database.bookChapterDao.getChapterCount(book.bookUrl)
        val simulatedCount = if (book.readSimulating()) {
            book.simulatedTotalChapterNum()
        } else chapterCount
        val safeChapter = book.durChapterIndex.coerceIn(0, (simulatedCount - 1).coerceAtLeast(0))
        val newerProgress = if (!chapterChanged) findNewerProgress(book) else null
        return OpenedMangaBook(
            book = MangaBookState(
                bookUrl = book.bookUrl,
                name = book.name,
                author = book.author,
                coverUrl = book.coverUrl,
                customCoverUrl = book.customCoverUrl,
                sourceOrigin = book.origin,
                sourceName = source?.bookSourceName.orEmpty(),
                sourceType = source?.getSourceType(),
                inBookshelf = inBookshelf,
                scrollMode = book.readConfig?.mangaScrollMode,
                sidePaddingDp = book.readConfig?.webtoonSidePaddingDp,
                isLocal = book.isLocal,
                chapterTitles = database.bookChapterDao.getChapterList(book.bookUrl).map { it.title },
            ),
            chapterIndex = safeChapter,
            pageIndex = book.durChapterPos.coerceAtLeast(0),
            chapterCount = simulatedCount,
            newerProgress = newerProgress,
        )
    }

    override suspend fun loadChapter(bookUrl: String, chapterIndex: Int): MangaChapterContent {
        val book = database.bookDao.getBook(bookUrl)
            ?: throw NoStackTraceException(application.getString(R.string.no_book))
        val chapter = database.bookChapterDao.getChapter(bookUrl, chapterIndex)
            ?: throw NoStackTraceException(application.getString(R.string.error_load_toc))
        return if (book.isLocal && localMangaLoader.supports(book)) {
            localMangaLoader.load(book, chapter)
        } else chapterPageLoader.load(book, chapter)
    }

    override suspend fun prefetchChapter(bookUrl: String, chapterIndex: Int) {
        val book = database.bookDao.getBook(bookUrl) ?: return
        if (book.isLocal) return
        bookCacheDownloadGateway.start(
            CacheDownloadRequest(
                bookUrl = bookUrl,
                selection = ChapterSelection.Single(chapterIndex),
                source = CacheDownloadSource.ReadPreload,
            )
        )
    }

    override suspend fun retainChapterResources(bookUrl: String, chapterIndexes: Set<Int>) {
        chapterPageLoader.retain(bookUrl, chapterIndexes)
    }

    override suspend fun releaseAllChapterResources() {
        chapterPageLoader.close()
        localMangaLoader.close()
    }

    override suspend fun persistProgress(bookUrl: String, chapterIndex: Int, pageIndex: Int) {
        val book = database.bookDao.getBook(bookUrl) ?: return
        book.durChapterIndex = chapterIndex
        book.durChapterPos = pageIndex
        book.durChapterTime = System.currentTimeMillis()
        database.bookChapterDao.getChapter(bookUrl, chapterIndex)?.let {
            book.durChapterTitle = it.title
        }
        database.bookDao.update(book)
    }

    override suspend fun applyProgress(bookUrl: String, progress: MangaProgressState) {
        persistProgress(bookUrl, progress.chapterIndex, progress.pageIndex)
    }

    override suspend fun resume(bookUrl: String) {
        if (readingStartedAt == null) readingStartedAt = System.currentTimeMillis()
    }

    override suspend fun pause(bookUrl: String, inBookshelf: Boolean) {
        val start = readingStartedAt ?: return
        readingStartedAt = null
        val end = System.currentTimeMillis()
        val book = database.bookDao.getBook(bookUrl) ?: return
        if (end - start >= 10_000) {
            readRecordRepository.saveReadSession(
                ReadRecordSession(
                    bookName = book.name,
                    bookAuthor = book.author,
                    startTime = start,
                    endTime = end,
                )
            )
        }
        if (backupSettingsGateway.currentSettings.syncBookProgressPlus) {
            uploadReadingProgressUseCase.execute(book.toProgressState())
        }
        if (inBookshelf && !BuildConfig.DEBUG) Backup.autoBack(application)
    }

    override suspend fun syncProgress(bookUrl: String): MangaProgressState? {
        if (!backupSettingsGateway.currentSettings.syncBookProgressPlus) return null
        val book = database.bookDao.getBook(bookUrl) ?: return null
        return syncOrUpload(book)
    }

    private suspend fun findNewerProgress(book: Book): MangaProgressState? {
        if (!backupSettingsGateway.currentSettings.syncBookProgress) return null
        return syncOrUpload(book)
    }

    private suspend fun syncOrUpload(book: Book): MangaProgressState? {
        val newer = compareProgress(
            book,
            getReadingProgressUseCase.execute(book.name, book.author),
        )
        if (newer == null) uploadReadingProgressUseCase.execute(book.toProgressState())
        return newer
    }

    private fun compareProgress(
        book: Book,
        progress: io.legado.app.domain.model.ReadingProgress?,
    ): MangaProgressState? {
        progress ?: return null
        val remoteIsNewer = progress.durChapterIndex > book.durChapterIndex ||
            progress.durChapterIndex == book.durChapterIndex &&
            progress.durChapterPos > book.durChapterPos
        return progress.takeIf { remoteIsNewer }?.let {
            MangaProgressState(
                bookName = it.name,
                bookAuthor = it.author,
                chapterIndex = it.durChapterIndex,
                pageIndex = it.durChapterPos,
                chapterTitle = it.durChapterTitle,
                updatedAt = it.durChapterTime,
            )
        }
    }

    private fun Book.toProgressState() = io.legado.app.domain.model.ReadingProgress(
        name = name,
        author = author,
        durChapterIndex = durChapterIndex,
        durChapterPos = durChapterPos,
        durChapterTime = durChapterTime,
        durChapterTitle = durChapterTitle,
    )

    private fun localBookExists(book: Book): Boolean = runCatching {
        LocalBook.getBookInputStream(book)
    }.isSuccess
}
