package io.legado.app.feature.reader.core.model

enum class ReaderTipValueType {
    NONE,
    CHAPTER_TITLE,
    TIME,
    BATTERY,
    PAGE,
    TOTAL_PROGRESS,
    PAGE_AND_TOTAL,
    BOOK_NAME,
    TIME_BATTERY,
    CHAPTER_INDEX_AND_TOTAL,
    CHAPTER_TITLE_ARROW,
    CUSTOM,
    WHOLE_BOOK_PAGE,
    WHOLE_BOOK_PAGE_AND_PROGRESS,
}

data class ReaderTipValueContext(
    val bookName: String,
    val chapterTitle: String,
    val time: String,
    val batteryPercent: Int,
    val chapterIndex: Int,
    val chapterCount: Int,
    val pageIndex: Int,
    val pageCount: Int,
    val readProgress: String,
    /** 页数文案：章还没排出任何页时是 `-`（对照旧 `PageView.setProgress` 的 `pageSizeInt <= 0` 分支）。 */
    val pageCountText: String = pageCount.toString(),
    /** 整书页数文案，已按旧 `whole_book_page_*` 字符串本地化并带 `- / -` 兜底。 */
    val wholeBookPageText: String = "",
    val wholeBookPageIndex: Int? = null,
    val wholeBookPageCount: Int? = null,
)

object ReaderTipValueFormatter {
    fun format(
        type: ReaderTipValueType,
        context: ReaderTipValueContext,
        customTemplate: String = "",
    ): String = with(context) {
        when (type) {
            ReaderTipValueType.NONE -> ""
            ReaderTipValueType.CHAPTER_TITLE -> chapterTitle
            ReaderTipValueType.TIME -> time
            ReaderTipValueType.BATTERY -> "$batteryPercent%"
            ReaderTipValueType.PAGE -> "${pageIndex + 1}/$pageCountText"
            ReaderTipValueType.TOTAL_PROGRESS -> readProgress
            ReaderTipValueType.PAGE_AND_TOTAL -> "${pageIndex + 1}/$pageCountText  $readProgress"
            ReaderTipValueType.BOOK_NAME -> bookName
            ReaderTipValueType.TIME_BATTERY -> "$time $batteryPercent%"
            ReaderTipValueType.CHAPTER_INDEX_AND_TOTAL -> "${chapterIndex + 1}/$chapterCount"
            ReaderTipValueType.CHAPTER_TITLE_ARROW -> chapterTitle
            ReaderTipValueType.CUSTOM -> resolveCustom(customTemplate, context)
            ReaderTipValueType.WHOLE_BOOK_PAGE -> wholeBookPage()
            ReaderTipValueType.WHOLE_BOOK_PAGE_AND_PROGRESS -> "${wholeBookPage()}  $readProgress"
        }
    }

    /**
     * 整书页数文案。生产路径由宿主用 `whole_book_page_*` 字符串本地化后传入（含 `全文 - / -`
     * 兜底）；未提供时退回旧行为，只给页码，绝不让该槽位变成空字符串。
     */
    private fun ReaderTipValueContext.wholeBookPage(): String =
        wholeBookPageText.ifBlank {
            "${wholeBookPageIndex ?: pageIndex + 1}/${wholeBookPageCount ?: pageCount}"
        }

    private fun resolveCustom(template: String, context: ReaderTipValueContext): String {
        val wholeIndex = context.wholeBookPageIndex ?: context.pageIndex + 1
        val wholeCount = context.wholeBookPageCount ?: context.pageCount
        return template
            .replace("{BookName}", context.bookName)
            .replace("{ChapterTitle}", context.chapterTitle)
            .replace("{Time}", context.time)
            .replace("{BatteryPercent}", "${context.batteryPercent}%")
            .replace("{ChapterIndex}", (context.chapterIndex + 1).toString())
            .replace("{ChapterSize}", context.chapterCount.toString())
            .replace("{PageIndex}", (context.pageIndex + 1).toString())
            .replace("{PageSize}", context.pageCountText)
            .replace("{PageRemaining}", context.pageRemainingText())
            .replace("{ReadProgress}", context.readProgress)
            .replace("{FullPageIndex}", wholeIndex.toString())
            .replace("{FullPageSize}", wholeCount.toString())
    }

    /**
     * 对照旧 `formatCustomTipPageRemaining`：只有"章还没排出页、余量无从算起"时给 `-`，
     * 其余（含排版中但已有页）都给数字。
     */
    private fun ReaderTipValueContext.pageRemainingText(): String =
        if (pageCountText == UNKNOWN_PAGE_COUNT) UNKNOWN_PAGE_COUNT
        else (pageCount - pageIndex - 1).coerceAtLeast(0).toString()

    const val UNKNOWN_PAGE_COUNT = "-"
}
