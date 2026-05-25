package io.legado.app.domain.usecase

import io.legado.app.constant.BookType
import io.legado.app.data.appDb
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.book.removeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AddToBookshelfUseCase {

    suspend fun execute(book: SearchBook) = withContext(Dispatchers.IO) {
        val b = book.toBook()
        b.removeType(BookType.notShelf)
        if (b.order == 0) {
            b.order = appDb.bookDao.minOrder - 1
        }
        b.save()
    }
}
