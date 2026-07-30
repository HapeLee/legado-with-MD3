package io.legado.app.domain.usecase

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.gateway.BookSearchGateway
import io.legado.app.domain.gateway.ChangeSourceSettingsGateway
import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import io.legado.app.domain.model.settings.ChangeSourceSettings
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.releaseHtmlData
import io.legado.app.help.source.SourceHelp
import io.legado.app.model.webBook.WebBook
import io.legado.app.ui.book.changesource.ObservableSourceConfig
import io.legado.app.utils.internString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeout

sealed interface ChangeSourceSearchEvent {
    data class Started(val totalSources: Int) : ChangeSourceSearchEvent
    data class Progress(
        val processedSources: Int,
        val totalSources: Int,
        val resultCount: Int,
        val sourceName: String,
    ) : ChangeSourceSearchEvent

    data class Result(
        val searchBook: SearchBook,
        val book: Book,
        val toc: List<BookChapter>?,
    ) : ChangeSourceSearchEvent

    data class Finished(val isEmpty: Boolean) : ChangeSourceSearchEvent
}

class ChangeSourceSearchUseCase(
    private val gateway: BookSearchGateway,
    private val changeSourceSettingsGateway: ChangeSourceSettingsGateway,
    private val downloadCacheSettingsGateway: DownloadCacheSettingsGateway,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun search(
        name: String,
        author: String,
        scope: io.legado.app.ui.book.search.SearchScope,
        oldBook: Book,
        fromReadBookActivity: Boolean,
    ): Flow<ChangeSourceSearchEvent> = flow {
        val contentProcessor = ContentProcessor.get(oldBook)
        val settings = changeSourceSettingsGateway.currentSettings
        val bookSourceParts = scope.getBookSourceParts()
        if (bookSourceParts.isEmpty()) {
            throw io.legado.app.exception.NoStackTraceException("启用书源为空")
        }

        val totalSources = bookSourceParts.size
        emit(ChangeSourceSearchEvent.Started(totalSources))

        var processedSources = 0
        var resultCount = 0
        val concurrency = downloadCacheSettingsGateway.currentSettings.threadCount.coerceAtLeast(1)

        bookSourceParts.asFlow()
            .mapNotNull { it.getBookSource() }
            .flatMapMerge(concurrency) { source ->
                flow {
                    val books = try {
                        withTimeout(60000L) {
                            searchSource(
                                source, name, author, oldBook, fromReadBookActivity,
                                contentProcessor,
                                settings,
                            )
                        }
                    } catch (_: Throwable) {
                        currentCoroutineContext().ensureActive()
                        emptyList()
                    }
                    emit(ChangeSourceResult(source, books))
                }.flowOn(Dispatchers.IO)
            }
            .collect { result ->
                currentCoroutineContext().ensureActive()
                result.books.forEach { loadedBook ->
                    resultCount++
                    emit(
                        ChangeSourceSearchEvent.Result(
                            searchBook = loadedBook.searchBook,
                            book = loadedBook.book,
                            toc = loadedBook.toc,
                        )
                    )
                }
                processedSources++
                emit(
                    ChangeSourceSearchEvent.Progress(
                        processedSources = processedSources,
                        totalSources = totalSources,
                        resultCount = resultCount,
                        sourceName = result.source.bookSourceName,
                    )
                )
            }

        emit(ChangeSourceSearchEvent.Finished(isEmpty = resultCount == 0))
    }.flowOn(Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun refresh(
        books: List<SearchBook>,
        oldBook: Book,
        fromReadBookActivity: Boolean,
    ): Flow<ChangeSourceSearchEvent> = flow {
        val contentProcessor = ContentProcessor.get(oldBook)
        val settings = changeSourceSettingsGateway.currentSettings
        val totalBooks = books.size
        val concurrency = downloadCacheSettingsGateway.currentSettings.threadCount.coerceAtLeast(1)
        var processedBooks = 0
        var resultCount = 0

        emit(ChangeSourceSearchEvent.Started(totalBooks))

        books.asFlow()
            .flatMapMerge(concurrency) { searchBook ->
                flow {
                    val loadedBook = try {
                        gateway.getBookSource(searchBook.origin)?.let { source ->
                            withTimeout(60000L) {
                                loadBookInfo(
                                    source = source,
                                    book = searchBook.toBook(),
                                    loadToc = settings.loadToc,
                                    loadWordCount = settings.loadWordCount,
                                    oldBook = oldBook,
                                    fromReadBookActivity = fromReadBookActivity,
                                    contentProcessor = contentProcessor,
                                )
                            }
                        }
                    } catch (_: Throwable) {
                        currentCoroutineContext().ensureActive()
                        null
                    }
                    emit(ChangeSourceRefreshResult(searchBook.originName, loadedBook))
                }.flowOn(Dispatchers.IO)
            }
            .collect { result ->
                currentCoroutineContext().ensureActive()
                result.loadedBook?.let { loadedBook ->
                    resultCount++
                    emit(
                        ChangeSourceSearchEvent.Result(
                            searchBook = loadedBook.searchBook,
                            book = loadedBook.book,
                            toc = loadedBook.toc,
                        )
                    )
                }
                processedBooks++
                emit(
                    ChangeSourceSearchEvent.Progress(
                        processedSources = processedBooks,
                        totalSources = totalBooks,
                        resultCount = resultCount,
                        sourceName = result.sourceName,
                    )
                )
            }

        emit(ChangeSourceSearchEvent.Finished(isEmpty = resultCount == 0))
    }.flowOn(Dispatchers.IO)

    private data class ChangeSourceResult(
        val source: BookSource,
        val books: List<LoadedSearchBook>,
    )

    private data class ChangeSourceRefreshResult(
        val sourceName: String,
        val loadedBook: LoadedSearchBook?,
    )

    private data class LoadedSearchBook(
        val searchBook: SearchBook,
        val book: Book,
        val toc: List<BookChapter>? = null,
    )

    private suspend fun searchSource(
        source: BookSource,
        name: String,
        author: String,
        oldBook: Book,
        fromReadBookActivity: Boolean,
        contentProcessor: ContentProcessor,
        settings: ChangeSourceSettings,
    ): List<LoadedSearchBook> {
        val checkAuthor = settings.checkAuthor
        val loadInfo = settings.loadInfo
        val loadToc = settings.loadToc
        val loadWordCount = settings.loadWordCount

        val resultBooks = WebBook.searchBookAwait(
            source, name,
            filter = { fName, fAuthor, _ ->
                fName == name && (!checkAuthor || fAuthor.contains(author))
            }
        )

        val processedBooks = mutableListOf<LoadedSearchBook>()
        for (searchBook in resultBooks) {
            currentCoroutineContext().ensureActive()
            when {
                loadInfo || loadToc || loadWordCount -> {
                    val book = searchBook.toBook()
                    processedBooks.add(
                        loadBookInfo(
                            source,
                            book,
                            loadToc,
                            loadWordCount,
                            oldBook,
                            fromReadBookActivity,
                            contentProcessor
                        )
                    )
                }

                else -> {
                    processedBooks.add(
                        LoadedSearchBook(
                            searchBook = searchBook,
                            book = searchBook.toBook(),
                        )
                    )
                }
            }
        }
        return processedBooks
    }

    private suspend fun loadBookInfo(
        source: BookSource,
        book: Book,
        loadToc: Boolean,
        loadWordCount: Boolean,
        oldBook: Book,
        fromReadBookActivity: Boolean,
        contentProcessor: ContentProcessor,
    ): LoadedSearchBook {
        if (book.tocUrl.isEmpty()) {
            WebBook.getBookInfoAwait(source, book)
        }
        if (loadToc || loadWordCount) {
            return loadBookToc(
                source,
                book,
                loadWordCount,
                oldBook,
                fromReadBookActivity,
                contentProcessor
            )
        }
        return LoadedSearchBook(
            searchBook = book.toSearchBook(),
            book = book,
        )
    }

    private suspend fun loadBookToc(
        source: BookSource,
        book: Book,
        loadWordCount: Boolean,
        oldBook: Book,
        fromReadBookActivity: Boolean,
        contentProcessor: ContentProcessor,
    ): LoadedSearchBook {
        val chapters = WebBook.getChapterListAwait(source, book).getOrThrow()
        for (chapter in chapters) {
            chapter.internString()
        }
        book.releaseHtmlData()
        val searchBook = if (loadWordCount) {
            loadBookWordCount(
                source,
                book,
                chapters,
                oldBook,
                fromReadBookActivity,
                contentProcessor
            )
        } else {
            book.toSearchBook()
        }
        return LoadedSearchBook(
            searchBook = searchBook,
            book = book,
            toc = chapters,
        )
    }

    private suspend fun loadBookWordCount(
        source: BookSource,
        book: Book,
        chapters: List<BookChapter>,
        oldBook: Book,
        fromReadBookActivity: Boolean,
        contentProcessor: ContentProcessor,
    ): SearchBook {
        if (chapters.isEmpty()) return book.toSearchBook()
        val chapterIndex = if (fromReadBookActivity) {
            BookHelp.getDurChapter(oldBook, chapters)
        } else {
            chapters.lastIndex
        }
        if (chapterIndex !in chapters.indices) return book.toSearchBook()
        val bookChapter = chapters[chapterIndex]
        var title = bookChapter.title.trim()
        if (title.length > 20) {
            title = title.substring(0, 20) + "…"
        }
        val startTime = System.currentTimeMillis()
        return try {
            val nextChapterUrl = chapters.getOrNull(chapterIndex + 1)?.url
            var content = WebBook.getContentAwait(source, book, bookChapter, nextChapterUrl, false)
            content = contentProcessor.getContent(oldBook, bookChapter, content, false).toString()
            val len = content.length
            val endTime = System.currentTimeMillis()
            book.toSearchBook().apply {
                chapterWordCountText = "[${chapterIndex + 1}] ${title}\n字数：${len}"
                chapterWordCount = len
                respondTime = (endTime - startTime).toInt()
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            val endTime = System.currentTimeMillis()
            book.toSearchBook().apply {
                chapterWordCountText =
                    "[${chapterIndex + 1}] ${title}\n获取字数失败：${t.localizedMessage}"
                chapterWordCount = -1
                respondTime = (endTime - startTime).toInt()
            }
        }
    }

    // Source management
    fun topSource(searchBook: SearchBook) {
        ObservableSourceConfig.setBookScore(searchBook, 1)
    }

    fun bottomSource(searchBook: SearchBook) {
        ObservableSourceConfig.setBookScore(searchBook, 0)
    }

    fun disableSource(searchBook: SearchBook) {
        io.legado.app.data.appDb.bookSourceDao.getBookSource(searchBook.origin)?.let { source ->
            source.enabled = false
            io.legado.app.data.appDb.bookSourceDao.update(source)
        }
    }

    fun deleteSource(searchBook: SearchBook) {
        SourceHelp.deleteBookSource(searchBook.origin)
        io.legado.app.data.appDb.searchBookDao.delete(searchBook)
    }
}
