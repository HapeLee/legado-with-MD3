package io.legado.app.domain.gateway

import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook

interface ExploreBooksGateway {
    suspend fun getBookSource(sourceUrl: String): BookSource?
    suspend fun exploreBooks(
        bookSource: BookSource,
        url: String,
        page: Int,
        key: String? = null
    ): List<SearchBook>
}
