package io.legado.app.help.book

import android.app.Application
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.feature.reader.platform.ReaderLocalPresentationResolver
import io.legado.app.model.localBook.LocalBookSemanticHtml
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
    fun `unmodified legacy EPUB cache upgrades and binds original Ruby presentation`() {
        val local = localBook("legacy.epub")
        val chapter = this.chapter.copy(bookUrl = local.bookUrl)
        val file = File(BookHelp.cachePath, local.getFolderName()).resolve(chapter.getFileName())
        try {
            file.parentFile!!.mkdirs()
            file.writeText("漢かん字")
            val html = "<ruby>漢<rt>かん</rt></ruby>字"

            assertNull(BookHelp.getCachedContentInfo(local, chapter))
            val content = BookHelp.getContent(local, chapter) { LocalBookSemanticHtml.upgradeCachedRubyText(html, it) }
            assertEquals("漢字", content)
            assertEquals(content, file.readText())
            assertEquals(content, BookHelp.getContent(local, chapter))
            assertEquals(2, BookHelp.getCachedContentLength(local, chapter))
            val presentation = ReaderLocalPresentationResolver.resolve(html)?.bind(checkNotNull(content))
            assertEquals("かん", presentation?.rubies?.single()?.text)
        } finally {
            BookHelp.clearCache(local)
        }
    }

    @Test
    fun `edited MOBI cache stays authoritative even when it contains old annotations`() {
        val local = localBook("edited.mobi")
        val chapter = this.chapter.copy(bookUrl = local.bookUrl)
        val file = BookHelp.getChapterContentFile(local, chapter)
        val edited = "漢かん字\nPersonal note"
        try {
            file.parentFile!!.mkdirs()
            file.writeText(edited)
            val html = "<ruby>漢<rt>かん</rt></ruby>字"
            val content = BookHelp.getContent(local, chapter) {
                LocalBookSemanticHtml.upgradeCachedRubyText(html, it, decodeEntities = false)
            }

            assertEquals(edited, content)
            assertEquals(edited, file.readText())
            assertEquals(edited, BookHelp.getContent(local, chapter))
            assertEquals(edited.length, BookHelp.getCachedContentLength(local, chapter))
        } finally {
            BookHelp.clearCache(local)
        }
    }

    private fun localBook(name: String) = Book(
        bookUrl = "/local/$name",
        origin = BookType.localTag,
        originName = name,
        name = name,
        type = BookType.local or BookType.text,
    )

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
