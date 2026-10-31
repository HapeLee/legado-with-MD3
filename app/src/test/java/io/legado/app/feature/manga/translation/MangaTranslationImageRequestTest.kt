package io.legado.app.feature.manga.translation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import coil3.ImageLoader
import coil3.decode.BitmapFactoryDecoder
import coil3.request.SuccessResult
import coil3.size.Size
import coil3.toBitmap
import io.legado.app.help.coil.CoverInterceptor
import io.legado.app.help.coil.MangaImageFileOwner
import io.legado.app.ui.book.manga.MangaReaderItemUi
import io.legado.app.ui.book.manga.MangaReaderSettings
import io.legado.app.ui.book.manga.imageRequest
import io.legado.app.ui.book.manga.requestId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MangaTranslationImageRequestTest {
    @Test fun `translated raster bypasses source rules and retains original load identity`() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val file = File.createTempFile("translated-identity", ".png", app.cacheDir)
        val bitmap = Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val original = MangaReaderItemUi.Page("page", "https://source.invalid/original.png", "book", 0, 1, 0, 1, "Chapter", "chapter")
        val translated = original.copy(translatedImageUrl = file.toURI().toString())
        val originalRequest = original.imageRequest(MangaReaderSettings(), app, Size.ORIGINAL)
        val owner = MangaImageFileOwner()
        val translatedRequest = translated.imageRequest(MangaReaderSettings(), app, Size(10, 10), fileOwner = owner)
        assertEquals(original.requestId, translated.requestId)
        assertEquals(original.imageUrl, translated.imageUrl)
        assertTrue(translatedRequest.data is File)
        assertNotEquals(originalRequest.memoryCacheKey, translatedRequest.memoryCacheKey)
        val loader = ImageLoader.Builder(app).components {
            add(BitmapFactoryDecoder.Factory())
            add(CoverInterceptor { _, _ -> error("A translated local file must not run source acquisition") })
        }.build()
        try {
            val result = loader.execute(translatedRequest)
            assertTrue(result.toString(), result is SuccessResult)
            val shown = (result as SuccessResult).image.toBitmap()
            assertEquals(Color.BLUE, shown.getPixel(shown.width / 2, shown.height / 2))
            assertEquals(32f / 48f, checkNotNull(owner.aspectRatio()), 0.000001f)
        } finally { owner.close(); loader.shutdown(); file.delete() }
    }
}
