package io.legado.app.ui.book.manga

import io.legado.app.data.entities.Book
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class MangaBookSnapshotTest {

    @Test
    fun `snapshot recreates an independent book for change source`() {
        val original = Book(
            bookUrl = "book-url",
            tocUrl = "toc-url",
            origin = "source-url",
            originName = "Source",
            name = "Book",
            author = "Author",
            type = 2,
            durChapterIndex = 7,
            readConfig = Book.ReadConfig(
                mangaScrollMode = 3,
                webtoonSidePaddingDp = 12,
            ),
        )

        val restored = MangaBookSnapshot.from(original).toBook()

        assertEquals(original, restored)
        assertNotSame(original, restored)
        assertNotSame(original.readConfig, restored.readConfig)
    }
}
