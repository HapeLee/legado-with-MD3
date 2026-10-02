package io.legado.app.help.coil

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import coil3.ImageLoader
import coil3.decode.BitmapFactoryDecoder
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Dimension
import coil3.size.Scale
import coil3.size.Size
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import androidx.compose.ui.geometry.Size as ComposeSize

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [26, 35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MangaWebtoonTilesTest {
    @Test
    fun `full width webtoon request loses horizontal detail on a very tall image`() = runBlocking {
        val file = File.createTempFile("webtoon-long", ".png")
        val bitmap = Bitmap.createBitmap(128, 12_000, Bitmap.Config.ARGB_8888)
        val loader = ImageLoader.Builder(RuntimeEnvironment.getApplication())
            .components { add(BitmapFactoryDecoder.Factory()) }.build()
        try {
            bitmap.eraseColor(android.graphics.Color.WHITE)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val request = ImageRequest.Builder(RuntimeEnvironment.getApplication()).data(file)
                .size(Size(Dimension(128), Dimension.Undefined)).scale(Scale.FILL).build()
            val result = loader.execute(request) as SuccessResult
            // Loading has succeeded, but the default height cap has also reduced its width.
            assertTrue(result.image.height <= 4096)
            assertTrue(result.image.width < 64)
        } finally {
            loader.shutdown()
            bitmap.recycle()
            file.delete()
        }
    }

    @Test
    fun `region painters preserve stripes and eink transforms are applied to each bounded tile`() =
        runBlocking {
            val original = Bitmap.createBitmap(128, 12_000, Bitmap.Config.ARGB_8888)
            try {
                val row =
                    IntArray(128) { if (it % 2 == 0) 0xff444444.toInt() else 0xffcccccc.toInt() }
                repeat(12_000) { original.setPixels(row, 0, 128, 0, it, 128, 1) }
                val sourceSize = IntSize(128, 12_000)
                for (tile in mangaWebtoonTiles(
                    sourceSize, sourceSize, Offset(0f, -6000f),
                    Rect(0f, 0f, 128f, 600f)
                )) {
                    // BitmapRegionDecoder JNI crashes in Windows Robolectric. Its real integration
                    // is covered separately by MangaWebtoonRegionInstrumentedTest on Android.
                    val bounds = tile.decodeBounds
                    val region = Bitmap.createBitmap(
                        original,
                        bounds.left,
                        bounds.top,
                        bounds.width,
                        bounds.height
                    )
                    try {
                        val painter = BitmapPainter(region.asImageBitmap())
                        val decoded =
                            render(painter, tile.decodeBounds.width, tile.decodeBounds.height)
                        try {
                            assertEquals(row[0], decoded.getPixel(0, 100))
                            assertEquals(row[1], decoded.getPixel(1, 100))
                            assertTrue(decoded.height <= 1026)
                        } finally {
                            decoded.recycle()
                        }
                        val eink = transformMangaWebtoonTile(
                            painter, tile,
                            listOf(io.legado.app.ui.book.manga.MangaEInkTransformation(128))
                        )
                        val filtered =
                            render(eink, tile.decodeBounds.width, tile.decodeBounds.height)
                        try {
                            assertEquals(android.graphics.Color.BLACK, filtered.getPixel(0, 100))
                            assertEquals(android.graphics.Color.WHITE, filtered.getPixel(1, 100))
                        } finally {
                            filtered.recycle()
                        }
                    } finally {
                        region.recycle()
                    }
                }
            } finally {
                original.recycle()
            }
        }

    private fun render(
        painter: androidx.compose.ui.graphics.painter.Painter,
        width: Int,
        height: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(
            Density(1f), LayoutDirection.Ltr, Canvas(bitmap.asImageBitmap()),
            ComposeSize(width.toFloat(), height.toFloat())
        ) {
            with(painter) { draw(size) }
        }
        return bitmap
    }

    @Test
    fun `fractional scaling draws adjacent regions without exposing page background`() {
        val sourceSize = IntSize(128, 12_000)
        val tiles = mangaWebtoonTiles(
            sourceSize, sourceSize, Offset(0f, -6000f),
            Rect(0f, 0f, 128f, 600f)
        )
        val bitmaps = tiles.associateWith { tile ->
            Bitmap.createBitmap(
                tile.decodeBounds.width,
                tile.decodeBounds.height,
                Bitmap.Config.ARGB_8888
            )
                .apply { eraseColor(android.graphics.Color.RED) }
        }
        val output = Bitmap.createBitmap(173, 16_219, Bitmap.Config.ARGB_8888)
        try {
            output.eraseColor(android.graphics.Color.GREEN)
            val painters =
                bitmaps.mapValues { BitmapPainter(it.value.asImageBitmap()) }.toImmutableMap()
            CanvasDrawScope().draw(
                Density(1f), LayoutDirection.Ltr, Canvas(output.asImageBitmap()),
                ComposeSize(output.width.toFloat(), output.height.toFloat())
            ) {
                drawMangaWebtoonTiles(sourceSize, painters)
            }
            val sorted = tiles.sortedBy { it.bounds.top }
            sorted.dropLast(1).forEach { tile ->
                val join =
                    (tile.bounds.bottom * output.height.toFloat() / sourceSize.height).toInt()
                for (y in join - 2..join + 2) for (x in 0 until output.width) {
                    assertEquals("gap at $x,$y", android.graphics.Color.RED, output.getPixel(x, y))
                }
            }
        } finally {
            output.recycle()
            bitmaps.values.forEach { it.recycle() }
        }
    }

    @Test
    fun `transparent regions replace the preview instead of blending the same image twice`() {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val output = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val expected = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(0x800000ff.toInt())
            val painter = BitmapPainter(bitmap.asImageBitmap())
            val tile = MangaWebtoonTile(IntRect(0, 0, 16, 16), IntRect(0, 0, 16, 16), 1)
            fun draw(target: Bitmap, tiles: Boolean) {
                target.eraseColor(android.graphics.Color.WHITE)
                CanvasDrawScope().draw(
                    Density(1f), LayoutDirection.Ltr, Canvas(target.asImageBitmap()),
                    ComposeSize(16f, 16f)
                ) {
                    with(painter) { draw(size) }
                    if (tiles) drawMangaWebtoonTiles(
                        IntSize(16, 16), persistentMapOf(tile to painter),
                        backgroundColor = Color.White
                    )
                }
            }
            draw(expected, false)
            draw(output, true)
            assertEquals(expected.getPixel(8, 8), output.getPixel(8, 8))
        } finally {
            bitmap.recycle()
            output.recycle()
            expected.recycle()
        }
    }
}
