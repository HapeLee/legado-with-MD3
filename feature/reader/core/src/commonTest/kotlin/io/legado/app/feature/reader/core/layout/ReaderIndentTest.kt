package io.legado.app.feature.reader.core.layout

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import io.legado.app.feature.reader.core.source.ReaderChapterSourceParser
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class ReaderIndentTest {
    private val shaper = ReaderTextShaper { text -> GlyphClusters(text.map(Char::toString), text.map {
        when (it) { 'A' -> 5f; ',' -> 3f; else -> 10f }
    }) }
    private val style = ReaderChapterMeasureStyle(ReaderTextStyle(0, 10f), ReaderTextStyle(0, 10f),
        2, ReaderTextAlignment.START, ReaderTextAlignment.START)
    private val config = ReaderPaginationConfig(0, "", 47, 100, 0f, 0f, 0f, 0f, 10f, 8f)

    private suspend fun layout(text: String, style: ReaderChapterMeasureStyle = this.style): List<ReaderElement.Text> {
        val source = ReaderChapterSourceParser.parse(0, "", listOf(text), false, false)
        val measured = ReaderChapterBlockMeasurer(shaper, shaper, { null }).measure(source, style)
            as ReaderChapterMeasureResult.Success
        return ReaderPaginator.paginateBlocks(measured.blocks, config).flatMap { it.elements }
            .filterIsInstance<ReaderElement.Text>()
    }

    @Test fun processedParagraphDoesNotReceiveASecondIndent() = runBlocking {
        val glyphs = layout("　　A甲乙丙")
        assertTrue(abs(20f - glyphs.first { it.value == "A" }.bounds.left) < 1e-5f)
        assertEquals(2, glyphs.first { it.value == "A" }.chapterPosition)
        assertEquals("　　A甲乙丙", glyphs.joinToString("") { it.value })
    }

    @Test fun virtualIndentDoesNotDependOnTheFirstBodyCharacter() = runBlocking {
        for (text in listOf("A甲", ",甲", "甲乙")) {
            assertTrue(abs(20f - layout(text).first().bounds.left) < 1e-5f, text)
        }
    }

    @Test fun justificationDoesNotStretchTheIndentPrefix() = runBlocking {
        val glyphs = layout("　　A甲乙丙", style.copy(bodyAlignment = ReaderTextAlignment.JUSTIFY))
        assertTrue(abs(20f - glyphs.first { it.value == "A" }.bounds.left) < 1e-5f)
        assertTrue(abs(47f - glyphs.first { it.value == "乙" }.bounds.right) < 1e-5f)
        assertTrue(abs(0f - glyphs.first { it.value == "丙" }.bounds.left) < 1e-5f)
    }

    @Test fun customIndentTextAndLetterSpacingAreMeasuredIndependently() = runBlocking {
        val custom = style.copy(bodyIndentText = "AA", letterSpacingEm = 0.1f)
        assertTrue(abs(12f - layout("甲乙", custom).first().bounds.left) < 1e-5f)
        assertTrue(abs(12f - layout("AA甲乙", custom).first { it.value == "甲" }.bounds.left) < 1e-5f)
        assertTrue(abs(0f - layout("A甲", style.copy(bodyIndentCharacters = 0)).first().bounds.left) < 1e-5f)
    }

    @Test fun textAfterAStandaloneImageInTheSameParagraphIsNotIndentedAgain() = runBlocking {
        val source = ReaderChapterSourceParser.parse(0, "", listOf("A<img src=\"cover\">甲"), false, false)
        val measured = ReaderChapterBlockMeasurer(shaper, shaper, { ReaderImageDimensions(100f, 100f) })
            .measure(source, style) as ReaderChapterMeasureResult.Success
        val paragraphs = measured.blocks.filterIsInstance<ReaderMeasuredBlock.InlineParagraph>()
        assertEquals(2, paragraphs.size)
        assertTrue(abs(20f - (paragraphs.first().indentWidthPx ?: -1f)) < 1e-5f)
        assertTrue(abs(0f - (paragraphs.last().indentWidthPx ?: -1f)) < 1e-5f)
        assertEquals(2, paragraphs.last().items.first().chapterPosition)
    }
}
