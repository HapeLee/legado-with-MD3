package io.legado.app.feature.reader.core.layout

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import io.legado.app.feature.reader.core.navigation.ReaderPageNavigator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderDoublePageTest {
    private val style = ReaderTextStyle(0, 10f)
    private val config = ReaderPaginationConfig(
        chapterIndex = 2, chapterTitle = "", viewportWidthPx = 100, viewportHeightPx = 40,
        paddingLeftPx = 5f, paddingRightPx = 5f, paddingTopPx = 0f, paddingBottomPx = 0f,
        lineHeightPx = 20f, baselineOffsetPx = 15f, columnCount = 2,
    )
    private val text = "甲乙丙丁戊己庚辛壬癸子丑寅卯辰巳午未申酉"
    private fun paragraph(value: String, position: Int = 0) = ReaderMeasuredParagraph(
        value, value.map(Char::toString), List(value.length) { 10f }, style, position,
    )

    @Test
    fun fillsLeftThenRightBeforeTurningAndPreservesEveryPosition() {
        val pages = ReaderPaginator.paginate(listOf(paragraph(text)), config)
        assertEquals(2, pages.size)
        assertEquals(text, pages.joinToString("") { it.text })
        val glyphs = pages.flatMap { it.elements }.filterIsInstance<ReaderElement.Text>()
        assertEquals((text.indices).toList(), glyphs.map { it.chapterPosition })
        assertEquals(5f, glyphs[0].bounds.left, 0f)
        assertEquals(55f, glyphs[8].bounds.left, 0f)
        assertEquals(0f, glyphs[8].bounds.top, 0f)
        assertEquals(5f, glyphs[16].bounds.left, 0f)
        assertTrue(glyphs.all { it.bounds.right <= 95f && it.bounds.bottom <= 40f })
        assertEquals(16, pages.first().elements.size)
    }

    @Test
    fun imageBreaksAndRulesStayInsideTheirOwnColumnWithoutBlankExtraPage() {
        val blocks = listOf(
            ReaderMeasuredBlock.Image("left", 80f, 80f, 0, pageBreakBefore = true, pageBreakAfter = true),
            ReaderMeasuredBlock.Image("right", 80f, 80f, 1, pageBreakBefore = true, pageBreakAfter = true),
            ReaderMeasuredBlock.Rule(0, 2f, 0f),
        )
        val pages = ReaderPaginator.paginateBlocks(blocks, config)
        assertEquals(2, pages.size)
        assertEquals(listOf(5f, 55f), pages.first().elements.map { it.bounds.left })
        assertEquals(listOf(45f, 95f), pages.first().elements.map { it.bounds.right })
        val rule = pages.last().elements.single() as ReaderElement.Rule
        assertEquals(5f, rule.bounds.left, 0f)
        assertEquals(45f, rule.bounds.right, 0f)
    }

    @Test
    fun explicitBreakAdvancesColumnAndShortChapterLeavesRightColumnEmpty() {
        val pages = ReaderPaginator.paginateBlocks(listOf(
            ReaderMeasuredBlock.Paragraph(paragraph("甲")),
            ReaderMeasuredBlock.PageBreak,
            ReaderMeasuredBlock.Paragraph(paragraph("乙", 2)),
        ), config)
        assertEquals(1, pages.size)
        assertEquals(listOf(5f, 55f), pages.single().elements.map { it.bounds.left })
        val short = ReaderPaginator.paginate(listOf(paragraph("甲")), config).single()
        assertEquals(1, short.elements.size)
        assertEquals(5f, short.elements.single().bounds.left, 0f)
    }

    @Test
    fun reflowLocatesSameCharacterAndBookmarkRangeIncludesBothColumns() {
        val double = ReaderPaginator.paginate(listOf(paragraph(text)), config)
        val single = ReaderPaginator.paginate(listOf(paragraph(text)), config.copy(viewportWidthPx = 50, columnCount = 1))
        assertEquals(0, ReaderPageNavigator.locate(double, 2, 12))
        assertEquals(1, ReaderPageNavigator.locate(single, 2, 12))
        val context = ReaderPageNavigator.pageContext(double, 0)!!
        assertEquals(0, context.startPosition)
        assertEquals(16, context.endPosition)
    }
}
