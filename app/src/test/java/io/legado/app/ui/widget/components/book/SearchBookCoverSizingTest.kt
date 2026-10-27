package io.legado.app.ui.widget.components.book

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchBookCoverSizingTest {

    @Test
    fun standardPortraitRatioPreservesExistingHeight() {
        assertEquals(168f, searchBookCoverHeightDp(5f / 7f), 0.01f)
    }

    @Test
    fun wideCoversKeepOriginalRatio() {
        assertEquals(120f, searchBookCoverHeightDp(1f), 0.01f)
    }

    @Test
    fun tallCoversClampToMaximumHeight() {
        assertEquals(233f, searchBookCoverHeightDp(0.4f), 0f)
    }

    @Test
    fun invalidRatiosUseTheStandardPortraitRatio() {
        assertEquals(168f, searchBookCoverHeightDp(Float.NaN), 0.01f)
    }
}
