package io.legado.app.ui.main.bookshelf

import androidx.compose.runtime.Stable
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import kotlin.math.max

@Stable
data class BookShelfItem(
    val bookUrl: String,
    val name: String,
    val author: String,
    val coverUrl: String?,
    val customCoverUrl: String?,
    val durChapterTitle: String?,
    val durChapterTime: Long,
    val durChapterPos: Int,
    val latestChapterTitle: String?,
    val latestChapterTime: Long,
    val totalChapterNum: Int,
    val durChapterIndex: Int,
    val type: Int,
    val group: Long,
    val order: Int,
    val canUpdate: Boolean = true
) {
    fun getDisplayCover() = if (customCoverUrl.isNullOrEmpty()) coverUrl else customCoverUrl

    val isLocal: Boolean get() = (type and BookType.local) > 0

    val isAudio: Boolean get() = (type and BookType.audio) > 0

    val isImage: Boolean get() = (type and BookType.image) > 0

    fun getUnreadChapterNum() = max(totalChapterNum - durChapterIndex - 1, 0)
}

fun Book.toBookShelfItem(): BookShelfItem {
    return BookShelfItem(
        bookUrl = bookUrl,
        name = name,
        author = author,
        coverUrl = coverUrl,
        customCoverUrl = customCoverUrl,
        durChapterTitle = durChapterTitle,
        durChapterTime = durChapterTime,
        durChapterPos = durChapterPos,
        latestChapterTitle = latestChapterTitle,
        latestChapterTime = latestChapterTime,
        totalChapterNum = totalChapterNum,
        durChapterIndex = durChapterIndex,
        type = type,
        group = group,
        order = order,
        canUpdate = canUpdate
    )
}
