package io.legado.app.help.coil

import android.app.Application
import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [26, 35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MangaImageDimensionsTest {
    private fun write(
        file: File,
        width: Int,
        height: Int,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG
    ) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { assertTrue(bitmap.compress(format, 100, it)) }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun `dimensions survive reopen and invalid index or replaced original is rebuilt`() {
        val root = Files.createTempDirectory("manga-dimensions").toFile()
        val file = File(root, "page.png")
        try {
            write(file, 40, 60)
            assertEquals(40f / 60f, MangaImageDimensions.read(file)!!, 0f)
            val index = File(root, ".dimensions/page.png")
            assertTrue(index.isFile)
            assertEquals(40f / 60f, MangaImageDimensions.read(file)!!, 0f)
            index.writeText("corrupt\\u????")
            assertEquals(40f / 60f, MangaImageDimensions.read(file)!!, 0f)
            write(file, 80, 40)
            assertEquals(2f, MangaImageDimensions.read(file)!!, 0f)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `dimensions account for EXIF rotation before layout`() {
        val root = Files.createTempDirectory("manga-exif").toFile()
        val file = File(root, "page.jpg")
        try {
            write(file, 40, 60, Bitmap.CompressFormat.JPEG)
            ExifInterface(file.path).apply {
                setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_ROTATE_90.toString()
                )
                saveAttributes()
            }
            assertEquals(60f / 40f, MangaImageDimensions.read(file)!!, 0f)
        } finally {
            root.deleteRecursively()
        }
    }
}
