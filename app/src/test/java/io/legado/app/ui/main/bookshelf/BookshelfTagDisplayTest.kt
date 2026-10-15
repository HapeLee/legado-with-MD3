package io.legado.app.ui.main.bookshelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfTagDisplayTest {
    @Test
    fun `all source combinations retain the original order`() {
        val expected = listOf(
            emptyList(),
            listOf("待看"),
            listOf("仙侠"),
            listOf("待看", "仙侠"),
            listOf("120万字"),
            listOf("待看", "120万字"),
            listOf("仙侠", "120万字"),
            listOf("待看", "仙侠", "120万字"),
        )
        expected.forEachIndexed { mask, tags ->
            assertEquals(
                "source mask $mask",
                tags,
                book.getDisplayTags(
                    showCustomTags = mask and 1 != 0,
                    showSourceTags = mask and 2 != 0,
                    showWordCount = mask and 4 != 0,
                ),
            )
        }
        assertEquals(expected.last(), book.toUiItem().displayTags)
    }

    @Test
    fun `overlapping source labels survive when custom tags are hidden`() {
        val overlapping = book.copy(customTag = "仙侠,待看\n120万字")
        assertEquals(listOf("仙侠", "待看", "120万字"), overlapping.getDisplayTags())
        assertEquals(
            listOf("仙侠", "120万字"),
            overlapping.getDisplayTags(showCustomTags = false),
        )
    }

    @Test
    fun `hiding every tag does not remove search metadata`() {
        val item = book.toUiItem()
        assertTrue(item.book.getDisplayTags(false, false, false).isEmpty())
        assertTrue(item.matches("待看"))
        assertTrue(item.matches("仙侠"))
        assertTrue(item.matches("120万字"))
    }

    @Test
    fun `missing metadata creates no empty labels`() {
        assertTrue(book.copy(customTag = null, kind = " ,\n", wordCount = " ")
            .getDisplayTags().isEmpty())
    }

    private val book = BookShelfItem(
        bookUrl = "book-url",
        name = "书名",
        author = "作者",
        origin = "source",
        originName = "书源",
        coverUrl = null,
        customCoverUrl = null,
        durChapterTitle = null,
        durChapterTime = 0L,
        durChapterPos = 0,
        latestChapterTitle = null,
        latestChapterTime = 0L,
        lastCheckCount = 0,
        totalChapterNum = 0,
        durChapterIndex = 0,
        type = 0,
        group = 0L,
        order = 0,
        customTag = "待看",
        kind = "仙侠",
        wordCount = "120万字",
    )
}
