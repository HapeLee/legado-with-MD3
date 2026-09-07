package io.legado.app.domain.gateway

import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.BookSearchScope

interface BookSearchGateway {
    suspend fun getBookSourceParts(scope: BookSearchScope): List<BookSourcePart>
    suspend fun getBookSource(sourceUrl: String): BookSource?
    suspend fun saveSearchBooks(books: List<SearchBook>)
}
