package io.legado.app.feature.reader.core.layout

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import kotlin.test.assertEquals
import kotlin.test.Test

class ReaderLineSpacingTest {
    private val style = ReaderTextStyle(0, 20f)
    private val config = ReaderPaginationConfig(0, "", 20, 50, 0f, 0f, 0f, 0f, 20f, 16f,
        lineSpacingMultiplier = 1.5f)
    private fun inline(text: String, multiplier: Float = 1.5f, title: Boolean = false) =
        ReaderMeasuredBlock.InlineParagraph(text.mapIndexed { index, char ->
            ReaderMeasuredInlineItem.Text(char.toString(), 20f, style, index)
        }, 0, ReaderTextAlignment.START, 20f, 16f, 20f,
            emphasized = title, lineSpacingMultiplier = multiplier)

    @Test fun paragraphUsesGlyphHeightForFitAndLineSpacingForAdvance() {
        val text = "甲乙丙"
        val paragraph = ReaderMeasuredParagraph(text, text.map(Char::toString), List(3) { 20f }, style, 0)
        val pages = ReaderPaginator.paginate(listOf(paragraph), config)
        assertEquals(listOf("甲乙", "丙"), pages.map { it.text })
        val glyphs = pages.first().elements.filterIsInstance<ReaderElement.Text>()
        assertEquals(listOf(0f, 30f), glyphs.map { it.bounds.top })
        assertEquals(listOf(20f, 50f), glyphs.map { it.bounds.bottom })
        assertEquals(listOf(16f, 46f), glyphs.map { it.baselinePx })
    }

    @Test fun inlineParagraphAndTitleUseIndependentLineSpacing() {
        val pages = ReaderPaginator.paginateBlocks(listOf(inline("甲乙丙")), config)
        assertEquals(listOf("甲乙", "丙"), pages.map { it.text })
        assertEquals(listOf(0f, 30f), pages.first().elements.map { it.bounds.top })
        val title = ReaderPaginator.paginateBlocks(listOf(inline("标题", 2f, true)), config.copy(viewportHeightPx = 60)).single()
        assertEquals(listOf(0f, 40f), title.elements.map { it.bounds.top })
        assertEquals(listOf(20f, 20f), title.elements.map { it.bounds.height })
    }

    @Test fun paragraphSpacingIsAdditionalToTheLastLineAdvance() {
        val page = ReaderPaginator.paginateBlocks(listOf(inline("甲"), inline("乙")),
            config.copy(viewportHeightPx = 60, paragraphSpacingPx = 5f)).single()
        assertEquals(listOf(0f, 35f), page.elements.map { it.bounds.top })
    }

    @Test fun doubledFontAndInlineImageDetermineHeightBeforeSpacingMultiplier() {
        val paragraph = inline("甲乙").copy(items = listOf(
            ReaderMeasuredInlineItem.Text("甲", 20f, style.copy(fontSizePx = 40f), 0),
            ReaderMeasuredInlineItem.Image("image", 20f, 30f, 1),
        ))
        val page = ReaderPaginator.paginateBlocks(listOf(paragraph), config.copy(viewportHeightPx = 90)).single()
        assertEquals(40f, page.elements[0].bounds.height, 0f)
        assertEquals(60f, page.elements[1].bounds.top, 0f)
        assertEquals(90f, page.elements[1].bounds.bottom, 0f)
    }

    @Test fun doubleColumnUsesSameFitRuleAndResetsAdvanceAtColumnBoundary() {
        val page = ReaderPaginator.paginateBlocks(listOf(inline("甲乙丙丁")),
            config.copy(viewportWidthPx = 40, columnCount = 2)).single()
        assertEquals(listOf(0f, 30f, 0f, 30f), page.elements.map { it.bounds.top })
        assertEquals(listOf(0f, 0f, 20f, 20f), page.elements.map { it.bounds.left })
    }
}
