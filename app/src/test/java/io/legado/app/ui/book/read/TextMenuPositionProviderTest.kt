package io.legado.app.ui.book.read

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class TextMenuPositionProviderTest {
    private fun provider(bounds: IntRect? = null, preferBelow: Boolean? = true) =
        TextMenuPositionProvider(
            density = 1f,
            startX = 40,
            startTopY = 450,
            startBottomY = 470,
            endX = 100,
            endBottomY = 480,
            shadowPadding = 12,
            preferBelow = preferBelow,
            availableBounds = bounds?.let { { it } },
        )

    private fun position(height: Int, windowHeight: Int = 800): IntOffset =
        provider().calculatePosition(
            IntRect.Zero, IntSize(400, windowHeight), LayoutDirection.Ltr, IntSize(392, height),
        )

    @Test
    fun `growing note stays below selection and clamps instead of flipping above`() {
        assertEquals(486, position(220).y)
        assertEquals(440, position(360).y)
    }

    @Test
    fun `keyboard moves card only as far as needed to remain visible`() {
        assertEquals(140, position(360, windowHeight = 500).y)
    }

    @Test
    fun `host IME bounds constrain popup even when platform reports full window`() {
        val provider = provider(IntRect(0, 24, 400, 500))
        assertEquals(
            IntOffset(4, 140),
            provider.calculatePosition(
                IntRect.Zero, IntSize(400, 800), LayoutDirection.Ltr, IntSize(392, 360),
            ),
        )
        // A platform-visible frame already shortened by IME must not be shortened again.
        assertEquals(
            IntOffset(4, 140),
            provider.calculatePosition(
                IntRect.Zero, IntSize(400, 500), LayoutDirection.Ltr, IntSize(392, 360),
            ),
        )
    }

    @Test
    fun `legacy text menu still flips above selection when necessary`() {
        assertEquals(
            98,
            provider(preferBelow = null).calculatePosition(
                IntRect.Zero, IntSize(400, 800), LayoutDirection.Ltr, IntSize(392, 360),
            ).y,
        )
    }
}
