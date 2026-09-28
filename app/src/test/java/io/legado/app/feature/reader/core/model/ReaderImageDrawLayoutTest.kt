package io.legado.app.feature.reader.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderImageDrawLayoutTest {
    private val container = ReaderRect(10f, 20f, 110f, 220f)

    @Test
    fun `wide image is centered vertically without distortion`() {
        val result = ReaderImageDrawLayout.fitCenter(container, 200, 100)!!

        assertEquals(10f, result.leftPx, 0f)
        assertEquals(95f, result.topPx, 0f)
        assertEquals(100f, result.widthPx, 0f)
        assertEquals(50f, result.heightPx, 0f)
    }

    @Test
    fun `portrait image is centered horizontally without distortion`() {
        val result = ReaderImageDrawLayout.fitCenter(container, 50, 200)!!

        assertEquals(35f, result.leftPx, 0f)
        assertEquals(20f, result.topPx, 0f)
        assertEquals(50f, result.widthPx, 0f)
        assertEquals(200f, result.heightPx, 0f)
    }

    @Test
    fun `invalid image or container dimensions do not produce draw geometry`() {
        assertNull(ReaderImageDrawLayout.fitCenter(container, 0, 100))
        assertNull(ReaderImageDrawLayout.fitCenter(ReaderRect(0f, 0f, 0f, 10f), 10, 10))
    }

    /**
     * 旧 `ImageColumn.draw`：宽恒为一个字符格，高 = 格宽 / 位图宽 * 位图高，竖直居中于行盒。
     * 位图 40×10、格宽 100f → 高 25f，中心仍是容器中心 120f。
     */
    @Test
    fun `inline cell fills the character cell width and follows the bitmap aspect`() {
        val result = ReaderImageDrawLayout.inlineCell(container, 40, 10)!!

        assertEquals(10f, result.leftPx, 0f)
        assertEquals(107.5f, result.topPx, 0f)
        assertEquals(100f, result.widthPx, 0f)
        assertEquals(25f, result.heightPx, 0f)
    }

    /** `div` 为负时允许高于当前行（旧注释「允许高度比字符更高」）。 */
    @Test
    fun `tall inline cell overflows the line box symmetrically`() {
        val result = ReaderImageDrawLayout.inlineCell(container, 10, 40)!!

        assertEquals(-80f, result.topPx, 0f)
        assertEquals(100f, result.widthPx, 0f)
        assertEquals(400f, result.heightPx, 0f)
    }

    /**
     * 测量期长宽比与位图不一致（例如测量时文件还不可解码、回落到错误占位图的方形尺寸）时，
     * 旧几何仍占满一个字符格宽；`fitCenter` 会把它缩成格内内接（这里只剩 1/4 格宽）。
     */
    @Test
    fun `inline cell keeps the full cell width when the measured aspect is wrong`() {
        val squareCell = ReaderRect(0f, 0f, 100f, 100f)
        val letterBoxed = ReaderImageDrawLayout.fitCenter(squareCell, 25, 100)!!
        assertEquals(25f, letterBoxed.widthPx, 0f)
        assertEquals(37.5f, letterBoxed.leftPx, 0f)

        val cell = ReaderImageDrawLayout.inlineCell(squareCell, 25, 100)!!
        assertEquals(100f, cell.widthPx, 0f)
        assertEquals(0f, cell.leftPx, 0f)
        assertEquals(400f, cell.heightPx, 0f)
        assertEquals(-150f, cell.topPx, 0f)
    }

    @Test
    fun `invalid inline cell dimensions do not produce draw geometry`() {
        assertNull(ReaderImageDrawLayout.inlineCell(container, 0, 10))
        assertNull(ReaderImageDrawLayout.inlineCell(ReaderRect(0f, 0f, 0f, 10f), 10, 10))
    }

    @Test
    fun `element dispatch keeps inline cells inline and standalone images centered`() {
        val bounds = ReaderRect(10f, 20f, 110f, 220f)
        val inline = ReaderElement.Image(bounds, "inline", null, inline = true)
        val standalone = ReaderElement.Image(bounds, "standalone", null)

        assertEquals(
            ReaderImageDrawLayout.inlineCell(bounds, 40, 10),
            ReaderImageDrawLayout.forElement(inline, 40, 10),
        )
        assertEquals(
            ReaderImageDrawLayout.fitCenter(bounds, 40, 10),
            ReaderImageDrawLayout.forElement(standalone, 40, 10),
        )
    }
}
