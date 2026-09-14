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
     * 连续滚动模式按 scrollExtentPx 堆叠相邻页（ScrollPageStack / ReaderPageViewportLayout）。
     * 旧 `TextChapterLayout` 的卷名/空正文章把标题居中后按排版游标 `durY` 发布页高
     * （`emptyContent && textPages.isEmpty()` 分支 + 收尾的 `height = durY + 20dp`），
     * 所以页高只覆盖居中后的卷名本身：下一章正文紧接卷名下方排下去，不先顶满一屏空白。
     * 居中位移必须计入页高，否则下一章正文会压在本页卷名上。
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
        // 居中后标题字形落在 [85, 105]，页高 = 标题底边 − 内容区顶 = 105 − 10 = 95；
        // 未居中时标题仍在 [15, 35]，页高 = 排版游标 25f。两者都不是内容区高度 170f。
        assertEquals(95f, centered.scrollExtentPx, 0.01f)
        assertEquals(25f, topAnchored.scrollExtentPx, 0.01f)
    }
}
