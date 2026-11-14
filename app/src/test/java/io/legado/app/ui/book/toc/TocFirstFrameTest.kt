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
import io.legado.app.help.config.AppConfigStore
import io.legado.app.support.InMemoryAppDatabaseFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx
import java.io.File

/**
 * 目录首帧行为基线。
 *
 * 回归背景：阅读页目录 Sheet 首次打开时，列表与顶部书籍信息全空，必须切 tab 或
 * 关闭重开才出现；而顶栏更多菜单（直接用 ReadBook.book 内存单例）一直正常。
 * 目录树曾挂在 `bookState` 与 `LaunchedEffect(LoadBook)` 两个单点上；首帧固化的最终
 * 原因则是状态值跨 composition 传递（玻璃 sheet 内容由 root dialog host 执行）。
 * 测试锁住：加载态语义、宿主 Book 兜底、组合期绑定、章节列表不依赖 books 表命中、
 * 订阅必须发生在渲染内容的 composition、以及加载态不挡住已到达的章节。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class TocFirstFrameTest {

    @Test
    fun `first frame is an explicit loading state and chapters arrive without extra intents`() =
        runBlocking {
            withFixture { fixture, store ->
                val book = Book(bookUrl = "https://toc-firstframe.example/book", name = "First frame")
                val db = fixture.database
                db.bookDao.insert(book)
                chaptersFor(book).forEach { db.bookChapterDao.insert(it) }
                val model = createModel(fixture, store)

                assertTrue(
                    "screenState 的空 initialValue 必须标记为加载中，而不是无语义的空列表",
                    TocUiState().isLoading,
                )
                val loaded = CompletableDeferred<TocUiState>()
                val collector = launch {
                    model.screenState.collect { state ->
                        if (!state.isLoading && state.action.items.isNotEmpty() &&
                            !loaded.isCompleted
                        ) {
                            loaded.complete(state)
                        }
                    }
                }
                // 全屏目录页路径：订阅建立之后才注入 bookUrl。
                model.onIntent(TocIntent.LoadBook(book.bookUrl))

                val reached = withTimeoutOrNull(5_000) { loaded.await() }
                assertNotNull("目录必须在没有额外 intent 的情况下自行加载完成", reached)
                assertEquals(3, reached!!.action.items.size)
                assertFalse("加载完成后 isLoading 必须为 false", reached.isLoading)

                // 重新进入复用同一个 VM：StateFlow 必须能立刻回放已加载的目录。
                val reentry = withTimeoutOrNull(3_000) {
                    model.screenState.first { !it.isLoading && it.action.items.isNotEmpty() }
                }
                assertNotNull("重新进入应直接拿到已加载的目录", reentry)
                assertEquals(3, reentry!!.action.items.size)
                collector.cancel()
            }
        }

    @Test
    fun `bindBook before subscription makes toc available without LaunchedEffect`() =
        runBlocking {
            withFixture { fixture, store ->
                val book = Book(bookUrl = "https://toc-bind.example/book", name = "Bound book")
                val db = fixture.database
                db.bookDao.insert(book)
                chaptersFor(book).forEach { db.bookChapterDao.insert(it) }
                val model = createModel(fixture, store)

                // 阅读页在组合期同步绑定；此处不发送任何 intent，模拟 LaunchedEffect
                // 还没执行（或永远不执行）时订阅就已经建立的场景。
                model.bindBook(book.bookUrl, book)

                val reached = withTimeoutOrNull(5_000) {
                    model.screenState.first { !it.isLoading && it.action.items.isNotEmpty() }
                }
                assertNotNull("组合期绑定后首次订阅就必须拿到目录，不能等重开 sheet", reached)
                assertEquals(3, reached!!.action.items.size)
                assertEquals(book.name, reached.book?.name)
            }
        }

    @Test
    fun `host book keeps header populated when books table has no row`() = runBlocking {
        withFixture { fixture, store ->
            val book = Book(bookUrl = "https://toc-host.example/book", name = "Host only")
            val model = createModel(fixture, store)

            // 不往 books / chapters 表写任何行：flowBook 与 flowChapters 都查不到。
            model.bindBook(book.bookUrl, book)

            val withHeader = withTimeoutOrNull(3_000) {
                model.screenState.first { it.book != null }
            }
            assertNotNull("书籍信息必须由宿主内存 Book 提供，不能依赖 books 表命中", withHeader)
            assertEquals(book.name, withHeader!!.book?.name)
        }
    }

    @Test
    fun `db book takes precedence over host book`() = runBlocking {
        withFixture { fixture, store ->
            val host = Book(bookUrl = "https://toc-precedence.example/book", name = "Host")
            val row = host.copy(name = "Db")
            fixture.database.bookDao.insert(row)
            val model = createModel(fixture, store)

            model.bindBook(host.bookUrl, host)

            val settled = withTimeoutOrNull(3_000) {
                model.screenState.first { it.book?.name == "Db" }
            }
            assertNotNull("DB 值必须优先于宿主 Book，保证进度与设置变更仍能刷新", settled)
        }
    }

    @Test
    fun `screenState keeps loading without any UI subscriber`() = runBlocking {
        withFixture { fixture, store ->
            val book = Book(bookUrl = "https://toc-eager.example/book", name = "Eager")
            val db = fixture.database
            db.bookDao.insert(book)
            chaptersFor(book).forEach { db.bookChapterDao.insert(it) }
            val model = createModel(fixture, store)
            model.bindBook(book.bookUrl, book)

            // 故意不订阅 screenState：Miuix HyperOS4 玻璃容器把内容注册到 root
            // dialog host，目录 Route 的订阅可能没有真正驱动上游。
            val reached = withTimeoutOrNull(3_000) {
                while (model.screenState.value.action.items.isEmpty()) {
                    kotlinx.coroutines.delay(10)
                }
                model.screenState.value
            }
            assertNotNull(
                "目录必须在没有 UI 订阅时也完成加载，否则独立组合里永远停在加载圈",
                reached,
            )
            assertEquals(3, reached!!.action.items.size)
        }
    }

    @Test
    fun `rawDataFlow never waits for secondary state flows on its first emission`() {
        val source = projectPath(
            "app/src/main/java/io/legado/app/ui/book/toc/TocViewModel.kt",
        ).readText()
        val block = source.substringAfter("override val rawDataFlow")
            .substringBefore("val useReplace get()")
        listOf(
            "downloadContextFlow",
            "uiConfigFlow",
            "statusFlagsFlow",
        ).forEach { upstream ->
            assertTrue(
                "$upstream 必须用 onStart 提供首帧默认值，" +
                    "否则目录会等最慢的上游（整书缓存扫描/角色与音频表）才第一次出现内容",
                Regex("$upstream\\s*\\.onStart").containsMatchIn(block),
            )
        }
        val firstUpstream = block.substringAfter("combine(")
            .substringBefore("downloadContextFlow")
        assertFalse(
            "章节列表必须以 bookUrlFlow 为触发源，不能挂在 bookState.filterNotNull() 上",
            firstUpstream.contains("bookState.filterNotNull()"),
        )
    }

    @Test
    fun `sheet content collects state in the composition that renders it`() {
        val source = projectPath(
            "app/src/main/java/io/legado/app/ui/book/read/sheet/ReaderBookSheet.kt",
        ).readText()
        val route = source.substringAfter("fun ReaderBookSheetRoute(")
            .substringBefore("private fun ReaderBookSheet(")
        val sheet = source.substringAfter("private fun ReaderBookSheet(")
            .substringBefore("private fun ReaderBookTocPage(")
        assertFalse(
            "Route 不能把 TocUiState 当值收集：玻璃 sheet 的 content 在另一个 composition " +
                "执行，Compose 记忆化的 lambda 会把跨 composition 捕获的值固化成首帧",
            Regex("collectAsState\\s*\\(").containsMatchIn(route),
        )
        assertFalse(
            "目录 Route 不能用 lifecycle 门控的收集",
            Regex("collectAsStateWithLifecycle\\s*\\(").containsMatchIn(route),
        )
        assertTrue(
            "Route 必须把稳定的 StateFlow 引用交给内容侧",
            route.contains("screenStateFlow = viewModel.screenState"),
        )
        assertTrue(
            "ReaderBookSheet 必须在 AppModalBottomSheet 的 content 内 collectAsState，" +
                "让订阅发生在真正渲染内容的 composition",
            sheet.contains("screenStateFlow: StateFlow<TocUiState>") &&
                sheet.contains("by screenStateFlow.collectAsState()"),
        )
    }

    @Test
    fun `toc page renders chapters as soon as they arrive`() {
        val source = projectPath(
            "app/src/main/java/io/legado/app/ui/book/read/sheet/ReaderBookSheet.kt",
        ).readText()
        val page = source.substringAfter("private fun ReaderBookTocPage(")
            .substringBefore("private fun ReaderSheetChapterList(")
        assertFalse(
            "加载态不能是唯一渲染闸门：isLoading 单独决定会挡住已到达的章节",
            page.contains("if (state.isLoading) {"),
        )
        assertTrue(
            "仅当加载中且 items 为空时显示加载态，其余情况渲染章节列表",
            page.contains("val showLoading = state.isLoading && state.action.items.isEmpty()"),
        )
        assertFalse(
            "临时诊断代码不应留在交付里",
            page.contains("TOC loading timeout") || page.contains("vmSnapshot"),
        )
    }

    private fun chaptersFor(book: Book) = (0 until 3).map { index ->
        BookChapter(
            bookUrl = book.bookUrl,
            url = "${book.bookUrl}/$index",
            title = "Chapter $index",
            index = index,
        )
    }

    private fun createModel(
        fixture: InMemoryAppDatabaseFixture,
        store: ViewModelStore,
    ): TocViewModel {
        val db = fixture.database
        val model = TocViewModel(
            RuntimeEnvironment.getApplication(),
            SavedStateHandle(),
            CacheBookChaptersUseCase(CacheBookDownloadRepository(db.bookDao)),
            BookRepository(db.bookDao, db.bookChapterDao, db),
            BookSourceRepository(db.bookSourceDao),
            BookmarkRepository(db.bookmarkDao),
            BookMarkingRepository(db.bookMarkingDao),
            ReadSettingsRepository(SettingsRepository(), MutableStateFlow(emptyPreferences())),
            OtherSettingsRepository(),
        )
        store.put("toc", model)
        return model
    }

    private suspend fun withFixture(
        block: suspend (InMemoryAppDatabaseFixture, ViewModelStore) -> Unit,
    ) {
        val app = RuntimeEnvironment.getApplication().also {
            it.injectAsAppCtx()
            AppConfigStore.init(it)
        }
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val store = ViewModelStore()
        try {
            InMemoryAppDatabaseFixture(app).use { fixture ->
                block(fixture, store)
            }
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    private fun projectPath(relativePath: String): File {
        val candidates = listOf(relativePath, relativePath.removePrefix("app/"))
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            candidates.forEach { candidatePath ->
                val candidate = File(directory, candidatePath)
                if (candidate.exists()) return candidate
            }
            if (File(directory, ".git").exists()) return File(directory, candidates.first())
            directory = directory.parentFile
        }
        error("cannot locate $relativePath")
    }
}
