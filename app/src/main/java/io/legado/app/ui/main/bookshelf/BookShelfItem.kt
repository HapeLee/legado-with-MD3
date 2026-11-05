package io.legado.app.ui.main.bookshelf

import androidx.compose.runtime.Stable
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.domain.model.PrivateAccessState
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
    val customTag: String? = null,
    val wordCount: String? = null
) {
    fun getDisplayCover() = if (customCoverUrl.isNullOrEmpty()) coverUrl else customCoverUrl

    val isLocal: Boolean get() = (type and BookType.local) > 0

    val isAudio: Boolean get() = (type and BookType.audio) > 0

    val isImage: Boolean get() = (type and BookType.image) > 0

    val isVideo: Boolean get() = (type and BookType.video) > 0

    val isNotShelf: Boolean get() = (type and BookType.notShelf) > 0

    val isNew: Boolean get() = lastCheckCount > 0

    fun getUnreadChapterNum() = max(totalChapterNum - durChapterIndex - 1, 0)

    /** 仅筛选展示内容；搜索仍使用包含全部来源的 [BookUiItem.displayTags]。 */
    fun getDisplayTags(
        showCustomTags: Boolean = true,
        showSourceTags: Boolean = true,
        showWordCount: Boolean = true,
    ): ImmutableList<String> {
        val tagList = mutableListOf<String>()
        if (showCustomTags) {
            customTag?.splitNotBlank(",", "\n")?.filter { it.isNotBlank() }?.let {
                tagList.addAll(it)
            }
        }
        if (showSourceTags) {
            kind?.splitNotBlank(",", "\n")?.filter { it.isNotBlank() }?.let {
                tagList.addAll(it.filterNot(tagList::contains))
            }
        }
        if (showWordCount && !wordCount.isNullOrBlank() && !tagList.contains(wordCount)) {
            tagList.add(wordCount)
        }
        return tagList.toImmutableList()
    }

    /** [isPrivate] 由书架计算单本标记与私密分组的并集，这里只携带结果。 */
    fun toUiItem(isPrivate: Boolean = false): BookUiItem {
        return BookUiItem(
            book = this,
            displayTags = getDisplayTags(),
            isPrivate = isPrivate
        )
    }
}

/**
 * 理想实现：专为 UI 设计的状态类
 */
@Stable
        /**
         * 这本书此刻是否处于锁定态（需要脱敏）。
         *
         * 唯一实现：凡是判断"某本书要不要遮"的地方都走这里，
         * 避免 UiState 与 ViewModel 各自复制一份谓词后漂移。
         */
fun BookUiItem.isLocked(access: PrivateAccessState, verifyOnOpenBook: Boolean): Boolean =
    isPrivate && verifyOnOpenBook && !access.isTargetGranted(book.bookUrl, book.group)

data class BookUiItem(
    val book: BookShelfItem,
    val displayTags: ImmutableList<String>,
    val isPrivate: Boolean = false
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
    kind = kind,
    customTag = customTag
)
