package io.legado.app.feature.reader.core.layout

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun latinWordMovesIntactToNextLine() {
        val result = breakText("one two".map(Char::toString), 55)

        assertEquals(2, result.lineCount)
        assertArrayEquals(intArrayOf(0, 4, 7), result.lineStarts)
        assertArrayEquals(floatArrayOf(40f, 30f), result.lineWidthsPx, 0f)
    }

    @Test
    fun overlongLatinWordFallsBackToCharacterBreaks() {
        val result = breakText("abcdef".map(Char::toString), 25)

        assertEquals(3, result.lineCount)
        assertArrayEquals(intArrayOf(0, 2, 4, 6), result.lineStarts)
        assertArrayEquals(floatArrayOf(20f, 20f, 20f), result.lineWidthsPx, 0f)
    }

    @Test
    fun latinHandlingDoesNotInterceptCjkPunctuation() {
        val result = breakText(listOf("我", "是", "，", "三"), 25)

        assertEquals(3, result.lineCount)
        assertArrayEquals(intArrayOf(0, 1, 3, 4), result.lineStarts)
        assertArrayEquals(floatArrayOf(10f, 20f, 10f), result.lineWidthsPx, 0f)
    }

    @Test
    fun latinWordBeforeCjkPeriodStaysIntact() {
        val result = breakText("aa words。".map(Char::toString), 80)

        assertArrayEquals(intArrayOf(0, 3, 9), result.lineStarts)
        assertArrayEquals(floatArrayOf(30f, 60f), result.lineWidthsPx, 0f)
    }

    @Test
    fun latinWordBeforeCjkCommaStaysIntact() {
        val result = breakText("aa layout、".map(Char::toString), 90)

        assertArrayEquals(intArrayOf(0, 3, 10), result.lineStarts)
        assertArrayEquals(floatArrayOf(30f, 70f), result.lineWidthsPx, 0f)
    }

    @Test
    fun latinWordBeforeAsciiPeriodStaysIntact() {
        val result = breakText("aa lines.".map(Char::toString), 80)

        assertArrayEquals(intArrayOf(0, 3, 9), result.lineStarts)
        assertArrayEquals(floatArrayOf(30f, 60f), result.lineWidthsPx, 0f)
    }

    @Test
    fun rewindDoesNotSplitLatinWordBeforeClosingPunctuation() {
        val words = "aa word。”".map(Char::toString)
        val result = ChineseLineBreaker(
            words, List(7) { 10f } + listOf(4f, 4f), 0, 75, 10f, 0f,
        )

        assertArrayEquals(intArrayOf(0, 3, 9), result.lineStarts)
        assertArrayEquals(floatArrayOf(30f, 48f), result.lineWidthsPx, 0f)
    }

    @Test
    fun latinWordWithApostropheAndDigitsStaysIntactBeforePunctuation() {
        val result = breakText("aa R2D2's.".map(Char::toString), 90)

        assertArrayEquals(intArrayOf(0, 3, 10), result.lineStarts)
        assertArrayEquals(floatArrayOf(30f, 70f), result.lineWidthsPx, 0f)
    }

    @Test
    fun latinGraphemeClusterStaysWithItsWordBeforePunctuation() {
        val result = breakText(listOf("a", "a", " ", "c", "a", "f", "e\u0301", "."), 70)

        assertArrayEquals(intArrayOf(0, 3, 9), result.lineStarts)
        assertArrayEquals(floatArrayOf(30f, 50f), result.lineWidthsPx, 0f)
    }

    @Test
    fun overlongLatinWordBeforePunctuationStillUsesCharacterFallback() {
        val result = breakText("abcdef.".map(Char::toString), 25)

        assertArrayEquals(intArrayOf(0, 2, 4, 5, 7), result.lineStarts)
        assertArrayEquals(floatArrayOf(20f, 20f, 10f, 20f), result.lineWidthsPx, 0f)
    }

    /**
     * 超长 Latin 单词前有正文时：先整体移到下一行，再在下一行按字符回退。上一行因此变短，
     * 这是浏览器 `overflow-wrap: break-word` 的同类行为，锁住以免被当成回退。
     */
    @Test
    fun overlongLatinWordAfterTextMovesDownThenBreaksByCharacter() {
        val result = breakText("aa supercalifragilistic".map(Char::toString), 80)

        assertArrayEquals(intArrayOf(0, 3, 11, 19, 23), result.lineStarts)
        assertArrayEquals(floatArrayOf(30f, 80f, 80f, 40f), result.lineWidthsPx, 0f)
    }

    /**
     * Latin 词边界回退不得制造避头尾违规：词首前是开引号/开括号时（`isForbiddenBreak` 判定
     * 非法），必须退回原有断点，否则开标点会落到行尾。
     */
    @Test
    fun latinRewindNeverProducesForbiddenBreak() {
        val texts = listOf(
            "aa “word”",
            "中文“English”中文",
            "中文（English）中文",
            "中文「English」中文",
            "aa (word) bb",
            "aa 中文“word”",
            "请阅读“README.md”文件中的说明。",
            "他打开“Settings”面板，然后点击“About”。",
            "这本书引用了《The Great Gatsby》里的一句话。",
        )
        texts.forEach { text ->
            val clusters = text.map(Char::toString)
            listOf(40, 60, 80, 100).forEach { limit ->
                val starts = breakText(clusters, limit).lineStarts
                for (index in 1 until starts.size - 1) {
                    assertFalse(
                        "「$text」limit=$limit 断点非法：" +
                                "行尾「${clusters[starts[index] - 1]}」/ 行首「${clusters[starts[index]]}」",
                        ChineseLineBreaker.isForbiddenBreak(
                            clusters[starts[index] - 1],
                            clusters[starts[index]],
                        ),
                    )
                }
            }
        }
    }

    /** 安全回退生效时，原有的 Latin 整词换行收益必须保留。 */
    @Test
    fun latinWordBoundaryStillKeepsWordsIntactWithoutOpeningMark() {
        val result = breakText("中文English中文".map(Char::toString), 80)

        assertArrayEquals(intArrayOf(0, 2, 10, 11), result.lineStarts)
        assertArrayEquals(floatArrayOf(20f, 80f, 10f), result.lineWidthsPx, 0f)
    }
}
