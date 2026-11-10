package io.legado.app.data.model

/** 一次批量字数回写的单行参数；供 Room @Transaction 方法使用，不是 Entity。 */
data class ChapterWordCountUpdate(
    val bookUrl: String,
    val url: String,
    val wordCount: String,
)
