package io.legado.app.data.repository.manga.translation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
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
class MangaAiAnchorsTest {
    @Test fun `anchored complete translation can supply explicit rows when optional style was omitted`() {
        val raw = """{"regions":[{"id":"a0","original":"hello","translation":"你好\n世界","sourceDirection":"horizontal"}]}"""
        val r = parseMangaAiTypesetting(resolveMangaAiAnchors(raw, listOf(anchor))).regions.single()
        assertEquals(box, r.textBox)
        assertEquals(listOf("你好", "世界"), r.typesetting!!.lines)
        assertEquals(12f, r.typesetting.fontSize, .01f)
        assertFalse(r.vertical)
    }

    @Test fun `a14 empty plan can recover its explicit translation using client geometry`() {
        val candidate = anchor.copy(id = "a14")
        val raw = """{"regions":[{"id":"a14","original":"アプリ","translation":"应用","sourceDirection":"VERTICAL_COLUMNS","typesetting":{"lines":[]}}]}"""
        val region = parseMangaAiTypesetting(resolveMangaAiAnchors(raw, listOf(candidate))).regions.single()
        assertEquals(listOf("应用"), region.typesetting!!.lines)
        assertEquals(box, region.layoutBox)
        assertTrue(region.typesetting.vertical)
        assertEquals(15f, region.typesetting.fontSize, .01f)
    }

    private val box = MangaTextBox(100f, 100f, 200f, 400f)
    private val anchor = MangaAiAnchor("a0", box, horizontalFontSize = 12f, verticalFontSize = 15f)
    private fun response(id: String = "a0", extra: String = "") =
        """{"regions":[{"id":"$id","original":"hello","typesetting":{"lines":["你好"],"fontScale":1.1}$extra}]}"""

    @Test fun `numbered anchors use measured source and preserve model lines without remote coordinates`() {
        val plan = parseMangaAiTypesetting(resolveMangaAiAnchors(response(), listOf(anchor)))
        val region = plan.regions.single()
        assertEquals(box, region.textBox); assertEquals(box, region.layoutBox)
        assertEquals(listOf(box), region.eraseBoxes)
        assertEquals(listOf("你好"), region.typesetting!!.lines)
        assertEquals(13.2f, region.typesetting.fontSize, .01f)
        val vertical = parseMangaAiTypesetting(resolveMangaAiAnchors(response(extra = ",\"vertical\":true"), listOf(anchor)))
        assertEquals(16.5f, vertical.regions.single().typesetting!!.fontSize, .01f)
    }

    @Test fun `model geometry cannot displace an explicitly referenced candidate`() {
        val bad = response(extra = ",\"textBox\":{\"left\":800,\"top\":800,\"right\":900,\"bottom\":900},\"aiGeometry\":{\"safeBox\":{\"left\":0,\"top\":0,\"right\":1,\"bottom\":1}}")
        val region = parseMangaAiTypesetting(resolveMangaAiAnchors(bad, listOf(anchor))).regions.single()
        assertEquals(box, region.textBox); assertNull(region.aiGeometry)
    }

    @Test fun `every anchor must be accounted for without unknown duplicates or invalid scales`() {
        for (bad in listOf(response("r0"), """{"regions":[],"noText":true}""",
            response().replace("1.1", "10"), response().replace("}]}", "},{\"id\":\"a0\",\"original\":\"x\"}]}"),
            """{"regions":[],"noText":true,"ignoredAnchorIds":["wrong"]}""")) {
            assertThrows(IllegalArgumentException::class.java) { resolveMangaAiAnchors(bad, listOf(anchor)) }
        }
        val rejected = """{"regions":[],"noText":true,"ignoredAnchorIds":["a0"]}"""
        assertTrue(parseMangaAiTypesetting(resolveMangaAiAnchors(rejected, listOf(anchor))).noText)
    }

    @Test fun `fixed layout only requires its known subset and never permits ignoring a fixed ID`() {
        val others = listOf(anchor, anchor.copy(id = "a1"))
        parseMangaAiTypesetting(resolveMangaAiAnchors(response(), others, setOf("a0")))
        assertThrows(IllegalArgumentException::class.java) {
            resolveMangaAiAnchors("""{"regions":[],"ignoredAnchorIds":["a0"]}""", others, setOf("a0"))
        }
    }

    @Test fun `undetected free lettering uses explicit pixel units on a non square page`() {
        val free = """{"regions":[{"id":"free-dot","original":"...","textBox":{"left":200,"top":400,"right":220,"bottom":460},"layoutBox":{"left":200,"top":400,"right":220,"bottom":460},"typesetting":{"lines":["……"],"fontSize":10}}],"ignoredAnchorIds":["a0"]}"""
        val plan = parseMangaAiTypesetting(resolveMangaAiAnchors(free, listOf(anchor), frame = MangaAiFrame("page", 400, 800)))
        assertEquals(MangaTextBox(500f, 500f, 550f, 575f), plan.regions.single().textBox)
        assertEquals(12.5f, plan.regions.single().typesetting!!.fontSize, .01f)
        assertThrows(IllegalArgumentException::class.java) { resolveMangaAiAnchors(free, listOf(anchor)) }
    }

    @Test fun `context margin anchors belong only to their core band and captions can use outside text detections`() {
        val band = MangaTranslationBand(100, 500, 200, 400)
        assertFalse(band.owns(MangaTextBox(10f, 0f, 50f, 100f)))
        assertTrue(band.owns(MangaTextBox(10f, 400f, 50f, 600f)))
        assertFalse(band.owns(MangaTextBox(10f, 850f, 50f, 950f)))
        val bitmap = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            val captions = mangaAiAnchors(bitmap, listOf(MangaBubbleDetection(MangaShapeRect(10, 10, 200, 35), .8f, 2)))
            assertEquals(1, captions.size)
        } finally { bitmap.recycle() }
    }

    @Test fun `explicit source flow distinguishes horizontal rows from vertical columns and defaults target to source`() {
        val horizontal = response(extra = ",\"sourceDirection\":\"HORIZONTAL_ROWS\",\"vertical\":true")
        val h = parseMangaAiTypesetting(resolveMangaAiAnchors(horizontal, listOf(anchor))).regions.single()
        assertFalse(h.vertical); assertFalse(h.typesetting!!.vertical)
        assertEquals(13.2f, h.typesetting.fontSize, .01f)
        val vertical = response(extra = ",\"sourceDirection\":\"VERTICAL_COLUMNS\"")
        val v = parseMangaAiTypesetting(resolveMangaAiAnchors(vertical, listOf(anchor))).regions.single()
        assertTrue(v.vertical); assertTrue(v.typesetting!!.vertical)
        assertEquals(16.5f, v.typesetting.fontSize, .01f)
        val explicitTarget = vertical.replace("\"fontScale\":1.1", "\"fontScale\":1.1,\"vertical\":false")
        assertFalse(parseMangaAiTypesetting(resolveMangaAiAnchors(explicitTarget, listOf(anchor))).regions.single().typesetting!!.vertical)
    }

    @Test fun `fixed recognition direction controls font reference even when layout model contradicts it`() {
        val corrected = resolveMangaAiAnchors(response(extra = ",\"sourceDirection\":\"VERTICAL_COLUMNS\""), listOf(anchor),
            sourceDirections = mapOf("a0" to false))
        val region = parseMangaAiTypesetting(corrected).regions.single()
        assertFalse(region.vertical)
        assertEquals(13.2f, region.typesetting!!.fontSize, .01f)
    }

    @Test fun `ink direction distinguishes a tall wrapped paragraph from actual vertical columns`() {
        val bitmap = Bitmap.createBitmap(100, 160, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 18f }
        val rect = Rect(10, 10, 90, 150)
        try {
            bitmap.eraseColor(Color.WHITE)
            listOf("漫画日本語", "漫画", "日本語の本", "本", "漫画日本").forEachIndexed { row, text ->
                canvas.drawText(text, 14f + (row % 2) * 5, 30f + row * 25, paint)
            }
            assertEquals(false, mangaAiSourceVerticalHint(bitmap, rect))
            bitmap.eraseColor(Color.WHITE)
            repeat(2) { column -> repeat(5) { row ->
                canvas.drawText("漫", 28f + column * 30, 30f + row * 25, paint)
            } }
            assertEquals(true, mangaAiSourceVerticalHint(bitmap, rect))
        } finally { bitmap.recycle() }
    }

    @Test fun `blank dense artwork and a single square glyph do not invent a direction`() {
        val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f }
        val rect = Rect(5, 5, 75, 75)
        try {
            bitmap.eraseColor(Color.WHITE)
            assertNull(mangaAiSourceVerticalHint(bitmap, rect))
            canvas.drawRect(25f, 25f, 55f, 55f, paint)
            assertNull(mangaAiSourceVerticalHint(bitmap, rect))
            paint.style = Paint.Style.FILL
            canvas.drawRect(7f, 7f, 73f, 73f, paint)
            assertNull(mangaAiSourceVerticalHint(bitmap, rect))
        } finally { bitmap.recycle() }
    }

    @Test fun `tight Latin wrapping is never classified vertical and a pixel hint never overrides a model plan`() {
        val bitmap = Bitmap.createBitmap(50, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 15f }
            val canvas = Canvas(bitmap)
            canvas.drawText("I'm", 10f, 35f, paint); canvas.drawText("sorry", 10f, 53f, paint)
            assertNotEquals(true, mangaAiSourceVerticalHint(bitmap, Rect(8, 20, 45, 60)))
            val hints = mangaAiAnchorHints(listOf(anchor.copy(sourceVerticalHint = true)), 400, 800)
            assertTrue(hints.contains("VERTICAL_COLUMNS"))
            assertFalse(parseMangaAiTypesetting(resolveMangaAiAnchors(
                response(extra = ",\"sourceDirection\":\"HORIZONTAL_ROWS\""),
                listOf(anchor.copy(sourceVerticalHint = true)))).regions.single().vertical)
        } finally { bitmap.recycle() }
    }

    @Test fun `separate text anchors in touching balloons remain separate and sheets stay bounded`() {
        val bitmap = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            val detections = listOf(
                MangaBubbleDetection(MangaShapeRect(100, 100, 200, 200), .9f, 0),
                MangaBubbleDetection(MangaShapeRect(180, 140, 290, 270), .9f, 0),
                MangaBubbleDetection(MangaShapeRect(120, 120, 160, 150), .9f, 1),
                MangaBubbleDetection(MangaShapeRect(200, 170, 260, 230), .9f, 1))
            val anchors = mangaAiAnchors(bitmap, detections)
            assertEquals(2, anchors.size)
            assertTrue(anchors[0].textBox.right < anchors[1].textBox.left)
            val same = mangaAiAnchors(bitmap, detections.filter { it.kind != 0 } +
                MangaBubbleDetection(MangaShapeRect(100, 100, 290, 270), .9f, 0))
            assertEquals(1, same.size)
            val sheet = mangaAiAnchorSheet(bitmap, anchors)
            try { assertEquals(384, sheet.width); assertEquals(160, sheet.height) } finally { sheet.recycle() }
            val large = mangaAiAnchorSheet(bitmap, List(64) { anchor.copy(id = "a$it") })
            try { assertTrue(large.width.toLong() * large.height < 2 * 1024 * 1024) } finally { large.recycle() }
        } finally { bitmap.recycle() }
    }
}
