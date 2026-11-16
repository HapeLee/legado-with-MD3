package io.legado.app.ui.widget.components.tabRow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassTabSamplingRangeTest {

    private fun range(
        tabCount: Int = 50,
        hasOverflow: Boolean = true,
        isRtl: Boolean = false,
        scrollValue: Float = 0f,
        viewportSize: Int = 800,
        shadowPaddingPx: Int = 24,
        tabWidthPx: Float = 200f,
        gapPx: Int = 12,
    ) = computeGlassTabSamplingRange(
        tabCount = tabCount,
        hasOverflow = hasOverflow,
        isRtl = isRtl,
        scrollValue = scrollValue,
        viewportSize = viewportSize,
        shadowPaddingPx = shadowPaddingPx,
        tabWidthPx = tabWidthPx,
        gapPx = gapPx,
    )

    @Test
    fun `no overflow samples every tab`() {
        assertEquals(0 until 20, range(tabCount = 20, hasOverflow = false))
    }

    @Test
    fun `rtl samples every tab`() {
        assertEquals(0 until 20, range(tabCount = 20, isRtl = true, scrollValue = 4000f))
    }

    @Test
    fun `non positive step samples every tab`() {
        assertEquals(0 until 20, range(tabCount = 20, tabWidthPx = -300f))
    }

    @Test
    fun `empty tab list stays empty`() {
        assertTrue(range(tabCount = 0).isEmpty())
    }

    @Test
    fun `range covers visible tabs with prefetch at scroll start`() {
        // step = 212, viewport 800 -> 4 visible slots, plus prefetch on each side.
        assertEquals(0 until 7, range())
    }

    @Test
    fun `range follows the scroll position`() {
        assertEquals(8 until 15, range(scrollValue = 212f * 10))
    }

    @Test
    fun `range clamps to the last tab`() {
        assertEquals(43 until 50, range(scrollValue = 212f * 45))
    }

    @Test
    fun `range always contains the tab under the scroll position`() {
        (0..49).forEach { position ->
            val result = range(scrollValue = 212f * position)
            assertTrue("tab $position missing from $result", position in result)
        }
    }
}
