package io.legado.app.model

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import io.legado.app.data.entities.Book
import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.model.settings.DownloadCacheSettings
import io.legado.app.domain.model.settings.OtherSettings
import io.legado.app.help.book.BookHelp
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import splitties.init.injectAsAppCtx
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [26, 35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageProviderDataUriSizeTest {
    private val book = Book(bookUrl = "svg-image-size", name = "SVG image size")
    private val wideSvg = """<svg width="1000" height="120" xmlns="http://www.w3.org/2000/svg"><rect width="100%" height="100%" fill="#FFFFFF"/></svg>"""

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        startKoin {
            modules(module {
                single<DownloadCacheSettingsGateway> {
                    object : DownloadCacheSettingsGateway {
                        override val currentSettings = DownloadCacheSettings()
                        override val settings = flowOf(currentSettings)
                        override suspend fun update(transform: (DownloadCacheSettings) -> DownloadCacheSettings) = Unit
                    }
                }
                single<OtherSettingsGateway> {
                    object : OtherSettingsGateway {
                        override val currentSettings = OtherSettings()
                        override val settings = flowOf(currentSettings)
                        override suspend fun update(transform: (OtherSettings) -> OtherSettings) = Unit
                    }
                }
            })
        }
    }

    @After
    fun tearDown() {
        BookHelp.clearCache(book)
        stopKoin()
    }

    private fun svgDataUri(markup: String, options: String = ""): String =
        "data:image/svg+xml;base64,${Base64.encodeToString(markup.toByteArray(), Base64.NO_WRAP)}$options"

    private fun png(): ByteArray {
        val bitmap = Bitmap.createBitmap(4, 6, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        return try {
            ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun `data uri svg size ignores the trailing image style options`() {
        val size = ImageProvider.dataUriImageSize(
            svgDataUri(wideSvg, """,{"style":"FULL"}""")
        )
        assertEquals(1000, size!!.width)
        assertEquals(120, size.height)
    }

    @Test
    fun `data uri svg size falls back to the viewBox`() {
        val markup = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 300 75"><rect width="100%" height="100%"/></svg>"""
        val size = ImageProvider.dataUriImageSize(svgDataUri(markup))
        assertEquals(300, size!!.width)
        assertEquals(75, size.height)
    }

    @Test
    fun `non svg data uri still reports its bitmap size`() {
        val src = "data:image/png;base64,${Base64.encodeToString(png(), Base64.NO_WRAP)}"
        val size = ImageProvider.dataUriImageSize(src)
        assertEquals(4, size!!.width)
        assertEquals(6, size.height)
    }

    @Test
    fun `plain url or invalid data uri has no intrinsic size`() {
        assertNull(ImageProvider.dataUriImageSize("https://invalid.example/card.jpg"))
        assertNull(ImageProvider.dataUriImageSize("data:image/png;base64,bm90IGFuIGltYWdl"))
    }

    /**
     * 首次分页时磁盘副本可能还没落盘或已损坏；data URI 的内在尺寸仍在 src 里，
     * 不能再回落到 512×512 的错误占位图比例（#2522 / #2607）。
     */
    @Test
    fun `broken cache file still yields the svg size on first pagination`() = runBlocking {
        val src = svgDataUri(wideSvg, """,{"style":"FULL"}""")
        BookHelp.writeImage(book, src, png())
        BookHelp.getImage(book, src).writeBytes(byteArrayOf(1, 2, 3))
        val size = ImageProvider.getImageSize(book, src, null)
        assertEquals(1000, size!!.width)
        assertEquals(120, size.height)
    }

    @Test
    fun `unrecognizable image reports unknown size instead of the error bitmap`() = runBlocking {
        val src = "https://invalid.example/broken.jpg"
        BookHelp.writeImage(book, src, png())
        BookHelp.getImage(book, src).writeBytes(byteArrayOf(1, 2, 3))
        assertNull(ImageProvider.getImageSize(book, src, null))
    }
}
