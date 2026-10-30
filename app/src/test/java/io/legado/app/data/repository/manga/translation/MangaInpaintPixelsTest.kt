package io.legado.app.data.repository.manga.translation

import android.app.Application
import android.graphics.*
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
class MangaInpaintPixelsTest {
    @Test fun `inference mask includes bilinear fringes while composition keeps original selection`() {
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            Canvas(bitmap).drawRect(8f, 8f, 12f, 16f, Paint().apply { color = Color.BLACK })
            val mask = BooleanArray(24 * 24) { i -> i % 24 in 8 until 12 && i / 24 in 8 until 16 }
            val input = mangaInpaintInput(bitmap, mask)
            input.pixels.forEachIndexed { i, pixel ->
                if (pixel != Color.WHITE) assertTrue("Resampled ink leaked into model context at $i", input.mask[i])
            }
            val original = IntArray(mask.size).also { bitmap.getPixels(it, 0, 24, 0, 0, 24, 24) }
            val result = mangaCompositeInpainting(original, IntArray(mask.size) { Color.RED }, mask)
            result.indices.filter { !mask[it] }.forEach { assertEquals(original[it], result[it]) }
        } finally { bitmap.recycle() }
    }

    @Test fun `mask extraction leaves source untouched and protects neighbors`() {
        val bitmap = Bitmap.createBitmap(200, 150, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            val paint = Paint().apply { color = Color.BLACK }
            Canvas(bitmap).apply { drawRect(55f, 50f, 65f, 70f, paint); drawRect(90f, 50f, 100f, 70f, paint) }
            val box = MangaTextBox(250f, 300f, 550f, 500f)
            val region = MangaTranslatedRegion("a", "two", "字", box, typesetting = MangaAiTypesetting(listOf("字"), 100f))
            val before = IntArray(200 * 150).also { bitmap.getPixels(it, 0, 200, 0, 0, 200, 150) }
            val mask = MangaPageComposer.erasureMask(bitmap, MangaTranslationPlan(listOf(region)), listOf(Rect(85, 45, 105, 75)))
            assertTrue(mask[60 * 200 + 60]); assertFalse(mask[60 * 200 + 95]); assertFalse(mask[0])
            val after = IntArray(before.size).also { bitmap.getPixels(it, 0, 200, 0, 0, 200, 150) }
            assertArrayEquals(before, after)
            val repaired = mangaCompositeInpainting(before, IntArray(before.size) { Color.RED }, mask)
            assertEquals(Color.RED, repaired[60 * 200 + 60]); assertEquals(Color.BLACK, repaired[60 * 200 + 95])
            assertEquals(Color.WHITE, repaired[0])
        } finally { bitmap.recycle() }
    }

    @Test fun `aspect fit and conservative mask reduction retain single pixel text strokes`() {
        val bitmap = Bitmap.createBitmap(1024, 256, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            val mask = BooleanArray(1024 * 256).apply { this[11 * 1024 + 777] = true }
            val input = mangaInpaintInput(bitmap, mask)
            assertEquals(512, input.width); assertEquals(128, input.height); assertEquals(192, input.top)
            assertTrue(input.mask[(192 + 5) * 512 + 388])
            assertFalse(input.mask.take(192 * 512).any { it })
        } finally { bitmap.recycle() }
    }

    @Test fun `old saved settings default to local and every downloadable model pins immutable bytes`() {
        assertEquals(MangaInpaintEngine.LOCAL, MangaTranslationPlan.json.decodeFromString<MangaTranslationSettings>("{}").inpaintEngine)
        assertEquals(MangaInpaintEngine.entries.filter { it.downloadable }.toSet(), MangaInpaintModelCatalog.files.keys)
        MangaInpaintModelCatalog.files.values.forEach {
            assertTrue(it.sha256.matches(Regex("[0-9a-f]{64}"))); assertTrue(it.bytes > 0)
            assertFalse(it.url.contains("/resolve/main/"))
        }
    }
}
