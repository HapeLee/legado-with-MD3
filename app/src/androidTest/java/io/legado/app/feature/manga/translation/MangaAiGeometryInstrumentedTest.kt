package io.legado.app.feature.manga.translation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.data.repository.manga.translation.*
import io.legado.app.domain.model.manga.*
import io.legado.app.help.coil.MangaAndroidRegionSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises the actual decoder and Android text rasterizer, with no external AI or model download. */
@RunWith(AndroidJUnit4::class)
class MangaAiGeometryInstrumentedTest {
    @Test fun authoredAiLayoutKeepsItsOriginalFramePositionAndRowsInNativeRendering() {
        val whole = Bitmap.createBitmap(800, 1200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val composed = whole.copy(Bitmap.Config.ARGB_8888, true)
        try {
            val region = MangaTranslatedRegion("r", "原文", "你好\n世界", MangaTextBox(50f, 50f, 250f, 250f),
                layoutBox = MangaTextBox(550f, 500f, 950f, 900f), sourceFontSize = 200f,
                typesetting = MangaAiTypesetting(listOf("你好", "世界"), 30f))
            val plan = MangaPageComposer.prepare(whole, MangaTranslationPlan(listOf(region)),
                listOf(MangaBubbleDetection(MangaShapeRect(0, 0, 200, 300), 1f, 0)))
            assertEquals(region, plan.regions.single())
            MangaPageComposer.draw(whole, plan, false, 1f)
            val band = MangaTranslationBand(0, 1200, 0, 1200)
            val bounds = region.overlayRect(800, 1200, band)
            val roi = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            try {
                val local = region.inOverlay(800, band, bounds)
                assertEquals(36f, local.typesetting!!.fontSize * roi.height / 1000, .01f)
                MangaPageComposer.draw(roi, MangaTranslationPlan(listOf(local)), false, 1f)
                Canvas(composed).drawBitmap(roi, bounds.left.toFloat(), bounds.top.toFloat(), null)
                val a = IntArray(800 * 1200).also { whole.getPixels(it, 0, 800, 0, 0, 800, 1200) }
                val b = IntArray(a.size).also { composed.getPixels(it, 0, 800, 0, 0, 800, 1200) }
                assertArrayEquals(a, b)
                val area = region.layoutBox.pixels(800, 1200)
                val ink = Rect(800, 1200, 0, 0)
                for (i in a.indices) if (mangaInkContrast(a[i], Color.WHITE, 48)) {
                    ink.left = minOf(ink.left, i % 800); ink.right = maxOf(ink.right, i % 800 + 1)
                    ink.top = minOf(ink.top, i / 800); ink.bottom = maxOf(ink.bottom, i / 800 + 1)
                }
                assertTrue(area.contains(ink))
                assertEquals(area.exactCenterX(), ink.exactCenterX(), 2f)
                assertEquals(area.exactCenterY(), ink.exactCenterY(), 2f)
            } finally { roi.recycle() }
        } finally { whole.recycle(); composed.recycle() }
    }

    @Test fun cleanupTailAndTransparentOverlaySurviveAndroidPngRoundTrip() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        for (patch in listOf(false, true)) {
            val file = File.createTempFile("manga-ink-delta-", ".png", app.cacheDir)
            val source = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            var delta: Bitmap? = null
            try {
                val paint = Paint().apply { color = Color.BLACK }
                Canvas(source).apply {
                    drawRect(80f, 50f, 95f, 75f, paint)
                    drawRect(90f, 79f, 96f, 82f, paint)
                    drawRect(210f, 50f, 225f, 75f, paint)
                }
                val working = checkNotNull(source.copy(Bitmap.Config.ARGB_8888, true))
                delta = working
                val before = IntArray(400 * 200).also { source.getPixels(it, 0, 400, 0, 0, 400, 200) }
                fun box(l: Int, t: Int, r: Int, b: Int) = MangaTextBox(l * 2.5f, t * 5f, r * 2.5f, b * 5f)
                val line = box(80, 50, 96, 75)
                val region = MangaTranslatedRegion("r", "LONG ORIGINAL", "短", box(80, 50, 230, 84), eraseBoxes = listOf(line),
                    layoutBox = box(80, 135, 230, 185), sourceFontSize = 100f)
                if (patch) MangaPageComposer.draw(working, MangaTranslationPlan(listOf(region)), true, 1f)
                else MangaPageComposer.erase(working, MangaTranslationPlan(listOf(region)))
                mangaMakeTransparentDelta(working, before)
                file.outputStream().use { assertTrue(working.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                val loaded = checkNotNull(BitmapFactory.decodeFile(file.absolutePath))
                try {
                    assertEquals(0, Color.alpha(loaded.getPixel(30, 60)))
                    Canvas(source).apply {
                        drawRect(20f, 50f, 40f, 80f, Paint().apply { color = Color.RED })
                        drawBitmap(loaded, 0f, 0f, null)
                    }
                    assertEquals(Color.WHITE, source.getPixel(92, 80))
                    assertEquals(Color.WHITE, source.getPixel(215, 65))
                    assertEquals(Color.RED, source.getPixel(25, 60))
                } finally { loaded.recycle() }
            } finally { delta?.recycle(); source.recycle(); file.delete() }
        }
    }

    @Test fun originalCropCalibratesBodyInkAndPreservesSourcePlacement() = runBlocking<Unit> {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val file = File.createTempFile("manga-ai-geometry-", ".png", app.cacheDir)
        val source = Bitmap.createBitmap(1200, 1800, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val target = source.copy(Bitmap.Config.ARGB_8888, true)
        try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 24f }
            val ink = Rect(); paint.getTextBounds("国", 0, 1, ink); ink.offset(410, 700)
            Canvas(source).drawText("国", 410f, 700f, paint)
            file.outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }
            fun normalized(r: Rect) = MangaTextBox(r.left * 1000f / source.width, r.top * 1000f / source.height,
                r.right * 1000f / source.width, r.bottom * 1000f / source.height)
            val region = MangaTranslatedRegion("r", "国", "国", normalized(ink), layoutBox = MangaTextBox(100f, 100f, 900f, 900f),
                aiGeometry = MangaAiGeometry(glyphSamples = listOf(MangaSourceGlyph("国", normalized(ink)))))
            val inputSource = MangaAndroidRegionSource.file(file)
            val decoder = inputSource.open()
            try {
                val plan = measureMangaOriginalFonts(decoder, MangaTranslationBand(0, 1800, 0, 1800), MangaTranslationPlan(listOf(region)))
                assertEquals(MangaFontEvidence.ORIGINAL_GLYPHS, plan.regions.single().fontMeasurement!!.evidence)
                MangaPageComposer.draw(target, plan, false, 1f)
                fun bounds(bitmap: Bitmap): Rect {
                    val r = Rect(bitmap.width, bitmap.height, 0, 0)
                    for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                        if (mangaInkContrast(bitmap.getPixel(x, y), Color.WHITE, 48)) {
                            r.left = minOf(r.left, x); r.top = minOf(r.top, y); r.right = maxOf(r.right, x + 1); r.bottom = maxOf(r.bottom, y + 1)
                        }
                    }
                    return r
                }
                val before = bounds(source); val after = bounds(target)
                assertEquals(before.height().toFloat(), after.height().toFloat(), 2f)
                assertEquals(before.exactCenterX(), after.exactCenterX(), 2f)
                assertEquals(before.exactCenterY(), after.exactCenterY(), 2f)
                val frame = mangaCropFrame(region, 1200, 1800, 0)
                withMangaOriginalCrop(decoder, MangaTranslationBand(0, 1800, 0, 1800), frame) { crop ->
                    assertTrue(crop.width < source.width && crop.height < source.height)
                }
                Log.i("MangaAiGeometryTest", "originalInk=${before.height()} translatedInk=${after.height()} originalCenter=${before.exactCenterX()},${before.exactCenterY()} translatedCenter=${after.exactCenterX()},${after.exactCenterY()} evidence=${plan.regions.single().fontMeasurement}")
            } finally { decoder.close(); inputSource.close() }
        } finally { source.recycle(); target.recycle(); file.delete() }
    }
}
