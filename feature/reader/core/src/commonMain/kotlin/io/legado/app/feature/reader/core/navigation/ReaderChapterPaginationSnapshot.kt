package io.legado.app.feature.reader.core.navigation

data class ReaderChapterPaginationSnapshot(
    val chapterIndex: Int,
    val pageStarts: List<Int>,
    val contentEnd: Int,
    val generation: Long,
) {
    val pageCount: Int get() = pageStarts.size
    val lastPageStart: Int? get() = pageStarts.lastOrNull()

    fun pageIndex(chapterPosition: Int): Int = pageStarts
        .indexOfLast { it <= chapterPosition }
        .coerceAtLeast(0)
        .coerceAtMost((pageStarts.size - 1).coerceAtLeast(0))

    fun pageStart(pageIndex: Int): Int? = pageStarts.getOrNull(pageIndex)

    fun nextPageStart(chapterPosition: Int): Int? = pageStarts.getOrNull(pageIndex(chapterPosition) + 1)

    fun previousPageStart(chapterPosition: Int): Int? = pageStarts.getOrNull(pageIndex(chapterPosition) - 1)
}
