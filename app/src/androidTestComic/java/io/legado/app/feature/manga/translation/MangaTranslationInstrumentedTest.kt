package io.legado.app.feature.manga.translation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.data.repository.manga.translation.MangaLocalTextDetector
import io.legado.app.data.repository.manga.translation.MangaPngWriter
import io.legado.app.domain.reader.MangaImageRegion
import io.legado.app.domain.model.manga.mangaTranslationBands
import io.legado.app.help.coil.MangaAndroidRegionSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MangaTranslationInstrumentedTest {
    @Test fun tallWebpPageKeepsExactDimensionsAcrossOddRenderBoundaries() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val bitmap = Bitmap.createBitmap(2268, 3154, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val file = File.createTempFile("manga-codec-test-", ".image", app.cacheDir)
        val encoded = File.createTempFile("manga-codec-output-", ".png", app.cacheDir)
        try {
            val canvas = Canvas(bitmap); val paint = Paint()
            for (y in 0 until bitmap.height) {
                paint.color = Color.rgb(y % 256, (y * 7) % 256, (y * 13) % 256)
                canvas.drawRect(0f, y.toFloat(), bitmap.width.toFloat(), y + 1f, paint)
            }
            val formats = if (android.os.Build.VERSION.SDK_INT >= 30)
                listOf(Bitmap.CompressFormat.PNG, Bitmap.CompressFormat.WEBP_LOSSLESS, Bitmap.CompressFormat.WEBP_LOSSY)
            else listOf(Bitmap.CompressFormat.PNG, Bitmap.CompressFormat.WEBP)
            for (format in formats) {
                file.outputStream().use { assertTrue(bitmap.compress(format, 90, it)) }
                val source = MangaAndroidRegionSource.file(file)
                try {
                    val decoder = source.open()
                    try {
                        MangaPngWriter(encoded.outputStream().buffered(), bitmap.width, bitmap.height).use { writer ->
                          for (band in mangaTranslationBands(bitmap.width, bitmap.height)) {
                            val tile = decoder.decodeRegion(MangaImageRegion(0, band.coreTop, bitmap.width, band.coreBottom), 1)
                            try {
                                assertEquals("$format rows ${band.coreTop}..${band.coreBottom} width", bitmap.width, tile.width)
                                assertEquals("$format rows ${band.coreTop}..${band.coreBottom} height", band.coreBottom - band.coreTop, tile.height)
                                if (format == Bitmap.CompressFormat.PNG || android.os.Build.VERSION.SDK_INT >= 30 && format == Bitmap.CompressFormat.WEBP_LOSSLESS)
                                    assertEquals("$format must not shift the first pixel row", bitmap.getPixel(42, band.coreTop), tile.getPixel(42, 0))
                                writer.append(tile, 0, tile.height)
                            } finally { tile.recycle() }
                          }
                        }
                        val corner = decoder.decodeRegion(MangaImageRegion(11, 1131, 101, 1160), 1)
                        try { assertEquals(90, corner.width); assertEquals(29, corner.height) } finally { corner.recycle() }
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(encoded.path, bounds)
                        assertEquals(bitmap.width, bounds.outWidth); assertEquals(bitmap.height, bounds.outHeight)
                    } finally { decoder.close() }
                } finally { source.close() }
            }
        } finally { bitmap.recycle(); file.delete(); encoded.delete() }
    }

    @Test fun bundledLocalDetectorRecognizesTextAndReturnsBoundedLineRegions() = runBlocking {
        val bitmap = Bitmap.createBitmap(1000, 400, Bitmap.Config.ARGB_8888)
        try {
            Canvas(bitmap).apply {
                drawColor(Color.WHITE)
                drawText("Hello comic reader", 50f, 180f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 70f })
            }
            val regions = MangaLocalTextDetector.detect(bitmap, "en")
            assertTrue(regions.any { it.original.contains("Hello", ignoreCase = true) })
            assertTrue(regions.flatMap { it.sourceLines }.any { it.quad != null })
            regions.forEach { it.textBox.validate(); it.eraseBoxes.forEach { box -> box.validate() } }
        } finally { bitmap.recycle() }
    }

    @Test fun streamedTallTranslationPngWorksWithTheReadersNativeRegionDecoder() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val file = File.createTempFile("translated-strip-", ".png", app.cacheDir)
        val stripe = Bitmap.createBitmap(128, 400, Bitmap.Config.ARGB_8888)
        try {
            MangaPngWriter(file.outputStream().buffered(), 128, 12_000).use { writer ->
                repeat(30) { i -> stripe.eraseColor(if (i % 2 == 0) Color.RED else Color.BLUE); writer.append(stripe, 0, 400) }
            }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            assertEquals(12_000, options.outHeight)
            val source = MangaAndroidRegionSource.file(file)
            try {
                val decoder = source.open()
                try {
                    val tile = decoder.decodeRegion(MangaImageRegion(0, 400, 128, 800), 1)
                    try { assertEquals(Color.BLUE, tile.getPixel(64, 200)) } finally { tile.recycle() }
                } finally { decoder.close() }
            } finally { source.close() }
        } finally { stripe.recycle(); file.delete() }
    }
}
