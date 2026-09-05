package io.legado.app.feature.reader.core.style

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

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
}
