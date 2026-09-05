package io.legado.app.feature.reader.core.transition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun `pausing a discrete timer preserves only its unelapsed duration`() {
        assertEquals(7_000L, ReaderAutoPagePolicy.remainingAfterPause(10_000L, 3_000L))
        assertEquals(1L, ReaderAutoPagePolicy.remainingAfterPause(10_000L, 12_000L))
        assertEquals(10_000L, ReaderAutoPagePolicy.remainingAfterPause(10_000L, -1L))
    }

    @Test
    fun `menu and text selection both preserve the discrete timer remainder`() {
        assertTrue(
            ReaderAutoPagePolicy.shouldPreserveRemainingTime(
                menuPaused = true,
                selectionPaused = false,
            )
        )
        assertTrue(
            ReaderAutoPagePolicy.shouldPreserveRemainingTime(
                menuPaused = false,
                selectionPaused = true,
            )
        )
        assertFalse(
            ReaderAutoPagePolicy.shouldPreserveRemainingTime(
                menuPaused = false,
                selectionPaused = false,
            )
        )
    }

    @Test
    fun `reveal indicator occupies the pixel immediately above progress`() {
        assertEquals(0f, ReaderAutoPagePolicy.indicatorTopPx(0f, 800f), 0f)
        assertEquals(199f, ReaderAutoPagePolicy.indicatorTopPx(200f, 800f), 0f)
        assertEquals(799f, ReaderAutoPagePolicy.indicatorTopPx(900f, 800f), 0f)
    }
}
