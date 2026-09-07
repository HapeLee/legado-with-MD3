package io.legado.app.data.entities

import io.legado.app.constant.BookType
import kotlin.math.max

/**
 * 书架书籍的 DAO 查询投影 DTO（[io.legado.app.data.dao.BookDao] 的 flowBookShelf* 返回类型）。
 *
 * 原位于 app 侧 `io.legado.app.ui.main.bookshelf` 包，随 BookDao 下沉到 commonMain。
 * 仅保留纯数据字段与位运算派生属性；依赖 compose.runtime（@Stable）与
 * kotlinx.collections.immutable（toUiItem/BookUiItem）的 UI 桥接部分留在 app 侧
 * `BookShelfItemUi.kt` 扩展。
 */
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

    val isNotShelf: Boolean get() = (type and BookType.notShelf) > 0

    val isNew: Boolean get() = lastCheckCount > 0

    fun getUnreadChapterNum() = max(totalChapterNum - durChapterIndex - 1, 0)
}
