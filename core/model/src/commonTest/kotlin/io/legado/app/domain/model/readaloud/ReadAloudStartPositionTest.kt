package io.legado.app.domain.model.readaloud

import kotlin.test.assertEquals
import kotlin.test.Test

class ReadAloudStartPositionTest {
    private val starts = listOf(0, 120, 260)

    @Test
    fun absoluteChapterPositionWinsOverLegacyPageArguments() {
        val result = resolveReadAloudStartPosition(
            requestedPageIndex = 0,
            requestedOffsetInPage = 999,
            requestedChapterPosition = 175,
            pageIndexAt = { position -> starts.indexOfLast { it <= position } },
            pageStart = starts::get,
        )
        assertEquals(ReadAloudStartPosition(pageIndex = 1, offsetInPage = 55), result)
    }

    @Test
    fun legacyPageRelativeRequestRemainsCompatible() {
        val result = resolveReadAloudStartPosition(
            requestedPageIndex = 2,
            requestedOffsetInPage = 17,
            requestedChapterPosition = null,
            pageIndexAt = { error("must not resolve an absent chapter position") },
            pageStart = { error("must not resolve an absent chapter position") },
        )
        assertEquals(ReadAloudStartPosition(pageIndex = 2, offsetInPage = 17), result)
    }
}
