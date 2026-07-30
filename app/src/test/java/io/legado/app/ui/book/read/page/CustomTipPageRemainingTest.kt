package io.legado.app.ui.book.read.page

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomTipPageRemainingTest {

    @Test
    fun `最后一页也只出数字不出本章完`() {
        val display = formatCustomTipPageRemaining(
            pageSize = 10,
            pageIndex = 9,
            isChapterCompleted = true,
        )

        assertEquals("0", display)
    }

    @Test
    fun `排完的章节给出剩余页数`() {
        val display = formatCustomTipPageRemaining(
            pageSize = 10,
            pageIndex = 5,
            isChapterCompleted = true,
        )

        assertEquals("4", display)
    }

    @Test
    fun `未排完的章节最后一页同样出数字`() {
        val display = formatCustomTipPageRemaining(
            pageSize = 10,
            pageIndex = 9,
            isChapterCompleted = false,
        )

        assertEquals("0", display)
    }

    @Test
    fun `未排完且页数未知时出未知标记`() {
        val display = formatCustomTipPageRemaining(
            pageSize = 0,
            pageIndex = 0,
            isChapterCompleted = false,
        )

        assertEquals("-", display)
    }
}
