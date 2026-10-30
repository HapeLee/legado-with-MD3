package io.legado.app.data.repository.manga.translation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import io.legado.app.domain.model.manga.*
import io.legado.app.domain.reader.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MangaAiCropsTest {
    private val source = MangaTranslatedRegion("r0", "原文", "译文", MangaTextBox(100f, 100f, 600f, 700f))
    private fun decoder(bitmap: Bitmap) = object : MangaRegionDecoder<Bitmap> {
        override val imageSize = MangaImageSize(bitmap.width, bitmap.height)
        val decoded = mutableListOf<MangaImageRegion>()
        override suspend fun decodeRegion(region: MangaImageRegion, sampleSize: Int): Bitmap {
            decoded += region
            val copy = Bitmap.createBitmap(bitmap, region.left, region.top, region.width, region.height).copy(Bitmap.Config.ARGB_8888, true)
            return if (sampleSize == 1) copy else Bitmap.createScaledBitmap(copy, region.width / sampleSize, region.height / sampleSize, true).also { copy.recycle() }
        }
        override fun close() = Unit
    }

    @Test fun `original crop respects band offset budget and cancellation ownership`() = runBlocking {
        val image = Bitmap.createBitmap(2600, 3000, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            val frame = MangaCropFrame("crop", "r", MangaImageRegion(10, 20, 2590, 2020), 2600, 2200)
            val band = MangaTranslationBand(300, 2500, 300, 2500)
            val d = decoder(image)
            var retained: Bitmap? = null
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val job = launch {
                withMangaOriginalCrop(d, band, frame) {
                    retained = it
                    assertTrue(it.width.toLong() * it.height <= 1024 * 1024 && maxOf(it.width, it.height) <= 2048)
                    withContext(NonCancellable) { entered.complete(Unit); release.await(); assertFalse(it.isRecycled) }
                }
            }
            entered.await(); job.cancel(); release.complete(Unit); job.join()
            assertTrue(retained!!.isRecycled)
            assertFalse(image.isRecycled)
            assertEquals(320, d.decoded.single().top)
        } finally { image.recycle() }
    }

    @Test fun `font measurement uses original body pixels ignores ruby and calibrates real rendered ink`() = runBlocking {
        for (font in listOf(10f, 24f, 60f)) {
            val image = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            val target = image.copy(Bitmap.Config.ARGB_8888, true).apply { eraseColor(Color.WHITE) }
            try {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = font }
                Canvas(image).drawText("H", 200f, 400f, paint)
                val ink = Rect(); paint.getTextBounds("H", 0, 1, ink); ink.offset(200, 400)
                fun box(r: Rect) = MangaTextBox(r.left * 1000f / 600, r.top * 1000f / 800, r.right * 1000f / 600, r.bottom * 1000f / 800)
                val region = MangaTranslatedRegion("r", "H", "H", box(ink), layoutBox = MangaTextBox(100f, 100f, 900f, 900f),
                    aiGeometry = MangaAiGeometry(glyphSamples = listOf(MangaSourceGlyph("H", box(ink)),
                        MangaSourceGlyph("H", box(ink), MangaGlyphRole.RUBY))))
                val measured = measureMangaOriginalFonts(decoder(image), MangaTranslationBand(0, 800, 0, 800), MangaTranslationPlan(listOf(region)))
                val result = measured.regions.single()
                assertEquals(MangaFontEvidence.ORIGINAL_GLYPHS, result.fontMeasurement!!.evidence)
                assertEquals(1, result.fontMeasurement.samples)
                assertEquals(ink.height().toFloat(), result.sourceFontSize!! * .8f, 1f)
                for (translation in listOf("H", "国")) {
                    target.eraseColor(Color.WHITE)
                    MangaPageComposer.draw(target, measured.copy(regions = listOf(result.copy(translation = translation))), false, 1f)
                    val rows = (0 until target.height).filter { y -> (0 until target.width).any { x -> mangaInkContrast(target.getPixel(x, y), Color.WHITE, 48) } }
                    assertEquals("font=$font text=$translation", ink.height().toFloat(), (rows.last() - rows.first() + 1).toFloat(), 2f)
                }
            } finally { image.recycle(); target.recycle() }
        }
    }

    @Test fun `safe rectangle clips layout without expanding erasure and diagnostic never changes original`() {
        val image = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            val safe = MangaTextBox(120f, 140f, 550f, 650f)
            val region = source.copy(aiGeometry = MangaAiGeometry(safeBox = safe))
            val plan = constrainMangaAiSafety(image, MangaTranslationPlan(listOf(region)))
            assertTrue(safe.contains(plan.regions.single().layoutBox))
            assertEquals(region.eraseBoxes, plan.regions.single().eraseBoxes)
            val marked = mangaGeometryDiagnostic(image, plan)
            marked.recycle()
            assertEquals(Color.WHITE, image.getPixel(40, 60))
            val fit = MangaShapeTextLayout.layout(200, 200, null, "Hi", MangaShapeTextLayout.Orientation.HORIZONTAL, 20, 20) { text, size -> text.length * size.toFloat() }
            val moved = MangaShapeTextLayout.anchor(fit, 200, 200, null, 40f, 50f)
            assertEquals(40f, (moved.runs.minOf { it.bounds.left } + moved.runs.maxOf { it.bounds.right }) / 2f, 1f)
        } finally { image.recycle() }
    }

    @Test fun `coarse source box cannot recenter translation away from refined original lettering`() {
        val image = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            val ink = MangaTextBox(100f, 200f, 300f, 300f)
            val region = MangaTranslatedRegion("r", "H", "H", MangaTextBox(50f, 50f, 900f, 900f),
                eraseBoxes = listOf(ink), sourceFontSize = 30f)
            MangaPageComposer.draw(image, MangaTranslationPlan(listOf(region)), false, 1f)
            val bounds = Rect(500, 500, 0, 0)
            for (y in 0 until 500) for (x in 0 until 500) if (mangaInkContrast(image.getPixel(x, y), Color.WHITE, 48)) {
                bounds.left = minOf(bounds.left, x); bounds.right = maxOf(bounds.right, x + 1)
                bounds.top = minOf(bounds.top, y); bounds.bottom = maxOf(bounds.bottom, y + 1)
            }
            assertEquals(100f, bounds.exactCenterX(), 2f)
            assertEquals(125f, bounds.exactCenterY(), 2f)
        } finally { image.recycle() }
    }
}
