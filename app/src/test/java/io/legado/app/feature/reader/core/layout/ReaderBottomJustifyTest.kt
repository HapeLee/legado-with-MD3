package io.legado.app.feature.reader.core.layout

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderBottomJustifyTest {
    private val style = ReaderTextStyle(0, 10f)

    private fun paragraph(text: String) = ReaderMeasuredBlock.Paragraph(
        ReaderMeasuredParagraph(
            text = text,
            clusters = text.map(Char::toString),
            clusterWidthsPx = FloatArray(text.length) { 10f },
            style = style,
            chapterPosition = 0,
        ),
    )

    @Test fun enabledSettingDistributesNearBottomSurplusAcrossTextRows() {
        val base = ReaderPaginationConfig(
            0, "", 10, 45, 0f, 0f, 0f, 5f, 10f, 8f,
            textBottomJustify = false,
        )
        val ordinary = ReaderPaginator.paginateBlocks(listOf(paragraph("甲乙丙")), base).single()
            .elements.filterIsInstance<ReaderElement.Text>()
        val justified = ReaderPaginator.paginateBlocks(
            listOf(paragraph("甲乙丙")),
            base.copy(textBottomJustify = true),
        ).single().elements.filterIsInstance<ReaderElement.Text>()

        assertEquals(listOf(0f, 10f, 20f), ordinary.map { it.bounds.top })
        assertEquals(listOf(0f, 15f, 30f), justified.map { it.bounds.top })
        assertEquals(40f, justified.last().bounds.bottom)
    }

    @Test fun doublePageJustifiesEachColumnIndependently() {
        val page = ReaderPaginator.paginateBlocks(
            listOf(paragraph("甲乙丙丁戊己")),
            ReaderPaginationConfig(
                0, "", 20, 40, 0f, 0f, 0f, 5f, 10f, 8f,
                columnCount = 2,
                textBottomJustify = true,
            ),
        ).single()
        val columns = page.elements.filterIsInstance<ReaderElement.Text>().groupBy { it.bounds.left }

        assertEquals(listOf(0f, 12.5f, 25f), columns.getValue(0f).map { it.bounds.top })
        assertEquals(listOf(0f, 12.5f, 25f), columns.getValue(10f).map { it.bounds.top })
        assertEquals(35f, columns.getValue(0f).last().bounds.bottom)
        assertEquals(35f, columns.getValue(10f).last().bounds.bottom)
    }

    @Test fun standaloneImageAtColumnBottomPreventsTextRowStretching() {
        val page = ReaderPaginator.paginateBlocks(
            listOf(
                paragraph("甲乙"),
                ReaderMeasuredBlock.Image("image", 10f, 10f, 2),
            ),
            ReaderPaginationConfig(
                0, "", 10, 45, 0f, 0f, 0f, 5f, 10f, 8f,
                textBottomJustify = true,
            ),
        ).single()
        val text = page.elements.filterIsInstance<ReaderElement.Text>()

        assertEquals(listOf(0f, 10f), text.map { it.bounds.top })
        assertEquals(20f, page.elements.filterIsInstance<ReaderElement.Image>().single().bounds.top)
    }

    /**
     * 旧 View 在 TextPage.upLinesPosition 里对拉伸量做了 `height += surplus`，
     * ContentTextView 再按该页高堆叠相邻页。滚动模式的 scrollExtentPx 必须保持同一语义，
     * 否则下一页会压在当前页被下移的最后几行上。
     */
    @Test
    fun continuousScrollExtentCoversJustifiedBottomLikeLegacyPageHeight() {
        val base = ReaderPaginationConfig(
            0, "", 40, 110, 0f, 0f, 0f, 0f, 20f, 15f,
            continuousScroll = true,
        )
        // 40px 宽、每字 10px：每行 4 字。7 行正文在 110px 内容区里断成 5 行 + 2 行两页。
        val text = "字".repeat(28)
        val ordinary = ReaderPaginator.paginateBlocks(listOf(paragraph(text)), base)
        val justified = ReaderPaginator.paginateBlocks(
            listOf(paragraph(text)),
            base.copy(textBottomJustify = true),
        )

        // 未拉伸时页高就是排版游标；拉伸后最后一行底边落到内容区底部，页高同步增加 10f。
        assertEquals(2, ordinary.size)
        assertEquals(100f, ordinary.first().scrollExtentPx, 0f)
        assertEquals(2, justified.size)
        assertEquals(110f, justified.first().scrollExtentPx, 0f)

        // 滚动栈把下一页画在上一页 scrollExtentPx 处（ScrollPageStack），因此页高必须
        // 覆盖拉伸后的行底，否则两页重叠。
        val justifiedNextTop = justified.first().scrollExtentPx +
                justified[1].elements.minOf { it.bounds.top }
        val justifiedBottom = justified.first().elements.maxOf { it.bounds.bottom }
        assertTrue(
            "滚动下一页顶部 $justifiedNextTop 不应早于上一页底部 $justifiedBottom",
            justifiedNextTop >= justifiedBottom,
        )
        val ordinaryNextTop = ordinary.first().scrollExtentPx +
                ordinary[1].elements.minOf { it.bounds.top }
        assertTrue(
            "未拉伸时相邻页也应无缝衔接",
            ordinaryNextTop >= ordinary.first().elements.maxOf { it.bounds.bottom },
        )
    }
}
