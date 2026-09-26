package io.legado.app.help.book

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.Book
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
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
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [26, 35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BookHelpImageFileTest {
    private val book = Book(bookUrl = "book-image-contract", name = "Image contract")

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
    }

    private fun png(): ByteArray {
        val bitmap = Bitmap.createBitmap(4, 6, Bitmap.Config.ARGB_8888)
        return try {
            ByteArrayOutputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun `valid cached original is reused without resolving its network URL`() = runBlocking {
        val src = "https://invalid.example/original.jpg,{\"headers\":{\"Referer\":\"original\"}}"
        val image = BookHelp.getImage(book, src)
        try {
            val bytes = png()
            BookHelp.writeImage(book, src, bytes)
            assertTrue(BookHelp.saveImage(null, book, src))
            assertArrayEquals(bytes, image.readBytes())
            val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            image.inputStream().use { BitmapFactory.decodeStream(it, null, dimensions) }
            assertEquals(4, dimensions.outWidth)
            assertEquals(6, dimensions.outHeight)
        } finally {
            image.delete()
        }
    }

    @Test
    fun `invalid bytes are rejected without replacing the cached original`() {
        val src = "https://invalid.example/replacement.jpg"
        val image = BookHelp.getImage(book, src)
        try {
            val original = png()
            BookHelp.writeImage(book, src, original)
            try {
                BookHelp.writeImage(book, src, "not an image".toByteArray())
                error("Expected invalid image to fail")
            } catch (_: IOException) {
                assertArrayEquals(original, image.readBytes())
            }
        } finally {
            image.delete()
        }
    }

    @Test
    fun `valid SVG survives the shared file validation path`() {
        val src = "https://invalid.example/page.svg"
        val image = BookHelp.getImage(book, src)
        try {
            val svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"8\" height=\"12\"><rect width=\"8\" height=\"12\"/></svg>".toByteArray()
            BookHelp.writeImage(book, src, svg)
            assertArrayEquals(svg, image.readBytes())
        } finally {
            image.delete()
        }
    }

    @Test
    fun `inline transfer replaces invalid legacy file with a validated original`() = runBlocking {
        val bytes = png()
        val src = "data:image/png;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
        val image = BookHelp.getImage(book, src)
        try {
            image.parentFile!!.mkdirs()
            image.writeBytes("old broken image".toByteArray())
            assertTrue(BookHelp.saveImage(null, book, src))
            assertArrayEquals(bytes, image.readBytes())
            assertTrue(BookHelp.saveImage(null, book, src))
        } finally {
            image.delete()
        }
    }

    @Test
    fun `failed inline image cannot leave a file that the next attempt treats as ready`() = runBlocking {
        val src = "data:image/png;base64,bm90IGFuIGltYWdl"
        val image = BookHelp.getImage(book, src)
        try {
            image.delete()
            assertFalse(BookHelp.saveImage(null, book, src))
            assertTrue(AppLog.logs.first().second.contains("图片数据无效"))
            assertFalse(image.exists())
            assertFalse(BookHelp.saveImage(null, book, src))
            assertFalse(image.exists())
        } finally {
            image.delete()
        }
    }
}
