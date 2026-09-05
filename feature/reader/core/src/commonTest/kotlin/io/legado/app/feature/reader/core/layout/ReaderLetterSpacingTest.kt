package io.legado.app.feature.reader.core.layout

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class ReaderLetterSpacingTest {
    private val style = ReaderTextStyle(0, 10f)
    private val config = ReaderPaginationConfig(0, "", 20, 100, 0f, 0f, 0f, 0f, 10f, 8f)
    private val paragraph = ReaderMeasuredParagraph("甲乙", listOf("甲", "乙"), listOf(10f, 10f), style, 0)
    private val inline = ReaderMeasuredBlock.InlineParagraph(listOf(
        ReaderMeasuredInlineItem.Text("甲", 10f, style, 0),
        ReaderMeasuredInlineItem.Text("乙", 10f, style, 1),
    ), 0, ReaderTextAlignment.START, 10f, 8f, 10f)

    @Test fun positiveSpacingParticipatesInWrappingForBothParagraphPaths() {
        for (block in listOf(ReaderMeasuredBlock.Paragraph(paragraph), inline)) {
            val page = ReaderPaginator.paginateBlocks(listOf(block), config.copy(letterSpacingPx = 3f)).single()
            assertEquals(listOf(0f, 10f), page.elements.map { it.bounds.top })
            assertTrue(page.elements.all { it.bounds.right <= 20f })
        }
    }

    @Test fun negativeSpacingAllowsTighterLinesWithoutChargingATrailingGap() {
        for (block in listOf(ReaderMeasuredBlock.Paragraph(paragraph), inline)) {
            val page = ReaderPaginator.paginateBlocks(listOf(block), config.copy(viewportWidthPx = 18, letterSpacingPx = -2f)).single()
            assertEquals(listOf(0f, 0f), page.elements.map { it.bounds.top })
            assertEquals(listOf(0f, 8f), page.elements.map { it.bounds.left })
        }
    }

    @Test fun exactFitAndCenteredAlignmentUseTheSameNaturalWidth() {
        val page = ReaderPaginator.paginateBlocks(listOf(inline.copy(alignment = ReaderTextAlignment.CENTER)),
            config.copy(viewportWidthPx = 25, letterSpacingPx = 3f)).single()
        val glyphs = page.elements.filterIsInstance<ReaderElement.Text>()
        assertEquals(listOf(1f, 14f), glyphs.map { it.bounds.left })
        assertEquals(listOf(0f, 0f), glyphs.map { it.bounds.top })
    }

    @Test fun paragraphSpacingOverridesBodyDefaultForTitleAndSubtitle() {
        val page = ReaderPaginator.paginateBlocks(listOf(
            inline.copy(letterSpacingPx = 4f, emphasized = true),
            inline.copy(letterSpacingPx = 1f, emphasized = true),
        ), config.copy(viewportWidthPx = 22, letterSpacingPx = 2f)).single()
        assertEquals(listOf(0f, 10f, 20f, 20f), page.elements.map { it.bounds.top })
        assertEquals(listOf(0f, 0f, 0f, 11f), page.elements.map { it.bounds.left })
    }
}
