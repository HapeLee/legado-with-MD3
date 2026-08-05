package io.legado.app.data.repository

import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.canSafelyRebindTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class BookImportRepository(
    private val appDb: AppDatabase,
) {

    fun flowLocalBooks(): Flow<List<Book>> = appDb.bookDao.flowLocal().flowOn(Dispatchers.IO)

    suspend fun findByFileName(fileName: String): Book? = withContext(Dispatchers.IO) {
        appDb.bookDao.getBookByFileName(fileName)
    }

    suspend fun findAndRebind(fileName: String, filePath: String): Book? =
        withContext(Dispatchers.IO) {
            val book = appDb.bookDao.getBookByFileName(fileName) ?: return@withContext null
            if (book.bookUrl == filePath || !book.canSafelyRebindTo(filePath)) {
                return@withContext book
            }

            val reboundBook = book.copy(bookUrl = filePath)
            appDb.runInTransaction {
                appDb.bookDao.replace(book, reboundBook)
                BookHelp.updateCacheFolder(book, reboundBook)
            }
            reboundBook
        }
}
