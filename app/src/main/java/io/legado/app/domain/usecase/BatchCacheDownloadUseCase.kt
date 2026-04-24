package io.legado.app.domain.usecase

import android.content.Context
import io.legado.app.constant.BookType
import io.legado.app.data.dao.BookDao
import io.legado.app.data.entities.Book
import io.legado.app.model.CacheBook

class BatchCacheDownloadUseCase(
    private val bookDao: BookDao
) {

    suspend fun execute(
        context: Context,
        bookUrls: Set<String>,
        downloadAllChapters: Boolean,
        skipAudioBooks: Boolean = false
    ): Int {
        if (bookUrls.isEmpty()) return 0
        var count = 0
        bookUrls.forEach { bookUrl ->
            val book = bookDao.getBook(bookUrl) ?: return@forEach
            if (startIfNeeded(context, book, downloadAllChapters, skipAudioBooks)) {
                count++
            }
        }
        return count
    }

    fun execute(
        context: Context,
        books: List<Book>,
        downloadAllChapters: Boolean,
        skipAudioBooks: Boolean = false
    ): Int {
        if (books.isEmpty()) return 0
        var count = 0
        books.forEach { book ->
            if (startIfNeeded(context, book, downloadAllChapters, skipAudioBooks)) {
                count++
            }
        }
        return count
    }

    private fun startIfNeeded(
        context: Context,
        book: Book,
        downloadAllChapters: Boolean,
        skipAudioBooks: Boolean
    ): Boolean {
        if (book.type and BookType.local > 0) return false
        if (skipAudioBooks && book.type and BookType.audio > 0) return false
        val indices = if (downloadAllChapters) {
            (0..book.lastChapterIndex).toList()
        } else {
            (book.durChapterIndex..book.lastChapterIndex).toList()
        }
        if (indices.isEmpty()) return false
        CacheBook.start(context, book, indices)
        return true
    }
}
