package io.legado.app.feature.reader.core.layout

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ChineseLineBreakerTest {
    private fun breakText(words: List<String>, widthPx: Int) = ChineseLineBreaker(
        words, List(words.size) { 10f }, 0, widthPx, 10f, 0f,
    )

    @Test fun normalBreak() {
        val result = breakText(listOf("我", "是", "一", "二", "三"), 25)
        assertEquals(3, result.lineCount)
        assertArrayEquals(intArrayOf(0, 2, 4, 5), result.lineStarts)
        assertArrayEquals(floatArrayOf(20f, 20f, 10f), result.lineWidthsPx, 0f)
    }

    @Test fun closingPunctuationDoesNotStartLine() {
        val result = breakText(listOf("我", "是", "，", "三"), 25)
        assertEquals(3, result.lineCount)
        assertArrayEquals(intArrayOf(0, 1, 3, 4), result.lineStarts)
        assertArrayEquals(floatArrayOf(10f, 20f, 10f), result.lineWidthsPx, 0f)
    }

    /**
     * 旧 ZhLayout CPS_1：连续两个后置标点且标点不可压缩时，标点留在本行、行宽允许超出
     * （`offset = 0f`）。取「行宽不超出」会把 `。”` 整体推到下一行行首。
     */
    @Test
    fun fullWidthClosingPunctuationHangsAtTheLineEnd() {
        val result = ChineseLineBreaker(
            listOf("我", "是", "一", "。", "”"),
            List(5) { 10f }, 0, 25, 10f, 0f,
        )
        assertEquals(2, result.lineCount)
        assertArrayEquals(intArrayOf(0, 2, 5), result.lineStarts)
        assertArrayEquals(floatArrayOf(20f, 30f), result.lineWidthsPx, 0f)
    }

    /** 窄标点（宽度小于一个汉字）仍按旧版 reCheck 回退，不悬挂。 */
    @Test
    fun narrowClosingPunctuationStillRewindsInsteadOfHanging() {
        val result = ChineseLineBreaker(
            listOf("我", "是", "一", "二", "。", "”"),
            listOf(10f, 10f, 10f, 10f, 4f, 4f), 0, 45, 10f, 0f,
        )
        assertEquals(2, result.lineCount)
        assertArrayEquals(intArrayOf(0, 3, 6), result.lineStarts)
        assertArrayEquals(floatArrayOf(30f, 18f), result.lineWidthsPx, 0f)
    }
}
