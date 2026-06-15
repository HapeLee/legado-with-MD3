package io.legado.app.domain.usecase

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.gateway.BookSearchGateway
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.primaryStr
import io.legado.app.help.book.releaseHtmlData
import io.legado.app.ui.book.changesource.ChangeSourceConfig
import io.legado.app.help.source.SourceHelp
import io.legado.app.model.webBook.WebBook
import io.legado.app.ui.book.changesource.ObservableSourceConfig
import io.legado.app.ui.config.otherConfig.OtherConfig
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

sealed interface ChangeSourceSearchEvent {
    data class Started(val totalSources: Int) : ChangeSourceSearchEvent
    data class Progress(
        val processedSources: Int,
        val totalSources: Int,
        val resultCount: Int,
        val sourceName: String,
    ) : ChangeSourceSearchEvent

    data class Result(val searchBook: SearchBook) : ChangeSourceSearchEvent
    data class Finished(val isEmpty: Boolean) : ChangeSourceSearchEvent
}

class ChangeSourceSearchUseCase(
    private val gateway: BookSearchGateway,
) {
    private val threadCount = OtherConfig.threadCount
    private val contentProcessor by lazy {
        // ContentProcessor needs the old book - will be set before search
        null as ContentProcessor?
    }

    // Shared state for TOC cache
    private val tocMap = ConcurrentHashMap<String, List<BookChapter>>()
    private val bookMap = ConcurrentHashMap<String, Book>()
    private val tocMapChapterCount = AtomicInteger(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun search(
        name: String,
        author: String,
        scope: io.legado.app.ui.book.search.SearchScope,
        oldBook: Book,
        fromReadBookActivity: Boolean,
    ): Flow<ChangeSourceSearchEvent> = flow {
        val contentProcessor = ContentProcessor.get(oldBook)
        val bookSourceParts = scope.getBookSourceParts()
        if (bookSourceParts.isEmpty()) {
            throw io.legado.app.exception.NoStackTraceException("启用书源为空")
        }

        tocMap.clear()
        bookMap.clear()
        tocMapChapterCount.set(0)

        val totalSources = bookSourceParts.size
        emit(ChangeSourceSearchEvent.Started(totalSources))

        var processedSources = 0
        var resultCount = 0
        val concurrency = threadCount.coerceAtLeast(1)

        bookSourceParts.asFlow()
            .mapNotNull { it.getBookSource() }
            .flatMapMerge(concurrency) { source ->
                flow {
                    val books = try {
                        withTimeout(60000L) {
                            searchSource(
                                source, name, author, oldBook, fromReadBookActivity,
                                contentProcessor
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
                result.books.forEach { searchBook ->
                    resultCount++
                    emit(ChangeSourceSearchEvent.Result(searchBook))
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

    private data class ChangeSourceResult(
        val source: BookSource,
        val books: List<SearchBook>,
    )

    private suspend fun searchSource(
        source: BookSource,
        name: String,
        author: String,
        oldBook: Book,
        fromReadBookActivity: Boolean,
        contentProcessor: ContentProcessor,
    ): List<SearchBook> {
        val checkAuthor = ChangeSourceConfig.checkAuthor
        val loadInfo = ChangeSourceConfig.loadInfo
        val loadToc = ChangeSourceConfig.loadToc
        val loadWordCount = ChangeSourceConfig.loadWordCount

        val resultBooks = WebBook.searchBookAwait(
            source, name,
            filter = { fName, fAuthor, _ ->
                fName == name && (!checkAuthor || fAuthor.contains(author))
            }
        )

        val processedBooks = mutableListOf<SearchBook>()
        for (searchBook in resultBooks) {
            currentCoroutineContext().ensureActive()
            when {
                loadInfo || loadToc || loadWordCount -> {
                    val book = searchBook.toBook()
                    val wordCountSearchBook = loadBookInfo(
                        source,
                        book,
                        loadToc,
                        loadWordCount,
                        oldBook,
                        fromReadBookActivity,
                        contentProcessor
                    )
                    processedBooks.add(wordCountSearchBook ?: book.toSearchBook())
                }

                else -> {
                    processedBooks.add(searchBook)
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
    ): SearchBook? {
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
        return null
    }

    private suspend fun loadBookToc(
        source: BookSource,
        book: Book,
        loadWordCount: Boolean,
        oldBook: Book,
        fromReadBookActivity: Boolean,
        contentProcessor: ContentProcessor,
    ): SearchBook? {
        val chapters = WebBook.getChapterListAwait(source, book).getOrThrow()
        for (chapter in chapters) {
            chapter.internString()
        }
        if (tocMapChapterCount.get() < 30000) {
            tocMapChapterCount.addAndGet(chapters.size)
            tocMap[book.primaryStr()] = chapters
        }
        bookMap[book.primaryStr()] = book
        book.releaseHtmlData()
        if (loadWordCount) {
            return loadBookWordCount(
                source,
                book,
                chapters,
                oldBook,
                fromReadBookActivity,
                contentProcessor
            )
        }
        return book.toSearchBook()
    }

    private suspend fun loadBookWordCount(
        source: BookSource,
        book: Book,
        chapters: List<BookChapter>,
        oldBook: Book,
        fromReadBookActivity: Boolean,
        contentProcessor: ContentProcessor,
    ): SearchBook? {
        if (chapters.isEmpty()) return null
        val chapterIndex = if (fromReadBookActivity) {
            BookHelp.getDurChapter(oldBook, chapters)
        } else {
            chapters.lastIndex
        }
        if (chapterIndex !in chapters.indices) return null
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
