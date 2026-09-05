package io.legado.app.feature.reader.core.transition

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderViewportLayerPolicyTest {
    @Test
    fun `continuous scroll owns background and page chrome at the viewport`() {
        assertTrue(ReaderViewportLayerPolicy.usesFixedBackground(ReaderTransitionMode.SCROLL))
        assertTrue(ReaderViewportLayerPolicy.usesFixedPageChrome(ReaderTransitionMode.SCROLL))
    }

    @Test
    fun `animated pages keep their own background and chrome`() {
        ReaderTransitionMode.entries.filterNot { it == ReaderTransitionMode.SCROLL }.forEach { mode ->
            assertFalse(ReaderViewportLayerPolicy.usesFixedBackground(mode))
            assertFalse(ReaderViewportLayerPolicy.usesFixedPageChrome(mode))
        }
    }
}
