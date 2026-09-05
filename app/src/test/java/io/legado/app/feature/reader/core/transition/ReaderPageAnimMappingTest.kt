package io.legado.app.feature.reader.core.transition

import io.legado.app.constant.PageAnim
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPageAnimMappingTest {
    @Test
    fun `Android page animation constants retain their shared transition mapping`() {
        assertEquals(ReaderTransitionMode.COVER, ReaderTransitionMode.fromPageAnim(PageAnim.coverPageAnim))
        assertEquals(ReaderTransitionMode.SLIDE, ReaderTransitionMode.fromPageAnim(PageAnim.slidePageAnim))
        assertEquals(ReaderTransitionMode.SIMULATION, ReaderTransitionMode.fromPageAnim(PageAnim.simulationPageAnim))
        assertEquals(ReaderTransitionMode.SCROLL, ReaderTransitionMode.fromPageAnim(PageAnim.scrollPageAnim))
        assertEquals(ReaderTransitionMode.FADE, ReaderTransitionMode.fromPageAnim(PageAnim.fadePageAnim))
        assertEquals(ReaderTransitionMode.NONE, ReaderTransitionMode.fromPageAnim(PageAnim.noAnim))
    }
}
