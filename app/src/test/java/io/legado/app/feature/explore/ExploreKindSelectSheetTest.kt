package io.legado.app.feature.explore

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.legado.app.data.dao.BookSourceDao
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.repository.CoverAlbumRepository
import io.legado.app.data.repository.ExploreRepository
import io.legado.app.data.repository.SettingsRepository
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.usecase.CoverAlbumUseCase
import io.legado.app.domain.usecase.ExploreKindUiUseCase
import io.legado.app.help.CacheManager
import io.legado.app.help.config.AppConfigStore
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.widget.components.explore.ExploreKindSelectSheet
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import splitties.init.injectAsAppCtx
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExploreKindSelectSheetTest {
    @get:Rule
    val compose = createComposeRule()
    private val dropdown = ExploreKind(
        title = "dropdown", type = ExploreKind.Type.select, chars = arrayOf("A", "B")
    )
    private val books = ExploreKind(title = "books", url = "https://test/books")
    private val refresh = ExploreKind(
        title = "refresh", type = ExploreKind.Type.button, action = "java.refreshExplore()"
    )
    private val actionCount = AtomicInteger()
    private var dismissCount = 0
    private var selected = emptyList<ExploreKind>()

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        app.injectAsAppCtx()
        AppConfigStore.init(app)
        CacheManager.putMemory("infoMap_sheet-test", "{\"dropdown\":\"A\"}")
        stopKoin()
        startKoin {
            modules(module {
                single { CoverAlbumUseCase(CoverAlbumRepository(app, SettingsRepository())) }
            })
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `dropdown opens options without selecting a category or dismissing sheet`() {
        showSheet()
        compose.onNodeWithContentDescription("dropdown A").performClick()
        compose.onNodeWithText("B").assertExists()
        compose.runOnIdle {
            assertEquals(0, dismissCount)
            assertEquals(emptyList<ExploreKind>(), selected)
        }
    }

    @Test
    fun `url category still selects and dismisses`() {
        showSheet()
        compose.onNodeWithContentDescription("books").performClick()
        compose.runOnIdle {
            assertEquals(listOf(books), selected)
            assertEquals(1, dismissCount)
        }
    }

    @Test
    fun `multiple mode keeps dropdown as a selectable configuration item`() {
        showSheet(multiple = true)
        compose.onNodeWithContentDescription("dropdown").performClick()
        compose.onNodeWithText("B").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, dismissCount) }
    }

    @Test
    fun `script button dispatches action without selecting category or dismissing`() {
        showSheet()
        compose.onNodeWithContentDescription("refresh").performClick()
        compose.waitUntil(5_000) { actionCount.get() == 1 }
        compose.runOnIdle {
            assertEquals(0, dismissCount)
            assertEquals(emptyList<ExploreKind>(), selected)
        }
    }

    private fun showSheet(multiple: Boolean = false) {
        val repository = object : ExploreRepository {
            override fun getBookshelfItems() = flowOf(emptyList<SearchBook>())
            override fun getExploreGroups() = flowOf(emptyList<String>())
            override fun getExploreSources(query: String, selectedGroup: String) =
                flowOf(emptyList<BookSourcePart>())

            override suspend fun getBookSource(sourceUrl: String): BookSource? = null
            override suspend fun getSourceExploreKinds(sourceUrl: String) =
                listOf(dropdown, books, refresh)

            override suspend fun refreshSourceExploreKinds(sourceUrl: String) =
                listOf(dropdown, books, refresh)

            override suspend fun topSource(bookSource: BookSourcePart) = Unit
            override suspend fun deleteSource(sourceUrl: String) = Unit
        }
        val dao = Proxy.newProxyInstance(
            BookSourceDao::class.java.classLoader, arrayOf(BookSourceDao::class.java)
        ) { _, method, _ ->
            if (method.name == "getBookSource") {
                actionCount.incrementAndGet()
                null // 观察动作进入 UseCase；不在点击分发测试中启动完整书源 JS 环境。
            } else error("Unexpected DAO call: ${method.name}")
        } as BookSourceDao
        val useCase = ExploreKindUiUseCase(dao)
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                AppTheme(AppUiConfiguration(), applyBackground = false) {
                    ExploreKindSelectSheet(
                        show = true, sourceUrl = "sheet-test", multiple = multiple,
                        onDismissRequest = { dismissCount++ }, onSelected = { selected = it },
                        repository = repository, useCase = useCase,
                    )
                }
            }
        }
    }
}
