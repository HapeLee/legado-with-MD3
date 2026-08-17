package io.legado.app.ui.book.manga

internal fun mangaChapterLoadingItem(
    chapterIndex: Int,
    message: String,
    failed: Boolean,
) = MangaReaderItemUi.ChapterEdge(
    key = "chapter-placeholder:$chapterIndex:${if (failed) "failed" else "loading"}",
    message = message,
    loading = !failed,
    retryChapterIndex = chapterIndex.takeIf { failed },
    fullScreen = true,
)

internal fun mangaClickRegionIndex(
    x: Float,
    y: Float,
    width: Int,
    height: Int,
): Int {
    val column = (x / (width.coerceAtLeast(1) / 3f)).toInt().coerceIn(0, 2)
    val row = (y / (height.coerceAtLeast(1) / 3f)).toInt().coerceIn(0, 2)
    return row * 3 + column
}

internal fun mangaClickActionAt(
    clickActions: List<Int>,
    x: Float,
    y: Float,
    width: Int,
    height: Int,
): Int = clickActions.getOrNull(mangaClickRegionIndex(x, y, width, height)) ?: 0

internal fun nextMangaClickAction(action: Int): Int = when (action) {
    -1 -> 0
    0 -> 1
    1 -> 2
    2 -> 3
    3 -> 4
    else -> -1
}

internal fun mangaPageStepTarget(
    currentIndex: Int,
    itemCount: Int,
    direction: Int,
): Int? {
    if (itemCount <= 0) return null
    val target = (currentIndex + direction).coerceIn(0, itemCount - 1)
    return target.takeIf { it != currentIndex }
}

internal fun shouldExposeMangaPages(currentChapterFinished: Boolean): Boolean =
    currentChapterFinished

enum class MangaChapterSwitch { NONE, NEXT, PREVIOUS }

/**
 * 决定可见页是否触发章节切换：只有当前章节已完全滑出视口时，
 * 才允许沿可见页的章节方向自动切章；否则只是停留在当前章记录进度。
 */
internal fun mangaChapterSwitchDecision(
    currentChapterIndex: Int,
    visibleChapterIndex: Int,
    currentChapterVisible: Boolean,
): MangaChapterSwitch = when {
    currentChapterIndex < visibleChapterIndex ->
        if (currentChapterVisible) MangaChapterSwitch.NONE else MangaChapterSwitch.NEXT

    currentChapterIndex > visibleChapterIndex ->
        if (currentChapterVisible) MangaChapterSwitch.NONE else MangaChapterSwitch.PREVIOUS

    else -> MangaChapterSwitch.NONE
}

internal fun shouldForceMangaChapterPosition(
    hasPages: Boolean,
    isLoading: Boolean,
    currentBookUrl: String,
    targetBookUrl: String,
    pendingExplicitChapterIndex: Int?,
    targetChapterIndex: Int,
): Boolean =
    !hasPages || isLoading || currentBookUrl != targetBookUrl ||
        pendingExplicitChapterIndex == targetChapterIndex
