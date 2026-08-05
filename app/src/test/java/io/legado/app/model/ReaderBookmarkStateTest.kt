package io.legado.app.model

import io.legado.app.data.entities.Bookmark
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderBookmarkStateTest {

    @After
    fun tearDown() {
        ReaderBookmarkState.clear()
    }

    private fun bookmark(chapterIndex: Int, chapterPos: Int) = Bookmark(
        time = chapterIndex * 1000L + chapterPos,
        bookName = "book",
        bookAuthor = "author",
        chapterIndex = chapterIndex,
        chapterPos = chapterPos,
    )

    @Test
    fun `page start is inclusive and page end is exclusive`() {
        ReaderBookmarkState.update(listOf(bookmark(chapterIndex = 3, chapterPos = 200)))

        // 页首命中
        assertTrue(ReaderBookmarkState.hasBookmarkInRange(3, startPos = 200, endPos = 400))
        // 上一页的尾界不应吞掉下一页的页首书签
        assertFalse(ReaderBookmarkState.hasBookmarkInRange(3, startPos = 0, endPos = 200))
        // 下一页不应命中
        assertFalse(ReaderBookmarkState.hasBookmarkInRange(3, startPos = 400, endPos = 600))
    }

    @Test
    fun `bookmarks are isolated per chapter`() {
        ReaderBookmarkState.update(listOf(bookmark(chapterIndex = 3, chapterPos = 200)))

        assertFalse(ReaderBookmarkState.hasBookmarkInRange(4, startPos = 200, endPos = 400))
        assertFalse(ReaderBookmarkState.hasBookmarkInRange(2, startPos = 200, endPos = 400))
    }

    @Test
    fun `update replaces the previous snapshot`() {
        ReaderBookmarkState.update(listOf(bookmark(chapterIndex = 3, chapterPos = 200)))
        ReaderBookmarkState.update(listOf(bookmark(chapterIndex = 5, chapterPos = 10)))

        assertFalse(ReaderBookmarkState.hasBookmarkInRange(3, startPos = 200, endPos = 400))
        assertTrue(ReaderBookmarkState.hasBookmarkInRange(5, startPos = 0, endPos = 100))
    }

    @Test
    fun `clear drops every bookmark`() {
        ReaderBookmarkState.update(listOf(bookmark(chapterIndex = 3, chapterPos = 200)))
        ReaderBookmarkState.clear()

        assertFalse(ReaderBookmarkState.hasBookmarkInRange(3, startPos = 200, endPos = 400))
    }
}
