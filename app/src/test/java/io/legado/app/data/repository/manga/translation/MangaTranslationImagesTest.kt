package io.legado.app.data.repository.manga.translation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import io.legado.app.domain.model.manga.*
import io.legado.app.domain.reader.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MangaTranslationImagesTest {
    @Test fun `layout ROI projection is exact at every output scale while source coverage is unchanged`() {
        val band = MangaTranslationBand(300, 3454, 400, 3300)
        val region = MangaTranslatedRegion("r", "原文", "译文", MangaTextBox(103.7f, 201.2f, 880.3f, 701.8f),
            layoutBox = MangaTextBox(133.44855f, 211.23767f, 635.3909f, 685.2174f),
            typesetting = MangaAiTypesetting(listOf("译文"), 14f, vertical = true))
        for (width in listOf(1280, 2268, 4000)) {
            val roi = region.overlayRect(width, 6000, band)
            val local = region.inOverlay(width, band, roi)
            assertEquals(region.pageLayoutRect(width, band), local.layoutBox.layoutPixels(roi.width(), roi.height()).apply { offset(roi.left, roi.top) })
            assertEquals(region.pageRect(region.textBox, width, band), local.textBox.pixels(roi.width(), roi.height()).apply { offset(roi.left, roi.top) })
            assertEquals(region.typesetting!!.lines, local.typesetting!!.lines)
        }
    }

    @Test fun `render ROI drops a recognition anchor wholly outside the corrected text area`() {
        val band = MangaTranslationBand(0, 500, 0, 500)
        val region = MangaTranslatedRegion("r", "source", "translation", MangaTextBox(300f, 400f, 700f, 600f),
            recognitionBox = MangaTextBox(300f, 10f, 700f, 50f))
        val rect = region.overlayRect(300, 500, band)
        val local = region.inOverlay(300, band, rect)
        assertNull(local.recognitionBox)
        MangaTranslationPlan(listOf(local)).validate()
        assertNotNull(region.recognitionBox)
    }
    @Test fun `line orientation distinguishes a tall paragraph from vertical columns`() {
        assertFalse(mangaVerticalText((0..5).map { Rect(10, it * 50, 180, it * 50 + 30) }))
        assertTrue(mangaVerticalText(listOf(Rect(20, 10, 50, 200), Rect(70, 10, 100, 200))))
        assertFalse(mangaVerticalText(emptyList()))
    }
    @Test fun `ordinary page planning never decodes a strip or runs auxiliary OCR`() = runBlocking {
        val decoder = object : MangaRegionDecoder<Bitmap> {
            override val imageSize = MangaImageSize(6000, 9000)
            override suspend fun decodeRegion(region: MangaImageRegion, sampleSize: Int): Bitmap = error("No preview needed to partition an ordinary page")
            override fun close() = Unit
        }
        assertEquals(listOf(MangaTranslationBand(0, 9000, 0, 9000)), mangaAnalysisBands(decoder, "ja"))
    }

    @Test fun `large page preview requests sampled pixels and keeps the complete page frame`() = runBlocking {
        var requested: MangaImageRegion? = null
        var sampling = 0
        val decoder = object : MangaRegionDecoder<Bitmap> {
            override val imageSize = MangaImageSize(6000, 9000)
            override suspend fun decodeRegion(region: MangaImageRegion, sampleSize: Int): Bitmap {
                requested = region; sampling = sampleSize
                return Bitmap.createBitmap(region.width / sampleSize, region.height / sampleSize, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            }
            override fun close() = Unit
        }
        val preview = mangaAnalysisPreview(decoder, 0, 9000)
        try {
            assertEquals(MangaImageRegion(0, 0, 6000, 9000), requested)
            assertTrue(sampling > 1)
            assertTrue(preview.width.toLong() * preview.height <= 2 * 1024 * 1024)
            assertEquals(Color.RED, preview.getPixel(preview.width / 2, preview.height / 2))
        } finally { preview.recycle() }
    }

    @Test fun `long strip chooses an actual gutter before forming overlapping analysis windows`() = runBlocking {
        val decoder = object : MangaRegionDecoder<Bitmap> {
            override val imageSize = MangaImageSize(512, 6000)
            override suspend fun decodeRegion(region: MangaImageRegion, sampleSize: Int): Bitmap {
                return Bitmap.createBitmap((region.width / sampleSize).coerceAtLeast(1), (region.height / sampleSize).coerceAtLeast(1), Bitmap.Config.ARGB_8888).apply {
                    eraseColor(Color.GRAY)
                    Canvas(this).drawRect(0f, (1700f - region.top) / sampleSize, width.toFloat(),
                        (1800f - region.top) / sampleSize, Paint().apply { color = Color.WHITE })
                }
            }
            override fun close() = Unit
        }
        val bands = mangaAnalysisBands(decoder, "und")
        assertEquals(1700, bands.first().coreBottom)
        assertEquals(6000, bands.sumOf { it.coreBottom - it.coreTop })
        bands.zipWithNext().forEach { (left, right) ->
            assertEquals(left.coreBottom, right.coreTop)
            assertTrue(left.bottom > right.top)
        }
    }

    @Test fun `context overlap gives each complete text region exactly one owner`() {
        val first = MangaTranslationBand(0, 1400, 0, 800)
        val second = MangaTranslationBand(400, 1800, 800, 1800)
        val firstView = MangaTranslatedRegion("r", "source", "translation", MangaTextBox(100f, 500f, 900f, 1100f / 1400 * 1000))
        val secondView = firstView.copy(textBox = MangaTextBox(100f, 300f / 1400 * 1000, 900f, 700f / 1400 * 1000))
        assertFalse(first.owns(firstView)); assertTrue(second.owns(secondView))
    }

    @Test fun `a larger target font grows the writable area instead of only shrinking the text`() {
        val bitmap = Bitmap.createBitmap(1200, 1200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            val box = MangaTextBox(300f, 300f, 700f, 400f)
            val plan = MangaTranslationPlan(listOf(MangaTranslatedRegion("r", "原文",
                "这是足够长的译文用来占满整个区域并需要换行", box, sourceFontSize = 30f)))
            val base = MangaPageComposer.planLayout(bitmap, plan, 1f).regions.single().layoutBox.layoutPixels(1200, 1200)
            val large = MangaPageComposer.planLayout(bitmap, plan, 1.5f).regions.single().layoutBox.layoutPixels(1200, 1200)
            assertTrue("150% must widen the writable area: base=$base large=$large",
                large.width().toLong() * large.height() > base.width().toLong() * base.height())
        } finally { bitmap.recycle() }
    }

    @Test fun `ROI projection preserves original pixel-aligned coordinates`() {
        val band = MangaTranslationBand(300, 2300, 400, 2000)
        val region = MangaTranslatedRegion("r", "source", "translation", MangaTextBox(103.7f, 201.2f, 880.3f, 701.8f), sourceFontSize = 17.5f)
        val rect = region.overlayRect(1952, 6000, band)
        val local = region.inOverlay(1952, band, rect)
        val roundTrip = local.textBox.pixels(rect.width(), rect.height()).apply { offset(rect.left, rect.top) }
        assertEquals(region.pageRect(region.textBox, 1952, band), roundTrip)
        assertEquals(region.sourceFontSize!! * (band.bottom - band.top) / 1000,
            local.sourceFontSize!! * rect.height() / 1000, 0.001f)
    }
}
