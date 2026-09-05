package io.legado.app.feature.reader.core.model

import kotlin.test.assertEquals
import kotlin.test.Test

class ReaderTipRowLayoutTest {

    @Test
    fun `row extent includes full font padding instead of nominal text size`() {
        assertEquals(
            34f,
            ReaderTipRowLayout.extent(
                paddingTopPx = 4f,
                fontTopPx = -20f,
                fontBottomPx = 6f,
                paddingBottomPx = 3f,
                dividerExtentPx = 1f,
            ),
            0f,
        )
    }

    @Test
    fun `header and footer baselines keep font top and bottom inside padding`() {
        assertEquals(24f, ReaderTipRowLayout.headerBaseline(4f, -20f), 0f)
        assertEquals(91f, ReaderTipRowLayout.footerBaseline(100f, 3f, 6f), 0f)
    }
}
