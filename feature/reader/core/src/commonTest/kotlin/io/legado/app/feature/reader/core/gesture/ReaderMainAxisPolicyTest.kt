package io.legado.app.feature.reader.core.gesture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderMainAxisPolicyTest {

    @Test
    fun `horizontal paging claims only a horizontal dominant drag`() {
        assertTrue(ReaderMainAxisPolicy.isHorizontalDominant(-20f, 12f))
        assertFalse(ReaderMainAxisPolicy.isHorizontalDominant(12f, -20f))
    }

    @Test
    fun `scroll claims only a vertical dominant drag`() {
        assertTrue(ReaderMainAxisPolicy.isVerticalDominant(12f, -20f))
        assertFalse(ReaderMainAxisPolicy.isVerticalDominant(-20f, 12f))
    }

    @Test
    fun `equal axes remain unclaimed`() {
        assertFalse(ReaderMainAxisPolicy.isHorizontalDominant(16f, -16f))
        assertFalse(ReaderMainAxisPolicy.isVerticalDominant(16f, -16f))
    }
}
