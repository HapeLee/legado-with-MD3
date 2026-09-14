package io.legado.app.ui.book.searchContent

import android.app.Application
import android.os.Looper
import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.SearchContentHistory
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.SearchContentRepository
import io.legado.app.domain.gateway.ThemeSettingsGateway
import io.legado.app.domain.model.settings.ThemeSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * 全文搜索的搜索历史只记录「用户确认过的查询」。
 *
 * 回归 issue #2113：输入过程中的自动搜索曾把每个拼音、半截词都写进 search_content_history，
 * 历史列表被中间态灌满。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SearchContentHistoryRecordingTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `typing only previews and never writes search history`() {
        val viewModel = viewModel(initialSearchWord = null)
        awaitBook(viewModel)

        viewModel.onIntent(SearchContentIntent.UpdateQuery("yan"))
        viewModel.onIntent(SearchContentIntent.UpdateQuery("yanjing"))
        idle()

        assertEquals(emptyList<String>(), awaitQueries(atLeast = 1, timeoutMs = 400))
    }

    @Test
    fun `the keyboard search key records the committed query`() {
        val viewModel = viewModel(initialSearchWord = null)
        awaitBook(viewModel)

        viewModel.onIntent(SearchContentIntent.UpdateQuery("yan"))
        idle()
        viewModel.onIntent(SearchContentIntent.SubmitSearch("眼镜"))

        assertEquals(listOf("眼镜"), awaitQueries(atLeast = 1))
    }

    @Test
    fun `a search started from a text selection is recorded`() {
        val viewModel = viewModel(initialSearchWord = "眼镜")
        awaitBook(viewModel)

        assertEquals(listOf("眼镜"), awaitQueries(atLeast = 1))
    }

    private fun viewModel(initialSearchWord: String?): SearchContentViewModel {
        runBlocking {
            db.bookDao.insert(Book(bookUrl = BOOK_URL, name = "测试书", author = "作者"))
        }
        return SearchContentViewModel(
            bookUrl = BOOK_URL,
            initialSearchWord = initialSearchWord,
            searchResultIndex = 0,
            bookRepository = BookRepository(db.bookDao, db.bookChapterDao, db),
            searchContentRepository = SearchContentRepository(historyDao = db.searchContentHistoryDao),
            themeSettingsGateway = FakeThemeSettingsGateway(),
        )
    }

    /** initBook() 在 IO 线程取书，书就位后才会有"确认搜索"的写入。 */
    private fun awaitBook(viewModel: SearchContentViewModel, timeoutMs: Long = 3_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            idle()
            if (viewModel.uiState.value.book != null) return
            Thread.sleep(20)
        }
        error("书籍未在 ${timeoutMs}ms 内加载完成")
    }

    /** 轮询等待历史写入（Room 的挂起写入落在自己的执行器上，主线程 idle 不等它）。 */
    private fun awaitQueries(atLeast: Int, timeoutMs: Long = 3_000): List<String> {
        val deadline = System.currentTimeMillis() + timeoutMs
        var queries = emptyList<String>()
        while (System.currentTimeMillis() < deadline) {
            idle()
            queries = runBlocking { db.searchContentHistoryDao.getAll().first() }
                .map(SearchContentHistory::query)
            if (queries.size >= atLeast) break
            Thread.sleep(20)
        }
        return queries
    }

    private fun idle() = Shadows.shadowOf(Looper.getMainLooper()).idle()

    private class FakeThemeSettingsGateway : ThemeSettingsGateway {
        override val currentSettings: ThemeSettings = ThemeSettings()
        override val settings: Flow<ThemeSettings> = MutableStateFlow(ThemeSettings())
        override suspend fun update(transform: (ThemeSettings) -> ThemeSettings) = Unit
    }

    private companion object {
        const val BOOK_URL = "test://book/1"
    }
}
