package io.legado.app.domain.usecase

import io.legado.app.constant.BookType
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.repository.BookRepository
import io.legado.app.help.book.removeType

class AddToBookshelfUseCase(
    private val bookRepository: BookRepository,
) {

    suspend fun execute(book: SearchBook) {
        val b = book.toBook()
        b.removeType(BookType.notShelf)
        if (b.order == 0) {
            b.order = bookRepository.getMinOrder() - 1
        }
        bookRepository.insert(b)
    }
}
