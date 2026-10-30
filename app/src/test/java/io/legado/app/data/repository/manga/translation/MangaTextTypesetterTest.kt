package io.legado.app.data.repository.manga.translation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MangaTextTypesetterTest {
    @Test fun `small preferred size and downscaling must never be silently enlarged`() {
        assertEquals(4f, MangaTextTypesetter.fit("Hi", false, 200, 100, 4f).fontSize, 0f)
        assertEquals(6f, MangaTextTypesetter.fit("Hi", false, 200, 100, 6f).fontSize, 0f)
    }

    @Test fun `fitting retains preferred size and finds the largest smaller size precisely`() {
        assertEquals(32f, MangaTextTypesetter.fit("Hi", false, 200, 100, 32f).fontSize, 0f)
        val text = "A longer comic sentence with several words."
        val fitted = MangaTextTypesetter.fit(text, false, 180, 105, 48f)
        assertTrue(fitted.fontSize < 48f)
        assertNotNull(MangaTextTypesetter.atSize(text, false, 180, 105, fitted.fontSize))
        assertNull(MangaTextTypesetter.atSize(text, false, 180, 105, fitted.fontSize + 0.5f))
    }

    @Test fun `oversized single glyph cannot pass a height-only fit check`() {
        assertNull(MangaTextTypesetter.atSize("W", false, 18, 200, 64f))
        assertThrows(IllegalArgumentException::class.java) { MangaTextTypesetter.fit("W", false, 4, 200, 64f) }
    }

    @Test fun `vertical columns retain graphemes paragraphs and punctuation boundaries`() {
        val text = "甲乙、丙丁「戊己」庚\n👩‍👩‍👧‍👦🇨🇳乙"
        val layout = MangaTextTypesetter.fit(text, true, 1000, 86, 32f)
        val chars = layout.columns.flatten()
        assertTrue(chars.contains("👩‍👩‍👧‍👦")); assertTrue(chars.contains("🇨🇳"))
        assertEquals(text.replace("\n", ""), chars.joinToString(""))
        assertTrue(layout.columns.drop(1).none { it.first() in listOf("、", "」") })
        assertTrue(layout.columns.none { it.last() == "「" })
        assertTrue(layout.columns.any { it.first() == "👩‍👩‍👧‍👦" })
    }

    @Test fun `emoji code units do not turn Latin translation into vertical lettering`() {
        assertFalse(MangaTextTypesetter.vertical("Hello 👩‍👩‍👧‍👦", true))
        assertTrue(MangaTextTypesetter.vertical("你好👩‍👩‍👧‍👦", true))
        assertFalse(MangaTextTypesetter.vertical("你好", false))
    }

    @Test fun `vertical glyph ink and outlines stay clear of all layout edges`() {
        val bitmap = Bitmap.createBitmap(500, 220, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val area = Rect(20, 20, 480, 200)
        MangaTextTypesetter.fit("甲乙丙丁戊己", true, area.width(), area.height(), 64f).draw(Canvas(bitmap), area, Color.BLACK)
        var ink = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (Color.red(bitmap.getPixel(x, y)) < 100) {
                ink++
                assertTrue("Clipped ink at $x,$y", x > area.left + 1 && x < area.right - 2 && y > area.top + 1 && y < area.bottom - 2)
            }
        }
        assertTrue(ink > 100)
        val report = java.io.File("build/reports/manga-translation/vertical-fit.png").apply { parentFile!!.mkdirs() }
        report.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
