package io.legado.app.data.repository

import android.app.Application
import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.TagGroupRule
import io.legado.app.domain.model.NewBookGroup
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
class BookGroupMutationRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: BookGroupMutationRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = BookGroupMutationRepository(
            database = database,
            tagGroupRuleApplier = TagGroupRuleApplier(database),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `应用规则失败时回滚分组和规则写入`() = runBlocking {
        val book = Book(
            bookUrl = "book-1",
            name = "Book",
            author = "Author",
            kind = "fantasy",
        )
        database.bookDao.insert(book)
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_book_update
            BEFORE UPDATE ON books
            BEGIN
                SELECT RAISE(ABORT, 'forced failure');
            END
            """.trimIndent()
        )

        val result = runCatching {
            repository.addGroup(
                NewBookGroup(
                    groupName = "Fantasy",
                    bookSort = -1,
                    enableRefresh = true,
                    isPrivate = false,
                    cover = null,
                    pattern = "fantasy",
                )
            )
        }

        assertTrue(result.isFailure)
        assertTrue(database.bookGroupDao.all.isEmpty())
        assertTrue(database.tagGroupRuleDao.getAll().isEmpty())
        assertEquals(0L, database.bookDao.getBook(book.bookUrl)?.group)
    }

    @Test
    fun `删除规则后保留已有分组成员`() = runBlocking {
        val group = BookGroup(groupId = 1L, groupName = "Fantasy")
        val rule = TagGroupRule(id = 1L, groupName = group.groupName, pattern = "fantasy")
        val book = Book(
            bookUrl = "book-1",
            name = "Book",
            author = "Author",
            kind = "fantasy",
            group = group.groupId,
        )
        database.bookGroupDao.insert(group)
        database.tagGroupRuleDao.insert(rule)
        database.bookDao.insert(book)

        repository.deleteTagGroupRule(rule.id)

        assertTrue(database.tagGroupRuleDao.getAll().isEmpty())
        assertEquals(group.groupId, database.bookDao.getBook(book.bookUrl)?.group)
    }

    @Test
    fun `新增带规则分组后立即更新匹配书籍`() = runBlocking {
        val book = Book(
            bookUrl = "book-1",
            name = "Book",
            author = "Author",
            kind = "fantasy",
        )
        database.bookDao.insert(book)

        repository.addGroup(
            NewBookGroup(
                groupName = "Fantasy",
                bookSort = -1,
                enableRefresh = true,
                isPrivate = false,
                cover = null,
                pattern = "fantasy",
            )
        )

        val group = database.bookGroupDao.all.single()
        assertEquals(group.groupId, database.bookDao.getBook(book.bookUrl)?.group)
        assertEquals(group.groupName, database.tagGroupRuleDao.getAll().single().groupName)
    }

    @Test
    fun `非法正则不会产生数据库写入`() = runBlocking {
        val result = runCatching {
            repository.addGroup(
                NewBookGroup(
                    groupName = "Broken",
                    bookSort = -1,
                    enableRefresh = true,
                    isPrivate = false,
                    cover = null,
                    pattern = "[",
                )
            )
        }

        assertTrue(result.isFailure)
        assertTrue(database.bookGroupDao.all.isEmpty())
        assertTrue(database.tagGroupRuleDao.getAll().isEmpty())
    }
}
