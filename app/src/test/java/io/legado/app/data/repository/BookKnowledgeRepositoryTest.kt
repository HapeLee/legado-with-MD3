package io.legado.app.data.repository

import android.app.Application
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.support.InMemoryAppDatabaseFixture
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

/**
 * 一本书的人物档案读取量：调用方要多少就得给多少。
 *
 * 消费方是「人物配音」「人物列表」「事记 / 关系图」这些按整本书列人的界面，
 * 少给一个人就是那一行在界面上凭空不存在。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BookKnowledgeRepositoryTest {
    private lateinit var fixture: InMemoryAppDatabaseFixture
    private val db get() = fixture.database
    private val bookUrl = "knowledge-repository-book"

    @Before fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        app.injectAsAppCtx()
        fixture = InMemoryAppDatabaseFixture(app)
    }

    @After fun tearDown() {
        fixture.close()
    }

    @Test fun `a caller asking for more than the old cap really gets them`() = runBlocking {
        repeat(100) { index ->
            db.bookKnowledgeDao.upsertCharacterProfile(
                BookCharacterProfile(
                    id = "p%03d".format(index),
                    bookUrl = bookUrl,
                    name = "人物%03d".format(index),
                    updatedAt = 1_000L + index,
                ),
            )
        }

        assertEquals(100, BookKnowledgeRepository(db.bookKnowledgeDao).getCharacterProfiles(bookUrl, 200).size)
    }

    @Test fun `an absurd limit is still bounded`() = runBlocking {
        assertEquals(
            0,
            BookKnowledgeRepository(db.bookKnowledgeDao)
                .getCharacterProfiles(bookUrl, Int.MAX_VALUE).size,
        )
    }
}
