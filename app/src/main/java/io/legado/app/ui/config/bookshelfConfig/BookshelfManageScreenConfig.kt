package io.legado.app.ui.config.bookshelfConfig

import io.legado.app.data.dao.BookGroupDao

class BookshelfManageScreenConfig(
    private val bookGroupDao: BookGroupDao
) {

    fun getBookSortByGroupId(groupId: Long): Int {
        return bookGroupDao.getByID(groupId)?.getRealBookSort() ?: BookshelfConfig.bookshelfSort
    }

    val bookshelfSortOrder: Int
        get() = BookshelfConfig.bookshelfSortOrder
}
