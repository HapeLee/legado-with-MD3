package io.legado.app.help.book

import android.app.Application
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.support.InMemoryAppDatabaseFixture
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [26])
class BookHelpChapterCacheTest {
    private val book = Book(bookUrl = "https://cache.example/book", name = "Chapter cache")
    private val chapter = BookChapter(bookUrl = book.bookUrl, url = "https://cache.example/1", title = "One")
    private val bookDir get() = File(BookHelp.cachePath, book.getFolderName())

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        BookHelp.clearCache(book)
        bookDir.mkdirs()
    }

    @After
    fun tearDown() = BookHelp.clearCache(book)

    @Test
    fun `old downloaded text remains readable and contributes to cache checks`() {
        File(bookDir, chapter.getFileName()).writeText("Old offline content")
        assertEquals("Old offline content", BookHelp.getContent(book, chapter))
        assertTrue(BookHelp.hasContent(book, chapter))
        assertTrue(BookHelp.isChapterCacheComplete(book, chapter))
        assertEquals(19, BookHelp.getCachedContentLength(book, chapter))
    }

    @Test
    fun `new content wins and invalidation deletes both formats`() {
        val old = File(bookDir, chapter.getFileName()).apply { writeText("Stale") }
        val current = BookHelp.getChapterContentFile(book, chapter)
            .apply { parentFile!!.mkdirs(); writeText("Fresh") }
        assertEquals("Fresh", BookHelp.getContent(book, chapter))
        BookHelp.delContent(book, chapter)
        assertFalse(current.exists())
        assertFalse(old.exists())
        assertNull(BookHelp.getContent(book, chapter))
    }

    @Test
    fun `keep duplicate title creates a durable marker and enabling removal clears both formats`() {
        InMemoryAppDatabaseFixture(RuntimeEnvironment.getApplication()).use { fixture ->
            fixture.database.bookDao.insert(book)
            BookHelp.setRemoveSameTitle(book, chapter, false)
            assertTrue(BookHelp.getChapterContentFile(book, chapter, "nr").isFile)
            assertTrue(BookHelp.getChapterFiles(book).contains(chapter.getFileName("nr")))
            assertFalse(BookHelp.removeSameTitle(book, chapter))
            File(bookDir, chapter.getFileName("nr")).createNewFile()
            BookHelp.setRemoveSameTitle(book, chapter, true)
            assertTrue(BookHelp.removeSameTitle(book, chapter))
            assertFalse(BookHelp.getChapterFiles(book).contains(chapter.getFileName("nr")))
        }
    }

    @Test
    fun `old title marker remains readable`() {
        File(bookDir, chapter.getFileName("nr")).createNewFile()
        assertFalse(BookHelp.removeSameTitle(book, chapter))
    }
}
