package io.legado.app.feature.reader.core.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReaderTextShaperContractTest {
    @Test
    fun fontBoundsExposeCanvasLineHeightAndBaselineOffset() {
        val bounds = ReaderFontBounds(topPx = -12f, bottomPx = 5f, descentPx = 4f)

        assertEquals(17f, bounds.heightPx, 0f)
        assertEquals(13f, bounds.baselineOffsetPx, 0f)
    }

    @Test
    fun lineMetricsDefaultToTheSamePositiveAscentDescentConvention() {
        val metrics = ReaderFontLineMetrics(heightPx = 20f, baselineOffsetPx = 15f)

        assertEquals(15f, metrics.ascentPx, 0f)
        assertEquals(5f, metrics.descentPx, 0f)
    }

    @Test
    fun functionalShaperHasNoPlatformMetricsByDefault() {
        val shaper = ReaderTextShaper { text -> GlyphClusters(listOf(text), listOf(text.length.toFloat())) }

        assertEquals(GlyphClusters(listOf("正文"), listOf(2f)), shaper.shape("正文"))
        assertNull(shaper.fontBounds)
        assertNull(shaper.fontLineMetrics)
    }
}
