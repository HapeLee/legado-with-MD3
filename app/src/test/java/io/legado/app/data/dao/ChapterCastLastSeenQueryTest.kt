package io.legado.app.data.dao

import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.ChapterRoleAssignment
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * AI 分配用的「上次出场」查询。
 *
 * 判据全在 SQL 里（`chapterIndex < 本章` + `max(chapterIndex)`），换成内存实现就验证不到：
 * 把当前章自己也算进去，每个角色都成了「上一章刚开口」，久未出场的旧人物再也认不出来。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [35])
class ChapterCastLastSeenQueryTest {

    private lateinit var db: AppDatabase

    private val bookUrl = "http://example.com/book/cast-last-seen"
    private val otherBook = "http://example.com/book/another"
    private val currentChapter = 10

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        val rows = listOf(
            // 老朋友：第 3 章、第 7 章各说过一次
            row("old", "柳生绮沙罗", chapter = 3),
            row("old", "柳生绮沙罗", chapter = 7),
            // 当前章这次不该算进「本章之前」
            row("old", "柳生绮沙罗", chapter = currentChapter),
            // 新人：就在上一章才开口
            row("new", "福顺", chapter = 9),
            // 别本书的同名角色，不该串味
            row("old", "柳生绮沙罗", chapter = 1, bookUrl = otherBook),
        )
        runBlocking {
            rows.forEach { db.chapterRoleAssignmentDao.upsert(it) }
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun row(
        characterId: String,
        characterName: String,
        chapter: Int,
        bookUrl: String = this.bookUrl,
    ) = ChapterRoleAssignment(
        bookUrl = bookUrl,
        chapterIndex = chapter,
        quoteOrdinal = 0,
        characterId = characterId,
        characterName = characterName,
    )

    @Test
    fun `reports only the latest appearance before the given chapter`() = runBlocking {
        val seen = db.chapterRoleAssignmentDao
            .getLastSeenBefore(bookUrl, currentChapter)
            .associate { it.characterId to it.lastChapterIndex }

        assertEquals(mapOf("old" to 7, "new" to 9), seen)
    }

    @Test
    fun `moving the cutoff earlier moves the reported chapter`() = runBlocking {
        val old = db.chapterRoleAssignmentDao
            .getLastSeenBefore(bookUrl, 5)
            .single { it.characterId == "old" }

        assertEquals(3, old.lastChapterIndex)
    }

    @Test
    fun `a character that had not spoken yet reports no row`() = runBlocking {
        assertEquals(0, db.chapterRoleAssignmentDao.getLastSeenBefore(bookUrl, 3).size)
    }

    @Test
    fun `a renamed character keeps one row per name but the newest chapter wins`() = runBlocking {
        db.chapterRoleAssignmentDao.upsert(row("old", "绮沙罗", chapter = 5))

        val seenByName = db.chapterRoleAssignmentDao
            .getLastSeenBefore(bookUrl, currentChapter)
            .filter { it.characterId == "old" }
            .associate { it.characterName to it.lastChapterIndex }

        assertEquals(mapOf("柳生绮沙罗" to 7, "绮沙罗" to 5), seenByName)
    }
}
