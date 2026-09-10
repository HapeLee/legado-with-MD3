package io.legado.app.feature.reader.core.layout

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPageUnderlineTest {
    private val style = ReaderTextStyle(0xff111111.toInt(), 10f)

    private fun paragraph(text: String, indent: Int = 0) = ReaderMeasuredBlock.Paragraph(
        ReaderMeasuredParagraph(
            text,
            text.map(Char::toString),
            List(text.length) { 10f },
            style,
            0,
            indentCharacters = indent,
        ),
    )

    @Test fun pageUnderlineUsesRenderedLineBoundsAndConfiguredDashPattern() {
        val page = ReaderPaginator.paginateBlocks(
            listOf(paragraph("甲乙", indent = 1)),
            ReaderPaginationConfig(
                0, "", 40, 40, 0f, 0f, 0f, 0f, 10f, 8f,
                pageUnderline = ReaderPageUnderline(
                    colorArgb = 0xff123456.toInt(),
                    widthPx = 2f,
                    offsetPx = 3f,
                    extendToColumn = false,
                    dashed = true,
                    dashOnPx = 7f,
                    dashOffPx = 4f,
                ),
            ),
        ).single()
        val underline = page.elements.filterIsInstance<ReaderElement.Rule>().single()

        assertEquals(10f, underline.bounds.left)
        assertEquals(30f, underline.bounds.right)
        assertEquals(13f, underline.bounds.top)
        assertEquals(0xff123456.toInt(), underline.colorArgb)
        assertEquals(2f, underline.widthPx)
        assertTrue(underline.dashed)
        assertEquals(7f, underline.dashOnPx)
        assertEquals(4f, underline.dashOffPx)
        assertTrue(underline.overlayStyledUnderline)
    }

    @Test
    fun nonExtendedUnderlineSkipsRetainedIndentGlyphs() {
        val items = listOf(
            ReaderMeasuredInlineItem.Text("　", 10f, style, 0),
            ReaderMeasuredInlineItem.Text("　", 10f, style, 1),
            ReaderMeasuredInlineItem.Text("甲", 10f, style, 2),
            ReaderMeasuredInlineItem.Text("乙", 10f, style, 3),
        )
        val page = ReaderPaginator.paginateBlocks(
            listOf(
                ReaderMeasuredBlock.InlineParagraph(
                    items = items,
                    indentCharacters = 0,
                    alignment = ReaderTextAlignment.START,
                    lineHeightPx = 10f,
                    baselineOffsetPx = 8f,
                    baseTextSizePx = 10f,
                    leadingIndentItems = 2,
                ),
            ),
            ReaderPaginationConfig(
                0, "", 40, 40, 0f, 0f, 0f, 0f, 10f, 8f,
                pageUnderline = ReaderPageUnderline(
                    0xff123456.toInt(), 1f, 0f, false, false,
                ),
            ),
        ).single()

        val underline = page.elements.filterIsInstance<ReaderElement.Rule>().single()
        assertEquals(20f, underline.bounds.left)
        assertEquals(40f, underline.bounds.right)
    }

    @Test fun contentRuleKeepsNormalContentLayer() {
        val page = ReaderPaginator.paginateBlocks(
            listOf(
                ReaderMeasuredBlock.Rule(
                    colorArgb = 0xff000000.toInt(),
                    widthPx = 2f,
                    dashed = false,
                    verticalPaddingPx = 3f,
                ),
            ),
            ReaderPaginationConfig(0, "", 40, 40, 0f, 0f, 0f, 0f, 10f, 8f),
        ).single()

        assertFalse(page.elements.filterIsInstance<ReaderElement.Rule>().single().overlayStyledUnderline)
    }

    @Test fun extendedUnderlineUsesOnlyItsCurrentDoublePageColumn() {
        val page = ReaderPaginator.paginateBlocks(
            listOf(paragraph("甲乙丙丁戊己")),
            ReaderPaginationConfig(
                0, "", 40, 35, 5f, 0f, 5f, 5f, 10f, 8f,
                columnCount = 2,
                pageUnderline = ReaderPageUnderline(0xff000000.toInt(), 1f, 0f, true, false),
            ),
        ).single()
        val underlines = page.elements.filterIsInstance<ReaderElement.Rule>()

        assertEquals(listOf(5f, 5f, 5f, 25f, 25f, 25f), underlines.map { it.bounds.left })
        assertEquals(listOf(15f, 15f, 15f, 35f, 35f, 35f), underlines.map { it.bounds.right })
    }

    @Test fun bottomJustificationMovesUnderlineWithItsTextRow() {
        val page = ReaderPaginator.paginateBlocks(
            listOf(paragraph("甲乙丙")),
            ReaderPaginationConfig(
                0, "", 10, 45, 0f, 0f, 0f, 5f, 10f, 8f,
                textBottomJustify = true,
                pageUnderline = ReaderPageUnderline(0xff000000.toInt(), 1f, 0f, false, false),
            ),
        ).single()

        assertEquals(
            listOf(10f, 25f, 40f),
            page.elements.filterIsInstance<ReaderElement.Rule>().map { it.bounds.top },
        )
    }
}
