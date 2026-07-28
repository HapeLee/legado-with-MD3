package io.legado.app.ui.book.read.page

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomTipPageRemainingTest {

    @Test
    fun `completed chapter shows chapter complete on last page`() {
        val display = formatCustomTipPageRemaining(
            pageSize = 10,
            pageIndex = 9,
            isChapterCompleted = true,
            chapterCompleteText = "本章完",
        )

        assertEquals("本章完", display)
    }

    @Test
    fun `completed chapter keeps remaining page count before last page`() {
        val display = formatCustomTipPageRemaining(
            pageSize = 10,
            pageIndex = 5,
            isChapterCompleted = true,
            chapterCompleteText = "本章完",
        )

        assertEquals("4", display)
    }

    @Test
    fun `incomplete chapter omits approximation marker without reporting chapter complete`() {
        val display = formatCustomTipPageRemaining(
            pageSize = 10,
            pageIndex = 9,
            isChapterCompleted = false,
            chapterCompleteText = "本章完",
        )

        assertEquals("0", display)
    }

    @Test
    fun `incomplete chapter without pages keeps unknown marker`() {
        val display = formatCustomTipPageRemaining(
            pageSize = 0,
            pageIndex = 0,
            isChapterCompleted = false,
            chapterCompleteText = "本章完",
        )

        assertEquals("-", display)
    }
}
