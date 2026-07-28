package io.legado.app.ui.book.read.page

internal fun formatCustomTipPageRemaining(
    pageSize: Int,
    pageIndex: Int,
    isChapterCompleted: Boolean,
    chapterCompleteText: String,
): String {
    val pageRemaining = (pageSize - pageIndex - 1).coerceAtLeast(0)
    return when {
        isChapterCompleted && pageRemaining == 0 -> chapterCompleteText
        isChapterCompleted -> pageRemaining.toString()
        pageSize <= 0 -> "-"
        else -> pageRemaining.toString()
    }
}
