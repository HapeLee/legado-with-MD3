package io.legado.app.feature.reader.legacy

import android.app.Application
import io.legado.app.data.entities.HighlightRule
import io.legado.app.feature.reader.core.source.ReaderChapterSource
import io.legado.app.feature.reader.core.source.ReaderChapterSourceParser
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

/**
 * 命中排版的分段口径：字距属于「命中段与相邻字之间」，所以只有段首那一个字让出 before、
 * 段末那一个字让出 after，段内一个字都不加；行距是行级属性，三段都带上，由分页按整行取较大值。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class LegacyReaderStyleRangeMapperTest {
    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
    }

    /** 「张三：“我是李四。”他惊了！」里 我=4 是=5 李=6 四=7。 */
    private fun body(text: String): ReaderChapterSource = ReaderChapterSourceParser.parse(
        chapterIndex = 0,
        title = "",
        paragraphs = listOf(text),
        includeTitle = false,
        adaptSpecialStyle = false,
    )

    private fun rangesFor(rule: HighlightRule) = LegacyReaderStyleRangeMapper.map(
        body("张三：“我是李四。”他惊了！"),
        listOf(rule),
        emptyList(),
    )

    @Test
    fun hitLetterSpacingSplitsIntoHeadBodyAndTail() {
        val ranges = rangesFor(
            HighlightRule(
                pattern = "我是李四",
                targetScope = HighlightRule.TARGET_BODY,
                letterSpacingBefore = 6f,
                letterSpacingAfter = 8f,
                lineSpacingTop = 2f,
                lineSpacingBottom = 3f,
            )
        )

        // 三段互不重叠：首字 4、段内 5..6、末字 7。
        assertEquals(listOf(4, 5, 7), ranges.map { it.start })
        assertEquals(listOf(5, 7, 8), ranges.map { it.endExclusive })
        assertEquals(listOf(6f, 0f, 0f), ranges.map { it.style.matchSpacingBeforePx })
        assertEquals(listOf(0f, 0f, 8f), ranges.map { it.style.matchSpacingAfterPx })
        // 行距三段都带：同一行里两条命中也只抬一次，谁大用谁。
        assertEquals(listOf(2f, 2f, 2f), ranges.map { it.style.linePadTopPx })
        assertEquals(listOf(3f, 3f, 3f), ranges.map { it.style.linePadBottomPx })
    }

    @Test
    fun aSingleCharacterHitCarriesBothSides() {
        val ranges = rangesFor(
            HighlightRule(
                pattern = "李",
                targetScope = HighlightRule.TARGET_BODY,
                letterSpacingBefore = 6f,
                letterSpacingAfter = 8f,
            )
        )

        // 它既是段首也是段尾，两边都要让，所以合成一段而不是三段。
        assertEquals(1, ranges.size)
        assertEquals(6, ranges.first().start)
        assertEquals(7, ranges.first().endExclusive)
        assertEquals(6f, ranges.first().style.matchSpacingBeforePx)
        assertEquals(8f, ranges.first().style.matchSpacingAfterPx)
    }

    @Test
    fun unsetSpacingLeavesTheStyleAtZero() {
        val ranges = rangesFor(
            HighlightRule(pattern = "我是李四", targetScope = HighlightRule.TARGET_BODY)
        )

        assertEquals(listOf(4, 5, 7), ranges.map { it.start })
        ranges.forEach {
            assertEquals(0f, it.style.matchSpacingBeforePx)
            assertEquals(0f, it.style.matchSpacingAfterPx)
            assertEquals(0f, it.style.linePadTopPx)
            assertEquals(0f, it.style.linePadBottomPx)
        }
    }
}
