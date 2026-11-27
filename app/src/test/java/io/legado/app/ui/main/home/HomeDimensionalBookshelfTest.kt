package io.legado.app.ui.main.home

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeWithVelocity
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.repository.CoverAlbumRepository
import io.legado.app.data.repository.SettingsRepository
import io.legado.app.domain.model.HomeDashboardSection
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.usecase.CoverAlbumUseCase
import io.legado.app.help.config.AppConfigStore
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.theme.LegadoTheme
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
class HomeDimensionalBookshelfTest {
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
    fun shelfHeightStaysFixedWhenCaptionAndPagerChange() {
        val books = listOf(
            HomeRecentBookUi("book:1", "Volume 1", "Author", null, null, null, null),
            HomeRecentBookUi("book:2", "夜航图书馆📚", "", null, null, null, null),
        ).toImmutableList()
        var state by mutableStateOf(
            HomeUiState(
                visibleSections = persistentSetOf(HomeDashboardSection.DimensionalBookshelf),
                dimensionalShelfBooks = books,
            ),
        )

        compose.setContent {
            AppTheme(AppUiConfiguration()) {
                Box(Modifier.width(400.dp)) {
                    HomeDashboardContent(
                        state = state,
                        onIntent = {},
                        onNavigateToReadRecord = {},
                        onNavigateToReadRecordOverview = {},
                        onBookDetails = {},
                    )
                }
            }
        }

        val application = RuntimeEnvironment.getApplication()
        val shelf = compose.onNodeWithContentDescription(application.getString(R.string.dimensional_bookshelf))
        val next = compose.onNodeWithContentDescription(application.getString(R.string.dimensional_bookshelf_next_book))
        val height = shelf.fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithText("Author").assertExists()
        compose.mainClock.autoAdvance = false
        try {
            next.performClick()
            repeat(8) {
                compose.mainClock.advanceTimeBy(80)
                assertEquals(height, shelf.fetchSemanticsNode().boundsInRoot.height, 0f)
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.onNodeWithContentDescription(books.last().name).assertIsSelected()
        compose.onNodeWithText("Author").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(dimensionalShelfBooks = books.takeLast(1).toImmutableList()) }
        assertEquals(height, shelf.fetchSemanticsNode().boundsInRoot.height, 0f)
        next.assertDoesNotExist()
    }

    @Test
    fun shelfFollowsSectionVisibilityAndUsesDashboardBookActions() {
        var state by mutableStateOf(HomeUiState(visibleSections = persistentSetOf()))
        var viewportWidth by mutableStateOf(400.dp)
        var opened: HomeIntent? = null
        var details: HomeRecentBookUi? = null
        val books = (1..18).map { index ->
            HomeRecentBookUi(
                bookUrl = "book:$index",
                name = if (index == 3) "夜航图书馆与失落的星辰地图" else "Volume $index",
                author = "Author $index",
                origin = null,
                coverPath = null,
                chapterTitle = null,
                chapterProgress = null,
            )
        }.toImmutableList()

        compose.setContent {
            AppTheme(AppUiConfiguration(), darkTheme = true) {
                Box(Modifier.width(viewportWidth).background(LegadoTheme.colorScheme.surface)) {
                    HomeDashboardContent(
                        state = state,
                        onIntent = { opened = it },
                        onNavigateToReadRecord = {},
                        onNavigateToReadRecordOverview = {},
                        onBookDetails = { details = it },
                    )
                }
            }
        }

        val shelfLabel = RuntimeEnvironment.getApplication().getString(R.string.dimensional_bookshelf)
        val next = RuntimeEnvironment.getApplication().getString(R.string.dimensional_bookshelf_next_book)
        val previous = RuntimeEnvironment.getApplication().getString(R.string.dimensional_bookshelf_previous_book)
        val shelf = compose.onNodeWithContentDescription(shelfLabel)
        shelf.assertDoesNotExist()
        compose.runOnIdle {
            state = state.copy(visibleSections = persistentSetOf(HomeDashboardSection.DimensionalBookshelf))
        }
        shelf.assertDoesNotExist()
        compose.runOnIdle { state = state.copy(dimensionalShelfBooks = books) }
        val firstBook = compose.onNodeWithContentDescription("Volume 1")
        val secondBook = compose.onNodeWithContentDescription("Volume 2")
        val thirdBook = compose.onNodeWithContentDescription(books[2].name)
        val fourthBook = compose.onNodeWithContentDescription("Volume 4")
        val baseline = fourthBook.fetchSemanticsNode().boundsInRoot.top
        val raisedSecond = secondBook.fetchSemanticsNode().boundsInRoot
        firstBook.assertIsSelected()
        val openFirst = firstBook.fetchSemanticsNode().boundsInRoot
        val compactShelf = shelf.fetchSemanticsNode().boundsInRoot
        assertEquals(400f, compactShelf.width, 1f)
        compose.onNodeWithText("01 / 18").assertExists()
        shelf.performTouchInput {
            click(Offset(openFirst.left - compactShelf.left - 14f, 152f))
        }
        compose.runOnIdle { assertEquals(null, opened) }
        val (coverTitle, title) = compose.onAllNodesWithText("Volume 1", useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.boundsInRoot }.sortedBy { it.top }
        val (coverAuthor, author) = compose.onAllNodesWithText("Author 1", useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.boundsInRoot }.sortedBy { it.top }
        assertTrue(coverTitle.top >= openFirst.top)
        assertTrue(coverAuthor.bottom <= openFirst.bottom)
        val nextButton = compose.onNodeWithContentDescription(next).fetchSemanticsNode().boundsInRoot
        assertEquals(title.left, author.left, 1f)
        assertTrue(title.bottom <= author.top)
        assertTrue(author.bottom <= nextButton.top)
        assertTrue(nextButton.center.x > compactShelf.center.x)
        assertTrue(openFirst.top + 24 < raisedSecond.top)
        assertTrue(raisedSecond.top + 8 < baseline)
        assertTrue(
            "The next book should overlap only a narrow edge of the focused cover",
            openFirst.right - raisedSecond.left in 16f..20f,
        )
        shelf.performTouchInput {
            swipeLeft(startX = width - 2f, endX = width - 54f, durationMillis = 650)
        }
        secondBook.assertIsSelected()
        val openSecond = secondBook.fetchSemanticsNode().boundsInRoot
        val leftHandBook = firstBook.fetchSemanticsNode().boundsInRoot
        val rightHandBook = thirdBook.fetchSemanticsNode().boundsInRoot
        assertEquals(3f, rightHandBook.top - leftHandBook.top, 1f)
        assertTrue(openSecond.top + 24 < leftHandBook.top)
        assertTrue(rightHandBook.top + 8 < baseline)
        val fourthAfterSwipe = fourthBook.fetchSemanticsNode().boundsInRoot
        val fifthAfterSwipe = compose.onNodeWithContentDescription("Volume 5").fetchSemanticsNode().boundsInRoot
        val sixthAfterSwipe = compose.onNodeWithContentDescription("Volume 6").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "The rail must rise as a distant book approaches the focus",
            fourthAfterSwipe.top < baseline - 1f,
        )
        assertEquals(openSecond.size, fourthAfterSwipe.size)
        assertEquals(fourthAfterSwipe.size, sixthAfterSwipe.size)
        assertEquals(
            fifthAfterSwipe.left - fourthAfterSwipe.left,
            sixthAfterSwipe.left - fifthAfterSwipe.left,
            1f,
        )
        assertTrue(
            "Regularly spaced books must follow a progressively sloping rail",
            sixthAfterSwipe.top - fifthAfterSwipe.top > fifthAfterSwipe.top - fourthAfterSwipe.top,
        )
        assertTrue(openSecond.left - leftHandBook.left >= 15f)
        secondBook.performTouchInput { click() }
        secondBook.performSemanticsAction(SemanticsActions.OnLongClick)
        compose.runOnIdle {
            assertEquals(HomeIntent.RecentHistoryBookClick("book:2"), opened)
            assertEquals(books[1], details)
        }
        compose.onNodeWithContentDescription(next).performClick()
        val chineseBook = compose.onNodeWithContentDescription(books[2].name).assertIsSelected()
        val volumePixels = chineseBook.captureToImage().toPixelMap()
        val spineInk = (3 until 11).sumOf { x ->
            (60 until 150).count { y -> volumePixels[x, y].luminance() > 0.3f }
        }
        assertTrue("The spine must draw the title between its number and foot", spineInk > 35)
        compose.onNodeWithContentDescription(next).performClick()
        compose.onNodeWithContentDescription("Volume 4").assertIsSelected()
        compose.runOnIdle { viewportWidth = 680.dp }
        val expandedShelf = shelf.fetchSemanticsNode().boundsInRoot
        assertEquals(680f, expandedShelf.width, 1f)
        compose.onNodeWithContentDescription("Volume 4").assertIsSelected()
        val rightRail = (11..13).map { number ->
            compose.onNodeWithContentDescription("Volume $number").fetchSemanticsNode().boundsInRoot.top
        }
        assertTrue("The visible rail must keep bending past the seventh slot", rightRail.zipWithNext().all { it.first < it.second })
        compose.mainClock.autoAdvance = false
        try {
            shelf.performTouchInput {
                swipeWithVelocity(
                    start = center + Offset(16f, 0f),
                    end = center - Offset(16f, 0f),
                    endVelocity = 780f,
                )
            }
            compose.mainClock.advanceTimeBy(96)
            val earlyCoast = thirdBook.fetchSemanticsNode().boundsInRoot.left
            compose.mainClock.advanceTimeBy(112)
            val lateCoast = thirdBook.fetchSemanticsNode().boundsInRoot.left
            assertTrue(
                "The book bank must keep moving after release ($earlyCoast → $lateCoast)",
                earlyCoast - lateCoast >= 8f,
            )
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.onNodeWithContentDescription("Volume 8").assertIsSelected()
        repeat(10) { compose.onNodeWithContentDescription(next).performClick() }
        val lastBook = compose.onNodeWithContentDescription("Volume 18").assertIsSelected()
        compose.onNodeWithText("18 / 18").assertExists()
        compose.onNodeWithContentDescription(next).performClick()
        firstBook.assertIsSelected()
        compose.onNodeWithContentDescription(previous).performClick()
        lastBook.assertIsSelected()
        shelf.performTouchInput {
            swipeLeft(startX = centerX + 48f, endX = centerX - 48f, durationMillis = 1300)
        }
        firstBook.assertIsSelected()
        shelf.performTouchInput {
            swipeRight(startX = centerX - 48f, endX = centerX + 48f, durationMillis = 1300)
        }
        lastBook.assertIsSelected()
        compose.runOnIdle { state = state.copy(dimensionalShelfBooks = books.takeLast(2).toImmutableList()) }
        lastBook.assertIsSelected()
        compose.onNodeWithContentDescription(next).performClick()
        compose.onNodeWithContentDescription("Volume 17").assertIsSelected()
        compose.onNodeWithContentDescription(previous).performClick()
        lastBook.assertIsSelected()
        compose.runOnIdle { state = state.copy(dimensionalShelfBooks = books.takeLast(1).toImmutableList()) }
        assertEquals(expandedShelf.center.x, lastBook.fetchSemanticsNode().boundsInRoot.center.x, 1f)
        compose.runOnIdle { state = state.copy(visibleSections = persistentSetOf()) }
        shelf.assertDoesNotExist()
    }
}
