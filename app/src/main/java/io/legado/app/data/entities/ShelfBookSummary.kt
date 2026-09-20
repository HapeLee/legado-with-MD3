package io.legado.app.data.entities

/**
 * 书架作品的轻量投影。
 *
 * 加入书架查重只需要「是否重名」与「展示已有作品」两件事，不需要 books 表的正文/简介/
 * 变量等重字段；换掉 `SELECT *` 可以避免每次点「加入书架」都把整本书的实体读进内存。
 */
data class ShelfBookSummary(
    val bookUrl: String = "",
    val name: String = "",
    val author: String = "",
    val coverUrl: String? = null,
    val customCoverUrl: String? = null,
    val origin: String = "",
    val originName: String = "",
    val totalChapterNum: Int = 0,
    val latestChapterTitle: String? = null,
    val durChapterTime: Long = 0L,
)
