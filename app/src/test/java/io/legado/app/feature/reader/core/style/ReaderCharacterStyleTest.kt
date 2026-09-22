package io.legado.app.feature.reader.core.style

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderCharacterStyleTest {
    @Test
    fun higherPriorityMarkingOverridesRegexStyle() {
        val ranges = listOf(
            ReaderStyleRange(0, 5, ReaderStyleTarget.BODY, ReaderCharacterStyle(colorArgb = 1), priority = 2),
            ReaderStyleRange(1, 3, ReaderStyleTarget.BODY, ReaderCharacterStyle(colorArgb = 2, markingId = "m"), priority = 10_000),
        )
        assertEquals(1, ReaderCharacterStyleResolver.resolve(ranges, 0, false)?.colorArgb)
        assertEquals("m", ReaderCharacterStyleResolver.resolve(ranges, 2, false)?.markingId)
        assertNull(ReaderCharacterStyleResolver.resolve(ranges, 2, true))
    }

    @Test
    fun equalPriorityUsesLastMatchingRangeWithoutCrossingTarget() {
        val ranges = listOf(
            ReaderStyleRange(
                0,
                4,
                ReaderStyleTarget.ALL,
                ReaderCharacterStyle(colorArgb = 1),
                priority = 3
            ),
            ReaderStyleRange(
                1,
                3,
                ReaderStyleTarget.BODY,
                ReaderCharacterStyle(colorArgb = 2),
                priority = 3
            ),
            ReaderStyleRange(
                1,
                3,
                ReaderStyleTarget.TITLE,
                ReaderCharacterStyle(colorArgb = 3),
                priority = 2
            ),
        )
        assertEquals(2, ReaderCharacterStyleResolver.resolve(ranges, 2, false)?.colorArgb)
        assertEquals(1, ReaderCharacterStyleResolver.resolve(ranges, 2, true)?.colorArgb)
        assertNull(ReaderCharacterStyleResolver.resolve(ranges, 5, false))
    }
}
