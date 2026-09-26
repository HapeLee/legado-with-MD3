package io.legado.app.help.coil

import android.app.Application
import android.graphics.Bitmap
import android.util.Base64
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.BitmapFactoryDecoder
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.ErrorResult
import coil3.request.allowHardware
import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookHelp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import splitties.init.injectAsAppCtx
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [26, 35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MangaFileRequestTest {
    private val book = Book(bookUrl = "manga-file-render", name = "File render")

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
    }

    @Test
    fun `Coil reports local original even when preview is reused from memory`() = runBlocking {
        val bitmap = Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888)
        val bytes = try {
            ByteArrayOutputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                it.toByteArray()
            }
        } finally { bitmap.recycle() }
        val src = "data:image/png;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
        val image = BookHelp.getImage(book, src)
        val owner = MangaImageFileOwner()
        val replacementOwner = MangaImageFileOwner()
        var transfers = 0
        val loader = ImageLoader.Builder(RuntimeEnvironment.getApplication())
            .components {
                // Robolectric Windows 的 ImageDecoder 文件 JNI 返回 "Only supported on Android"。
                // 使用真实 BitmapFactory 解码验证相同的 FileFetcher/缓存/拦截器链路。
                add(BitmapFactoryDecoder.Factory())
                add(CoverInterceptor { request, data ->
                    BookHelp.acquireReadingImage(
                        null, book, data, onDownload = request.extras[CoverExtras.MangaFileTransferStarted] ?: {},
                    )
                })
            }.build()
        try {
            val request = ImageRequest.Builder(RuntimeEnvironment.getApplication())
                .data(src).size(20, 30).allowHardware(false)
                .apply {
                    extras[CoverExtras.Manga] = true
                    extras[CoverExtras.MangaBookUrl] = book.bookUrl
                    extras[CoverExtras.MangaFileOwner] = owner
                    extras[CoverExtras.MangaFileTransferStarted] = { transfers++ }
                }.build()
            val firstResult = loader.execute(request)
            if (firstResult is ErrorResult) throw firstResult.throwable
            val first = firstResult as SuccessResult
            assertEquals(image, first.request.data)
            assertEquals(DataSource.DISK, first.dataSource)
            assertTrue(image.isFile)
            val nextResult = loader.execute(request)
            if (nextResult is ErrorResult) throw nextResult.throwable
            val next = nextResult as SuccessResult
            assertEquals(image, next.request.data)
            assertEquals(DataSource.MEMORY_CACHE, next.dataSource)
            assertEquals(1, transfers)
            // 模拟 Compose 请求先退出，区域解码器最后退出。
            val tiles = requireNotNull(owner.borrowForTiles()).second
            owner.close()
            BookHelp.clearCache(book)
            assertTrue(image.exists())
            tiles.close()
            BookHelp.clearCache(book)
            assertFalse(image.exists())
            // 预览在内存中也必须先恢复被淘汰的原图，并报告真实获取，不能继续冒充 Ready。
            val replacement = request.newBuilder().apply {
                extras[CoverExtras.MangaFileOwner] = replacementOwner
            }.build()
            val restored = loader.execute(replacement)
            if (restored is ErrorResult) throw restored.throwable
            assertEquals(image, restored.request.data)
            assertTrue(image.exists())
            assertEquals(2, transfers)
        } finally {
            owner.close()
            replacementOwner.close()
            loader.shutdown()
            BookHelp.clearCache(book)
        }
    }
}
