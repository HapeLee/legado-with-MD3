package io.legado.app.feature.reader.core.layout

import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertTrue

class ChineseLineBreakerTest {
    private fun breakText(words: List<String>, widthPx: Int) = ChineseLineBreaker(
        words, List(words.size) { 10f }, 0, widthPx, 10f, 0f,
    )

    @Test fun normalBreak() {
        val result = breakText(listOf("我", "是", "一", "二", "三"), 25)
        assertEquals(3, result.lineCount)
        assertTrue(intArrayOf(0, 2, 4, 5).contentEquals(result.lineStarts))
        assertTrue(floatArrayOf(20f, 20f, 10f).contentEquals(result.lineWidthsPx))
    }

    @Test fun closingPunctuationDoesNotStartLine() {
        val result = breakText(listOf("我", "是", "，", "三"), 25)
        assertEquals(3, result.lineCount)
        assertTrue(intArrayOf(0, 1, 3, 4).contentEquals(result.lineStarts))
        assertTrue(floatArrayOf(10f, 20f, 10f).contentEquals(result.lineWidthsPx))
    }
}
