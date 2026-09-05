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
            ReaderTipValueType.PAGE -> "${pageIndex + 1}/$pageCount"
            ReaderTipValueType.TOTAL_PROGRESS -> readProgress
            ReaderTipValueType.PAGE_AND_TOTAL -> "${pageIndex + 1}/$pageCount  $readProgress"
            ReaderTipValueType.BOOK_NAME -> bookName
            ReaderTipValueType.TIME_BATTERY -> "$time $batteryPercent%"
            ReaderTipValueType.CHAPTER_INDEX_AND_TOTAL -> "${chapterIndex + 1}/$chapterCount"
            ReaderTipValueType.CHAPTER_TITLE_ARROW -> chapterTitle
            ReaderTipValueType.CUSTOM -> resolveCustom(customTemplate, context)
            ReaderTipValueType.WHOLE_BOOK_PAGE -> wholeBookPage()
            ReaderTipValueType.WHOLE_BOOK_PAGE_AND_PROGRESS -> "${wholeBookPage()}  $readProgress"
        }
    }

    private fun ReaderTipValueContext.wholeBookPage(): String =
        if (wholeBookPageIndex != null && wholeBookPageCount != null) {
            "$wholeBookPageIndex/$wholeBookPageCount"
        } else {
            "${pageIndex + 1}/$pageCount"
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
            .replace("{PageSize}", context.pageCount.toString())
            .replace("{PageRemaining}", (context.pageCount - context.pageIndex - 1).coerceAtLeast(0).toString())
            .replace("{ReadProgress}", context.readProgress)
            .replace("{FullPageIndex}", wholeIndex.toString())
            .replace("{FullPageSize}", wholeCount.toString())
    }
}
