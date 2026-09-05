package io.legado.app.feature.reader.core.selection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSelectionLifecyclePolicyTest {

    @Test
    fun `programmatic page change clears selection tied to old page`() {
        assertTrue(
            ReaderSelectionLifecyclePolicy.shouldClearForPageChange(
                ReaderPageChangeOrigin.PROGRAMMATIC,
            )
        )
    }

    @Test
    fun `selection drag scroll retains cross page selection`() {
        assertFalse(
            ReaderSelectionLifecyclePolicy.shouldClearForPageChange(
                ReaderPageChangeOrigin.SELECTION_DRAG_SCROLL,
            )
        )
    }

    @Test
    fun `auto page pauses only while a selection exists`() {
        assertTrue(ReaderSelectionLifecyclePolicy.shouldPauseAutoPage(hasSelection = true))
        assertFalse(ReaderSelectionLifecyclePolicy.shouldPauseAutoPage(hasSelection = false))
    }

    @Test
    fun `visible selection menu is reanchored only for real geometry change`() {
        assertTrue(
            ReaderSelectionLifecyclePolicy.shouldReanchorMenuAfterLayoutChange(
                hasSelection = true,
                menuVisible = true,
                previousLayoutRevision = 10L,
                currentLayoutRevision = 11L,
            )
        )
        assertFalse(
            ReaderSelectionLifecyclePolicy.shouldReanchorMenuAfterLayoutChange(
                hasSelection = true,
                menuVisible = true,
                previousLayoutRevision = 10L,
                currentLayoutRevision = 10L,
            )
        )
        assertFalse(
            ReaderSelectionLifecyclePolicy.shouldReanchorMenuAfterLayoutChange(
                hasSelection = true,
                menuVisible = false,
                previousLayoutRevision = 10L,
                currentLayoutRevision = 11L,
            )
        )
    }
}
