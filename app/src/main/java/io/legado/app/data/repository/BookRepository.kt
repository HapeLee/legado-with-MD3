package io.legado.app.data.repository

import io.legado.app.data.appDb
import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.entities.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BookRepository(
    private val bookDao: BookDao,
    private val bookChapterDao: BookChapterDao
) {
    suspend fun getBookCoverByName(bookName: String): String? {
        return withContext(Dispatchers.IO) {
            bookDao.findByName(bookName).firstOrNull()?.getDisplayCover()
        }
    }

    suspend fun getChapterTitle(bookName: String, chapterIndex: Int): String? {
        return withContext(Dispatchers.IO) {
            val book = bookDao.findByName(bookName).firstOrNull()
            val bookUrl = book?.bookUrl
            if (bookUrl.isNullOrEmpty()) return@withContext null

            bookChapterDao.getChapterTitleByUrlAndIndex(bookUrl, chapterIndex)
        }
    }

    suspend fun getBook(bookUrl: String): Book? {
        return appDb.bookDao.getBook(bookUrl)
    }

}
