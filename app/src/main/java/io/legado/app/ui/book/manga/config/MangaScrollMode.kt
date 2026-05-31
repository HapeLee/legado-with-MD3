package io.legado.app.ui.book.manga.config

object MangaScrollMode {
    const val PAGE_LEFT_TO_RIGHT = 1      // 单页式（从左到右）
    const val PAGE_RIGHT_TO_LEFT = 2      // 单页式（从右到左）
    const val PAGE_TOP_TO_BOTTOM = 3      // 单页式（从上到下）
    const val WEBTOON = 4                 // 条漫
    const val WEBTOON_WITH_GAP = 5        // 条漫（页面有空隙）

    val ALL = listOf(
        PAGE_LEFT_TO_RIGHT,
        PAGE_RIGHT_TO_LEFT,
        PAGE_TOP_TO_BOTTOM,
        WEBTOON,
        WEBTOON_WITH_GAP
    )

    fun labelOf(mode: Int): String = when (mode) {
        PAGE_LEFT_TO_RIGHT -> "Single page (LTR)"
        PAGE_RIGHT_TO_LEFT -> "Single page (RTL)"
        PAGE_TOP_TO_BOTTOM -> "Single page (TTB)"
        WEBTOON -> "Webtoon"
        WEBTOON_WITH_GAP -> "Webtoon (with gap)"
        else -> "Unknown"
    }
}

