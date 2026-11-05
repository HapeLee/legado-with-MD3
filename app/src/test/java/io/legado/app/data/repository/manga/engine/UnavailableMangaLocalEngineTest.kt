package io.legado.app.data.repository.manga.engine

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import io.legado.app.domain.model.manga.MangaInpaintEngine
import io.legado.app.domain.model.manga.MangaLocalOcrEngine
import io.legado.app.domain.model.manga.MangaOcrOptions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A product without the local engine must report a missing capability, never an empty recognition:
 * translation, cache identity and automatic queues all treat "no text" as a valid result.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UnavailableMangaLocalEngineTest {
    private val message = "Manga local engine is not installed"
    private val engine = UnavailableMangaLocalEngine(message)

    private fun bitmap() = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }

    @Test fun `bubble detection refuses instead of reporting no bubbles`() = runBlocking {
        val bitmap = bitmap()
        try {
            val failure = runCatching { engine.detectBubbles(bitmap) }.exceptionOrNull()
            assertTrue(failure is MangaLocalEngineUnavailableException)
            assertEquals(message, failure?.message)
        } finally { bitmap.recycle() }
    }

    @Test fun `recognition refuses instead of reporting no text`() = runBlocking {
        val bitmap = bitmap()
        try {
            val failure = runCatching {
                engine.recognize(bitmap, "ja", MangaLocalOcrEngine.PADDLE_V5, MangaOcrOptions())
            }.exceptionOrNull()
            assertTrue(failure is MangaLocalEngineUnavailableException)
        } finally { bitmap.recycle() }
    }

    @Test fun `inpainting readiness and erasure both refuse`() = runBlocking {
        assertTrue(runCatching { engine.requireInpaint(MangaInpaintEngine.MI_GAN) }
            .exceptionOrNull() is MangaLocalEngineUnavailableException)
        val bitmap = bitmap()
        try {
            val failure = runCatching {
                engine.erase(bitmap, BooleanArray(bitmap.width * bitmap.height), MangaInpaintEngine.MI_GAN)
            }.exceptionOrNull()
            assertTrue(failure is MangaLocalEngineUnavailableException)
        } finally { bitmap.recycle() }
    }

    @Test fun `band hints degrade to gutters instead of failing the page`() = runBlocking {
        val bitmap = bitmap()
        try { assertNull(engine.locate(bitmap, "ja")) } finally { bitmap.recycle() }
    }
}
