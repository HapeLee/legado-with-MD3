package io.legado.app.ui.book.info

import org.junit.Assert.assertEquals
import org.junit.Test

class BookInfoCoverSizingTest {

    @Test
    fun standardPortraitRatioPreservesExistingDetailHeight() {
        assertEquals(336f, bookInfoCoverHeightDp(5f / 7f), 0.01f)
    }

    @Test
    fun wideCoversKeepOriginalRatio() {
        assertEquals(240f / 3f, bookInfoCoverHeightDp(3f), 0.01f)
    }

    @Test
    fun tallCoversUseWidthScaledMaximumHeight() {
        assertEquals(140f * 240f / 72f, bookInfoCoverHeightDp(0.4f), 0.01f)
    }

    @Test
    fun invalidRatiosUseTheStandardPortraitRatio() {
        assertEquals(336f, bookInfoCoverHeightDp(Float.NaN), 0.01f)
    }
}
