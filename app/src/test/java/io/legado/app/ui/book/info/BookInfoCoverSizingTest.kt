package io.legado.app.ui.book.info

import org.junit.Assert.assertEquals
import org.junit.Test

class BookInfoCoverSizingTest {

    @Test
    fun standardPortraitRatioPreservesExistingDetailHeight() {
        assertEquals(156.8f, bookInfoCoverHeightDp(5f / 7f), 0.01f)
    }

    @Test
    fun wideCoversUseWidthScaledMinimumHeight() {
        assertEquals(80f * 112f / 72f, bookInfoCoverHeightDp(1f), 0.01f)
    }

    @Test
    fun tallCoversUseWidthScaledMaximumHeight() {
        assertEquals(140f * 112f / 72f, bookInfoCoverHeightDp(0.4f), 0.01f)
    }

    @Test
    fun invalidRatiosUseTheStandardPortraitRatio() {
        assertEquals(156.8f, bookInfoCoverHeightDp(Float.NaN), 0.01f)
    }
}
