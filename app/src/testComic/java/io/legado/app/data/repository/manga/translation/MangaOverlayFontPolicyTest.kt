package io.legado.app.data.repository.manga.translation

import org.junit.Assert.*
import org.junit.Test

class MangaOverlayFontPolicyTest {
    @Test fun `fixed area keeps the source ceiling even when short translations have spare space`() {
        assertEquals(MangaGlyphSizeRange(7, 20), translationGlyphSizeRange(20f, 100f, constrainToBounds = true))
        assertEquals(MangaGlyphSizeRange(4, 10), translationGlyphSizeRange(10f, 100f, constrainToBounds = true))
        assertEquals(MangaGlyphSizeRange(7, 20), translationGlyphSizeRange(20f, 72f, constrainToBounds = true))
    }
    @Test fun `vertical source length independently caps annotation inflated column width`() {
        assertEquals(20f, translationSourceGlyphHeight(listOf(48, 50), 60, 10, listOf(105, 105)), .01f)
        assertEquals(21f, translationSourceGlyphHeight(listOf(20, 22), 60), .01f)
    }
    @Test fun `invalid sizes are rejected instead of overflowing search`() {
        assertNull(translationGlyphSizeRange(Float.NaN, 100f, constrainToBounds = true))
        assertNull(translationGlyphSizeRange(20f, 0f, constrainToBounds = true))
    }
    @Test fun `nearby intersecting bubbles are not treated as duplicate detections`() {
        val detections = MangaBubbleDetector.postprocess(200, 100, longArrayOf(0, 0, 0, 2),
            arrayOf(floatArrayOf(0f, 0f, 110f, 100f), floatArrayOf(80f, 0f, 190f, 100f),
                floatArrayOf(1f, 1f, 111f, 100f), floatArrayOf(20f, 20f, 40f, 40f)), floatArrayOf(.9f, .8f, .7f, .6f))
        assertEquals(3, detections.size)
        assertEquals(2, detections.count { it.kind == 0 })
    }
}
