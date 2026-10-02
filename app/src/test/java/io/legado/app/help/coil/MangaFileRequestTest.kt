package io.legado.app.help.coil

import android.app.Application
import android.graphics.Bitmap
import android.util.Base64
import coil3.ColorImage
import coil3.ImageLoader
import coil3.decode.BitmapFactoryDecoder
import coil3.decode.DataSource
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.crossfadeMillis
import coil3.request.maxBitmapSize
import coil3.size.Dimension
import coil3.size.Scale
import coil3.size.Size
import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookHelp
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.saket.telephoto.subsamplingimage.SubSamplingImageSource
import okio.Path.Companion.toOkioPath
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
    fun `switching loaded long webtoon to paged keeps preview bounded and original leased`() =
        runBlocking {
            val src = "https://invalid.example/mode-switch-long.png"
            val raw = BookHelp.getImage(book, src)
            val bitmap = Bitmap.createBitmap(128, 12_000, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(android.graphics.Color.RED)
                ByteArrayOutputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    BookHelp.writeImage(book, src, it.toByteArray())
                }
            } finally {
                bitmap.recycle()
            }
            val loader = ImageLoader.Builder(RuntimeEnvironment.getApplication()).components {
                add(BitmapFactoryDecoder.Factory())
                add(CoverInterceptor { _, data -> BookHelp.acquireReadingImage(null, book, data) })
            }.build()
            try {
                for (webtoon in listOf(true, false, true, false)) {
                    val owner = MangaImageFileOwner()
                    try {
                        val request = ImageRequest.Builder(RuntimeEnvironment.getApplication())
                            .data(src).size(Size(Dimension(128), Dimension.Undefined))
                            .scale(Scale.FILL).allowHardware(false)
                            .memoryCacheKey("mode-switch-long")
                            // Telephoto overrides this on every paged request.
                            .maxBitmapSize(if (webtoon) Size(4096, 4096) else Size.ORIGINAL)
                            .apply {
                                extras[CoverExtras.MangaFileOwner] = owner
                                extras[CoverExtras.MangaWebtoon] = webtoon
                            }.build()
                        val result = loader.execute(request)
                        if (result is ErrorResult) throw result.throwable
                        require(result is SuccessResult)
                        assertTrue(
                            "Preview height ${result.image.height} cannot be drawn safely",
                            result.image.height <= 4096
                        )
                        if (!webtoon) assertEquals(raw, result.request.data)
                        val borrowed = requireNotNull(owner.borrowForTiles())
                        try {
                            assertEquals(raw, borrowed.first)
                            owner.close()
                            BookHelp.clearCache(book)
                            assertTrue(raw.isFile)
                        } finally {
                            borrowed.second.close()
                        }
                    } finally {
                        owner.close()
                    }
                }
            } finally {
                loader.shutdown(); BookHelp.clearCache(book)
            }
        }

    @Test
    fun `webtoon reuses persisted preview after memory eviction and corrupt preview falls back to original`() =
        runBlocking {
            val src = "https://invalid.example/persisted-preview.png"
            val raw = BookHelp.getImage(book, src)
            val bitmap = Bitmap.createBitmap(80, 120, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(android.graphics.Color.RED)
                val bytes = ByteArrayOutputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray()
                }
                BookHelp.writeImage(book, src, bytes)
            } finally {
                bitmap.recycle()
            }
            val owner = MangaImageFileOwner()
            val loader = ImageLoader.Builder(RuntimeEnvironment.getApplication()).components {
                add(BitmapFactoryDecoder.Factory())
                add(CoverInterceptor { _, data -> BookHelp.acquireReadingImage(null, book, data) })
            }.build()
            var ratio: Float? = null
            var failures = 0
            try {
                val request =
                    ImageRequest.Builder(RuntimeEnvironment.getApplication()).data(src).size(20, 30)
                        .memoryCacheKey("persisted-webtoon").apply {
                            extras[CoverExtras.MangaFileOwner] = owner
                            extras[CoverExtras.MangaWebtoon] = true
                            extras[CoverExtras.MangaAspectRatio] = { ratio = it }
                        }.listener(onError = { _, _ -> failures++ }).build()
                val first = loader.execute(request)
                if (first is ErrorResult) throw first.throwable
                assertEquals(80f / 120f, ratio!!, 0f)
                val key = MangaPreviewCache.key(first.request.memoryCacheKey!!, request)
                val preview = withTimeout(5_000) {
                    var snapshot = MangaPreviewCache.open(key)
                    while (snapshot == null) {
                        delay(10); snapshot = MangaPreviewCache.open(key)
                    }
                    snapshot
                }
                val previewFile = preview.data.toFile()
                preview.close()
                loader.memoryCache!!.clear()
                val reused = loader.execute(request)
                if (reused is ErrorResult) throw reused.throwable
                assertEquals(previewFile, reused.request.data)
                assertEquals(
                    android.graphics.Color.RED,
                    (reused.image as coil3.BitmapImage).bitmap.getPixel(0, 0)
                )
                previewFile.writeText("corrupted preview")
                loader.memoryCache!!.clear()
                val recovered = loader.execute(request)
                if (recovered is ErrorResult) throw recovered.throwable
                assertEquals(raw, recovered.request.data)
                assertEquals(0, failures)
                withTimeout(5_000) {
                    while (true) {
                        val repaired = MangaPreviewCache.open(key)
                        if (repaired != null) {
                            repaired.close(); break
                        }
                        delay(10)
                    }
                }
                // 预览文件命中后，高清层仍借原图；请求退出后瓦片租约继续阻止清理。
                val tiles = requireNotNull(
                    mangaWebtoonTileSource(
                        reused as SuccessResult, owner,
                        RuntimeEnvironment.getApplication()
                    )
                )
                try {
                    owner.close()
                    BookHelp.clearCache(book)
                    assertTrue(raw.isFile)
                } finally {
                    tiles.close()
                }
                BookHelp.clearCache(book)
                assertFalse(raw.exists())
            } finally {
                owner.close(); loader.shutdown(); BookHelp.clearCache(book)
            }
        }

    @Test
    fun `adjacent preview is reused on entering page and replaced original invalidates bitmap cache`() =
        runBlocking {
            val src = "https://invalid.example/warmed-page.png"
            val file = BookHelp.getImage(book, src)
            fun writePng(color: Int) {
                val bitmap = Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(color)
                    val output = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                    BookHelp.writeImage(book, src, output.toByteArray())
                } finally {
                    bitmap.recycle()
                }
            }

            val previewOwner = MangaImageFileOwner()
            val visibleOwner = MangaImageFileOwner()
            var transfers = 0
            val loader = ImageLoader.Builder(RuntimeEnvironment.getApplication()).components {
                add(BitmapFactoryDecoder.Factory())
                add(CoverInterceptor { _, data ->
                    BookHelp.acquireReadingImage(null, book, data, onDownload = { transfers++ })
                })
            }.build()
            try {
                writePng(android.graphics.Color.RED)
                val request = ImageRequest.Builder(RuntimeEnvironment.getApplication())
                    .data(src).size(20, 30).allowHardware(false).memoryCacheKey("adjacent-page")
                    .crossfade(200)
                    .apply { extras[CoverExtras.MangaFileOwner] = previewOwner }.build()
                val preview = loader.execute(request)
                if (preview is ErrorResult) throw preview.throwable
                val visible = request.newBuilder().apply {
                    extras[CoverExtras.MangaFileOwner] = visibleOwner
                }.build()
                val reused = loader.execute(visible)
                if (reused is ErrorResult) throw reused.throwable
                assertEquals(DataSource.MEMORY_CACHE, (reused as SuccessResult).dataSource)
                assertEquals(0, reused.request.crossfadeMillis)
                assertEquals(200, (preview as SuccessResult).request.crossfadeMillis)
                val oldModified = file.lastModified()
                writePng(android.graphics.Color.BLUE)
                assertTrue(file.setLastModified(oldModified + 2_000))
                val replaced = loader.execute(visible)
                if (replaced is ErrorResult) throw replaced.throwable
                assertEquals(DataSource.DISK, (replaced as SuccessResult).dataSource)
                assertEquals(
                    android.graphics.Color.BLUE,
                    (replaced.image as coil3.BitmapImage).bitmap.getPixel(0, 0)
                )
                assertEquals(0, transfers)
            } finally {
                previewOwner.close()
                visibleOwner.close()
                loader.shutdown()
                BookHelp.clearCache(book)
            }
        }

    @Test
    fun `file prefetch does not decode or notify Ready and visible request gets a real bitmap`() =
        runBlocking {
            val bitmap = Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888)
            val bytes = try {
                ByteArrayOutputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    it.toByteArray()
                }
            } finally {
                bitmap.recycle()
            }
            val src = "data:image/png;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
            val owner = MangaImageFileOwner()
            val displayOwner = MangaImageFileOwner()
            var acquisitions = 0
            var ready = 0
            var originalRatio: Float? = null
            val loader = ImageLoader.Builder(RuntimeEnvironment.getApplication()).components {
                add(BitmapFactoryDecoder.Factory())
                add(CoverInterceptor { _, data ->
                    acquisitions++
                    BookHelp.acquireReadingImage(null, book, data)
                })
            }.build()
            try {
                val request = ImageRequest.Builder(RuntimeEnvironment.getApplication())
                    .data(src).size(20, 30).allowHardware(false)
                    .apply {
                        extras[CoverExtras.MangaFileOwner] = owner
                        extras[CoverExtras.MangaAspectRatio] = { originalRatio = it }
                    }
                    .listener(onSuccess = { _, _ -> ready++ }).build()
                val preload = loader.execute(request.asMangaPrefetch())
                if (preload is ErrorResult) throw preload.throwable
                assertTrue(preload.image is ColorImage)
                assertEquals(0, ready)
                assertEquals(40f / 60f, originalRatio!!, 0f)
                val displayed = loader.execute(request.newBuilder().apply {
                    extras[CoverExtras.MangaFileOwner] = displayOwner
                }.build())
                if (displayed is ErrorResult) throw displayed.throwable
                assertFalse(displayed.image is ColorImage)
                assertEquals(DataSource.DISK, (displayed as SuccessResult).dataSource)
                assertEquals(1, ready)
                loader.execute(request)
                // 同 owner 的后续预览直接使用已持有的有效原图，无需重新查库/获取。
                assertEquals(2, acquisitions)
            } finally {
                owner.close()
                displayOwner.close()
                loader.shutdown()
                BookHelp.clearCache(book)
            }
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
            val tiles = recoveringMangaTileSource(SubSamplingImageSource.file(image.toOkioPath()), owner)
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
