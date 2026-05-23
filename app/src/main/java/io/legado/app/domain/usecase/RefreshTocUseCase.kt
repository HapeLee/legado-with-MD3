package io.legado.app.domain.usecase

import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.BookSourceRepository
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.addType
import io.legado.app.help.book.isUpError
import io.legado.app.help.book.removeType
import io.legado.app.help.book.sync
import io.legado.app.model.ReadBook
import io.legado.app.model.webBook.WebBook
import kotlinx.coroutines.ensureActive

class RefreshTocUseCase(
    private val bookRepository: BookRepository,
    private val bookSourceRepository: BookSourceRepository
) {
    suspend fun execute(
        bookUrl: String,
        onSuccess: suspend (BookSource, Book) -> Unit = { _, _ -> }
    ): Result<Unit> = kotlin.runCatching {
        val book = bookRepository.getBook(bookUrl) ?: throw Exception("Book not found")
        val source = bookSourceRepository.getBookSource(book.origin)
        if (source == null) {
            if (!book.isUpError) {
                book.addType(BookType.updateError)
                bookRepository.update(book)
            }
            throw Exception("Source not found")
        }

        val oldBook = book.copy()
        if (book.tocUrl.isBlank()) {
            WebBook.getBookInfoAwait(source, book)
        } else {
            WebBook.runPreUpdateJs(source, book)
        }
        val toc = WebBook.getChapterListAwait(source, book).getOrThrow()
        book.sync(oldBook)
        book.removeType(BookType.updateError)
        if (book.bookUrl == bookUrl) {
            bookRepository.update(book)
        } else {
            bookRepository.replace(oldBook, book)
            BookHelp.updateCacheFolder(oldBook, book)
        }
        bookRepository.deleteChaptersByBook(bookUrl)
        bookRepository.insertChapters(*toc.toTypedArray())
        ReadBook.onChapterListUpdated(book)
        onSuccess(source, book)
    }.onFailure {
        kotlin.coroutines.coroutineContext.ensureActive()
        bookRepository.getBook(bookUrl)?.let { book ->
            book.addType(BookType.updateError)
            bookRepository.update(book)
        }
    }
}
