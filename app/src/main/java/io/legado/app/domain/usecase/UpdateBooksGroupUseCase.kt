package io.legado.app.domain.usecase

import io.legado.app.data.dao.BookDao

class UpdateBooksGroupUseCase(
    private val bookDao: BookDao
) {

    suspend fun replaceGroup(bookUrls: Set<String>, groupId: Long) {
        updateGroups(bookUrls) { groupId }
    }

    suspend fun updateGroups(bookUrls: Set<String>, transform: (Long) -> Long) {
        if (bookUrls.isEmpty()) return
        val updateBooks = bookUrls.mapNotNull { bookUrl ->
            bookDao.getBook(bookUrl)?.let { book ->
                val targetGroup = transform(book.group)
                if (targetGroup == book.group) {
                    null
                } else {
                    book.copy(group = targetGroup)
                }
            }
        }
        if (updateBooks.isNotEmpty()) {
            bookDao.update(*updateBooks.toTypedArray())
        }
    }
}
