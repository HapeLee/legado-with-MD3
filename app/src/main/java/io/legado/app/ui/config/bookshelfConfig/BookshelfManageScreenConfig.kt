package io.legado.app.ui.config.bookshelfConfig

import io.legado.app.constant.PreferKey
import io.legado.app.data.dao.BookGroupDao
import io.legado.app.ui.config.prefDelegate

class BookshelfManageScreenConfig(
    private val bookGroupDao: BookGroupDao
) {

    var bookExportFileName by prefDelegate<String?>(PreferKey.bookExportFileName, null)

    var episodeExportFileName by prefDelegate(PreferKey.episodeExportFileName, "")

    var exportCharset by prefDelegate(PreferKey.exportCharset, "UTF-8")

    var exportUseReplace by prefDelegate(PreferKey.exportUseReplace, true)

    var exportToWebDav by prefDelegate(PreferKey.exportToWebDav, false)

    var exportNoChapterName by prefDelegate(PreferKey.exportNoChapterName, false)

    var enableCustomExport by prefDelegate(PreferKey.enableCustomExport, false)

    var exportType by prefDelegate(PreferKey.exportType, 0)

    var exportPictureFile by prefDelegate(PreferKey.exportPictureFile, false)

    var parallelExportBook by prefDelegate(PreferKey.parallelExportBook, false)

    fun getBookSortByGroupId(groupId: Long): Int {
        return bookGroupDao.getByID(groupId)?.getRealBookSort() ?: BookshelfConfig.bookshelfSort
    }

    val bookshelfSortOrder: Int
        get() = BookshelfConfig.bookshelfSortOrder
}
