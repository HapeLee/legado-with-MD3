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
    private val glyphWidthPx = 10f

    private fun textStyle(linearText: Boolean = false) =
        ReaderTextStyle(0, 10f, linearText = linearText)

    /** 与排版一致：bounds.right = left + 字宽，下一个 bounds.left = 上一个 right + gap。 */
    private fun row(
        values: List<String>,
        gapPx: Float,
        linearText: Boolean = false,
        widthOf: (Int) -> Float = { glyphWidthPx },
    ): List<ReaderElement.Text> {
        var left = 0f
        val style = textStyle(linearText)
        return values.mapIndexed { index, value ->
            val width = widthOf(index)
            val element = ReaderElement.Text(
                bounds = ReaderRect(left, 0f, left + width, 12f),
                baselinePx = 10f,
                value = value,
                style = style,
                selected = false,
                emphasized = false,
                chapterPosition = index,
            )
            left += width + gapPx
            element
        }
    }

    /**
     * 整段绘制后第 i 个字形的落笔位置必须还是原 `bounds.left`。
     *
     * 画笔会先在首字之前垫半个字距（`mergedDrawStartLeftPx` 已把它减掉），之后每个
     * 字形之间插入量化后的整数字距。
     */
    private fun assertGlyphsOnLayoutX(run: ReaderTextDrawRun, elements: List<ReaderElement.Text>) {
        val spacing = minikinLetterSpacingPx(run.gapExtraPx, run.style.linearText)
        val halfLeft = minikinLeadingHalfSpacingPx(run.gapExtraPx, run.style.linearText)
        var x = run.mergedDrawStartLeftPx() + halfLeft
        elements.forEachIndexed { index, element ->
            assertEquals("第 $index 个字", element.bounds.left, x, 0.001f)
            x += element.bounds.width + spacing
        }
    }

    @Test
    fun integerGapMergesAndCompensatesTheLeadingHalfSpacing() {
        val elements = row(listOf("甲", "乙", "丙"), gapPx = 2f)
        val runs = buildMergedTextRuns(elements)

        assertEquals(3, runs.size)
        val run = runs.getValue(elements[0])
        assertSame(elements[0], run.first)
        assertEquals("甲乙丙", run.text)
        assertEquals(2f, run.gapExtraPx, 0f)
        // minikin：letterSpace = round(2) = 2，首字之前先垫 floor(2 / 2) = 1。
        assertEquals(elements[0].bounds.left - 1f, run.mergedDrawStartLeftPx(), 0f)
        assertGlyphsOnLayoutX(run, elements)
    }

    @Test
    fun fractionalGapFallsBackToPerElementDrawing() {
        // 两端对齐摊出来的缝隙是浮点的，画笔只能按整数 px 插；差一点就会随字数累积，
        // 行尾被内容裁剪框切掉，所以这种行必须留在逐字绘制路径。
        for (gap in listOf(0.6f, 1.6f, 2.4f)) {
            val elements = row(listOf("甲", "乙", "丙"), gapPx = gap)
            assertTrue("gap=$gap", buildMergedTextRuns(elements).isEmpty())
        }
    }

    @Test
    fun gapQuantizationFollowsMinikinRounding() {
        assertEquals(2f, minikinLetterSpacingPx(1.6f, linearText = false), 0f)
        // C round()：.5 远离零，与 kotlin.math.round 的凑偶不同，这里必须按 minikin 的口径。
        assertEquals(3f, minikinLetterSpacingPx(2.5f, linearText = false), 0f)
        assertEquals(-3f, minikinLetterSpacingPx(-2.5f, linearText = false), 0f)
        assertEquals(-1f, minikinLetterSpacingPx(-1f, linearText = false), 0f)
        // linearText 不做取整，缝隙可以原样交给画笔。
        assertEquals(2.4f, minikinLetterSpacingPx(2.4f, linearText = true), 0f)
        // 半格前置：非 linearText 是 floor(整数 / 2)，linearText 是精确一半。
        assertEquals(1f, minikinLeadingHalfSpacingPx(2.4f, linearText = false), 0f)
        assertEquals(-1f, minikinLeadingHalfSpacingPx(-1f, linearText = false), 0f)
        assertEquals(1.2f, minikinLeadingHalfSpacingPx(2.4f, linearText = true), 0f)
    }

    @Test
    fun linearTextKeepsFractionalGapExact() {
        val elements = row(listOf("甲", "乙", "丙"), gapPx = 2.4f, linearText = true)
        val runs = buildMergedTextRuns(elements)

        assertEquals(3, runs.size)
        val run = runs.getValue(elements[0])
        // 排版累加的是浮点缝隙，这里按段内实测缝隙断言，避免用 2.4f 这种十进制字面量比相等。
        assertEquals(2.4f, run.gapExtraPx, 0.001f)
        assertEquals(
            elements[0].bounds.left - run.gapExtraPx / 2f,
            run.mergedDrawStartLeftPx(),
            0.001f,
        )
        assertGlyphsOnLayoutX(run, elements)
    }

    @Test
    fun negativeIntegerGapMergesWithoutShiftingTheRun() {
        val elements = row(listOf("甲", "乙", "丙"), gapPx = -1f)
        val runs = buildMergedTextRuns(elements)

        val run = runs.getValue(elements[0])
        assertEquals(-1f, run.gapExtraPx, 0f)
        // floor(-1 / 2) = -1：起点反而右移一格。
        assertEquals(elements[0].bounds.left + 1f, run.mergedDrawStartLeftPx(), 0f)
        assertGlyphsOnLayoutX(run, elements)
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
        assertGlyphsOnLayoutX(runs.getValue(elements[0]), elements.subList(0, 2))
        assertGlyphsOnLayoutX(runs.getValue(elements[5]), elements.subList(5, 7))
        assertTrue(runs.values.all { run -> run.text.all { it.code !in 0xD800..0xDFFF } })
    }

    @Test
    fun zeroWidthIndentPlaceholderSplitsTheRun() {
        // HTML 结构块隐藏的段首缩进占位在排版里宽度是 0，画笔却会按字体给它真实 advance。
        val elements = row(
            listOf("甲", "乙", "　", "丙"),
            gapPx = 2f,
            widthOf = { index -> if (index == 2) 0f else glyphWidthPx },
        )
        val runs = buildMergedTextRuns(elements)

        assertEquals("甲乙", runs.getValue(elements[0]).text)
        assertFalse(runs.containsKey(elements[2]))
        assertFalse(runs.containsKey(elements[3]))
    }

    @Test
    fun wordSpaceSplitsTheRun() {
        // 按词两端对齐会把额外余量摊进空格元素自己的 bounds 宽度，统一字距复现不了。
        val elements = row(listOf("甲", " ", "乙"), gapPx = 2f)
        assertTrue(buildMergedTextRuns(elements).isEmpty())
    }
}
