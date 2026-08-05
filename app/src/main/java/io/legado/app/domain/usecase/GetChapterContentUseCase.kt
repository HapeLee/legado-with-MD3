package io.legado.app.domain.usecase

import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookSourceDao
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.model.webBook.WebBook

class GetChapterContentUseCase(
    private val bookSourceDao: BookSourceDao,
    private val bookChapterDao: BookChapterDao,
) {

    /**
     * Get TOC for a book. If tocUrl is empty, fetches book info first.
     */
    suspend fun getToc(book: Book): Pair<List<BookChapter>, BookSource> {
        val source = bookSourceDao.getBookSource(book.origin)
            ?: throw NoStackTraceException("书源不存在")
        if (book.tocUrl.isEmpty()) {
            WebBook.getBookInfoAwait(source, book)
        }
        val toc = WebBook.getChapterListAwait(source, book).getOrThrow()
        return Pair(toc, source)
    }

    /**
     * Get content for a specific chapter.
     */
    suspend fun getContent(
        book: Book,
        chapter: BookChapter,
        nextChapterUrl: String?,
    ): String {
        val bookSource = bookSourceDao.getBookSource(book.origin)
            ?: throw NoStackTraceException("书源不存在")
        return WebBook.getContentAwait(bookSource, book, chapter, nextChapterUrl, false)
    }

    /**
     * Find the chapter index in a new TOC matching the current chapter.
     */
    fun getDurChapterIndex(
        chapterIndex: Int,
        chapterTitle: String,
        toc: List<BookChapter>,
        totalChapterNum: Int = 0,
    ): Int {
        return io.legado.app.help.book.BookHelp.getDurChapter(
            chapterIndex,
            chapterTitle,
            toc,
            totalChapterNum,
        )
    }
}
