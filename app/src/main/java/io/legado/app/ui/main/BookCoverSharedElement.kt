package io.legado.app.ui.main

/**
 * 图书形变动画封面键规范。
 * 通过显式的作用域命名空间（shelf / detail）实现天然隔离，
 * 杜绝字符串模糊匹配与不同层级间的锚点污染。
 */
object BookCoverSharedElement {
    fun forShelf(bookUrl: String, sourceId: String? = null): String {
        val source = sourceId?.takeIf { it.isNotBlank() } ?: return "cover:shelf:$bookUrl"
        return "cover:shelf:$source:$bookUrl"
    }

    fun forDetail(bookUrl: String): String = "cover:detail:$bookUrl"
}

fun bookCoverSharedElementKey(bookUrl: String, sourceId: String? = null): String {
    val source = sourceId?.takeIf { it.isNotBlank() } ?: return "book-cover:$bookUrl"
    return "book-cover:$source:$bookUrl"
}

fun bookInfoCoverSharedElementKey(bookUrl: String): String =
    BookCoverSharedElement.forDetail(bookUrl)
