package io.legado.app.domain.usecase

import io.legado.app.data.entities.SearchBook
import io.legado.app.data.repository.SearchRepository

class SaveSearchBooksUseCase(
    private val searchRepository: SearchRepository,
) {
    suspend fun save(book: SearchBook) = save(listOf(book))
    suspend fun save(books: List<SearchBook>) = searchRepository.saveSearchBooks(books)
}
