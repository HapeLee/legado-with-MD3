package io.legado.app.feature.reader.core.navigation

/** Immutable reader-session values; mutable book and chapter objects stay with the platform session. */
data class ReaderSessionSnapshot(
    val bookUrl: String? = null,
    val bookName: String? = null,
    val chapterIndex: Int = 0,
    val chapterPos: Int = 0,
    val chapterCount: Int = 0,
    val simulatedChapterCount: Int = 0,
    val isLocalBook: Boolean = true,
)
