package io.legado.app.data.dao

import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.SearchBook
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment

/**
 * 书架投影的简介优先级: 用户改的 > 书源列表规则 > 书源详情规则。
 * 走真实的 Room 查询, 因为优先级是写在 SQL 里的。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [35])
class BookShelfIntroQueryTest {

    private lateinit var db: AppDatabase

    private val detailIntro = "📡 当前服务：https://example.cf"
    private val listIntro = "这里是属于斗气的世界"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun shelfIntro(): String? = runBlocking {
        db.bookDao.flowBookShelf().first().single().intro
    }

    private fun newBook() = SearchBook(
        bookUrl = "http://example.com/book/1",
        origin = "http://example.com",
        name = "某某传",
        author = "张三",
        intro = listIntro
    ).toBook().apply {
        //详情规则随后覆盖 intro, 模拟聚合书源把服务状态写进详情简介
        intro = detailIntro
    }

    @Test
    fun shelfPrefersListIntroOverDetailIntro() {
        db.bookDao.insert(newBook())

        assertEquals(listIntro, shelfIntro())
    }

    @Test
    fun shelfFallsBackToDetailIntroWhenListIntroMissing() {
        db.bookDao.insert(newBook().apply { listIntro = null })

        assertEquals(detailIntro, shelfIntro())
    }

    @Test
    fun customIntroStillWins() {
        db.bookDao.insert(newBook().apply { customIntro = "我自己写的简介" })

        assertEquals("我自己写的简介", shelfIntro())
    }

    @Test
    fun listIntroSurvivesRoundTrip() {
        db.bookDao.insert(newBook())

        val saved: Book = db.bookDao.getBook("http://example.com/book/1")!!
        assertEquals(listIntro, saved.listIntro)
        assertEquals(detailIntro, saved.intro)
    }
}
