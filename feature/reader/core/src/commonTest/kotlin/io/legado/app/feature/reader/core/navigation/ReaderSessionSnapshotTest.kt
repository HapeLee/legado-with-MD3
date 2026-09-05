package io.legado.app.feature.reader.core.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderSessionSnapshotTest {
    @Test
    fun defaultsToAnEmptyLocalReaderSession() {
        val snapshot = ReaderSessionSnapshot()

        assertNull(snapshot.bookUrl)
        assertNull(snapshot.bookName)
        assertEquals(0, snapshot.chapterIndex)
        assertEquals(0, snapshot.chapterPos)
        assertEquals(0, snapshot.chapterCount)
        assertEquals(0, snapshot.simulatedChapterCount)
        assertTrue(snapshot.isLocalBook)
    }

    @Test
    fun retainsThePublishedReaderPositionWithoutBookObjects() {
        val snapshot = ReaderSessionSnapshot(
            bookUrl = "https://example.com/book",
            bookName = "Book",
            chapterIndex = 3,
            chapterPos = 42,
            chapterCount = 10,
            simulatedChapterCount = 12,
            isLocalBook = false,
        )

        assertEquals("https://example.com/book", snapshot.bookUrl)
        assertEquals("Book", snapshot.bookName)
        assertEquals(3, snapshot.chapterIndex)
        assertEquals(42, snapshot.chapterPos)
        assertEquals(10, snapshot.chapterCount)
        assertEquals(12, snapshot.simulatedChapterCount)
        assertEquals(false, snapshot.isLocalBook)
    }
}
