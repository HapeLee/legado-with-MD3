package io.legado.app.feature.reader.core.layout

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderLayoutMathTest {
    @Test fun surrogatePairFormsOneGlyphCluster() {
        val result = clusterGlyphs("😀", floatArrayOf(20f, 0f))
        assertEquals(listOf("😀"), result.text)
        assertArrayEquals(floatArrayOf(20f), result.widthsPx, 0f)
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
}
