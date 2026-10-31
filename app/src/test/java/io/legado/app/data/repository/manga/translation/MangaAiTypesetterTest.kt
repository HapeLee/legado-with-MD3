package io.legado.app.data.repository.manga.translation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
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
class MangaAiTypesetterTest {
    @Test fun `short CJK address and ellipsis occupy one vertical column in a narrow capsule`() {
        assertShortCapsuleDrawing(true, true)
    }

    @Test fun `vertical source in a narrow capsule corrects horizontal CJK target direction`() {
        assertShortCapsuleDrawing(true, false)
    }

    @Test fun `tall capsule alone does not convert a horizontal source to vertical`() {
        assertShortCapsuleDrawing(false, false)
    }

    private fun assertShortCapsuleDrawing(sourceVertical: Boolean, targetVertical: Boolean) {
        val image = bitmap()
        val lines = if (targetVertical) listOf("大", "叔", "...") else listOf("大叔...")
        val source = MangaTextBox(400f, 100f, 500f, 600f)
        val paragraph = region.copy(original = "おじさん……", translation = lines.joinToString("\n"),
            textBox = source, layoutBox = source, vertical = sourceVertical, sourceColumnCount = 3,
            typesetting = MangaAiTypesetting(lines, 32f, vertical = targetVertical))
        try {
            MangaAiTypesetter.draw(image, paragraph, 1f, false, emptyList())
            val bounds = ink(image)
            if (sourceVertical) assertTrue("Short translation still draws as a horizontal strip: $bounds", bounds.height() > bounds.width() * 2)
            else assertTrue("A horizontal source must retain horizontal target lettering: $bounds", bounds.width() > bounds.height() * 1.5f)
            assertTrue(source.layoutPixels(image.width, image.height).contains(bounds))
            assertEquals(lines, paragraph.typesetting!!.lines)
            assertEquals(targetVertical, paragraph.typesetting.vertical)
        } finally { image.recycle() }
    }

    @Test fun `oversized authored font cannot conceal flattening that appears only after fitting`() {
        assertTallVerticalDrawing(7, 200f, MangaTextBox(400f, 100f, 600f, 900f))
    }

    @Test fun `genuine wide source columns are preserved when their count and width already fit`() {
        val image = bitmap()
        val lines = listOf("总是", "支持", "着我", "就像", "王子", "一样", "天地")
        val paragraph = region.copy(original = lines.joinToString("\n"), translation = lines.joinToString("\n"),
            textBox = MangaTextBox(50f, 400f, 950f, 700f), layoutBox = MangaTextBox(50f, 400f, 950f, 700f),
            vertical = true, sourceColumnCount = 7, typesetting = MangaAiTypesetting(lines, 40f, vertical = true))
        try {
            MangaAiTypesetter.draw(image, paragraph, 1f, false, emptyList())
            assertEquals(7, measureMangaSourceColumns(image, paragraph))
        } finally { image.recycle() }
    }

    @Test fun `known source count cannot disable repair of a flat vertical strip`() {
        assertTallVerticalDrawing(7, 40f, MangaTextBox(400f, 100f, 600f, 900f))
    }

    @Test fun `source count repair can shrink below the former sixty five percent floor`() {
        assertTallVerticalDrawing(2, 200f, MangaTextBox(400f, 100f, 600f, 900f))
    }

    @Test fun `vertical layout repairs short columns even when the box is not very tall`() {
        assertTallVerticalDrawing(null, 40f, MangaTextBox(300f, 100f, 700f, 580f))
    }

    private fun assertTallVerticalDrawing(sourceColumns: Int?, font: Float, box: MangaTextBox) {
        val image = bitmap()
        val lines = listOf("总是", "支持", "着我", "就像", "王子", "一样", "……")
        val paragraph = region.copy(original = "原文", translation = lines.joinToString("\n"),
            vertical = true, sourceColumnCount = sourceColumns, layoutBox = box,
            typesetting = MangaAiTypesetting(lines, font, vertical = true))
        try {
            MangaAiTypesetter.draw(image, paragraph, 1f, false, emptyList())
            val bounds = ink(image)
            assertTrue("Vertical lettering remains a flat strip: $bounds, sourceColumns=$sourceColumns", bounds.height() > bounds.width() * 2)
            assertTrue(box.layoutPixels(image.width, image.height).contains(bounds))
            assertEquals(lines, paragraph.typesetting!!.lines)
        } finally { image.recycle() }
    }

    @Test fun `AI three short columns draw as two columns when the source has two`() {
        val image = Bitmap.createBitmap(200, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            val region = MangaTranslatedRegion("r", "原文第一列\n原文第二列", "这是保持两列完整译文", MangaTextBox(100f, 100f, 900f, 900f),
                vertical = true, typesetting = MangaAiTypesetting(listOf("这是保持", "两列完整", "译文"), 60f, vertical = true))
            MangaAiTypesetter.draw(image, region, 1f, false, emptyList())
            assertEquals(2, measureMangaSourceColumns(image, region))
        } finally { image.recycle() }
    }

    @Test fun `source columns are counted independently from target rows`() {
        val box = MangaTextBox(100f, 100f, 400f, 800f)
        val region = MangaTranslatedRegion("r", "原文第一列\n原文第二列", "完整的译文", box, vertical = true,
            typesetting = MangaAiTypesetting(listOf("完整", "的", "译文"), 20f, vertical = true))
        assertEquals(2, mangaSourceColumnCount(region))
        assertEquals(2, mangaSourceColumnCount(region.copy(sourceColumnCount = 2, original = "合并的原文")))
        assertNull(mangaSourceColumnCount(region.copy(vertical = false)))
    }

    @Test fun `short AI columns in a tall vertical paragraph use height rather than shrinking into a flat strip`() {
        val image = bitmap()
        val lines = listOf("总是", "支持", "着我", "就像", "王子", "一样", "……")
        val paragraph = region.copy(translation = lines.joinToString("\n"),
            layoutBox = MangaTextBox(400f, 100f, 600f, 900f),
            typesetting = MangaAiTypesetting(lines, 40f, vertical = true))
        try {
            MangaPageComposer.draw(image, MangaTranslationPlan(listOf(paragraph)), false, 1f)
            val bounds = ink(image)
            assertTrue("Vertical lettering must use the tall area: $bounds", bounds.height() > bounds.width() * 2)
            assertEquals(lines, paragraph.typesetting!!.lines)
        } finally { image.recycle() }
    }

    @Test fun `vertical ellipsis uses vertical ink while horizontal text retains horizontal ink`() {
        for (vertical in listOf(true, false)) {
            val image = bitmap()
            try {
                val punctuation = region.copy(translation = "…", typesetting = MangaAiTypesetting(listOf("…"), 80f, vertical = vertical))
                MangaPageComposer.draw(image, MangaTranslationPlan(listOf(punctuation)), false, 1f)
                val bounds = ink(image)
                if (vertical) assertTrue("Ellipsis must rotate in vertical text: $bounds", bounds.height() > bounds.width() * 2)
                else assertTrue(bounds.width() > bounds.height() * 2)
            } finally { image.recycle() }
        }
    }

    private val region = MangaTranslatedRegion("r", "SOURCE", "HELLO\nWORLD", MangaTextBox(50f, 50f, 200f, 200f),
        layoutBox = MangaTextBox(500f, 400f, 950f, 900f), sourceFontSize = 300f,
        typesetting = MangaAiTypesetting(listOf("HELLO", "WORLD"), 40f))
    private fun bitmap() = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
    private fun ink(bitmap: Bitmap): Rect {
        val r = Rect(bitmap.width, bitmap.height, 0, 0)
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) if (mangaInkContrast(bitmap.getPixel(x, y), Color.WHITE, 48)) {
            r.left = minOf(r.left, x); r.right = maxOf(r.right, x + 1); r.top = minOf(r.top, y); r.bottom = maxOf(r.bottom, y + 1)
        }
        return r
    }

    @Test fun `AI authored boxes and font bypass local bubble enlargement and source recentering`() {
        val image = bitmap()
        try {
            val plan = MangaTranslationPlan(listOf(region))
            val prepared = MangaPageComposer.prepare(image, plan, listOf(MangaBubbleDetection(MangaShapeRect(0, 0, 100, 100), 1f, 0)))
            assertEquals(region, prepared.regions.single())
            MangaPageComposer.draw(image, prepared, false, 1f)
            val bounds = ink(image)
            val area = region.layoutBox.pixels(500, 500)
            assertTrue(area.contains(bounds))
            assertEquals(area.exactCenterX(), bounds.exactCenterX(), 2f)
            assertEquals(area.exactCenterY(), bounds.exactCenterY(), 2f)
            val occupied = (0 until 500).map { y -> (0 until 500).any { x -> mangaInkContrast(image.getPixel(x, y), Color.WHITE, 48) } }
            assertEquals(2, occupied.indices.count { occupied[it] && (it == 0 || !occupied[it - 1]) })
        } finally { image.recycle() }
    }

    @Test fun `large AI fonts fit the complete fixed rows without rewrapping or escaping their area`() {
        val image = bitmap()
        try {
            val small = region.copy(layoutBox = MangaTextBox(500f, 500f, 800f, 700f),
                typesetting = region.typesetting!!.copy(fontSize = 300f))
            MangaPageComposer.draw(image, MangaTranslationPlan(listOf(small)), false, 1.5f)
            assertTrue(small.layoutBox.pixels(500, 500).contains(ink(image)))
            assertTrue(ink(image).width() > 30)
        } finally { image.recycle() }
    }

    @Test fun `authored font size and alignment survive original frame to overlay projection exactly`() {
        val whole = bitmap(); val combined = bitmap()
        try {
            val band = MangaTranslationBand(0, 500, 0, 500)
            val bounds = region.overlayRect(500, 500, band)
            val roi = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            try {
                MangaPageComposer.draw(whole, MangaTranslationPlan(listOf(region)), false, 1f)
                val local = region.inOverlay(500, band, bounds)
                assertEquals(20f, local.typesetting!!.fontSize * roi.height / 1000, .01f)
                MangaPageComposer.draw(roi, MangaTranslationPlan(listOf(local)), false, 1f)
                Canvas(combined).drawBitmap(roi, bounds.left.toFloat(), bounds.top.toFloat(), null)
                val a = IntArray(500 * 500).also { whole.getPixels(it, 0, 500, 0, 0, 500, 500) }
                val b = IntArray(a.size).also { combined.getPixels(it, 0, 500, 0, 0, 500, 500) }
                assertArrayEquals(a, b)
            } finally { roi.recycle() }
        } finally { whole.recycle(); combined.recycle() }
    }

    @Test fun `vertical target direction and top alignment come from the AI plan`() {
        val image = bitmap()
        try {
            val vertical = region.copy(translation = "天地\n玄黄", typesetting = MangaAiTypesetting(listOf("天地", "玄黄"), 50f,
                vertical = true, align = MangaTextAlign.RIGHT, verticalAlign = MangaTextVerticalAlign.TOP))
            MangaPageComposer.draw(image, MangaTranslationPlan(listOf(vertical)), false, 1f)
            val bounds = ink(image); val area = vertical.layoutBox.pixels(500, 500)
            assertTrue(area.contains(bounds))
            assertTrue(bounds.right > area.right - 10 && bounds.top < area.top + 12)
        } finally { image.recycle() }
    }

    @Test fun `unreadable AI foreground colors cannot make a completed translation invisible`() {
        val image = bitmap()
        try {
            val invisible = region.copy(typesetting = region.typesetting!!.copy(color = "#ffffff"))
            MangaPageComposer.draw(image, MangaTranslationPlan(listOf(invisible)), false, 1f)
            assertTrue(ink(image).width() > 30)
        } finally { image.recycle() }
    }

    @Test fun `AI cleanup samples colored lettering background instead of the search ROI exterior`() {
        for (patch in listOf(false, true)) {
            val image = Bitmap.createBitmap(180, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            try {
                val blue = Color.rgb(80, 140, 190)
                Canvas(image).apply {
                    drawRect(10f, 25f, 170f, 55f, Paint().apply { color = blue })
                    drawRect(50f, 32f, 57f, 46f, Paint().apply { color = Color.WHITE })
                    drawRect(62f, 32f, 69f, 46f, Paint().apply { color = Color.WHITE })
                }
                val source = MangaTextBox(45f / 180 * 1000, 28f / 100 * 1000, 75f / 180 * 1000, 52f / 100 * 1000)
                val letter = MangaTranslatedRegion("a0", "HI", "你好", source,
                    typesetting = MangaAiTypesetting(listOf("你好"), 140f))
                MangaPageComposer.erase(image, MangaTranslationPlan(listOf(letter)), patch)
                assertEquals(blue, image.getPixel(53, 38))
                assertEquals(blue, image.getPixel(65, 38))
                assertEquals(blue, image.getPixel(40, 26))
                assertEquals(Color.WHITE, image.getPixel(53, 22))
            } finally { image.recycle() }
        }
    }

    @Test fun `paragraph height cannot authorize erasing a long border segment`() {
        for (patch in listOf(false, true)) {
            val image = Bitmap.createBitmap(160, 180, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            try {
                val paint = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f }
                Canvas(image).apply {
                    drawOval(20f, 10f, 140f, 170f, paint)
                    drawLine(30f, 60f, 30f, 115f, paint)
                    paint.style = Paint.Style.FILL
                    drawRect(45f, 45f, 52f, 57f, paint)
                    drawRect(45f, 85f, 52f, 97f, paint)
                }
                val before = image.getPixel(24, 50)
                val source = MangaTextBox(23f / 160 * 1000, 40f / 180 * 1000, 115f / 160 * 1000, 130f / 180 * 1000)
                val letter = MangaTranslatedRegion("a0", "A MULTILINE PARAGRAPH", "译文", source,
                    typesetting = MangaAiTypesetting(listOf("译文"), 12f / 180 * 1000))
                MangaPageComposer.erase(image, MangaTranslationPlan(listOf(letter)), patch)
                assertEquals(Color.WHITE, image.getPixel(48, 50))
                assertEquals(Color.WHITE, image.getPixel(48, 90))
                assertEquals(before, image.getPixel(24, 50))
                assertEquals(Color.BLACK, image.getPixel(30, 90))
                assertEquals(Color.BLACK, image.getPixel(20, 90))
            } finally { image.recycle() }
        }
    }
}
