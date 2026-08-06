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

    private fun bookmark(
        bookName: String = "book",
        bookAuthor: String = "author",
        chapterIndex: Int,
        chapterPos: Int,
    ) = Bookmark(
        time = chapterIndex * 1000L + chapterPos,
        bookName = bookName,
        bookAuthor = bookAuthor,
        chapterIndex = chapterIndex,
        chapterPos = chapterPos,
    )

    @Test
    fun `page start is inclusive and page end is exclusive`() {
        ReaderBookmarkState.update("book", "author", listOf(bookmark(chapterIndex = 3, chapterPos = 200)))

        // 页首命中
        assertTrue(ReaderBookmarkState.hasBookmarkInRange("book", "author", 3, startPos = 200, endPos = 400))
        // 上一页的尾界不应吞掉下一页的页首书签
        assertFalse(ReaderBookmarkState.hasBookmarkInRange("book", "author", 3, startPos = 0, endPos = 200))
        // 下一页不应命中
        assertFalse(ReaderBookmarkState.hasBookmarkInRange("book", "author", 3, startPos = 400, endPos = 600))
    }

    @Test
    fun `bookmarks are isolated per chapter`() {
        ReaderBookmarkState.update("book", "author", listOf(bookmark(chapterIndex = 3, chapterPos = 200)))

        assertFalse(ReaderBookmarkState.hasBookmarkInRange("book", "author", 4, startPos = 200, endPos = 400))
        assertFalse(ReaderBookmarkState.hasBookmarkInRange("book", "author", 2, startPos = 200, endPos = 400))
    }

    @Test
    fun `update replaces the previous snapshot`() {
        ReaderBookmarkState.update("book", "author", listOf(bookmark(chapterIndex = 3, chapterPos = 200)))
        ReaderBookmarkState.update("book", "author", listOf(bookmark(chapterIndex = 5, chapterPos = 10)))

        assertFalse(ReaderBookmarkState.hasBookmarkInRange("book", "author", 3, startPos = 200, endPos = 400))
        assertTrue(ReaderBookmarkState.hasBookmarkInRange("book", "author", 5, startPos = 0, endPos = 100))
    }

    @Test
    fun `book key mismatch does not serve bookmarks`() {
        ReaderBookmarkState.update("book", "author", listOf(bookmark(chapterIndex = 3, chapterPos = 200)))

        // 另一本书在旧快照到达前查询：键不一致 → 视为无书签，不串台
        assertFalse(ReaderBookmarkState.hasBookmarkInRange("otherBook", "author", 3, startPos = 200, endPos = 400))
        // 同书同作者 → 命中
        assertTrue(ReaderBookmarkState.hasBookmarkInRange("book", "author", 3, startPos = 200, endPos = 400))
    }

    @Test
    fun `clear drops every bookmark`() {
        ReaderBookmarkState.update("book", "author", listOf(bookmark(chapterIndex = 3, chapterPos = 200)))
        ReaderBookmarkState.clear()

        assertFalse(ReaderBookmarkState.hasBookmarkInRange("book", "author", 3, startPos = 200, endPos = 400))
    }
}
