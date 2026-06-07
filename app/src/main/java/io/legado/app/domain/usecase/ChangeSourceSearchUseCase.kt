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
import io.legado.app.help.config.AppConfig
import io.legado.app.help.source.SourceHelp
import io.legado.app.model.webBook.WebBook
import io.legado.app.ui.book.changesource.ObservableSourceConfig
import io.legado.app.ui.config.otherConfig.OtherConfig
import io.legado.app.utils.internString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

sealed interface ChangeSourceSearchEvent {
    data object Started : ChangeSourceSearchEvent
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
    private var tocMapChapterCount = 0

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
        tocMapChapterCount = 0

        emit(ChangeSourceSearchEvent.Started)

        var processedSources = 0
        val totalSources = bookSourceParts.size

        for (bs in bookSourceParts) {
            currentCoroutineContext().ensureActive()
            val source = bs.getBookSource() ?: continue
            try {
                withTimeout(60000L) {
                    searchSource(
                        source, name, author, oldBook, fromReadBookActivity,
                        contentProcessor
                    )
                }.forEach { searchBook ->
                    emit(ChangeSourceSearchEvent.Result(searchBook))
                }
            } catch (_: Throwable) {
                currentCoroutineContext().ensureActive()
            }
            processedSources++
            emit(
                ChangeSourceSearchEvent.Progress(
                    processedSources = processedSources,
                    totalSources = totalSources,
                    resultCount = 0,
                    sourceName = source.bookSourceName,
                )
            )
        }

        emit(ChangeSourceSearchEvent.Finished(isEmpty = true))
    }.flowOn(Dispatchers.IO)

    private suspend fun searchSource(
        source: BookSource,
        name: String,
        author: String,
        oldBook: Book,
        fromReadBookActivity: Boolean,
        contentProcessor: ContentProcessor,
    ): List<SearchBook> {
        val checkAuthor = AppConfig.changeSourceCheckAuthor
        val loadInfo = AppConfig.changeSourceLoadInfo
        val loadToc = AppConfig.changeSourceLoadToc
        val loadWordCount = AppConfig.changeSourceLoadWordCount

        val resultBooks = WebBook.searchBookAwait(
            source, name,
            filter = { fName, fAuthor ->
                fName == name && (!checkAuthor || fAuthor.contains(author))
            }
        )

        val processedBooks = mutableListOf<SearchBook>()
        for (searchBook in resultBooks) {
            currentCoroutineContext().ensureActive()
            when {
                loadInfo || loadToc || loadWordCount -> {
                    val book = searchBook.toBook()
                    try {
                        loadBookInfo(
                            source,
                            book,
                            loadToc,
                            loadWordCount,
                            oldBook,
                            fromReadBookActivity,
                            contentProcessor
                        )
                        val processedSearchBook = book.toSearchBook()
                        processedBooks.add(processedSearchBook)
                    } catch (e: Throwable) {
                        if (e is CancellationException) throw e
                        processedBooks.add(searchBook)
                    }
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
    ) {
        if (book.tocUrl.isEmpty()) {
            WebBook.getBookInfoAwait(source, book)
        }
        if (loadToc || loadWordCount) {
            loadBookToc(
                source,
                book,
                loadWordCount,
                oldBook,
                fromReadBookActivity,
                contentProcessor
            )
        }
    }

    private suspend fun loadBookToc(
        source: BookSource,
        book: Book,
        loadWordCount: Boolean,
        oldBook: Book,
        fromReadBookActivity: Boolean,
        contentProcessor: ContentProcessor,
    ) {
        val chapters = WebBook.getChapterListAwait(source, book).getOrThrow()
        for (chapter in chapters) {
            chapter.internString()
        }
        if (tocMapChapterCount < 30000) {
            tocMapChapterCount += chapters.size
            tocMap[book.primaryStr()] = chapters
        }
        bookMap[book.primaryStr()] = book
        book.releaseHtmlData()
        if (loadWordCount) {
            loadBookWordCount(
                source,
                book,
                chapters,
                oldBook,
                fromReadBookActivity,
                contentProcessor
            )
        }
    }

    private suspend fun loadBookWordCount(
        source: BookSource,
        book: Book,
        chapters: List<BookChapter>,
        oldBook: Book,
        fromReadBookActivity: Boolean,
        contentProcessor: ContentProcessor,
    ) {
        if (chapters.isEmpty()) return
        val chapterIndex = if (fromReadBookActivity) {
            BookHelp.getDurChapter(oldBook, chapters)
        } else {
            chapters.lastIndex
        }
        if (chapterIndex !in chapters.indices) return
        val bookChapter = chapters[chapterIndex]
        var title = bookChapter.title.trim()
        if (title.length > 20) {
            title = title.substring(0, 20) + "…"
        }
        val startTime = System.currentTimeMillis()
        try {
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
