package io.legado.app.feature.reader.core.layout

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPunctuationLayoutTest {
    private val style = ReaderTextStyle(0xff111111.toInt(), 10f)
    private val config = ReaderPaginationConfig(
        chapterIndex = 0,
        chapterTitle = "",
        viewportWidthPx = 35,
        viewportHeightPx = 1000,
        paddingLeftPx = 5f,
        paddingTopPx = 0f,
        paddingRightPx = 5f,
        paddingBottomPx = 0f,
        lineHeightPx = 10f,
        baselineOffsetPx = 8f,
    )

    private fun paragraph(value: String) = ReaderMeasuredBlock.InlineParagraph(
        items = value.mapIndexed { index, char ->
            ReaderMeasuredInlineItem.Text(char.toString(), 10f, style, index)
        },
        indentCharacters = 0,
        alignment = ReaderTextAlignment.START,
        lineHeightPx = 10f,
        baselineOffsetPx = 8f,
        baseTextSizePx = 10f,
    )

    /** #2499: the breaker's natural width may exceed the limit; published glyphs must fit. */
    @Test
    fun hangingClosingPunctuationFitsEvenOnTheLastLineAndWithoutJustification() {
        val value = "我是一。”"
        for (alignment in ReaderTextAlignment.entries) {
            for (html in listOf(false, true)) {
                val page = ReaderPaginator.paginateBlocks(
                    listOf(paragraph(value).copy(
                        alignment = alignment,
                        justifyAtWordBoundaries = html,
                    )),
                    config,
                ).single()
                val glyphs = page.elements.filterIsInstance<ReaderElement.Text>()
                val rows = glyphs.groupBy { it.bounds.top }.values.toList()

                assertEquals(listOf("我是", "一。”"), rows.map { row -> row.joinToString("") { it.value } })
                assertTrue("alignment=$alignment html=$html", glyphs.all {
                    it.bounds.left >= 5f && it.bounds.right <= 30.001f
                })
                assertEquals(listOf(5f, 12.5f, 20f), rows.last().map { it.bounds.left })
                assertEquals(value, page.text)
                assertEquals(value.indices.toList(), glyphs.map { it.chapterPosition })
            }
        }
    }

    @Test
    fun punctuationFitsAcrossWidthsIndentsAndLetterSpacingWithoutChangingTextPositions() {
        val value = "“公子可以和我说话，我能听到公子心声。”林仙儿说道。“我知道。”"
        for (width in listOf(45, 80, 125)) {
            for (spacing in listOf(-1f, 0f, 2f)) {
                val pageConfig = config.copy(viewportWidthPx = width, letterSpacingPx = spacing)
                val page = ReaderPaginator.paginateBlocks(
                    listOf(paragraph(value).copy(
                        indentWidthPx = 10f,
                        restLineIndentWidthPx = 5f,
                        alignment = ReaderTextAlignment.JUSTIFY,
                    )),
                    pageConfig,
                ).single()
                val glyphs = page.elements.filterIsInstance<ReaderElement.Text>()
                val rows = glyphs.groupBy { it.bounds.top }.values

                assertEquals(value, page.text)
                assertEquals(value.indices.toList(), glyphs.map { it.chapterPosition })
                assertTrue("width=$width spacing=$spacing", glyphs.all {
                    it.bounds.left >= 5f && it.bounds.right <= width - 5f + 0.001f
                })
                rows.zipWithNext().forEach { (previous, next) ->
                    assertFalse(ChineseLineBreaker.isForbiddenBreak(previous.last().value, next.first().value))
                }
            }
        }
    }

    @Test
    fun closingStraightQuoteAndPipeStayOnTheSentenceLineInsideTheMargins() {
        for (mark in listOf("\"", "|")) {
            val value = "${mark}Long Night.$mark"
            val page = ReaderPaginator.paginateBlocks(
                listOf(paragraph(value)), config.copy(viewportWidthPx = 130),
            ).single()
            val glyphs = page.elements.filterIsInstance<ReaderElement.Text>()

            assertEquals(1, glyphs.map { it.bounds.top }.distinct().size)
            assertTrue(glyphs.all { it.bounds.left >= 5f && it.bounds.right <= 125.001f })
            assertEquals(value, page.text)
        }
    }

    @Test
    fun closingQuoteAfterASourceSpaceDoesNotGetItsOwnLine() {
        val value = "\"Long Night. \""
        val page = ReaderPaginator.paginateBlocks(
            listOf(paragraph(value)), config.copy(viewportWidthPx = 140),
        ).single()
        val glyphs = page.elements.filterIsInstance<ReaderElement.Text>()

        assertEquals(1, glyphs.map { it.bounds.top }.distinct().size)
        assertTrue(glyphs.all { it.bounds.right <= 135.001f })
        assertEquals(value, page.text)
    }

    @Test
    fun englishDialogueKeepsOpeningAndClosingMarksAttachedAcrossVisualLines() {
        val values = listOf(
            "\"Not today,\" the reader replied. \"This sentence has enough words to wrap across several narrow lines.\"",
            "|Goal: keep this message together when the closing pipe reaches the edge of a narrow page.|",
            "\"A short example,\" she said. \"Another sentence checks the opening quote after an earlier closing quote.\"",
        )
        for (value in values) {
            for (width in listOf(130, 180, 235)) {
                for (html in listOf(false, true)) {
                    val page = ReaderPaginator.paginateBlocks(
                        listOf(paragraph(value).copy(
                            alignment = ReaderTextAlignment.JUSTIFY,
                            justifyAtWordBoundaries = html,
                        )),
                        config.copy(viewportWidthPx = width),
                    ).single()
                    val glyphs = page.elements.filterIsInstance<ReaderElement.Text>()
                    val rows = glyphs.groupBy { it.bounds.top }.values

                    assertEquals(value, page.text)
                    assertEquals(value.indices.toList(), glyphs.map { it.chapterPosition })
                    rows.forEach { row ->
                        val first = row.first()
                        val last = row.last { it.value.isNotBlank() }
                        if (first.value in listOf("\"", "|")) {
                            assertTrue("Closing mark starts a row: $value at ${first.chapterPosition}",
                                first.chapterPosition == 0 || value[first.chapterPosition - 1].isWhitespace())
                        }
                        if (last.value in listOf("\"", "|")) {
                            assertTrue("Opening mark ends a row: $value at ${last.chapterPosition}",
                                last.chapterPosition == value.lastIndex || value[last.chapterPosition + 1].isWhitespace())
                        }
                    }
                    assertTrue(glyphs.filterNot { it.value.isBlank() }.all {
                        it.bounds.left >= 5f && it.bounds.right <= width - 5f + 0.001f
                    })
                }
            }
        }
    }

    @Test
    fun invisibleTrailingWordSpaceDoesNotTightenVisibleLetters() {
        val page = ReaderPaginator.paginateBlocks(
            listOf(paragraph("one two")), config.copy(viewportWidthPx = 45),
        ).single()
        val firstWord = page.elements.filterIsInstance<ReaderElement.Text>().take(3)

        assertEquals(listOf(5f, 15f, 25f), firstWord.map { it.bounds.left })
    }
}
