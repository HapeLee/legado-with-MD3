package io.legado.app.ui.main.bookshelf

import androidx.compose.runtime.Stable
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.utils.splitNotBlank
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.max

@Stable
data class BookShelfItem(
    val bookUrl: String,
    val name: String,
    val author: String,
    val origin: String,
    val originName: String,
    val coverUrl: String?,
    val customCoverUrl: String?,
    val durChapterTitle: String?,
    val durChapterTime: Long,
    val durChapterPos: Int,
    val latestChapterTitle: String?,
    val latestChapterTime: Long,
    val lastCheckCount: Int,
    val totalChapterNum: Int,
    val durChapterIndex: Int,
    val type: Int,
    val group: Long,
    val order: Int,
    val canUpdate: Boolean = true,
    val intro: String? = null,
    val kind: String? = null,
    val wordCount: String? = null
) {
    fun getDisplayCover() = if (customCoverUrl.isNullOrEmpty()) coverUrl else customCoverUrl

    val isLocal: Boolean get() = (type and BookType.local) > 0

    val isAudio: Boolean get() = (type and BookType.audio) > 0

    val isImage: Boolean get() = (type and BookType.image) > 0

    val isNotShelf: Boolean get() = (type and BookType.notShelf) > 0

    val isNew: Boolean get() = lastCheckCount > 0

    fun getUnreadChapterNum() = max(totalChapterNum - durChapterIndex - 1, 0)

    /**
     * 将 DTO 转换为专为 Compose 设计的 UI 状态
     */
    fun toUiItem(): BookUiItem {
        val tagList = mutableListOf<String>()
        kind?.splitNotBlank(",", "\n")?.filter { it.isNotBlank() }?.let {
            tagList.addAll(it)
        }
        if (!wordCount.isNullOrBlank() && !tagList.contains(wordCount)) {
            tagList.add(wordCount)
        }

        return BookUiItem(
            book = this,
            displayTags = tagList.toImmutableList()
        )
    }
}

/**
 * 理想实现：专为 UI 设计的状态类
 */
@Stable
data class BookUiItem(
    val book: BookShelfItem,
    val displayTags: ImmutableList<String>
) {
    fun matches(key: String): Boolean {
        return book.name.contains(key, true) ||
                book.author.contains(key, true) ||
                book.originName.contains(key, true) ||
                displayTags.any { it.contains(key, true) }
    }
}

fun BookShelfItem.toLightBook() = Book(
    bookUrl = bookUrl,
    origin = origin,
    originName = originName,
    name = name,
    author = author,
    coverUrl = coverUrl,
    customCoverUrl = customCoverUrl,
    latestChapterTitle = latestChapterTitle,
    latestChapterTime = latestChapterTime,
    lastCheckCount = lastCheckCount,
    totalChapterNum = totalChapterNum,
    durChapterTitle = durChapterTitle,
    durChapterIndex = durChapterIndex,
    durChapterPos = durChapterPos,
    durChapterTime = durChapterTime,
    type = type,
    group = group,
    order = order,
    canUpdate = canUpdate,
    wordCount = wordCount,
    kind = kind
)
