package io.legado.app.feature.reader.core.layout

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 卷/空正文章整页只有标题时的垂直居中行为。 */
class ReaderVolumeTitlePageTest {
    private val style = ReaderTextStyle(0, 10f)

    private fun titleParagraph(value: String, position: Int = 0) = ReaderMeasuredParagraph(
        value, value.map(Char::toString), List(value.length) { 10f }, style, position,
        isTitle = true,
    )

    private fun config(centerVertical: Boolean, continuousScroll: Boolean = false) =
        ReaderPaginationConfig(
        chapterIndex = 0, chapterTitle = "第一卷", viewportWidthPx = 100, viewportHeightPx = 200,
        paddingLeftPx = 5f, paddingRightPx = 5f, paddingTopPx = 10f, paddingBottomPx = 20f,
        lineHeightPx = 20f, baselineOffsetPx = 15f,
        titleTopSpacingPx = 5f,
        titlePageCenterVertical = centerVertical,
            continuousScroll = continuousScroll,
    )

    @Test
    fun titleOnlyPageIsVerticallyCenteredInContentArea() {
        val page = ReaderPaginator.paginate(
            listOf(titleParagraph("第一卷 风起")), config(centerVertical = true),
        ).single()
        val glyphs = page.elements.filterIsInstance<ReaderElement.Text>()
        val top = glyphs.minOf { it.bounds.top }
        val bottom = glyphs.maxOf { it.bounds.bottom }
        // 内容区 [10, 180]，占位 20f：上留白应等于下留白
        assertEquals(85f, top, 0.01f)
        assertEquals(105f, bottom, 0.01f)
        assertEquals(170f - (top - 10f), bottom - 10f, 0.01f)
    }

    @Test
    fun defaultLayoutKeepsTitleTopAnchored() {
        val page = ReaderPaginator.paginate(
            listOf(titleParagraph("第一卷 风起")), config(centerVertical = false),
        ).single()
        val top = page.elements.minOf { it.bounds.top }
        assertEquals(15f, top, 0f)
    }

    @Test
    fun baselineShiftsTogetherWithBounds() {
        val page = ReaderPaginator.paginate(
            listOf(titleParagraph("第一卷")), config(centerVertical = true),
        ).single()
        val glyphs = page.elements.filterIsInstance<ReaderElement.Text>()
        val top = glyphs.minOf { it.bounds.top }
        // 居中后 titleTopSpacing 被抵消：基线 = 内容区顶 + 半留白 + baselineOffset
        glyphs.forEach { glyph ->
            assertEquals(top + 15f, glyph.baselinePx, 0.01f)
        }
    }

    /**
     * 连续滚动模式按 scrollExtentPx 堆叠相邻页（ScrollPageStack / ReaderPageViewportLayout），
     * 短页的页高下限是「内容区高度」，因此卷名页无论是否居中都会撑满一屏；否则下一章正文
     * 会压在本页卷名上。同见 `ReaderLongImageScrollTest
     * .continuousPagesExcludeFixedViewportChromeFromTheirStackingExtent`。
     */
    @Test
    fun scrollModeVerticallyCenteredTitleStaysInsidePageExtent() {
        val centered = ReaderPaginator.paginate(
            listOf(titleParagraph("第一卷 风起")),
            config(centerVertical = true, continuousScroll = true),
        ).single()
        val topAnchored = ReaderPaginator.paginate(
            listOf(titleParagraph("第一卷 风起")),
            config(centerVertical = false, continuousScroll = true),
        ).single()
        val centeredBottom = centered.elements.filterIsInstance<ReaderElement.Text>()
            .maxOf { it.bounds.bottom }
        // 下一章从 contentTopPx + scrollExtentPx 处开始绘制：卷名必须落在本页页高内。
        assertTrue(
            "卷名 bottom=$centeredBottom 超出本页页高 " +
                    "${centered.contentTopPx + centered.scrollExtentPx}",
            centeredBottom <= centered.contentTopPx + centered.scrollExtentPx,
        )
        // 内容区高度 = 200 − 10 − 20 = 170：短页一律取该下限，居中与否都不改变页高。
        assertEquals(170f, centered.scrollExtentPx, 0.01f)
        assertEquals(170f, topAnchored.scrollExtentPx, 0.01f)
    }
}
