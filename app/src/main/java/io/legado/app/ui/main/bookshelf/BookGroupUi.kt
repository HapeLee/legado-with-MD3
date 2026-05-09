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
)

fun BookGroup.toBookGroupUi() = BookGroupUi(
    groupId = groupId,
    groupName = groupName,
    cover = cover,
    order = order,
    enableRefresh = enableRefresh,
    show = show,
    bookSort = bookSort
)
