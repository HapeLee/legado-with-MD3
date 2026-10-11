package io.legado.app.domain.usecase

import io.legado.app.domain.model.BookShelfState

data class BookShelfKey(
    val name: String,
    val author: String,
    val url: String?
)

class ResolveBookShelfStateUseCase {

    fun execute(
        name: String,
        author: String,
        url: String?,
        shelf: Set<BookShelfKey>
    ): BookShelfState {
        // 同源同地址即视为已在书架（书名可能在书架里被改过，不能作为唯一依据）
        if (url != null && shelf.any { it.url == url }) {
            return BookShelfState.IN_SHELF
        }

        val exactMatch = shelf.any {
            it.name == name && it.author == author && it.url == url
        }
        if (exactMatch) return BookShelfState.IN_SHELF

        val sameNameAuthor = shelf.any {
            it.name == name && it.author == author && it.url != url
        }
        if (sameNameAuthor) return BookShelfState.SAME_NAME_AUTHOR

        return BookShelfState.NOT_IN_SHELF
    }
}
