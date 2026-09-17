package io.legado.app.feature.reader.core.transition

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderAutoPagePolicyTest {
    @Test
    fun `e ink uses discrete page changes while normal displays reveal progressively`() {
        assertEquals(ReaderAutoPageVisualMode.DISCRETE, ReaderAutoPagePolicy.visualMode(true))
        assertEquals(ReaderAutoPageVisualMode.PROGRESSIVE, ReaderAutoPagePolicy.visualMode(false))
    }

    @Test
    fun `page duration follows the supported legacy speed range`() {
        assertEquals(1_000L, ReaderAutoPagePolicy.pageDurationMillis(0))
        assertEquals(30_000L, ReaderAutoPagePolicy.pageDurationMillis(30))
        assertEquals(120_000L, ReaderAutoPagePolicy.pageDurationMillis(121))
    }

    @Test
    fun `reveal indicator occupies the pixel immediately above progress`() {
        assertEquals(0f, ReaderAutoPagePolicy.indicatorTopPx(0f, 800f), 0f)
        assertEquals(199f, ReaderAutoPagePolicy.indicatorTopPx(200f, 800f), 0f)
        assertEquals(799f, ReaderAutoPagePolicy.indicatorTopPx(900f, 800f), 0f)
    }
}
