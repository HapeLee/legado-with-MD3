package io.legado.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class CheckBookContentQualityUseCaseTest {

    @Test
    fun parseChapterIndicesSupportsRangesAndIgnoresOutOfBounds() {
        assertEquals(
            listOf(1, 3, 4, 5),
            CheckBookContentQualityUseCase.parseChapterIndices("1, 3-5, 99", 5),
        )
    }

    @Test
    fun parseChapterIndicesSupportsChineseRangeSeparators() {
        assertEquals(
            listOf(2, 3, 4),
            CheckBookContentQualityUseCase.parseChapterIndices("第4至2章", 4),
        )
    }
}
