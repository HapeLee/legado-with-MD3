package io.legado.app.ui.main.bookshelf

import androidx.compose.runtime.Stable
import io.legado.app.data.entities.BookGroup

@Stable
data class BookGroupUi(
    val groupId: Long,
    val groupName: String,
    val cover: String?,
    val order: Int,
    val enableRefresh: Boolean,
    val show: Boolean,
    val bookSort: Int
) {
    companion object {
        const val IdAll = -1L
        const val IdRoot = -100L
        const val IdLocal = -2L
        const val IdAudio = -3L
        const val IdNetNone = -4L
        const val IdLocalNone = -5L
        const val IdManga = -7L
        const val IdText = -8L
        const val IdError = -11L
        const val IdReading = -20L
        const val IdUnread = -21L
        const val IdReadFinished = -22L
    }
}

fun BookGroup.toBookGroupUi() = BookGroupUi(
    groupId = groupId,
    groupName = groupName,
    cover = cover,
    order = order,
    enableRefresh = enableRefresh,
    show = show,
    bookSort = bookSort
)
