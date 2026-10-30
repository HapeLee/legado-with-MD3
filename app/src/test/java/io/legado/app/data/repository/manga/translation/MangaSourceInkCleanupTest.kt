package io.legado.app.data.repository.manga.translation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import io.legado.app.domain.model.manga.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MangaSourceInkCleanupTest {
    @Test fun `outlined dark glyph cores are included in the shared mask on a dark gradient`() {
        val width = 80; val height = 70
        val colors = IntArray(width * height) { i -> val v = i / width; Color.rgb(v, v, v) }
        for (y in 18..41) for (x in 24..43) colors[y * width + x] = Color.WHITE
        for (y in 22..37) for (x in 28..39) colors[y * width + x] = Color.BLACK
        val source = Rect(24, 18, 44, 42)
        val mask = mangaSourceInkCoverage(colors, width, height, Color.BLACK, source, source,
            true, null, emptyList(), 3, false, 24f)
        assertTrue("Black stroke inside the white outline must reach every inpainting engine", mask[30 * width + 33])
        assertTrue(mask[19 * width + 25])
        assertFalse("Unrelated gradient must remain outside the mask", mask[30 * width + 60])
        val protected = mangaSourceInkCoverage(colors, width, height, Color.BLACK, source, source,
            true, null, listOf(source), 3, false, 24f)
        assertFalse(protected.any { it })
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.setPixels(colors, 0, width, 0, 0, width, height)
            val textBox = MangaTextBox(24 * 1000f / width, 18 * 1000f / height, 44 * 1000f / width, 42 * 1000f / height)
            val region = MangaTranslatedRegion("outline", "原文句", "译", textBox, vertical = true,
                typesetting = MangaAiTypesetting(listOf("译"), 24 * 1000f / height, vertical = true))
            val shared = MangaPageComposer.erasureMask(bitmap, MangaTranslationPlan(listOf(region)))
            assertTrue("Production mask passed to LaMa and MI-GAN must contain the black core", shared[30 * width + 33])
            assertEquals("Mask generation must leave source pixels intact", Color.BLACK, bitmap.getPixel(33, 30))
        } finally { bitmap.recycle() }

    }

    @Test fun `small clipped outline fragments are not source glyphs`() {
        val width = 40; val height = 50
        val colors = IntArray(width * height) { Color.WHITE }
        // A curved balloon crossing a tight crop becomes a tiny triangular component.
        for (i in 0..8) colors[i * width + i] = Color.BLACK
        for (y in 20..30) for (x in 16..24) colors[y * width + x] = Color.BLACK
        for (patch in listOf(false, true)) {
            val mask = mangaSourceInkCoverage(colors, width, height, Color.WHITE,
                Rect(2, 2, 35, 45), Rect(2, 2, 35, 45), true, null, emptyList(), 6, patch, 15f)
            assertFalse("Crop fragments must preserve balloon borders", mask[5 * width + 5])
            assertTrue("Complete source glyph must still be removed", mask[25 * width + 20])
        }
    }

    private fun box(l: Int, t: Int, r: Int, b: Int) = MangaTextBox(l * 1000f / 400, t * 1000f / 200, r * 1000f / 400, b * 1000f / 200)
    private fun paint(bitmap: Bitmap, l: Int, t: Int, r: Int, b: Int, color: Int = Color.BLACK) {
        Canvas(bitmap).drawRect(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat(), Paint().apply { this.color = color })
    }
    private fun assertClear(bitmap: Bitmap, l: Int, t: Int, r: Int, b: Int, background: Int = Color.WHITE) {
        for (y in t until b) for (x in l until r) assertEquals("Original ink remains at $x,$y", background, bitmap.getPixel(x, y))
    }

    @Test fun `bottom detached strokes and short translation word tails are cleared in both local modes`() {
        for (patch in listOf(false, true)) {
            val image = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            try {
                paint(image, 80, 50, 92, 74); paint(image, 101, 50, 113, 74)
                paint(image, 101, 78, 109, 82) // A detached descender below the OCR line.
                paint(image, 201, 50, 213, 74); paint(image, 225, 50, 237, 74) // End of the original, beyond the short translation.
                paint(image, 35, 48, 38, 88, Color.RED)
                val line = box(80, 50, 114, 75)
                val region = MangaTranslatedRegion("r", "LONG ORIGINAL", "好", box(80, 50, 240, 84),
                    eraseBoxes = listOf(line), sourceLines = listOf(MangaSourceLine("LONG ORIGINAL", line)),
                    layoutBox = box(80, 140, 240, 190), sourceFontSize = 100f)
                val plan = MangaTranslationPlan(listOf(region))
                if (patch) MangaPageComposer.draw(image, plan, true, 1f) else MangaPageComposer.erase(image, plan)
                assertClear(image, 101, 78, 109, 82)
                assertClear(image, 201, 50, 213, 74); assertClear(image, 225, 50, 237, 74)
                assertEquals(Color.RED, image.getPixel(36, 60))
            } finally { image.recycle() }
        }
    }

    @Test fun `layout mask holes cannot restore original source ink after erasure`() {
        for (patch in listOf(false, true)) {
            val image = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            try {
                paint(image, 120, 60, 136, 82)
                val mask = BooleanArray(240 * 160) { p ->
                    val x = p % 240 + 60; val y = p / 240 + 20
                    x !in 120 until 136 || y !in 60 until 82
                }
                val runs = mutableListOf<Int>(); var start = -1
                for (p in 0..mask.size) {
                    if (p < mask.size && mask[p] && start < 0) start = p
                    if ((p == mask.size || !mask[p]) && start >= 0) { runs += start; runs += p - start; start = -1 }
                }
                val region = MangaTranslatedRegion("r", "SOURCE", "好", box(118, 58, 140, 86),
                    layoutBox = box(90, 135, 240, 175), sourceFontSize = 80f,
                    bubbleShape = MangaBubbleShape("bubble", box(60, 20, 300, 180), 240, 160, runs))
                val plan = MangaTranslationPlan(listOf(region))
                if (patch) MangaPageComposer.draw(image, plan, true, 1f) else MangaPageComposer.erase(image, plan)
                assertClear(image, 120, 60, 136, 82)
            } finally { image.recycle() }
        }
    }

    @Test fun `vertical source columns clear trailing glyphs and protect another region`() {
        val image = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            paint(image, 80, 35, 96, 55); paint(image, 80, 140, 96, 160)
            paint(image, 102, 142, 112, 162) // A different, explicitly protected source.
            val anchor = box(80, 35, 96, 55)
            val region = MangaTranslatedRegion("r", "長い原文", "短", box(80, 35, 112, 165),
                eraseBoxes = listOf(anchor), vertical = true)
            MangaPageComposer.erase(image, MangaTranslationPlan(listOf(region)), protectedRects = listOf(Rect(102, 140, 114, 165)))
            assertClear(image, 80, 140, 96, 160)
            assertEquals(Color.BLACK, image.getPixel(106, 150))
        } finally { image.recycle() }
    }

    @Test fun `transparent cleanup deltas retain tail changes without overwriting neighboring translations`() {
        val original = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            paint(original, 200, 60, 220, 82)
            val delta = original.copy(Bitmap.Config.ARGB_8888, true)
            val before = IntArray(400 * 200).also { delta.getPixels(it, 0, 400, 0, 0, 400, 200) }
            try {
                paint(delta, 200, 60, 220, 82, Color.WHITE)
                mangaMakeTransparentDelta(delta, before)
                assertEquals(0, Color.alpha(delta.getPixel(30, 60)))
                assertEquals(Color.WHITE, delta.getPixel(210, 70))
                paint(original, 30, 50, 45, 80, Color.RED)
                Canvas(original).drawBitmap(delta, 0f, 0f, null)
                assertClear(original, 200, 60, 220, 82)
                assertEquals(Color.RED, original.getPixel(35, 60))
            } finally { delta.recycle() }
        } finally { original.recycle() }
    }

    @Test fun `detached antialiased source strokes retain the original ink color at different opacity`() {
        for ((bg, ink) in listOf(Color.WHITE to Color.BLACK, Color.BLACK to Color.WHITE)) {
            val image = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(bg) }
            try {
                paint(image, 90, 50, 104, 75, ink)
                paint(image, 96, 78, 102, 81, Color.rgb(127, 127, 127))
                val line = box(90, 50, 105, 75)
                val region = MangaTranslatedRegion("r", "字", "短", box(90, 50, 105, 83), eraseBoxes = listOf(line))
                MangaPageComposer.erase(image, MangaTranslationPlan(listOf(region)))
                assertClear(image, 96, 78, 102, 81, bg)
            } finally { image.recycle() }
        }
    }
}
