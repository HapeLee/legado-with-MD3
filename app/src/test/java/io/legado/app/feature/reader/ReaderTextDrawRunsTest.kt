package io.legado.app.feature.reader

import io.legado.app.feature.reader.core.layout.clusterGlyphs
import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderRect
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTextDrawRunsTest {
    private val style = ReaderTextStyle(0, 10f)
    private val glyphWidthPx = 10f

    /** 与排版一致：bounds.right = left + 字宽，下一个 bounds.left = 上一个 right + gap。 */
    private fun row(values: List<String>, gapPx: Float): List<ReaderElement.Text> {
        var left = 0f
        return values.mapIndexed { index, value ->
            val element = ReaderElement.Text(
                bounds = ReaderRect(left, 0f, left + glyphWidthPx, 12f),
                baselinePx = 10f,
                value = value,
                style = style,
                selected = false,
                emphasized = false,
                chapterPosition = index,
            )
            left += glyphWidthPx + gapPx
            element
        }
    }

    @Test
    fun mergedRunStartsAtFirstBoundsLeftAndKeepsEveryGlyphOnItsLayoutX() {
        val elements = row(listOf("甲", "乙", "丙"), gapPx = 2f)
        val runs = buildMergedTextRuns(elements)

        assertEquals(3, runs.size)
        val run = runs.getValue(elements[0])
        assertSame(elements[0], run.first)
        assertEquals("甲乙丙", run.text)
        assertEquals(2f, run.gapExtraPx, 0f)
        assertEquals(elements[0].bounds.left, run.mergedDrawStartLeftPx(), 0f)

        // 一次 drawText：画笔在每个字符后插入 gapExtraPx，字形宽度等于排版宽度。
        var x = run.mergedDrawStartLeftPx()
        elements.forEachIndexed { index, element ->
            assertEquals("第 $index 个字", element.bounds.left, x, 0.001f)
            x += element.bounds.width + run.gapExtraPx
        }
    }

    @Test
    fun zeroAndNegativeGapsDoNotShiftTheRunEither() {
        for (gap in listOf(0f, -1f)) {
            val elements = row(listOf("甲", "乙", "丙"), gapPx = gap)
            val runs = buildMergedTextRuns(elements)
            val run = runs.getValue(elements[0])
            assertEquals(gap, run.gapExtraPx, 0f)
            assertEquals("gap=$gap", elements[0].bounds.left, run.mergedDrawStartLeftPx(), 0f)
            var x = run.mergedDrawStartLeftPx()
            elements.forEachIndexed { index, element ->
                assertEquals("gap=$gap 第 $index 个字", element.bounds.left, x, 0.001f)
                x += element.bounds.width + run.gapExtraPx
            }
        }
    }

    @Test
    fun combiningMarksAndSurrogatePairsFallBackToPerElementDrawing() {
        // 排版把零宽后续字符并入同一个元素，这些 value 会含多个 UTF-16 code unit。
        assertEquals(listOf("e\u0301"), clusterGlyphs("e\u0301", floatArrayOf(10f, 0f)).text)
        assertEquals(listOf("\uD83D\uDE00"), clusterGlyphs("\uD83D\uDE00", floatArrayOf(10f, 0f)).text)

        val combining = "e\u0301"
        val variation = "字\uFE0F"
        val emoji = "\uD83D\uDE00"
        val elements = row(
            listOf("甲", "乙", combining, variation, emoji, "丙", "丁"),
            gapPx = 2f,
        )
        val runs = buildMergedTextRuns(elements)

        assertFalse(runs.containsKey(elements[2]))
        assertFalse(runs.containsKey(elements[3]))
        assertFalse(runs.containsKey(elements[4]))
        assertEquals("甲乙", runs.getValue(elements[0]).text)
        assertEquals("丙丁", runs.getValue(elements[5]).text)
        assertEquals(
            elements[0].bounds.left,
            runs.getValue(elements[0]).mergedDrawStartLeftPx(),
            0f,
        )
        assertEquals(
            elements[5].bounds.left,
            runs.getValue(elements[5]).mergedDrawStartLeftPx(),
            0f,
        )
        assertTrue(runs.values.all { run -> run.text.all { it.code !in 0xD800..0xDFFF } })
    }
}
