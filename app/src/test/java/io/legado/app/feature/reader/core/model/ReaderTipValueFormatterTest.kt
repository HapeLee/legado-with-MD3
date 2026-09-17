package io.legado.app.feature.reader.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

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

    /** 宿主（`LegacyReaderPageDecorationFactory`）传入的是带本地化前缀的整书页数文案。 */
    @Test
    fun usesLocalizedWholeBookTextWhenProvided() {
        val localized = context.copy(wholeBookPageText = "全文 42 / 100")
        assertEquals(
            "全文 42 / 100",
            ReaderTipValueFormatter.format(ReaderTipValueType.WHOLE_BOOK_PAGE, localized),
        )
        assertEquals(
            "全文 42 / 100  24.0%",
            ReaderTipValueFormatter.format(
                ReaderTipValueType.WHOLE_BOOK_PAGE_AND_PROGRESS,
                localized
            ),
        )
    }

    /** 章还没排出任何页时页数未知：页码槽与 `{PageSize}`/`{PageRemaining}` 都给 `-`。 */
    @Test
    fun unknownChapterPageCountRendersDash() {
        val unknown = context.copy(
            pageCountText = ReaderTipValueFormatter.UNKNOWN_PAGE_COUNT,
            pageCount = 1,
        )
        assertEquals("2/-", ReaderTipValueFormatter.format(ReaderTipValueType.PAGE, unknown))
        assertEquals(
            "2/-  24.0%",
            ReaderTipValueFormatter.format(ReaderTipValueType.PAGE_AND_TOTAL, unknown),
        )
        assertEquals(
            "2/-|-",
            ReaderTipValueFormatter.format(
                ReaderTipValueType.CUSTOM,
                unknown,
                "{PageIndex}/{PageSize}|{PageRemaining}",
            ),
        )
    }
}
