package io.legado.app.data.repository.manga.translation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import io.legado.app.domain.model.manga.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MangaPageComposerTest {
    @Test fun `AI source rectangles may overlap but indistinguishable centers remain a conflict`() {
        val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            val a = MangaTranslatedRegion("a", "first", "第一", MangaTextBox(100f, 100f, 550f, 550f),
                typesetting = MangaAiTypesetting(listOf("第一"), 30f))
            val b = MangaTranslatedRegion("b", "second", "第二", MangaTextBox(450f, 400f, 900f, 900f),
                typesetting = MangaAiTypesetting(listOf("第二"), 30f))
            val result = MangaPageComposer.prepare(bitmap, MangaTranslationPlan(listOf(a, b)))
            assertFalse(MangaPageComposer.hasLayoutOverlap(result))
            assertEquals(listOf(a.textBox, b.textBox), result.regions.map { it.textBox })
            assertEquals(listOf(a.eraseBoxes, b.eraseBoxes), result.regions.map { it.eraseBoxes })
            val conflict = MangaPageComposer.prepare(bitmap, MangaTranslationPlan(listOf(a, b.copy(textBox = a.textBox, layoutBox = a.layoutBox, eraseBoxes = a.eraseBoxes))))
            assertTrue(MangaPageComposer.hasLayoutOverlap(conflict))
        } finally { bitmap.recycle() }
    }

    @Test fun `AI authored overlapping layouts shrink without moving source or changing lines`() {
        val bitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            val a = MangaTranslatedRegion("a", "first", "第一行", MangaTextBox(100f, 300f, 300f, 600f),
                layoutBox = MangaTextBox(80f, 200f, 600f, 800f), typesetting = MangaAiTypesetting(listOf("第一行"), 40f))
            val b = a.copy(id = "b", original = "second", translation = "第二行", textBox = MangaTextBox(700f, 300f, 900f, 600f),
                eraseBoxes = listOf(MangaTextBox(700f, 300f, 900f, 600f)), layoutBox = MangaTextBox(400f, 200f, 920f, 800f),
                typesetting = MangaAiTypesetting(listOf("第二行"), 40f))
            val prepared = MangaPageComposer.prepare(bitmap, MangaTranslationPlan(listOf(a, b)))
            assertFalse(MangaPageComposer.hasLayoutOverlap(prepared))
            assertEquals(a, prepared.regions[0].copy(layoutBox = a.layoutBox))
            assertEquals(b, prepared.regions[1].copy(layoutBox = b.layoutBox))
            MangaPageComposer.draw(bitmap, prepared, false, 1f)
            val reversed = MangaPageComposer.prepare(bitmap, MangaTranslationPlan(listOf(b, a)))
            assertEquals(prepared.regions.associate { it.id to it.layoutBox }, reversed.regions.associate { it.id to it.layoutBox })
        } finally { bitmap.recycle() }
    }

    @Test fun `small preview does not impose an eight pixel source font floor`() {
        val bitmap = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 20f }
        Canvas(bitmap).drawText("HELLO", 300f, 600f, paint)
        val preview = Bitmap.createScaledBitmap(bitmap, 300, 400, true)
        val region = MangaTranslatedRegion("r", "HELLO", "HELLO", MangaTextBox(245f, 360f, 315f, 380f))
        val measured = MangaPageComposer.anchor(preview, MangaTranslationPlan(listOf(region))).regions.single()
        val actualSize = measured.sourceFontSize!! * bitmap.height / 1000
        assertTrue("Original 20px text became $actualSize px after preview scaling", actualSize in 15f..26f)
        preview.recycle(); bitmap.recycle()
    }

    @Test fun `legacy line ceiling follows source scale without automatic enlargement`() {
        val original = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 40f }
        val originalBounds = Rect()
        paint.getTextBounds("HELLO", 0, 5, originalBounds)
        Canvas(original).drawText("HELLO", 60f, 150f, paint)
        val region = MangaTranslatedRegion("r", "HELLO", "国", MangaTextBox(110f, 230f, 430f, 310f), layoutBox = MangaTextBox(100f, 100f, 900f, 900f))
        val anchored = MangaPageComposer.anchor(original, MangaTranslationPlan(listOf(region)))
        for (scale in listOf(0.75f, 1f, 1.25f)) {
            val translated = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            MangaPageComposer.draw(translated, anchored, false, scale)
            val rows = (0 until 500).filter { y -> (0 until 500).any { x -> Color.red(translated.getPixel(x, y)) < 100 } }
            val actualHeight = rows.last() - rows.first() + 1
            assertTrue("Overlay target at $scale became $actualHeight", actualHeight >= originalBounds.height() * scale * .75f &&
                actualHeight <= originalBounds.height() * scale * 1.1f)
            translated.recycle()
        }
        original.recycle()
    }

    @Test fun `tight leading cannot merge two lines into one oversized source glyph`() {
        val bitmap = Bitmap.createBitmap(500, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 40f }
        Canvas(bitmap).apply { drawText("HELLO", 30f, 100f, paint); drawText("WORLD", 30f, 131f, paint) }
        val region = MangaTranslatedRegion("r", "HELLO\nWORLD", "Hi", MangaTextBox(40f, 200f, 500f, 460f))
        val size = MangaPageComposer.anchor(bitmap, MangaTranslationPlan(listOf(region))).regions.single().sourceFontSize!! * bitmap.height / 1000
        assertTrue("Two 40px lines were measured as $size", size in 30f..48f)
        bitmap.recycle()
    }

    @Test fun `previously overlapping layout boxes are separated without changing text or erasure`() {
        val bitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val first = MangaTranslatedRegion("a", "Hi", "A translated message", MangaTextBox(60f, 350f, 200f, 500f),
            layoutBox = MangaTextBox(30f, 150f, 600f, 850f), sourceFontSize = 100f)
        val second = MangaTranslatedRegion("b", "Bye", "Another translated message", MangaTextBox(660f, 350f, 800f, 500f),
            layoutBox = MangaTextBox(400f, 150f, 970f, 850f), sourceFontSize = 100f)
        val plan = MangaPageComposer.planLayout(bitmap, MangaTranslationPlan(listOf(first, second)))
        assertFalse(Rect.intersects(plan.regions[0].layoutBox.pixels(300, 200), plan.regions[1].layoutBox.pixels(300, 200)))
        assertEquals(first, plan.regions[0].copy(layoutBox = first.layoutBox))
        assertEquals(second, plan.regions[1].copy(layoutBox = second.layoutBox))
        val reversed = MangaPageComposer.planLayout(bitmap, MangaTranslationPlan(listOf(second, first)))
        assertEquals(plan.regions.associate { it.id to it.layoutBox }, reversed.regions.associate { it.id to it.layoutBox })
        bitmap.recycle()
    }

    @Test fun `horizontal dialogue in a tall capsule recovers source ink outside an offset AI box`() {
        for ((bg, ink) in listOf(Color.WHITE to Color.BLACK, Color.BLACK to Color.WHITE)) {
            val bitmap = Bitmap.createBitmap(300, 500, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.LTGRAY) }
            val canvas = Canvas(bitmap)
            val capsule = RectF(70f, 35f, 230f, 465f)
            canvas.drawRoundRect(capsule, 80f, 80f, Paint().apply { color = bg })
            canvas.drawRoundRect(capsule, 80f, 80f, Paint().apply { color = ink; style = Paint.Style.STROKE; strokeWidth = 3f })
            for (y in listOf(95, 155, 215, 275, 335, 395)) {
                canvas.drawRect(108f, y.toFloat(), 121f, y + 18f, Paint().apply { color = ink })
                canvas.drawRect(145f, y.toFloat(), 160f, y + 18f, Paint().apply { color = ink })
            }
            val offset = MangaTextBox(500f, 340f, 815f, 640f) // x150..245, y170..320; misses most source rows.
            val region = MangaTranslatedRegion("r", "Several horizontal lines of comic dialogue", "Translated dialogue that wraps naturally.", offset)
            val prepared = MangaPageComposer.prepare(bitmap, MangaTranslationPlan(listOf(region)))
            val actual = prepared.regions.single()
            assertEquals(offset, actual.recognitionBox)
            assertFalse(actual.vertical)
            val layout = actual.layoutBox.pixels(300, 500)
            assertEquals(150f, layout.exactCenterX(), 4f)
            assertEquals(250f, layout.exactCenterY(), 8f)
            MangaPageComposer.erase(bitmap, prepared)
            for (y in listOf(95, 155, 215, 275, 335, 395)) {
                assertEquals("Uncovered source at row $y", bg, bitmap.getPixel(112, y + 8))
                assertEquals("Uncovered source at row $y", bg, bitmap.getPixel(150, y + 8))
            }
            assertEquals(ink, bitmap.getPixel(70, 250))
            MangaPageComposer.draw(bitmap, prepared, false, 1f)
            val report = java.io.File("build/reports/manga-translation/capsule-${if (bg == Color.WHITE) "light" else "dark"}.png").apply { parentFile!!.mkdirs() }
            report.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun `capsule recovery leaves neighboring unassigned balloon untouched`() {
        val bitmap = Bitmap.createBitmap(400, 500, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.LTGRAY) }
        val canvas = Canvas(bitmap)
        for (left in listOf(30f, 220f)) {
            val area = RectF(left, 30f, left + 150f, 470f)
            canvas.drawRoundRect(area, 75f, 75f, Paint().apply { color = Color.WHITE })
            canvas.drawRoundRect(area, 75f, 75f, Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 3f })
            canvas.drawRect(left + 45f, 100f, left + 65f, 120f, Paint().apply { color = Color.BLACK })
            canvas.drawRect(left + 80f, 360f, left + 100f, 380f, Paint().apply { color = Color.BLACK })
        }
        val box = MangaTextBox(100f, 300f, 430f, 600f)
        val prepared = MangaPageComposer.prepare(bitmap, MangaTranslationPlan(listOf(MangaTranslatedRegion("r", "Comic source dialogue", "Hello", box))))
        MangaPageComposer.erase(bitmap, prepared)
        assertEquals(Color.WHITE, bitmap.getPixel(80, 110))
        assertEquals(Color.BLACK, bitmap.getPixel(270, 110))
        assertEquals(Color.BLACK, bitmap.getPixel(305, 370))
        bitmap.recycle()
    }

    @Test fun `balloon recovery does not merge two known dialogue regions`() {
        val bitmap = Bitmap.createBitmap(300, 500, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        Canvas(bitmap).drawRoundRect(RectF(60f, 30f, 240f, 470f), 90f, 90f,
            Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 3f })
        val first = MangaTranslatedRegion("a", "First line", "第一行", MangaTextBox(300f, 200f, 700f, 300f))
        val second = first.copy(id = "b", textBox = MangaTextBox(300f, 600f, 700f, 700f))
        val plan = MangaTranslationPlan(listOf(first, second))
        assertEquals(plan, recoverMangaBubbles(bitmap, plan))
        bitmap.recycle()
    }

    @Test fun `an open balloon cannot expand erasure onto a white comic page`() {
        val bitmap = Bitmap.createBitmap(300, 500, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        Canvas(bitmap).apply {
            drawRoundRect(RectF(60f, 30f, 240f, 470f), 90f, 90f,
                Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 3f })
            drawRect(55f, 240f, 70f, 260f, Paint().apply { color = Color.WHITE })
            drawRect(100f, 250f, 180f, 270f, Paint().apply { color = Color.BLACK })
        }
        val plan = MangaTranslationPlan(listOf(MangaTranslatedRegion("r", "Comic source text", "Hello", MangaTextBox(320f, 480f, 650f, 560f))))
        assertEquals(plan, recoverMangaBubbles(bitmap, plan))
        bitmap.recycle()
    }

    @Test fun `single CJK enclosing radical is lettering rather than a bubble outline`() {
        val bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        Canvas(bitmap).apply {
            drawRect(40f, 40f, 120f, 120f, Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 4f })
            drawRect(65f, 65f, 95f, 95f, Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 4f })
        }
        val plan = MangaTranslationPlan(listOf(MangaTranslatedRegion("r", "回", "Back", MangaTextBox(225f, 225f, 775f, 775f))))
        MangaPageComposer.erase(bitmap, refineMangaLettering(bitmap, plan))
        assertEquals(Color.WHITE, bitmap.getPixel(40, 80)); assertEquals(Color.WHITE, bitmap.getPixel(65, 80))
        bitmap.recycle()
    }

    @Test fun `detached single pixel punctuation stays in the refined erasure mask`() {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        Canvas(bitmap).drawRect(50f, 50f, 60f, 80f, Paint().apply { color = Color.BLACK })
        bitmap.setPixel(90, 78, Color.BLACK)
        val plan = MangaTranslationPlan(listOf(MangaTranslatedRegion("r", "Hi.", "你好。", MangaTextBox(200f, 200f, 600f, 450f))))
        MangaPageComposer.erase(bitmap, refineMangaLettering(bitmap, plan))
        assertEquals(Color.WHITE, bitmap.getPixel(90, 78))
        bitmap.recycle()
    }

    @Test fun `long translation uses safe bubble space before shrinking the original font`() {
        val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val text = "A comic sentence that needs more space."
        val region = MangaTranslatedRegion("r", "Hello", text, MangaTextBox(350f, 450f, 650f, 550f), sourceFontSize = 80f)
        val area = MangaPageComposer.planLayout(bitmap, MangaTranslationPlan(listOf(region))).regions.single().layoutBox.pixels(300, 300)
        assertTrue(area.width() > 130 && area.height() > 70)
        assertNotNull(MangaTextTypesetter.atSize(text, false, area.width(), area.height(), 24f))
        assertTrue(area.left >= 45 && area.top >= 75 && area.right <= 255 && area.bottom <= 225)
        val measured = MangaPageComposer.planLayout(bitmap, MangaTranslationPlan(listOf(region)))
        MangaPageComposer.draw(bitmap, measured, true, 1f)
        val report = java.io.File("build/reports/manga-translation/long-text-fit.png").apply { parentFile!!.mkdirs() }
        report.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun `coarse AI block refines into line masks and preserves bubble outline`() {
        val bitmap = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        Canvas(bitmap).apply {
            drawRect(25f, 25f, 215f, 215f, Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f })
            drawRect(60f, 65f, 75f, 85f, Paint().apply { color = Color.BLACK })
            drawRect(95f, 65f, 110f, 85f, Paint().apply { color = Color.BLACK })
            drawRect(70f, 130f, 85f, 150f, Paint().apply { color = Color.BLACK })
        }
        val region = MangaTranslatedRegion("r", "HELLO\nWORLD", "Hi", MangaTextBox(90f, 90f, 910f, 910f))
        val refined = refineMangaLettering(bitmap, MangaTranslationPlan(listOf(region))).validate()
        assertEquals(2, refined.regions.single().eraseBoxes.size)
        assertEquals(region.textBox, refined.regions.single().textBox)
        val measured = MangaPageComposer.prepare(bitmap, MangaTranslationPlan(listOf(region.copy(translation = "A longer sentence with more dialogue."))))
        val layout = measured.regions.single().layoutBox.pixels(240, 240)
        assertTrue(layout.toString(), layout.left >= 26 && layout.top >= 26 && layout.right <= 214 && layout.bottom <= 214)
        MangaPageComposer.erase(bitmap, refined)
        assertEquals(Color.WHITE, bitmap.getPixel(65, 75)); assertEquals(Color.WHITE, bitmap.getPixel(75, 140))
        assertEquals(Color.BLACK, bitmap.getPixel(25, 100))
        MangaPageComposer.draw(bitmap, measured, false, 1f)
        val report = java.io.File("build/reports/manga-translation/coarse-bubble-fit.png").apply { parentFile!!.mkdirs() }
        report.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun `inpainting never copies an adjacent border into removed lettering`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        Canvas(bitmap).apply {
            drawRect(40f, 40f, 46f, 60f, Paint().apply { color = Color.BLACK })
            drawRect(38f, 35f, 39f, 65f, Paint().apply { color = Color.BLACK })
        }
        MangaPageComposer.erase(bitmap, MangaTranslationPlan(listOf(MangaTranslatedRegion("r", "I", "Hi", MangaTextBox(400f, 400f, 460f, 600f)))))
        assertEquals(Color.WHITE, bitmap.getPixel(40, 50))
        assertEquals(Color.BLACK, bitmap.getPixel(38, 50))
        bitmap.recycle()
    }

    @Test fun `colored lettering with equal luminance is still removed`() {
        val bg = Color.rgb(255, 0, 0)
        val ink = Color.rgb(0, 130, 0)
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(bg) }
        Canvas(bitmap).drawRect(40f, 35f, 60f, 65f, Paint().apply { color = ink })
        val plan = MangaTranslationPlan(listOf(MangaTranslatedRegion("r", "H", "Hi", MangaTextBox(350f, 300f, 650f, 700f))))
        MangaPageComposer.erase(bitmap, plan)
        assertEquals(bg, bitmap.getPixel(50, 50))
        bitmap.recycle()
    }

    @Test fun `precise detector masks are preserved and geometry preparation is idempotent`() {
        val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val box = MangaTextBox(100f, 100f, 900f, 800f)
        val masks = listOf(MangaTextBox(200f, 200f, 700f, 300f), MangaTextBox(200f, 500f, 700f, 600f))
        val region = MangaTranslatedRegion("r", "Hello\nWorld", "Hi", box, eraseBoxes = masks)
        val prepared = MangaPageComposer.prepare(bitmap, MangaTranslationPlan(listOf(region)))
        assertEquals(masks, prepared.regions.single().eraseBoxes)
        assertEquals(prepared, MangaPageComposer.prepare(bitmap, prepared))
        bitmap.recycle()
    }

    @Test fun `textured anchor backgrounds keep the supplied erasure masks`() {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        for (y in 0 until 200) for (x in 0 until 200) bitmap.setPixel(x, y, if ((x + y) % 2 == 0) Color.WHITE else Color.GRAY)
        val box = MangaTextBox(200f, 200f, 800f, 800f)
        val plan = MangaTranslationPlan(listOf(MangaTranslatedRegion("r", "Hello", "Hi", box)))
        assertEquals(plan, refineMangaLettering(bitmap, plan))
        bitmap.recycle()
    }

    @Test fun `a fitting translation keeps the source area on light and dark backgrounds`() {
        for (bg in listOf(Color.WHITE, Color.BLACK)) {
            val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(bg) }
            val region = MangaTranslatedRegion("r", "Hello", "你好", MangaTextBox(300f, 300f, 600f, 600f), sourceFontSize = 100f)
            val expanded = MangaPageComposer.planLayout(bitmap, MangaTranslationPlan(listOf(region))).regions.single()
            assertEquals(Rect(60, 60, 120, 120), expanded.layoutBox.pixels(200, 200))
            assertEquals(region, expanded)
            bitmap.recycle()
        }
    }

    @Test fun `local layout stops before bubble borders and colored artwork`() {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        Canvas(bitmap).apply {
            drawRect(54f, 30f, 55f, 150f, Paint().apply { color = Color.BLACK })
            drawRect(124f, 30f, 125f, 150f, Paint().apply { color = Color.RED })
        }
        val region = MangaTranslatedRegion("r", "Hello", "This is a longer sentence.", MangaTextBox(300f, 300f, 600f, 600f), sourceFontSize = 100f)
        val area = MangaPageComposer.planLayout(bitmap, MangaTranslationPlan(listOf(region))).regions.single().layoutBox.pixels(200, 200)
        assertEquals(55, area.left); assertEquals(124, area.right)
        assertEquals(Color.RED, bitmap.getPixel(124, 60))
        bitmap.recycle()
    }

    @Test fun `local layout reserves other blocks and preserves explicit legacy layout`() {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val first = MangaTranslatedRegion("a", "one", "一段需要更多空间的译文。", MangaTextBox(300f, 300f, 600f, 600f), sourceFontSize = 100f)
        val second = first.copy(id = "b", translation = "二", textBox = MangaTextBox(650f, 300f, 800f, 600f), layoutBox = MangaTextBox(650f, 250f, 900f, 650f))
        val planned = MangaPageComposer.planLayout(bitmap, MangaTranslationPlan(listOf(first, second))).regions
        assertEquals(130, planned.first().layoutBox.pixels(200, 200).right)
        assertEquals(second, planned.last())
        assertFalse(Rect.intersects(planned.first().layoutBox.pixels(200, 200), planned.last().layoutBox.pixels(200, 200)))
        bitmap.recycle()
    }

    @Test fun `streaming PNG preserves size colors and row ordering across strips`() {
        val out = ByteArrayOutputStream()
        val red = Bitmap.createBitmap(19, 7, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val blue = Bitmap.createBitmap(19, 11, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        MangaPngWriter(out, 19, 13).use { it.append(red, 2, 5); it.append(blue, 1, 8) }
        val bytes = out.toByteArray()
        val result = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(19, result.width); assertEquals(13, result.height)
        assertEquals(Color.RED, result.getPixel(5, 4)); assertEquals(Color.BLUE, result.getPixel(5, 5))
        red.recycle(); blue.recycle(); result.recycle()
    }

    @Test fun `local erase removes lettering without touching artwork outside the tight erase box`() {
        val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        for (x in 35..38) for (y in 32..45) bitmap.setPixel(x, y, Color.BLACK)
        bitmap.setPixel(10, 10, Color.RED)
        val box = MangaTextBox(375f, 375f, 550f, 625f)
        val plan = MangaTranslationPlan(listOf(MangaTranslatedRegion("r0", "字", "text", box)))
        MangaPageComposer.erase(bitmap, plan)
        assertEquals(Color.WHITE, bitmap.getPixel(36, 38))
        assertEquals(Color.RED, bitmap.getPixel(10, 10))
        bitmap.recycle()
    }

    @Test fun `incomplete streaming image fails rather than publishing a truncated file`() {
        val out = ByteArrayOutputStream()
        val writer = MangaPngWriter(out, 10, 20)
        assertThrows(IllegalArgumentException::class.java) { writer.close() }
    }

    @Test fun `erase includes outlined ink just outside an underestimated anchor on light and dark backgrounds`() {
        for ((bg, ink) in listOf(Color.WHITE to Color.BLACK, Color.BLACK to Color.WHITE)) {
            val bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888).apply { eraseColor(bg) }
            Canvas(bitmap).drawRect(40f, 40f, 62f, 62f, Paint().apply { color = ink })
            bitmap.setPixel(10, 10, Color.RED)
            val box = MangaTextBox(350f, 350f, 500f, 500f) // 42..60, ink extends 2px outside.
            MangaPageComposer.erase(bitmap, MangaTranslationPlan(listOf(MangaTranslatedRegion("r", "字", "Hi", box))))
            for (y in 40..61) for (x in 40..61) assertEquals("Residual ink at $x,$y", bg, bitmap.getPixel(x, y))
            assertEquals(Color.RED, bitmap.getPixel(10, 10))
            bitmap.recycle()
        }
    }

    @Test fun `patch clears original lettering even when layout anchor is elsewhere`() {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        Canvas(bitmap).drawRect(40f, 40f, 62f, 62f, Paint().apply { color = Color.BLACK })
        val region = MangaTranslatedRegion("r", "字", "Hi", MangaTextBox(210f, 210f, 300f, 300f),
            layoutBox = MangaTextBox(500f, 500f, 950f, 950f), sourceFontSize = 100f)
        MangaPageComposer.draw(bitmap, MangaTranslationPlan(listOf(region)), true, 1f)
        for (y in 40..61) for (x in 40..61) assertEquals(Color.WHITE, bitmap.getPixel(x, y))
        bitmap.recycle()
    }

    @Test fun `added erasure margin preserves disconnected artwork beside lettering`() {
        val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        Canvas(bitmap).apply {
            drawRect(101f, 110f, 105f, 140f, Paint().apply { color = Color.BLACK })
            drawRect(94f, 115f, 96f, 135f, Paint().apply { color = Color.RED })
        }
        val region = MangaTranslatedRegion("r", "text", "Hi", MangaTextBox(100f / 300 * 1000, 100f / 300 * 1000, 200f / 300 * 1000, 150f / 300 * 1000))
        MangaPageComposer.erase(bitmap, MangaTranslationPlan(listOf(region)))
        assertEquals(Color.WHITE, bitmap.getPixel(103, 120))
        assertEquals(Color.RED, bitmap.getPixel(94, 120))
        bitmap.recycle()
    }

    @Test fun `source font follows actual lines rather than block height and exceeds the old 64px cap`() {
        val bitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 92f }
        Canvas(bitmap).apply { drawText("HELLO", 30f, 120f, paint); drawText("WORLD", 30f, 240f, paint) }
        val plan = MangaTranslationPlan(listOf(MangaTranslatedRegion("r", "HELLO\nWORLD", "Hi", MangaTextBox(40f, 70f, 750f, 500f))))
        val anchored = MangaPageComposer.anchor(bitmap, plan)
        val size = anchored.regions.single().sourceFontSize!! * bitmap.height / 1000
        val ink = Rect(); paint.getTextBounds("HELLO", 0, 5, ink)
        assertEquals("Baseline is source ink thickness, not the source font em", ink.height().toFloat(), size, 3f)
        assertTrue(size > 64f)
        assertEquals(anchored, MangaPageComposer.anchor(bitmap, anchored))
        bitmap.recycle()
    }

    @Test fun `source-relative scaling changes typography without a fixed 64px ceiling`() {
        fun inkHeight(scale: Float): Int {
            val bitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            val region = MangaTranslatedRegion("r", "HELLO", "Hi", MangaTextBox(100f, 100f, 900f, 900f), sourceFontSize = 200f)
            MangaPageComposer.draw(bitmap, MangaTranslationPlan(listOf(region)), true, scale)
            val rows = (0 until bitmap.height).filter { y -> (0 until bitmap.width).any { x -> Color.red(bitmap.getPixel(x, y)) < 100 } }
            bitmap.recycle()
            return rows.last() - rows.first() + 1
        }
        val normal = inkHeight(1f)
        val larger = inkHeight(1.5f)
        assertTrue(normal > 64)
        assertTrue(larger > normal * 1.35f && larger < normal * 1.65f)
    }

    @Test fun `vertical source baseline uses single glyph width independently of target script`() {
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 92f }
        Canvas(bitmap).apply { drawText("H", 60f, 120f, paint); drawText("H", 60f, 240f, paint) }
        val region = MangaTranslatedRegion("r", "HH", "HH", MangaTextBox(100f, 75f, 350f, 650f), vertical = true)
        val plan = MangaPageComposer.anchor(bitmap, MangaTranslationPlan(listOf(region)))
        val size = plan.regions.single().sourceFontSize!! * bitmap.height / 1000
        val originalInk = Rect()
        paint.getTextBounds("H", 0, 1, originalInk)
        assertEquals(originalInk.width().toFloat(), size, 3f)
        val otherScript = MangaPageComposer.anchor(bitmap, MangaTranslationPlan(listOf(region.copy(translation = "国")))).regions.single()
        assertEquals(plan.regions.single().sourceFontSize, otherScript.sourceFontSize)
        bitmap.recycle()
    }

    @Test fun `unmeasurable source cannot use a huge block as a huge font`() {
        val bitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val region = MangaTranslatedRegion("r", "Hi", "你好", MangaTextBox(50f, 50f, 950f, 950f))
        val size = MangaPageComposer.anchor(bitmap, MangaTranslationPlan(listOf(region))).regions.single().sourceFontSize!! * 500 / 1000
        assertTrue("Uncertain source estimated as $size px", size < 25f)
        bitmap.recycle()
    }

    @Test fun `vertical layout collisions stay separated after original size projection`() {
        val bitmap = Bitmap.createBitmap(307, 211, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val first = MangaTranslatedRegion("a", "First", "First translation", MangaTextBox(400f, 100f, 600f, 250f),
            layoutBox = MangaTextBox(100f, 50f, 900f, 600f), sourceFontSize = 100f)
        val secondSource = MangaTextBox(400f, 750f, 600f, 900f)
        val second = first.copy(id = "b", original = "Second", translation = "Second translation", textBox = secondSource,
            eraseBoxes = listOf(secondSource), layoutBox = MangaTextBox(100f, 400f, 900f, 950f))
        val planned = MangaPageComposer.planLayout(bitmap, MangaTranslationPlan(listOf(first, second)))
        assertFalse(MangaPageComposer.hasLayoutOverlap(planned))
        assertFalse(Rect.intersects(planned.regions[0].layoutBox.pixels(3000, 2000), planned.regions[1].layoutBox.pixels(3000, 2000)))
        bitmap.recycle()
    }
}
