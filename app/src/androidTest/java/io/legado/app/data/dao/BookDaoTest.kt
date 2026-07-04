package io.legado.app.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.constant.BookType
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookDaoTest {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun replaceAllRemovesBooksUsingPreviousSourceUrls() {
        val oldSourceBook = Book(
            bookUrl = "https://old.example/book",
            name = "Book",
            author = "Author",
            origin = "https://old.example",
        )
        val deviceOnlyBook = Book(
            bookUrl = "https://device.example/book",
            name = "Device book",
            author = "Author",
            origin = "https://device.example",
        )
        val restoredBook = Book(
            bookUrl = "https://new.example/book",
            name = "Book",
            author = "Author",
            origin = "https://new.example",
        )
        database.bookDao.insert(oldSourceBook, deviceOnlyBook)

        database.bookDao.replaceAll(listOf(restoredBook))

        assertEquals(listOf(restoredBook), database.bookDao.all)
    }

    @Test
    fun getShelfBookConflictReturnsMostRecentlyReadShelfBook() {
        val olderBook = Book(
            bookUrl = "https://older.example/book",
            name = "Book",
            author = "Author",
            durChapterTime = 100,
        )
        val newerBook = Book(
            bookUrl = "https://newer.example/book",
            name = "Book",
            author = "Author",
            durChapterTime = 200,
        )
        val previewBook = Book(
            bookUrl = "https://preview.example/book",
            name = "Book",
            author = "Author",
            durChapterTime = 300,
            type = BookType.notShelf,
        )
        database.bookDao.insert(olderBook, newerBook, previewBook)

        val conflict = database.bookDao.getShelfBookConflict("Book", "Author")

        assertEquals(newerBook, conflict)
    }
}
