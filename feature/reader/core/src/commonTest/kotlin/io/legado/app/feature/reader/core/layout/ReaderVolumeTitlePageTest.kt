package io.legado.app.feature.reader.core.layout

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import kotlin.test.assertEquals
import kotlin.test.Test

/** 卷/空正文章整页只有标题时的垂直居中行为。 */
class ReaderVolumeTitlePageTest {
    private val style = ReaderTextStyle(0, 10f)

    private fun titleParagraph(value: String, position: Int = 0) = ReaderMeasuredParagraph(
        value, value.map(Char::toString), List(value.length) { 10f }, style, position,
        isTitle = true,
    )

    private fun config(centerVertical: Boolean) = ReaderPaginationConfig(
        chapterIndex = 0, chapterTitle = "第一卷", viewportWidthPx = 100, viewportHeightPx = 200,
        paddingLeftPx = 5f, paddingRightPx = 5f, paddingTopPx = 10f, paddingBottomPx = 20f,
        lineHeightPx = 20f, baselineOffsetPx = 15f,
        titleTopSpacingPx = 5f,
        titlePageCenterVertical = centerVertical,
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
}
