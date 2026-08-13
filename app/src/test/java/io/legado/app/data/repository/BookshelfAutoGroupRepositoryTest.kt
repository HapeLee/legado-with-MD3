package io.legado.app.data.repository

import android.app.Application
import androidx.room.Room
import io.legado.app.constant.BookType
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.domain.model.BookshelfAutoGroupPlan
import io.legado.app.domain.model.BookshelfAutoGroupPlanBook
import io.legado.app.domain.model.BookshelfAutoGroupPlanGroup
import io.legado.app.domain.model.BookshelfAutoGroupErrorReason
import io.legado.app.domain.model.BookshelfAutoGroupException
import io.legado.app.domain.model.BookshelfAutoGroupOptions
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BookshelfAutoGroupRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: BookshelfAutoGroupRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = BookshelfAutoGroupRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `loads only shelf books and maps normalized introduction and groups`() = runBlocking {
        database.bookGroupDao.insert(BookGroup(groupId = 1L, groupName = "Fantasy"))
        database.bookDao.insert(
            Book(
                bookUrl = "shelf",
                name = "Shelf",
                author = "Author",
                intro = "first\n\nsecond",
                group = 1L,
            ),
            Book(
                bookUrl = "search-result",
                name = "Search result",
                author = "Author",
                type = BookType.notShelf,
            ),
        )

        val source = repository.loadSource()

        assertEquals(1, source.books.size)
        assertEquals("first second", source.books.single().intro)
        assertEquals(listOf("Fantasy"), source.books.single().currentGroupNames)
        assertEquals(listOf("Fantasy"), source.existingGroupNames)
    }

    @Test
    fun `does not expose books or group names from private groups`() = runBlocking {
        database.bookGroupDao.insert(
            BookGroup(groupId = 1L, groupName = "Public"),
            BookGroup(groupId = Long.MIN_VALUE, groupName = "Private", isPrivate = true),
        )
        database.bookDao.insert(
            book("public").copy(group = 1L),
            book("private").copy(group = Long.MIN_VALUE),
            book("public-and-private").copy(group = Long.MIN_VALUE or 1L),
        )

        val source = repository.loadSource()

        assertEquals(listOf("public"), source.books.map { it.bookUrl })
        assertEquals(listOf("Public"), source.existingGroupNames)
    }

    @Test
    fun `reuses groups creates groups and counts missing books`() = runBlocking {
        database.bookGroupDao.insert(BookGroup(groupId = 1L, groupName = "Existing"))
        database.bookDao.insert(book("book-1"), book("book-2"))
        val plan = BookshelfAutoGroupPlan(
            groups = listOf(
                group("existing", "Existing", listOf(planBook("book-1"))),
                group("new", "New", listOf(planBook("book-2"), planBook("deleted"))),
            )
        )

        val result = repository.applyPlan(plan, fullOptions)

        assertEquals(1, result.createdGroupCount)
        assertEquals(1, result.reusedGroupCount)
        assertEquals(2, result.updatedBookCount)
        assertEquals(1, result.ignoredBookCount)
        assertEquals(1L, database.bookDao.getBook("book-1")?.group)
        val newGroup = database.bookGroupDao.all.single { it.groupName == "New" }
        assertEquals(newGroup.groupId, database.bookDao.getBook("book-2")?.group)
    }

    @Test
    fun `does not create an empty group for missing books`() = runBlocking {
        val plan = BookshelfAutoGroupPlan(
            groups = listOf(group("missing", "Missing", listOf(planBook("deleted"))))
        )

        val result = repository.applyPlan(plan, fullOptions)

        assertEquals(0, result.createdGroupCount)
        assertEquals(1, result.ignoredBookCount)
        assertTrue(database.bookGroupDao.all.isEmpty())
    }

    @Test
    fun `preserves private membership while replacing public groups`() = runBlocking {
        database.bookGroupDao.insert(
            BookGroup(groupId = 1L, groupName = "Old public"),
            BookGroup(groupId = 2L, groupName = "Private", isPrivate = true),
            BookGroup(groupId = 4L, groupName = "New public"),
        )
        database.bookDao.insert(book("book-1").copy(group = 3L))

        repository.applyPlan(
            BookshelfAutoGroupPlan(
                groups = listOf(group("new", "New public", listOf(planBook("book-1"))))
            ),
            fullOptions,
        )

        assertEquals(6L, database.bookDao.getBook("book-1")?.group)
    }

    @Test
    fun `rolls back group creation when book update fails`() = runBlocking {
        database.bookDao.insert(book("book-1"))
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_auto_group_book_update
            BEFORE UPDATE ON books
            BEGIN
                SELECT RAISE(ABORT, 'forced failure');
            END
            """.trimIndent()
        )
        val plan = BookshelfAutoGroupPlan(
            groups = listOf(group("new", "New", listOf(planBook("book-1"))))
        )

        val result = runCatching { repository.applyPlan(plan, fullOptions) }

        assertTrue(result.isFailure)
        assertTrue(database.bookGroupDao.all.isEmpty())
        assertEquals(0L, database.bookDao.getBook("book-1")?.group)
    }

    @Test
    fun `rejects plan atomically when all group bits are occupied`() = runBlocking {
        val groups = (0 until Long.SIZE_BITS).map { bit ->
            BookGroup(groupId = 1L shl bit, groupName = "Group $bit")
        }
        database.bookGroupDao.insert(*groups.toTypedArray())
        database.bookDao.insert(book("book-1").copy(group = 1L))

        val error = runCatching {
            repository.applyPlan(
                BookshelfAutoGroupPlan(
                    groups = listOf(group("new", "New", listOf(planBook("book-1"))))
                ),
                fullOptions,
            )
        }.exceptionOrNull()

        assertTrue(error is BookshelfAutoGroupException)
        assertEquals(
            BookshelfAutoGroupErrorReason.GroupCapacityExceeded,
            (error as BookshelfAutoGroupException).reason,
        )
        assertEquals(Long.SIZE_BITS, database.bookGroupDao.all.size)
        assertEquals(1L, database.bookDao.getBook("book-1")?.group)
    }

    @Test
    fun `incremental apply skips a book grouped after analysis`() = runBlocking {
        database.bookGroupDao.insert(BookGroup(groupId = 1L, groupName = "Manually grouped"))
        database.bookDao.insert(book("book-1").copy(group = 1L))

        val result = repository.applyPlan(
            BookshelfAutoGroupPlan(
                groups = listOf(group("new", "New", listOf(planBook("book-1"))))
            ),
            BookshelfAutoGroupOptions(incrementalOnly = true),
        )

        assertEquals(0, result.createdGroupCount)
        assertEquals(0, result.updatedBookCount)
        assertEquals(1, result.ignoredBookCount)
        assertEquals(1L, database.bookDao.getBook("book-1")?.group)
        assertTrue(database.bookGroupDao.all.none { it.groupName == "New" })
    }

    private fun book(url: String) = Book(bookUrl = url, name = url, author = "Author")

    private fun planBook(url: String) = BookshelfAutoGroupPlanBook(url, url, "", emptyList(), "")

    private fun group(key: String, name: String, books: List<BookshelfAutoGroupPlanBook>) =
        BookshelfAutoGroupPlanGroup(key, name, "", false, books)

    private val fullOptions = BookshelfAutoGroupOptions(incrementalOnly = false)
}
