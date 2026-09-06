package io.legado.app.ui.config.bookshelfConfig

import io.legado.app.data.dao.BookGroupDao
import io.legado.app.domain.gateway.BookshelfSettingsGateway
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext

class BookshelfManageScreenConfig(
    private val bookGroupDao: BookGroupDao
) {
    private val bookshelfSettingsGateway
        get() = GlobalContext.get().get<BookshelfSettingsGateway>()

    fun getBookSortByGroupId(groupId: Long): Int {
        val defaultSort = bookshelfSettingsGateway.currentSettings.bookshelfSort
        return runBlocking { bookGroupDao.getByID(groupId) }?.getRealBookSort(defaultSort) ?: defaultSort
    }

    val bookshelfSortOrder: Int
        get() = bookshelfSettingsGateway.currentSettings.bookshelfSortOrder
}
