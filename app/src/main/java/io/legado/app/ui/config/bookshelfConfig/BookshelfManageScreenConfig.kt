package io.legado.app.ui.config.bookshelfConfig

import io.legado.app.data.dao.BookGroupDao

class BookshelfManageScreenConfig(
    private val bookGroupDao: BookGroupDao
) {

    fun getBookSortByGroupId(groupId: Long): Int {
        val defaultSort = BookshelfConfig.bookshelfSort
        return bookGroupDao.getByID(groupId)?.getRealBookSort(defaultSort) ?: defaultSort
    }

    val bookshelfSortOrder: Int
        get() = BookshelfConfig.bookshelfSortOrder
}
