package io.legado.app.ui.book.toc

import android.app.Application
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.repository.BookMarkingRepository
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.BookSourceRepository
import io.legado.app.data.repository.BookmarkRepository
import io.legado.app.data.repository.CacheBookDownloadRepository
import io.legado.app.data.repository.OtherSettingsRepository
import io.legado.app.data.repository.ReadSettingsRepository
import io.legado.app.data.repository.SettingsRepository
import io.legado.app.domain.usecase.CacheBookChaptersUseCase
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.AppConfigStore
import io.legado.app.support.InMemoryAppDatabaseFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class TocCacheRenameTest {
    @Test
    fun `retained toc rescans the renamed cache directory using current book metadata`() = runBlocking {
        val app = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx(); AppConfigStore.init(it) }
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val before = Book(bookUrl = "https://toc-cache.example/book", name = "Before toc")
        val after = before.copy(name = "After toc")
        val first = BookChapter(bookUrl = before.bookUrl, url = "https://toc-cache.example/0", title = "One", index = 0)
        val second = first.copy(url = "https://toc-cache.example/1", title = "Two", index = 1)
        val store = ViewModelStore()
        try {
            InMemoryAppDatabaseFixture(app).use { fixture ->
                val db = fixture.database
                db.bookDao.insert(before)
                db.bookChapterDao.insert(first)
                BookHelp.getChapterContentFile(before, first).apply { parentFile!!.mkdirs(); writeText("Offline") }
                val model = TocViewModel(
                    app, SavedStateHandle(mapOf("bookUrl" to before.bookUrl)),
                    CacheBookChaptersUseCase(CacheBookDownloadRepository(db.bookDao)),
                    BookRepository(db.bookDao, db.bookChapterDao, db),
                    BookSourceRepository(db.bookSourceDao), BookmarkRepository(db.bookmarkDao),
                    BookMarkingRepository(db.bookMarkingDao),
                    ReadSettingsRepository(SettingsRepository(), MutableStateFlow(emptyPreferences())),
                    OtherSettingsRepository(),
                )
                store.put("toc", model)
                // Observe the real eager cache owner without activating unrelated title/audio flows.
                @Suppress("UNCHECKED_CAST")
                val cached = TocViewModel::class.java.getDeclaredField("_cachedChapterIndices")
                    .apply { isAccessible = true }.get(model) as StateFlow<Set<Int>>
                withTimeout(5_000) { cached.first { 0 in it } }
                BookHelp.updateCacheFolder(before, after)
                db.bookDao.update(after)
                withTimeout(5_000) { model.bookState.first { it?.name == after.name } }
                db.bookChapterDao.insert(second)
                BookHelp.getChapterContentFile(after, second).apply { parentFile!!.mkdirs(); writeText("More offline") }
                model.onIntent(TocIntent.LoadBook(after.bookUrl))
                val result = withTimeout(5_000) { cached.first { it.isEmpty() || 1 in it } }
                assertEquals(setOf(0, 1), result)
                store.clear()
            }
        } finally {
            store.clear()
            BookHelp.clearCache(before)
            BookHelp.clearCache(after)
            Dispatchers.resetMain()
        }
    }
}
