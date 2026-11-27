package io.legado.app.ui.widget.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.legado.app.core.ui.bookshelf.dimensionalBookshelfHeight
import io.legado.app.data.repository.CoverAlbumRepository
import io.legado.app.data.repository.SettingsRepository
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.model.settings.BookshelfSettings
import io.legado.app.domain.usecase.CoverAlbumUseCase
import io.legado.app.help.config.AppConfigStore
import io.legado.app.ui.main.bookshelf.BookShelfItem
import io.legado.app.ui.main.bookshelf.BookshelfPage
import io.legado.app.ui.main.bookshelf.BookshelfUiState
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.widget.components.lazylist.FastScrollLazyVerticalGrid
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w720dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BookshelfFastScrollerTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        val application = RuntimeEnvironment.getApplication()
        application.injectAsAppCtx()
        AppConfigStore.init(application)
        stopKoin()
        startKoin {
            modules(module {
                single { CoverAlbumUseCase(CoverAlbumRepository(application, SettingsRepository())) }
            })
        }
    }

    @After
    fun tearDown() = stopKoin()

    @Test
    fun dragFromRestoredGridReachesTopAndContinuesPastFullHeightHeader() {
        val gridState = LazyGridState(firstVisibleItemIndex = 7)
        compose.setContent {
            AppTheme(AppUiConfiguration()) {
                FastScrollLazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    modifier = Modifier.size(320.dp, 260.dp).testTag("grid"),
                    leadingItemHeight = 420.dp,
                ) {
                    item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.height(420.dp))
                    }
                    items(40) { Box(Modifier.height(60.dp)) }
                }
            }
        }

        assertEquals(7, gridState.firstVisibleItemIndex)
        val grid = compose.onNodeWithTag("grid")
        val thumb = compose.onNodeWithTag("fast-scroll-grid-thumb")
        val start = thumb.fetchSemanticsNode().boundsInRoot.center -
            grid.fetchSemanticsNode().boundsInRoot.topLeft
        grid.performTouchInput {
            down(start)
            moveBy(Offset(0f, -start.y - 24f))
        }
        compose.runOnIdle {
            assertEquals(0, gridState.firstVisibleItemIndex)
            assertEquals(0, gridState.firstVisibleItemScrollOffset)
            assertTrue(gridState.layoutInfo.visibleItemsInfo.all { it.index == 0 })
        }
        thumb.assertExists()

        grid.performTouchInput {
            moveBy(Offset(0f, 170f))
            up()
        }
        compose.runOnIdle {
            assertTrue("The same drag must scrub back into the book rows", gridState.firstVisibleItemIndex > 0)
        }
    }

    @Test
    fun bookshelfPageKeepsFirstBookRowAfterReenteringGroup() {
        val gridState = LazyGridState()
        val sample = BookShelfItem(
            bookUrl = "sample",
            name = "Sample",
            author = "Author",
            origin = "origin",
            originName = "Origin",
            coverUrl = null,
            customCoverUrl = null,
            durChapterTitle = null,
            durChapterTime = 0L,
            durChapterPos = 0,
            latestChapterTitle = null,
            latestChapterTime = 0L,
            lastCheckCount = 0,
            totalChapterNum = 1,
            durChapterIndex = 0,
            type = 0,
            group = 0L,
            order = 0,
        )
        val books = (1..10).map { sample.copy(bookUrl = "book:$it", name = "Book $it").toUiItem() }.toImmutableList()
        val settings = BookshelfSettings(bookshelfDimensionalShelf = true, showBookshelfFastScroller = true)
        val shelfState = BookshelfUiState(settings = settings)
        var groupVisible by mutableStateOf(true)
        var expectedHeight = 0
        compose.setContent {
            AppTheme(AppUiConfiguration()) {
                BoxWithConstraints(Modifier.size(400.dp, 280.dp)) {
                    val shelfHeight = dimensionalBookshelfHeight(maxWidth)
                    val height = with(LocalDensity.current) { shelfHeight.roundToPx() + 2 * 8.dp.roundToPx() }
                    SideEffect { expectedHeight = height }
                    if (groupVisible) {
                        BookshelfPage(
                            gridState = gridState,
                            paddingValues = PaddingValues(0.dp),
                            bottomOverlayPadding = 0.dp,
                            books = books,
                            uiState = shelfState,
                            selectedBookUrls = persistentSetOf(),
                            canReorderBooks = false,
                            onToggleBookSelection = {},
                            draggingBooks = null,
                            pendingSavedBooks = null,
                            onDragStarted = {},
                            onMoveBook = { _, _, _ -> },
                            onDragFinished = {},
                            onGlobalSearch = {},
                            onBookClick = { _, _ -> },
                            onBookLongClick = { _, _ -> },
                            onSeriesRename = {},
                            onSeriesManage = {},
                            onSeriesDissolve = { _, _ -> },
                            onSeriesSetCover = { _, _ -> },
                            onSeriesExpandChange = {},
                            sharedCoverGroupId = 1L,
                        )
                    }
                }
            }
        }

        compose.runOnIdle {
            assertEquals(expectedHeight, gridState.layoutInfo.visibleItemsInfo.first { it.index == 0 }.size.height)
            gridState.requestScrollToItem(1)
        }
        compose.runOnIdle {
            assertEquals(1, gridState.firstVisibleItemIndex)
            assertEquals(0, gridState.firstVisibleItemScrollOffset)
            groupVisible = false
        }
        compose.runOnIdle { groupVisible = true }
        compose.runOnIdle {
            assertEquals(1, gridState.firstVisibleItemIndex)
            assertEquals(0, gridState.firstVisibleItemScrollOffset)
        }
    }
}
