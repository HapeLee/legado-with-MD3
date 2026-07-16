package io.legado.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

class TTSReadAloudProgressTest {

    @Test
    fun nextParagraphAdvancesFromCurrentRangePosition() {
        assertEquals(
            201,
            nextParagraphPosition(
                currentPosition = 180,
                paragraphLength = 100,
                paragraphStartPosition = 80,
            )
        )
    }

    @Test
    fun nextParagraphPreservesInitialSelectionOffset() {
        assertEquals(
            201,
            nextParagraphPosition(
                currentPosition = 120,
                paragraphLength = 100,
                paragraphStartPosition = 20,
            )
        )
    }
}
