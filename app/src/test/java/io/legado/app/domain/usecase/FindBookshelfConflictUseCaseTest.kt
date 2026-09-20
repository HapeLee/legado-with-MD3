package io.legado.app.domain.usecase

import android.app.Application
import androidx.room.Room
import io.legado.app.constant.BookType
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.data.repository.BookRepository
import io.legado.app.help.config.AppConfigStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class FindBookshelfConflictUseCaseTest {

    private lateinit var database: AppDatabase
    private lateinit var useCase: FindBookshelfConflictUseCase

    @Before
    fun setUp() {
        AppConfigStore.init(RuntimeEnvironment.getApplication())
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries().build()
        useCase = FindBookshelfConflictUseCase(
            BookRepository(
                database.bookDao,
                database.bookChapterDao,
                database
            )
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `same name and author is reported as conflict`() = runBlocking {
        insertShelfBook("https://old.example/book", "斗破苍穹", "天蚕土豆")
        val incoming =
            Book(bookUrl = "https://new.example/book", name = "斗破苍穹", author = "天蚕土豆")

        val conflict = useCase.execute(incoming)

        assertEquals(
            listOf("https://old.example/book"),
            conflict?.candidates?.map { it.bookUrl },
        )
    }

    @Test
    fun `name is matched after trimming and folding whitespace`() = runBlocking {
        insertShelfBook("https://old.example/book", "斗破  苍穹 ", "天蚕土豆")
        val incoming = Book(
            bookUrl = "https://new.example/book",
            name = "斗破 苍穹",
            author = "天蚕土豆",
        )

        val conflict = useCase.execute(incoming)

        assertEquals(1, conflict?.candidates?.size)
    }

    @Test
    fun `blank author on either side is still a conflict`() = runBlocking {
        insertShelfBook("https://old.example/book", "斗破苍穹", "")
        val incoming =
            Book(bookUrl = "https://new.example/book", name = "斗破苍穹", author = "天蚕土豆")

        val conflict = useCase.execute(incoming)

        assertEquals(1, conflict?.candidates?.size)
    }

    @Test
    fun `different author is not a conflict`() = runBlocking {
        insertShelfBook("https://old.example/book", "斗破苍穹", "天蚕土豆")
        val incoming =
            Book(bookUrl = "https://new.example/book", name = "斗破苍穹", author = "其他人")

        assertNull(useCase.execute(incoming))
    }

    @Test
    fun `different name is not a conflict`() = runBlocking {
        insertShelfBook("https://old.example/book", "斗破苍穹", "天蚕土豆")
        val incoming =
            Book(bookUrl = "https://new.example/book", name = "武动乾坤", author = "天蚕土豆")

        assertNull(useCase.execute(incoming))
    }

    @Test
    fun `the book being added is never its own conflict`() = runBlocking {
        insertShelfBook("https://old.example/book", "斗破苍穹", "天蚕土豆")
        val incoming =
            Book(bookUrl = "https://old.example/book", name = "斗破苍穹", author = "天蚕土豆")

        assertNull(useCase.execute(incoming))
    }

    @Test
    fun `removed from shelf books are ignored`() = runBlocking {
        database.bookDao.insert(
            Book(
                bookUrl = "https://preview.example/book",
                name = "斗破苍穹",
                author = "天蚕土豆",
                type = BookType.notShelf,
            )
        )
        val incoming =
            Book(bookUrl = "https://new.example/book", name = "斗破苍穹", author = "天蚕土豆")

        assertNull(useCase.execute(incoming))
    }

    private suspend fun insertShelfBook(bookUrl: String, name: String, author: String) {
        database.bookDao.insert(Book(bookUrl = bookUrl, name = name, author = author))
    }
}
