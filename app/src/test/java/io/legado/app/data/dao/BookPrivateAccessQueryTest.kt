package io.legado.app.data.dao

import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.SearchBook
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 私密判定的 SQL 侧。
 *
 * 这些规则全部写在 SQL 里：单本标记 ∪ 所属私密分组（位掩码并集），
 * 以及"整组隐藏"与"单本脱敏"两套语义的边界——换成内存实现就验证不到了。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [35])
class BookPrivateAccessQueryTest {

    private lateinit var db: AppDatabase

    // 分组 id 是位掩码，这里取两个不重叠的位
    private val privateGroupId = 0b100L
    private val publicGroupId = 0b1000L

    private val privateGroupBook = "http://example.com/book/private-group"
    private val publicGroupBook = "http://example.com/book/public-group"
    private val markedBook = "http://example.com/book/marked"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        runBlocking {
            db.bookGroupDao.insert(
                BookGroup(groupId = privateGroupId, groupName = "私密分组", isPrivate = true),
                BookGroup(groupId = publicGroupId, groupName = "公开分组")
            )
            db.bookDao.insert(
                newBook(privateGroupBook, privateGroupId),
                newBook(publicGroupBook, publicGroupId),
                newBook(markedBook, publicGroupId)
            )
            db.bookDao.setBooksPrivate(setOf(markedBook), isPrivate = true)
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun singleBookMark_roundTripsThroughSetAndQuery() = runBlocking {
        assertEquals(listOf(markedBook), db.bookDao.flowPrivateBookUrls().first())
    }

    @Test
    fun singleBookMark_canBeRemovedAgain() = runBlocking {
        db.bookDao.setBooksPrivate(setOf(markedBook), isPrivate = false)

        assertTrue(db.bookDao.flowPrivateBookUrls().first().isEmpty())
    }

    @Test
    fun singleBookMark_isPrivateEvenOutsidePrivateGroups() = runBlocking {
        assertTrue(db.bookDao.privateFacts(markedBook).isPrivate)
    }

    @Test
    fun bookInPrivateGroup_isPrivateByUnion() = runBlocking {
        // 单本没标记，但所属分组私密——并集判定要认出来
        assertTrue(db.bookDao.privateFacts(privateGroupBook).isPrivate)
    }

    @Test
    fun ordinaryBook_isNotPrivate() = runBlocking {
        assertFalse(db.bookDao.privateFacts(publicGroupBook).isPrivate)
    }

    @Test
    fun groupMask_isReturnedAlongsideTheJudgement() = runBlocking {
        // 阅读器入口的闸门要拿这个掩码复用"私密分组已解锁"的授权：
        // 取错会让刚在书架解锁完分组的用户，点开组内一本书又被拦一次
        assertEquals(privateGroupId, db.bookDao.privateFacts(privateGroupBook).groupMask)
        assertEquals(publicGroupId, db.bookDao.privateFacts(publicGroupBook).groupMask)
    }

    @Test
    fun privateGroupBooks_areHiddenFromPublicShelf() = runBlocking {
        val urls = db.bookDao.flowBookShelf().first().map { it.bookUrl }

        // 整组隐藏：私密分组的书不出现在公开书架
        assertFalse(privateGroupBook in urls)
        assertTrue(publicGroupBook in urls)
        // 单本标记只做脱敏、不隐藏：它仍然留在公开书架上
        assertTrue(markedBook in urls)
    }

    @Test
    fun privateGroupBooks_areVisibleInsideTheirOwnGroup() = runBlocking {
        val urls = db.bookDao.flowBookShelfByUserGroup(privateGroupId).first().map { it.bookUrl }

        assertEquals(listOf(privateGroupBook), urls)
    }

    @Test
    fun privateGroupBookCount_isVisibleToItsOwnGroupQuery() = runBlocking {
        assertEquals(1, db.bookDao.flowUserGroupBookCount(privateGroupId).first())
    }

    private fun newBook(url: String, group: Long) = SearchBook(
        bookUrl = url,
        origin = "http://example.com",
        name = "书-$url",
        author = "作者"
    ).toBook().apply {
        this.group = group
    }
}
