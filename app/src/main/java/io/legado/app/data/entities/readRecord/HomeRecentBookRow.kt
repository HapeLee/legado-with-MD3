package io.legado.app.data.entities.readRecord

data class HomeRecentBookRow(
    val recordName: String,
    val recordAuthor: String,
    val bookUrl: String?,
    val origin: String?,
    val coverUrl: String?,
    val customCoverUrl: String?,
    val chapterTitle: String?,
    val totalChapterNum: Int?,
    val chapterIndex: Int?,
)

