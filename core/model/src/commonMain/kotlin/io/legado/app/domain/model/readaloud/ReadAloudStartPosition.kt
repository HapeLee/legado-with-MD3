package io.legado.app.domain.model.readaloud

data class ReadAloudStartPosition(
    val pageIndex: Int,
    val offsetInPage: Int,
)

/**
 * Resolves the new Canvas-facing absolute chapter position at the legacy service boundary.
 * Page-relative callers remain supported until all external entry points migrate.
 */
fun resolveReadAloudStartPosition(
    requestedPageIndex: Int,
    requestedOffsetInPage: Int,
    requestedChapterPosition: Int?,
    pageIndexAt: (Int) -> Int,
    pageStart: (Int) -> Int,
): ReadAloudStartPosition {
    val chapterPosition = requestedChapterPosition
        ?: return ReadAloudStartPosition(requestedPageIndex, requestedOffsetInPage.coerceAtLeast(0))
    val pageIndex = pageIndexAt(chapterPosition)
    return ReadAloudStartPosition(
        pageIndex = pageIndex,
        offsetInPage = (chapterPosition - pageStart(pageIndex)).coerceAtLeast(0),
    )
}
