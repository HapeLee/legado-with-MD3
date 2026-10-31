package io.legado.app.data.repository.manga.translation

import android.app.Application
import android.graphics.*
import io.legado.app.domain.model.manga.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MangaContainerGeometryTest {
    @Test fun `OCR block spanning two bubbles is split before translation while rows in each bubble join`() {
        val a = MangaSourceLine("first", MangaTextBox(100f, 200f, 300f, 300f))
        val b = MangaSourceLine("part", MangaTextBox(100f, 400f, 300f, 500f))
        val c = MangaSourceLine("continuation", MangaTextBox(600f, 200f, 800f, 300f))
        val block = MangaTranslatedRegion("r0", "first\npart\ncontinuation", "", MangaTextBox(100f, 200f, 800f, 500f), sourceLines = listOf(a, b, c))
        val detections = listOf(MangaBubbleDetection(MangaShapeRect(0, 0, 45, 100), .9f, 0),
            MangaBubbleDetection(MangaShapeRect(50, 0, 100, 100), .9f, 0))
        val regions = MangaContainerGeometry.regroup(100, 100, listOf(block), detections)
        assertEquals(listOf("first\npart", "continuation"), regions.map { it.original })
        assertEquals(listOf(2, 1), regions.map { it.eraseBoxes.size })
        assertEquals(2, regions.map { it.id }.distinct().size)
        assertTrue(regions[0].textBox.right < regions[1].textBox.left)
    }

    @Test fun `complete OCR blocks keep source edges beyond an incomplete line box`() {
        val full = MangaTextBox(100f, 200f, 800f, 500f)
        val line = MangaSourceLine("complete source text", MangaTextBox(110f, 210f, 400f, 450f))
        val block = MangaTranslatedRegion("r0", line.text, line.text, full, sourceLines = listOf(line), eraseBoxes = listOf(line.bounds))
        val grouped = MangaContainerGeometry.regroup(400, 200, listOf(block), emptyList()).single()
        assertEquals(full, grouped.textBox)
        assertEquals(listOf(line.bounds), grouped.eraseBoxes)
        assertEquals(listOf(line), grouped.sourceLines)
    }

    @Test fun `closed circular containers retain separate persistent masks and survive ROI mapping`() {
        val bitmap = Bitmap.createBitmap(340, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.LTGRAY) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val canvas = Canvas(bitmap)
        for (x in listOf(90f, 245f)) {
            paint.style = Paint.Style.FILL; paint.color = Color.WHITE; canvas.drawCircle(x, 100f, 85f, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.color = Color.BLACK; canvas.drawCircle(x, 100f, 85f, paint)
        }
        paint.style = Paint.Style.FILL; paint.color = Color.BLACK; paint.textSize = 26f
        canvas.drawText("HELLO", 45f, 105f, paint); canvas.drawText("WORLD", 195f, 105f, paint)
        fun box(l: Int, r: Int) = MangaTextBox(l * 1000f / 340, 400f, r * 1000f / 340, 550f)
        val plan = MangaTranslationPlan(listOf(MangaTranslatedRegion("a", "HELLO", "你好", box(40, 140)),
            MangaTranslatedRegion("b", "WORLD", "世界", box(190, 295))))
        val detections = listOf(MangaBubbleDetection(MangaShapeRect(5, 15, 175, 185), .9f, 0),
            MangaBubbleDetection(MangaShapeRect(160, 15, 330, 185), .9f, 0))
        val prepared = MangaPageComposer.prepare(bitmap, plan, detections)
        assertTrue(prepared.regions.all { it.bubbleShape != null })
        assertEquals(2, prepared.regions.map { it.bubbleShape!!.id }.distinct().size)
        assertFalse(MangaPageComposer.hasLayoutOverlap(prepared))
        val restored = MangaTranslationPlan.json.decodeFromString<MangaTranslationPlan>(MangaTranslationPlan.json.encodeToString(MangaTranslationPlan.serializer(), prepared)).validate()
        assertEquals(prepared, restored)
        val band = MangaTranslationBand(0, 200, 0, 200)
        restored.regions.forEach { region ->
            val roi = region.overlayRect(340, 200, band)
            val local = region.inOverlay(340, band, roi)
            val globalMask = MangaContainerGeometry.raster(region.bubbleShape!!, 340, 200, roi)
            val localMask = MangaContainerGeometry.raster(local.bubbleShape!!, roi.width(), roi.height(), Rect(0, 0, roi.width(), roi.height()))
            assertArrayEquals(globalMask, localMask)
        }
        MangaPageComposer.erase(bitmap, prepared)
        MangaPageComposer.draw(bitmap, prepared, false, 1f)
        val file = java.io.File("build/reports/manga-translation/overlay-two-bubbles.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertEquals(Color.LTGRAY, bitmap.getPixel(0, 0))
        bitmap.recycle()
    }

    @Test fun `model cannot supply client trusted masks and malformed cache runs fail validation`() {
        val shape = MangaBubbleShape("b", MangaTextBox(0f, 0f, 1000f, 1000f), 10, 10, listOf(0, 100))
        val region = MangaTranslatedRegion("r", "hello", "你好", shape.bounds, bubbleShape = shape)
        val json = MangaTranslationPlan.json.encodeToString(MangaTranslationPlan.serializer(), MangaTranslationPlan(listOf(region)))
        assertNull(MangaTranslationPlan.parse(json).regions.single().bubbleShape)
        assertThrows(IllegalArgumentException::class.java) { shape.copy(runs = listOf(90, 20)).validate() }
        assertThrows(IllegalArgumentException::class.java) { shape.copy(runs = listOf(20, 10, 10, 20)).validate() }
    }
}
