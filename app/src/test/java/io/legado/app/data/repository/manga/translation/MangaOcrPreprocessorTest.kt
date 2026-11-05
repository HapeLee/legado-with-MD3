package io.legado.app.data.repository.manga.translation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import io.legado.app.domain.model.manga.MangaOcrOptions
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MangaOcrPreprocessorTest {
    @Test fun `optional scale keeps small images at two times and bounds large or narrow inputs`() {
        assertEquals(MangaOcrInputSize(200, 160), mangaOcrInputSize(100, 80, true))
        for ((w, h) in listOf(2000 to 3000, 1 to 200000, 40000 to 30000)) {
            val size = mangaOcrInputSize(w, h, true)
            assertTrue(size.width > 0 && size.height > 0)
            assertTrue(maxOf(size.width, size.height) <= 4096)
            assertTrue(size.width.toLong() * size.height <= 4_194_304)
        }
    }

    @Test fun `defaults pass the original bitmap without copying or recycling it`() = runBlocking {
        val source = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        try {
            withMangaOcrInput(source, MangaOcrOptions()) { assertSame(source, it) }
            assertFalse(source.isRecycled)
        } finally { source.recycle() }
    }

    @Test fun `Otsu separates weak ink and background without modifying source pixels`() = runBlocking {
        val source = Bitmap.createBitmap(10, 4, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.rgb(190, 190, 190))
        for (y in 0 until 4) for (x in 0..3) source.setPixel(x, y, Color.rgb(120, 120, 120))
        var working: Bitmap? = null
        try {
            withMangaOcrInput(source, MangaOcrOptions(upscale2x = true, binarize = true)) { image ->
                working = image
                assertEquals(20, image.width); assertEquals(8, image.height)
                assertEquals(Color.BLACK, image.getPixel(2, 2))
                assertEquals(Color.WHITE, image.getPixel(17, 2))
                assertEquals(Color.rgb(120, 120, 120), source.getPixel(1, 1))
                assertEquals(Color.rgb(190, 190, 190), source.getPixel(8, 1))
            }
            assertTrue(working!!.isRecycled)
        } finally { source.recycle() }
    }

    @Test fun `invert is applied before binary threshold and retains original image`() = runBlocking {
        val source = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        source.setPixel(0, 0, Color.BLACK); source.setPixel(1, 0, Color.WHITE)
        try {
            withMangaOcrInput(source, MangaOcrOptions(invert = true, binarize = true)) {
                assertEquals(Color.WHITE, it.getPixel(0, 0)); assertEquals(Color.BLACK, it.getPixel(1, 0))
                assertEquals(Color.BLACK, source.getPixel(0, 0))
            }
        } finally { source.recycle() }
    }

    @Test fun `uniform white images do not gain false black pixels`() = runBlocking {
        val source = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            withMangaOcrInput(source, MangaOcrOptions(binarize = true)) {
                val pixels = IntArray(256); it.getPixels(pixels, 0, 16, 0, 0, 16, 16)
                assertTrue(pixels.all { pixel -> pixel == Color.WHITE })
            }
        } finally { source.recycle() }
    }

    @Test fun `cancellation holds working image until consumer exits and then recycles only the copy`() = runBlocking {
        val source = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val entered = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        var working: Bitmap? = null
        try {
            val task = launch {
                withMangaOcrInput(source, MangaOcrOptions(upscale2x = true)) {
                    working = it
                    withContext(NonCancellable) { entered.complete(Unit); finish.await(); assertFalse(it.isRecycled) }
                }
            }
            entered.await(); task.cancel()
            assertFalse(working!!.isRecycled)
            finish.complete(Unit); task.join()
            assertTrue(working!!.isRecycled); assertFalse(source.isRecycled)
        } finally { source.recycle() }
    }
}
