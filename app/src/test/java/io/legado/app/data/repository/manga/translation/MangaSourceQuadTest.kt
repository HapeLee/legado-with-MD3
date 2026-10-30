package io.legado.app.data.repository.manga.translation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Rect
import io.legado.app.domain.model.manga.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class MangaSourceQuadTest {
    @Test fun `saved quad survives serialization and continuous ROI mapping on non square pages`() {
        val box = MangaTextBox(190f, 300f, 400f, 500f)
        val quad = MangaTextQuad(listOf(MangaTextPoint(200f, 300f), MangaTextPoint(400f, 320f),
            MangaTextPoint(390f, 500f), MangaTextPoint(190f, 480f)))
        val region = MangaTranslatedRegion("r0", "source", "译文", box, sourceLines = listOf(MangaSourceLine("source", box, quad)))
        val plan = MangaTranslationPlan(listOf(region))
        val json = MangaTranslationPlan.json
        val saved = json.decodeFromString<MangaTranslationPlan>(json.encodeToString(MangaTranslationPlan.serializer(), plan)).validate()
        assertEquals(quad, saved.regions.single().sourceLines.single().quad)
        val band = MangaTranslationBand(100, 600, 100, 600)
        val roi = Rect(100, 200, 500, 500)
        val local = saved.regions.single().inOverlay(1000, band, roi).sourceLines.single().quad!!
        val pixels = local.pixelCorners(roi.width(), roi.height())
        val original = quad.pixelCorners(1000, 500)
        for (i in 0..3) {
            assertEquals(original[i * 2], pixels[i * 2] + roi.left, .01f)
            assertEquals(original[i * 2 + 1] + band.top, pixels[i * 2 + 1] + roi.top, .01f)
        }
        assertEquals(quad, region.sourceLines.single().quad)
    }

    @Test fun `rotated source uses perpendicular thickness instead of inflated axis aligned height`() {
        val bitmap = Bitmap.createBitmap(1000, 500, Bitmap.Config.ARGB_8888)
        try {
            // Pixel edges (120,160), (280,280), (268,296), (108,176): length 200, thickness 20.
            val box = MangaTextBox(108f, 320f, 280f, 592f)
            val quad = MangaTextQuad(listOf(MangaTextPoint(120f, 320f), MangaTextPoint(280f, 560f),
                MangaTextPoint(268f, 592f), MangaTextPoint(108f, 352f)))
            quad.validate()
            val region = MangaTranslatedRegion("r0", "SOURCE", "译文", box, sourceLines = listOf(MangaSourceLine("SOURCE", box, quad)))
            val anchored = MangaPageComposer.anchor(bitmap, MangaTranslationPlan(listOf(region)))
            assertEquals(40f, anchored.regions.single().sourceFontSize!!, .01f)
        } finally { bitmap.recycle() }
    }

    @Test fun `invalid crossed source quad is rejected and old source lines remain compatible`() {
        val json = MangaTranslationPlan.json
        val old = json.decodeFromString<MangaSourceLine>("""{"text":"a","bounds":{"left":10,"top":20,"right":30,"bottom":40}}""")
        assertNull(old.quad)
        val crossed = MangaTextQuad(listOf(MangaTextPoint(0f, 0f), MangaTextPoint(100f, 100f),
            MangaTextPoint(100f, 0f), MangaTextPoint(0f, 100f)))
        assertTrue(runCatching { crossed.validate() }.isFailure)
    }
}
