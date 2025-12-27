package io.legado.app.data.repository

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book

class BookRepository {

    suspend fun getBook(bookUrl: String): Book? {
        return appDb.bookDao.getBook(bookUrl)
    }

}
