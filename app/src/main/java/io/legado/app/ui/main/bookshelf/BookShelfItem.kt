package io.legado.app.ui.main.bookshelf

import androidx.compose.runtime.Stable
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookShelfItem
import io.legado.app.utils.splitNotBlank
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * [BookShelfItem]（已下沉 core:data）的 Compose UI 桥接扩展。
 *
 * 原 [BookShelfItem.toUiItem] 成员方法依赖 kotlinx.collections.immutable，
 * 随实体下沉时抽成扩展留在 app 侧（data 层不依赖 UI 框架）。
 */

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

/**
 * 将 DTO 转换为专为 Compose 设计的 UI 状态
 */
fun BookShelfItem.toUiItem(): BookUiItem {
    val tagList = mutableListOf<String>()
    customTag?.splitNotBlank(",", "\n")?.filter { it.isNotBlank() }?.let {
        tagList.addAll(it)
    }
    kind?.splitNotBlank(",", "\n")?.filter { it.isNotBlank() }?.let {
        tagList.addAll(it.filterNot(tagList::contains))
    }
    val wordCountValue = wordCount
    if (!wordCountValue.isNullOrBlank() && wordCountValue !in tagList) {
        tagList.add(wordCountValue)
    }

    return BookUiItem(
        book = this,
        displayTags = tagList.toImmutableList()
    )
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
