package io.legado.app.data.repository.manga.translation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import io.legado.app.domain.model.manga.MangaTextBox
import io.legado.app.domain.model.manga.MangaTranslatedRegion
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MangaNoFitRegressionTest {
    private val measurer = MangaShapeTextLayout.TextMeasurer { text, size -> text.length * size * .56f }

    @Test fun `rectangle fast path is identical to a full mask and an empty mask stays invalid`() {
        for (orientation in MangaShapeTextLayout.Orientation.entries) for (text in listOf("普通的中文对白", "supercalifragilistic", "她说：“")) {
            val full = MangaShapeTextLayout.layout(90, 60, BooleanArray(90 * 60) { true }, text, orientation, 8, 30, measurer)
            val rectangle = MangaShapeTextLayout.layout(90, 60, null, text, orientation, 8, 30, measurer)
            assertEquals(full, rectangle)
        }
        val invalid = MangaShapeTextLayout.layout(90, 60, BooleanArray(90 * 60), "不能绕过掩膜", MangaShapeTextLayout.Orientation.HORIZONTAL, 8, 30, measurer)
        assertFalse(invalid.accepted)
        assertEquals(MangaShapeTextLayout.Reason.INVALID_MASK, invalid.reason)
    }

    @Test fun `long word fills available width instead of degrading to one letter per line`() {
        val word = "supercalifragilistic"
        val layout = MangaShapeTextLayout.layout(90, 60, BooleanArray(90 * 60) { true }, word,
            MangaShapeTextLayout.Orientation.HORIZONTAL, 12, 30, measurer)
        assertTrue("${layout.reason}", layout.accepted)
        assertEquals(word, layout.runs.joinToString("") { it.text })
        assertTrue(layout.runs.size < word.length)
        assertTrue(layout.runs.all { it.bounds.left >= 0 && it.bounds.right <= 90 && it.bounds.top >= 0 && it.bounds.bottom <= 60 })
    }

    @Test fun `literal opening punctuation at the end of a dialogue fragment is renderable`() {
        val text = "她说：“"
        val layout = MangaShapeTextLayout.layout(200, 80, BooleanArray(200 * 80) { true }, text,
            MangaShapeTextLayout.Orientation.HORIZONTAL, 12, 30, measurer)
        assertTrue("${layout.reason}", layout.accepted)
        assertEquals(text, layout.runs.joinToString("") { it.text })
        assertFalse(MangaShapeTextLayout.respectsKinsoku(listOf("她说：（", "继续）")))
    }

    @Test fun `default scale can shrink a coarse AI estimate while keeping complete text inside its area`() {
        val bitmap = Bitmap.createBitmap(220, 140, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val box = MangaTextBox(30 * 1000f / 220, 30 * 1000f / 140, 190 * 1000f / 220, 110 * 1000f / 140)
        val region = MangaTranslatedRegion("ai", "SOURCE", "这是较长的中文译文需要完整显示在有限的气泡区域内。", box)
        try {
            MangaOverlayTypesetter.draw(bitmap, region, 80f, 1f, true, Color.WHITE)
            val area = box.pixels(bitmap.width, bitmap.height)
            var dark = 0
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) if (Color.red(bitmap.getPixel(x, y)) < 100) {
                dark++
                assertTrue("Text escaped at $x,$y", area.contains(x, y))
            }
            assertTrue(dark > 0)
        } finally { bitmap.recycle() }
    }
}
