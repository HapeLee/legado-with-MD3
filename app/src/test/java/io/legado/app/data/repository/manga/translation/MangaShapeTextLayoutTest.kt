// SPDX-License-Identifier: Apache-2.0
// Adapted from ciddwd/overlay-translator, commit 64a7aa2c9605c88dd35bdeea42ab5c0f1c18ce5e.
// Modifications: Legado package, geometry types and bounded Android pipeline integration.
package io.legado.app.data.repository.manga.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaShapeTextLayoutTest {
    @Test fun sourceColumnCountTakesPriorityOverLargerFontWithMoreColumns() {
        val text = "这是需要保持两列的完整译文内容"
        val measure = MangaShapeTextLayout.TextMeasurer { value, size -> value.length * size.toFloat() }
        val result = MangaShapeTextLayout.layout(160, 180, null, text,
            MangaShapeTextLayout.Orientation.VERTICAL_RIGHT_TO_LEFT, 10, 36, measure,
            preferSingleLine = false, preferredColumnCount = 2)
        assertTrue(result.accepted)
        assertEquals(2, result.runs.size)
        assertEquals(text, result.runs.joinToString("") { it.text })
        assertTrue(result.runs[0].bounds.left > result.runs[1].bounds.left)
        assertTrue(result.fontSizePx < 36)
    }

    @Test fun shortButtonPrefersACompleteSingleLineOverLargerWrappedGlyphs() {
        val result=MangaShapeTextLayout.layout(43,35,BooleanArray(43*35) { true },"确定",
            MangaShapeTextLayout.Orientation.HORIZONTAL,13,56,
            MangaShapeTextLayout.TextMeasurer { value,size -> value.length*size.toFloat() },true)
        assertTrue(result.accepted)
        assertEquals(1,result.runs.size)
        assertEquals("确定",result.runs.single().text)
    }

    @Test fun verticalRunsBalanceTheTranslatedTextAndPreserveReadingOrder() {
        val text="今天起要重新开始调查遗迹"
        val result=MangaShapeTextLayout.layout(120,180,BooleanArray(120*180) { true },text,
            MangaShapeTextLayout.Orientation.VERTICAL_RIGHT_TO_LEFT,26,26,
            MangaShapeTextLayout.TextMeasurer { value,size -> value.length*size.toFloat() })
        assertTrue(result.accepted)
        assertEquals(text,result.runs.joinToString("") { it.text })
        val lengths=result.runs.map { it.text.length }
        assertTrue(lengths.max()-lengths.min()<=1)
    }

    @Test
    fun `table driven reflow removes CJK line breaks without joining separated Latin words`() {
        data class Case(val text: String, val expected: String)
        listOf(
            Case("你今天居然选了\n一条超短的裙子……\n来工作室……", "你今天居然选了一条超短的裙子……来工作室……"),
            Case("翻訳した\r\n文章です", "翻訳した文章です"),
            Case("Hello\nworld", "Hello world"),
            Case("오늘\n번역", "오늘 번역"),
            Case("\n  原文 \r\n  译文\n", "原文译文"),
            Case("第一句 第二句", "第一句 第二句"),
            Case("", ""),
        ).forEach { case ->
            assertEquals(case.text, case.expected, MangaShapeTextLayout.normalize(case.text))
        }
    }

    private val measurer = MangaShapeTextLayout.TextMeasurer { text, size ->
        text.sumOf { character ->
            if (character.code < 128) size * 0.56 else size.toDouble()
        }.toFloat()
    }

    @Test
    fun `table driven layouts fit every returned rectangle inside the bubble mask`() {
        data class Case(
            val name: String,
            val width: Int,
            val height: Int,
            val mask: BooleanArray,
            val text: String,
            val orientation: MangaShapeTextLayout.Orientation,
        )

        val cases = listOf(
            Case(
                name = "horizontal ellipse",
                width = 140,
                height = 92,
                mask = ellipseMask(140, 92, 68.0, 44.0),
                text = "这是一段用于验证椭圆气泡形状感知排版的较长译文需要在中间显示更多文字",
                orientation = MangaShapeTextLayout.Orientation.HORIZONTAL,
            ),
            Case(
                name = "horizontal rounded rectangle",
                width = 150,
                height = 76,
                mask = insetRectangleMask(150, 76, 4),
                text = "Foreign forum posts and overseas listings can be translated directly.",
                orientation = MangaShapeTextLayout.Orientation.HORIZONTAL,
            ),
            Case(
                name = "vertical right to left ellipse",
                width = 92,
                height = 150,
                mask = ellipseMask(92, 150, 44.0, 73.0),
                text = "这是竖排漫画气泡的译文用于验证从右向左换列",
                orientation = MangaShapeTextLayout.Orientation.VERTICAL_RIGHT_TO_LEFT,
            ),
            Case(
                name = "vertical left to right rectangle",
                width = 88,
                height = 142,
                mask = insetRectangleMask(88, 142, 3),
                text = "竖排文字也可以从左向右依次换列",
                orientation = MangaShapeTextLayout.Orientation.VERTICAL_LEFT_TO_RIGHT,
            ),
        )

        cases.forEach { case ->
            val result = MangaShapeTextLayout.layout(
                width = case.width,
                height = case.height,
                mask = case.mask,
                text = case.text,
                orientation = case.orientation,
                minimumFontSizePx = 8,
                maximumFontSizePx = 30,
                textMeasurer = measurer,
            )
            assertTrue("${case.name}: ${result.reason}", result.accepted)
            assertTrue(case.name, result.runs.isNotEmpty())
            result.runs.forEach { run ->
                assertRectInsideMask(
                    caseName = case.name,
                    width = case.width,
                    height = case.height,
                    mask = case.mask,
                    left = run.bounds.left,
                    top = run.bounds.top,
                    right = run.bounds.right,
                    bottom = run.bounds.bottom,
                )
            }
        }
    }

    @Test
    fun `ellipse gives longer horizontal rows near its center`() {
        val result = MangaShapeTextLayout.layout(
            width = 140,
            height = 92,
            mask = ellipseMask(140, 92, 68.0, 44.0),
            text = "气泡顶部较窄中间较宽所以中间行应该容纳更多译文字符底部再次收窄形成自然的漫画排版效果",
            orientation = MangaShapeTextLayout.Orientation.HORIZONTAL,
            minimumFontSizePx = 8,
            maximumFontSizePx = 24,
            textMeasurer = measurer,
        )

        assertTrue(result.accepted)
        assertTrue(result.runs.size >= 3)
        val middle = result.runs[result.runs.size / 2].text.length
        assertTrue(middle >= result.runs.first().text.length)
        assertTrue(middle >= result.runs.last().text.length)
    }

    @Test
    fun `vertical direction controls physical column order`() {
        val mask = insetRectangleMask(96, 150, 2)
        val rtl = MangaShapeTextLayout.layout(
            width = 96,
            height = 150,
            mask = mask,
            text = "第一列第二列第三列需要足够长才能产生多列布局",
            orientation = MangaShapeTextLayout.Orientation.VERTICAL_RIGHT_TO_LEFT,
            minimumFontSizePx = 12,
            maximumFontSizePx = 24,
            textMeasurer = measurer,
        )
        val ltr = MangaShapeTextLayout.layout(
            width = 96,
            height = 150,
            mask = mask,
            text = "第一列第二列第三列需要足够长才能产生多列布局",
            orientation = MangaShapeTextLayout.Orientation.VERTICAL_LEFT_TO_RIGHT,
            minimumFontSizePx = 12,
            maximumFontSizePx = 24,
            textMeasurer = measurer,
        )

        assertTrue(rtl.accepted)
        assertTrue(ltr.accepted)
        assertTrue(rtl.runs.size >= 2)
        assertTrue(ltr.runs.size >= 2)
        assertTrue(rtl.runs.first().bounds.left > rtl.runs.last().bounds.left)
        assertTrue(ltr.runs.first().bounds.left < ltr.runs.last().bounds.left)
    }

    @Test
    fun `kinsoku punctuation is not left at prohibited line edges`() {
        val result = MangaShapeTextLayout.layout(
            width = 92,
            height = 100,
            mask = insetRectangleMask(92, 100, 2),
            text = "他说：「这里不能，把右括号放到下一行，也不能让（留在上一行。」",
            orientation = MangaShapeTextLayout.Orientation.HORIZONTAL,
            minimumFontSizePx = 8,
            maximumFontSizePx = 20,
            textMeasurer = measurer,
        )

        assertTrue(result.accepted)
        assertTrue(MangaShapeTextLayout.respectsKinsoku(result.runs.map { it.text }))
    }

    @Test
    fun `table driven kinsoku allows paragraph leading punctuation only on the first run`() {
        data class Case(
            val name: String,
            val runs: List<String>,
            val expected: Boolean,
        )
        val cases = listOf(
            Case(
                name = "paragraph starts with ellipsis",
                runs = listOf("……开场", "继续。"),
                expected = true,
            ),
            Case(
                name = "later run starts with comma",
                runs = listOf("第一行", "，第二行"),
                expected = false,
            ),
            Case(
                name = "run ends with opening bracket",
                runs = listOf("第一行（", "第二行）"),
                expected = false,
            ),
        )

        cases.forEach { case ->
            assertEquals(
                case.name,
                case.expected,
                MangaShapeTextLayout.respectsKinsoku(case.runs),
            )
        }
    }

    @Test
    fun `invalid and undersized masks fall back safely`() {
        data class Case(
            val name: String,
            val width: Int,
            val height: Int,
            val mask: BooleanArray,
            val reason: MangaShapeTextLayout.Reason,
        )
        val cases = listOf(
            Case(
                "empty mask",
                20,
                20,
                BooleanArray(400),
                MangaShapeTextLayout.Reason.INVALID_MASK,
            ),
            Case(
                "smaller than minimum font",
                7,
                30,
                BooleanArray(210) { true },
                MangaShapeTextLayout.Reason.MASK_TOO_SMALL,
            ),
        )

        cases.forEach { case ->
            val result = MangaShapeTextLayout.layout(
                width = case.width,
                height = case.height,
                mask = case.mask,
                text = "测试",
                orientation = MangaShapeTextLayout.Orientation.HORIZONTAL,
                minimumFontSizePx = 8,
                maximumFontSizePx = 20,
                textMeasurer = measurer,
            )
            assertFalse(case.name, result.accepted)
            assertEquals(case.name, case.reason, result.reason)
        }
    }

    private fun assertRectInsideMask(
        caseName: String,
        width: Int,
        height: Int,
        mask: BooleanArray,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        assertTrue(caseName, left >= 0 && top >= 0 && right <= width && bottom <= height)
        for (y in top until bottom) {
            for (x in left until right) {
                assertTrue("$caseName pixel ($x,$y)", mask[y * width + x])
            }
        }
    }

    private fun insetRectangleMask(
        width: Int,
        height: Int,
        inset: Int,
    ): BooleanArray = BooleanArray(width * height) { index ->
        val x = index % width
        val y = index / width
        x in inset until width - inset && y in inset until height - inset
    }

    private fun ellipseMask(
        width: Int,
        height: Int,
        radiusX: Double,
        radiusY: Double,
    ): BooleanArray {
        val centerX = (width - 1) / 2.0
        val centerY = (height - 1) / 2.0
        return BooleanArray(width * height) { index ->
            val x = index % width
            val y = index / width
            val dx = (x - centerX) / radiusX
            val dy = (y - centerY) / radiusY
            dx * dx + dy * dy <= 1.0
        }
    }
}
