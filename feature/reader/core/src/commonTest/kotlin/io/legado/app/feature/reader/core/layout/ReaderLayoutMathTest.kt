package io.legado.app.feature.reader.core.layout

import kotlin.test.assertEquals
import kotlin.test.Test

class ReaderLayoutMathTest {
    @Test fun surrogatePairFormsOneGlyphCluster() {
        val result = clusterGlyphs("😀", floatArrayOf(20f, 0f))
        assertEquals(listOf("😀"), result.text)
        assertEquals(listOf(20f), result.widthsPx)
    }

    @Test fun calculatesSingleAndDoublePageBounds() {
        assertEquals(
            ReaderContentBounds(1032, 1856, 1056, 1888),
            calculateContentBounds(1080, 1920, 24, 32, 24, 32),
        )
        assertEquals(
            ReaderContentBounds(492, 1856, 516, 1888),
            calculateContentBounds(1080, 1920, 24, 32, 24, 32, 2),
        )
    }

    @Test fun joinsCombiningMarksButKeepsJoinersAsSeparateClusters() {
        assertEquals(
            GlyphClusters(listOf("A\u0301", "B"), listOf(10f, 12f)),
            clusterGlyphs("A\u0301B", floatArrayOf(10f, 0f, 12f)),
        )
        assertEquals(
            GlyphClusters(listOf("A", "\u200D", "B"), listOf(10f, 0f, 12f)),
            clusterGlyphs("A\u200DB", floatArrayOf(10f, 0f, 12f)),
        )
    }

    @Test fun readsWidthsFromTheRequestedMeasurementOffset() {
        assertEquals(
            GlyphClusters(listOf("甲", "乙"), listOf(7f, 9f)),
            clusterGlyphs("甲乙", floatArrayOf(99f, 7f, 9f), start = 1),
        )
    }
}
