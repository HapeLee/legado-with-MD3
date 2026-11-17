package io.legado.app.data.repository

import android.app.Application
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.support.InMemoryAppDatabaseFixture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [26])
class BookRepositoryWordCountTest {

    @Test
    fun `directory reload preserves counts but respects incoming nonempty counts`() = runBlocking {
        InMemoryAppDatabaseFixture(RuntimeEnvironment.getApplication()).use { fixture ->
            val db = fixture.database
            val book = Book(bookUrl = "file:///book.epub", name = "Book")
            db.bookDao.insert(book)
            db.bookChapterDao.insert(
                BookChapter(bookUrl = book.bookUrl, url = "one", index = 0, wordCount = "100"),
                BookChapter(bookUrl = book.bookUrl, url = "two", index = 1, wordCount = "200"),
                BookChapter(bookUrl = book.bookUrl, url = "three", index = 2, wordCount = "300"),
            )
            val chapters = listOf(
                BookChapter(bookUrl = book.bookUrl, url = "one", index = 0, title = "Renamed"),
                BookChapter(bookUrl = book.bookUrl, url = "two", index = 1, wordCount = "250"),
                BookChapter(bookUrl = book.bookUrl, url = "three", index = 2, wordCount = ""),
                BookChapter(bookUrl = book.bookUrl, url = "four", index = 3),
            )

            BookRepository(db.bookDao, db.bookChapterDao, db)
                .replaceChaptersAndUpdateBook(book, chapters)

            val saved = db.bookChapterDao.getChapterList(book.bookUrl)
            assertEquals(listOf("100", "250", "300", null), saved.map { it.wordCount })
            assertEquals("Renamed", saved.first().title)
        }
    }

    @Test
    fun `URL rebind preserves counts from old book and removes old chapters`() = runBlocking {
        InMemoryAppDatabaseFixture(RuntimeEnvironment.getApplication()).use { fixture ->
            val db = fixture.database
            val oldBook = Book(bookUrl = "file:///old.epub", name = "Book")
            val newBook = oldBook.copy(bookUrl = "file:///new.epub")
            db.bookDao.insert(oldBook, newBook)
            db.bookChapterDao.insert(
                BookChapter(bookUrl = oldBook.bookUrl, url = "one", index = 0, wordCount = "100"),
                BookChapter(bookUrl = newBook.bookUrl, url = "one", index = 0, wordCount = "999"),
            )

            BookRepository(db.bookDao, db.bookChapterDao, db).replaceChaptersAndUpdateBook(
                newBook,
                listOf(BookChapter(bookUrl = newBook.bookUrl, url = "one", index = 0)),
                oldBookUrl = oldBook.bookUrl,
            )

            assertEquals("100", db.bookChapterDao.getChapter(newBook.bookUrl, 0)?.wordCount)
            assertTrue(db.bookChapterDao.getChapterList(oldBook.bookUrl).isEmpty())
        }
    }

    @Test
    fun `word count write preserves a title changed after the chapter snapshot`() {
        InMemoryAppDatabaseFixture(RuntimeEnvironment.getApplication()).use { fixture ->
            val db = fixture.database
            val book = Book(bookUrl = "file:///book.mobi", name = "Book")
            db.bookDao.insert(book)
            db.bookChapterDao.insert(
                BookChapter(bookUrl = book.bookUrl, url = "one", title = "Original"),
            )
            val snapshot = requireNotNull(db.bookChapterDao.getChapter(book.bookUrl, 0))
            db.bookChapterDao.update(snapshot.copy(title = "User title"))

            db.bookChapterDao.upWordCount(snapshot.bookUrl, snapshot.url, "100")

            val saved = requireNotNull(db.bookChapterDao.getChapter(book.bookUrl, 0))
            assertEquals("User title", saved.title)
            assertEquals("100", saved.wordCount)
        }
    }
}
