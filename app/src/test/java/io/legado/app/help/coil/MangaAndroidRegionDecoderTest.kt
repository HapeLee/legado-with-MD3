package io.legado.app.help.coil

import android.app.Application
import android.graphics.Bitmap
import io.legado.app.domain.reader.MangaImageRegion
import io.legado.app.domain.reader.MangaImageSize
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.Closeable
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [26, 35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MangaAndroidRegionDecoderTest {
    @Test
    fun `all EXIF orientations preserve exact tile pixels`() = runBlocking {
        val expected = listOf(
            intArrayOf(1, 2, 3, 4, 5, 6), intArrayOf(2, 1, 4, 3, 6, 5),
            intArrayOf(6, 5, 4, 3, 2, 1), intArrayOf(5, 6, 3, 4, 1, 2),
            intArrayOf(1, 3, 5, 2, 4, 6), intArrayOf(5, 3, 1, 6, 4, 2),
            intArrayOf(6, 4, 2, 5, 3, 1), intArrayOf(2, 4, 6, 1, 3, 5),
        )
        for (orientation in 1..8) {
            var closes = 0
            val decoder = MangaAndroidRegionDecoder(object : MangaAndroidRegionBackend {
                override val size = MangaImageSize(2, 3)
                override fun decode(region: MangaImageRegion, sampleSize: Int): Bitmap {
                    assertEquals(MangaImageRegion(0, 0, 2, 3), region)
                    assertEquals(1, sampleSize)
                    return Bitmap.createBitmap(
                        IntArray(6) { 0xff000000.toInt() or (it + 1) },
                        2, 3, Bitmap.Config.ARGB_8888
                    )
                }

                override fun close() {
                    closes++
                }
            }, orientation)
            val size = decoder.imageSize
            val bitmap = decoder.decodeRegion(MangaImageRegion(0, 0, size.width, size.height), 1)
            try {
                decoder.close(); decoder.close()
                assertEquals(1, closes)
                assertFalse(bitmap.isRecycled)
                val pixels = IntArray(6)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                assertArrayEquals(expected[orientation - 1].map { 0xff000000.toInt() or it }
                    .toIntArray(), pixels)
            } finally {
                bitmap.recycle(); decoder.close()
            }
        }
    }

    @Test
    fun `closing waits for native decoding and cancelled work is not published`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val gate = CountDownLatch(1)
        val closed = AtomicBoolean()
        val published = AtomicBoolean()
        val decoder = MangaAndroidRegionDecoder(object : MangaAndroidRegionBackend {
            override val size = MangaImageSize(2, 3)
            override fun decode(region: MangaImageRegion, sampleSize: Int): Bitmap {
                started.complete(Unit)
                check(gate.await(10, TimeUnit.SECONDS))
                assertFalse(closed.get())
                return Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888)
            }

            override fun close() {
                closed.set(true)
            }
        }, 1)
        val work =
            launch { decoder.decodeRegion(MangaImageRegion(0, 0, 2, 3), 1); published.set(true) }
        try {
            withTimeout(5_000) { started.await() }
            work.cancel()
            val closing = async(Dispatchers.Default) { decoder.close() }
            assertFalse(closed.get())
            gate.countDown()
            withTimeout(5_000) { work.join(); closing.await() }
            assertTrue(closed.get())
            assertFalse(published.get())
        } finally {
            gate.countDown(); work.join(); decoder.close()
        }
    }

    @Test
    fun `invalid regions and closed decoders fail without touching the native backend`() =
        runBlocking {
            val decoder = MangaAndroidRegionDecoder(object : MangaAndroidRegionBackend {
                override val size = MangaImageSize(2, 3)
                override fun decode(region: MangaImageRegion, sampleSize: Int): Bitmap =
                    error("Must not decode")

                override fun close() = Unit
            }, 1)
            try {
                try {
                    decoder.decodeRegion(MangaImageRegion(-1, 0, 2, 3), 1); fail()
                } catch (_: IllegalArgumentException) {
                }
                try {
                    decoder.decodeRegion(MangaImageRegion(0, 0, 2, 3), 3); fail()
                } catch (_: IllegalArgumentException) {
                }
                decoder.close()
                try {
                    decoder.decodeRegion(MangaImageRegion(0, 0, 2, 3), 1); fail()
                } catch (_: IllegalStateException) {
                }
            } finally {
                decoder.close()
            }
        }

    @Test
    fun `original source closes its independent lease exactly once`() {
        var closes = 0
        val source = MangaAndroidRegionSource.file(File("unused.png"), Closeable { closes++ })
        source.close(); source.close()
        assertEquals(1, closes)
    }
}
