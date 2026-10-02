package io.legado.app.help.coil

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.ImageLoader
import coil3.decode.BitmapFactoryDecoder
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Dimension
import coil3.size.Scale
import coil3.size.Size
import io.legado.app.domain.reader.MangaImageRegion
import io.legado.app.domain.reader.MangaImageSize
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Closeable
import java.io.File
import androidx.compose.ui.geometry.Size as ComposeSize

@RunWith(AndroidJUnit4::class)
class MangaWebtoonRegionInstrumentedTest {
    @Test
    fun nativeJpegExifOrientationMapsDisplayedRegionsToOriginalPixels() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val file = File.createTempFile("webtoon-exif", ".jpg", context.cacheDir)
        val original = Bitmap.createBitmap(96, 64, Bitmap.Config.ARGB_8888)
        try {
            val pixels = IntArray(96 * 64) { index ->
                if (index % 96 < 48) android.graphics.Color.RED else android.graphics.Color.BLUE
            }
            original.setPixels(pixels, 0, 96, 0, 0, 96, 64)
            file.outputStream().use { original.compress(Bitmap.CompressFormat.JPEG, 100, it) }
            ExifInterface(file).apply {
                setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_ROTATE_90.toString()
                )
                saveAttributes()
            }
            val source = MangaAndroidRegionSource.file(file)
            try {
                val decoder = source.open()
                try {
                    assertEquals(MangaImageSize(64, 96), decoder.imageSize)
                    val top = decoder.decodeRegion(MangaImageRegion(0, 0, 32, 32), 1)
                    val bottom = decoder.decodeRegion(MangaImageRegion(0, 64, 32, 96), 1)
                    try {
                        decoder.close()
                        assertTrue(android.graphics.Color.red(top.getPixel(16, 16)) > 240)
                        assertTrue(android.graphics.Color.blue(bottom.getPixel(16, 16)) > 240)
                        assertTrue(android.graphics.Color.blue(top.getPixel(16, 16)) < 10)
                    } finally {
                        top.recycle(); bottom.recycle()
                    }
                } finally {
                    decoder.close()
                }
            } finally {
                source.close()
            }
        } finally {
            original.recycle(); file.delete()
        }
    }

    @Test
    fun fullWidthWebtoonRequestLosesHorizontalDetailOnVeryTallImage() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val file = File.createTempFile("webtoon-long", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(128, 12_000, Bitmap.Config.ARGB_8888)
        val loader = ImageLoader.Builder(ApplicationProvider.getApplicationContext<Application>())
            .components { add(BitmapFactoryDecoder.Factory()) }.build()
        try {
            bitmap.eraseColor(android.graphics.Color.WHITE)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val request =
                ImageRequest.Builder(ApplicationProvider.getApplicationContext<Application>())
                    .data(file)
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
    fun nativeLongImageRegionsPreserveStripesAndApplyEinkWithoutWholeImageDecode() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val file = File.createTempFile("webtoon-detail", ".png", context.cacheDir)
        val original = Bitmap.createBitmap(128, 12_000, Bitmap.Config.ARGB_8888)
        val loader =
            ImageLoader.Builder(context).components { add(BitmapFactoryDecoder.Factory()) }.build()
        val owner = MangaImageFileOwner()
        try {
            val row = IntArray(128) { if (it % 2 == 0) 0xff444444.toInt() else 0xffcccccc.toInt() }
            repeat(12_000) { original.setPixels(row, 0, 128, 0, it, 128, 1) }
            file.outputStream().use { original.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val result = loader.execute(
                ImageRequest.Builder(context).data(file)
                    .size(Size(Dimension(128), Dimension.Undefined)).scale(Scale.FILL).build()
            ) as SuccessResult
            // 模拟结果来自预览缓存；高清解码仍必须读取 owner 持有的原图。
            owner.attach(file, Closeable {})
            val cached = result.copy(
                request = result.request.newBuilder()
                    .data(File(context.cacheDir, "unread-webtoon-preview.png")).build()
            )
            val source = requireNotNull(mangaWebtoonTileSource(cached, owner, context))
            try {
                val decoder = source.open()
                try {
                    assertEquals(MangaImageSize(128, 12_000), decoder.imageSize)
                    val imageSize = IntSize(decoder.imageSize.width, decoder.imageSize.height)
                    val tile = mangaWebtoonTiles(
                        imageSize, imageSize, Offset(0f, -6000f),
                        Rect(0f, 0f, 128f, 600f)
                    ).first()
                    val bounds = tile.decodeBounds
                    val painter = BitmapPainter(
                        decoder.decodeRegion(
                            MangaImageRegion(bounds.left, bounds.top, bounds.right, bounds.bottom),
                            tile.sampleSize,
                        ).asImageBitmap()
                    )
                    val decoded = render(painter, tile.decodeBounds.width, tile.decodeBounds.height)
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
                    val filtered = render(eink, tile.decodeBounds.width, tile.decodeBounds.height)
                    try {
                        assertEquals(android.graphics.Color.BLACK, filtered.getPixel(0, 100))
                        assertEquals(android.graphics.Color.WHITE, filtered.getPixel(1, 100))
                    } finally {
                        filtered.recycle()
                    }
                } finally {
                    decoder.close()
                }
            } finally {
                source.close()
            }
        } finally {
            owner.close()
            loader.shutdown()
            original.recycle()
            file.delete()
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
}
