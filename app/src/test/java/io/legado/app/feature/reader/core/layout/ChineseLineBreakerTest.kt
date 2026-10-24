package io.legado.app.feature.reader.core.layout

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ChineseLineBreakerTest {
    private fun breakText(words: List<String>, widthPx: Int) = ChineseLineBreaker(
        words, FloatArray(words.size) { 10f }, 0, widthPx, 10f, 0f,
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
            FloatArray(5) { 10f }, 0, 25, 10f, 0f,
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
            floatArrayOf(10f, 10f, 10f, 10f, 4f, 4f), 0, 45, 10f, 0f,
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

    /**
     * 词间空格只作行尾断点：断在空格上时，空格留在上一行行尾（尾随空格不可见），
     * 下一行仍从词首开始。否则英文正文每换一行都会在第一个词前多出一个空格。
     */
    @Test
    fun wordSpaceStaysAtTheEndOfThePreviousLine() {
        val clusters = "one two three four five six seven".map(Char::toString)
        for (limit in 25..120) {
            val result = breakText(clusters, limit)
            val starts = result.lineClusterStarts
            for (line in 1 until result.lineCount) {
                assertFalse(
                    "limit=$limit 第 $line 行以空格开头",
                    ChineseLineBreaker.isWordSpace(clusters[starts[line]]),
                )
            }
        }
    }

    /** 连续多个空格同样不能把后面的空格带到行首。 */
    @Test
    fun consecutiveWordSpacesStayAtTheEndOfThePreviousLine() {
        val result = breakText("aa  bb".map(Char::toString), 35)

        assertArrayEquals(intArrayOf(0, 4, 6), result.lineClusterStarts)
        assertArrayEquals(floatArrayOf(40f, 20f), result.lineWidthsPx, 0f)
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
            words, FloatArray(7) { 10f } + floatArrayOf(4f, 4f), 0, 75, 10f, 0f,
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
     * 词首前是开括号时，开括号连同单词一起移到下一行行首（开括号在行首合法），
     * 单词不再被拆开。#2449：`...the [Energy` 在行尾被拆成 `[E|nergy`。
     */
    @Test
    fun latinWordAfterOpeningBracketMovesIntactToNextLine() {
        val result = breakText("aa [Energy]".map(Char::toString), 90)

        assertArrayEquals(intArrayOf(0, 3, 11), result.lineStarts)
        assertArrayEquals(floatArrayOf(30f, 80f), result.lineWidthsPx, 0f)
    }

    /** 开引号与开括号同样连词带回下一行：`aa “word”` → `aa ` / `“word”`。 */
    @Test
    fun latinWordAfterOpeningQuoteMovesIntactToNextLine() {
        val result = breakText("aa “word”".map(Char::toString), 60)

        assertArrayEquals(intArrayOf(0, 3, 9), result.lineStarts)
        assertArrayEquals(floatArrayOf(30f, 60f), result.lineWidthsPx, 0f)
    }

    /** 连续开标点（`“(`）整串连同单词回退，不把前一个开标点留在行尾。 */
    @Test
    fun consecutiveOpeningMarksCarryWithTheWord() {
        val result = breakText("aa “(word”".map(Char::toString), 80)

        assertArrayEquals(intArrayOf(0, 3, 10), result.lineStarts)
        assertArrayEquals(floatArrayOf(30f, 70f), result.lineWidthsPx, 0f)
    }

    /**
     * 开标点串一直贴到本行行首时（本行从 `“(Energy` 开始），无处可退：再退本行就空了，
     * 且会把开标点留在行尾。保留原断点按字符回退，同超长词。
     */
    @Test
    fun openingMarkRunAtLineStartKeepsCharacterFallback() {
        val result = breakText("“(Energy".map(Char::toString), 50)

        assertArrayEquals(intArrayOf(0, 5, 8), result.lineStarts)
        assertArrayEquals(floatArrayOf(50f, 30f), result.lineWidthsPx, 0f)
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
            "aa [Energy] bb",
            "the [Energy Contraction] technique",
            "中文[English]中文",
            "aa “(word”",
            "状态[HP 100/100]恢复",
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

    /**
     * 一整串连续收尾标点（`Yes！！！！！！！ ”`）比行尾剩余空间宽时，不许整块掉到下一行
     * 留下空洞：标点与标点之间允许断开，本行填满。
     * 单个收尾标点（`。”` 这类不同标点之间）归悬挂与回退分支管，这条只放开同一标点连打。
     */
    @Test
    fun consecutiveClosingPunctuationSplitsToFillTheLine() {
        val result = ChineseLineBreaker(
            listOf("“", "￼", "Y", "e", "s", "！", "！", "！",
                "！", "！", "！", "！", "”"),
            floatArrayOf(10f, 40f, 6f, 6f, 6f, 10f, 10f, 10f, 10f, 10f, 10f, 10f, 10f),
            0, 100, 10f, 0f,
        )
        assertArrayEquals(intArrayOf(0, 8, 13), result.lineClusterStarts)
        assertArrayEquals(floatArrayOf(98f, 50f), result.lineWidthsPx, 0f)
    }
}
