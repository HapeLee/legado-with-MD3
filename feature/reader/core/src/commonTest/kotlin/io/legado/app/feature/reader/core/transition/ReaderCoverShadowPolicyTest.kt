package io.legado.app.feature.reader.core.transition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderCoverShadowPolicyTest {
    @Test
    fun `cover shadow stays broad and translucent`() {
        val alpha = (ReaderCoverShadowPolicy.colorArgb ushr 24) and 0xFF

        assertTrue(alpha in 32..64)
        assertTrue(ReaderCoverShadowPolicy.widthDp >= 32f)
    }

    @Test
    fun `next shadow starts at moving current page right edge`() {
        assertEquals(
            720f,
            ReaderCoverShadowPolicy.edgePx(
                direction = ReaderTurnDirection.NEXT,
                displayOffsetPx = -360f,
                pageWidthPx = 1080f,
            ),
            0f,
        )
    }

    @Test
    fun `previous shadow starts at incoming previous page right edge`() {
        assertEquals(
            360f,
            ReaderCoverShadowPolicy.edgePx(
                direction = ReaderTurnDirection.PREVIOUS,
                displayOffsetPx = 360f,
                pageWidthPx = 1080f,
            ),
            0f,
        )
    }
}
