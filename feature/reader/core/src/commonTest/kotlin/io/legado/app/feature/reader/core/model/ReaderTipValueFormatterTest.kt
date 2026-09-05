package io.legado.app.feature.reader.core.model

import kotlin.test.assertEquals
import kotlin.test.Test

class ReaderTipValueFormatterTest {
    private val context = ReaderTipValueContext(
        bookName = "书名",
        chapterTitle = "第三章",
        time = "12:34",
        batteryPercent = 76,
        chapterIndex = 2,
        chapterCount = 10,
        pageIndex = 1,
        pageCount = 5,
        readProgress = "24.0%",
        wholeBookPageIndex = 42,
        wholeBookPageCount = 100,
    )

    @Test
    fun formatsBuiltInPageTips() {
        assertEquals("2/5", ReaderTipValueFormatter.format(ReaderTipValueType.PAGE, context))
        assertEquals("2/5  24.0%", ReaderTipValueFormatter.format(ReaderTipValueType.PAGE_AND_TOTAL, context))
        assertEquals("12:34 76%", ReaderTipValueFormatter.format(ReaderTipValueType.TIME_BATTERY, context))
        assertEquals("42/100  24.0%", ReaderTipValueFormatter.format(ReaderTipValueType.WHOLE_BOOK_PAGE_AND_PROGRESS, context))
    }

    @Test
    fun resolvesEveryCustomPlaceholder() {
        val template = "{BookName}|{ChapterTitle}|{Time}|{BatteryPercent}|{ChapterIndex}/{ChapterSize}|" +
            "{PageIndex}/{PageSize}|{PageRemaining}|{ReadProgress}|{FullPageIndex}/{FullPageSize}"
        assertEquals(
            "书名|第三章|12:34|76%|3/10|2/5|3|24.0%|42/100",
            ReaderTipValueFormatter.format(ReaderTipValueType.CUSTOM, context, template),
        )
    }
}
