package io.legado.app.domain.usecase

import io.legado.app.constant.BookType
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.repository.BookRepository
import io.legado.app.domain.model.BookshelfConflict
import io.legado.app.help.book.removeType

sealed interface AddToBookshelfResult {
    /** 已直接加入书架。 */
    data object Added : AddToBookshelfResult

    /** 发现疑似同一作品，需要用户先选择共存还是迁移。 */
    data class Conflict(val conflict: BookshelfConflict) : AddToBookshelfResult
}

class AddToBookshelfUseCase(
    private val bookRepository: BookRepository,
    private val findBookshelfConflictUseCase: FindBookshelfConflictUseCase,
) {

    suspend fun execute(book: SearchBook): AddToBookshelfResult {
        val b = book.toBook()
        b.removeType(BookType.notShelf)
        val conflict = findBookshelfConflictUseCase.execute(b)
        if (conflict != null) {
            return AddToBookshelfResult.Conflict(conflict)
        }
        if (b.order == 0) {
            b.order = bookRepository.getMinOrder() - 1
        }
        bookRepository.insert(b)
        return AddToBookshelfResult.Added
    }
}
