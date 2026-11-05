package io.legado.app.ui.widget.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.repository.CoverAlbumRepository
import io.legado.app.data.repository.SettingsRepository
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.usecase.CoverAlbumUseCase
import io.legado.app.help.config.AppConfigStore
import io.legado.app.ui.book.info.BookInfoIntent
import io.legado.app.ui.book.info.BookInfoScreen
import io.legado.app.ui.book.info.BookInfoUiState
import io.legado.app.ui.book.read.ReadAloudCapsule
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.theme.LocalLegadoThemeColors
import io.legado.app.ui.widget.components.button.series.MediumAnimatedButton
import io.legado.app.ui.widget.components.button.series.SmallAnimatedButton
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.reader.ReaderMenuDismissLayer
import kotlinx.collections.immutable.persistentListOf
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
import org.robolectric.annotation.LooperMode
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "en-rUS-w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class AccessibilityRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        AppConfigStore.init(context)
    }

    @Test
    fun animatedButtonsKeepOneNameBeforeDuringAndAfterTextAnimation() {
        var clicks = 0
        show {
            Column {
                SmallAnimatedButton(
                    checked = false, onCheckedChange = { clicks++ }, icon = AppIcons.Search,
                    text = "Visual label", contentDescription = "Search books",
                    modifier = Modifier.testTag("small"),
                )
                MediumAnimatedButton(
                    checked = false, onCheckedChange = { clicks++ }, icon = AppIcons.Search,
                    text = "Search sources", modifier = Modifier.testTag("medium"),
                )
            }
        }
        fun assertNames() {
            compose.onNodeWithTag("small").assertContentDescriptionEquals("Search books").assertHasClickAction()
            compose.onNodeWithTag("medium").assertContentDescriptionEquals("Search sources").assertHasClickAction()
        }
        assertNames()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("small").performClick()
        compose.onNodeWithTag("medium").performClick()
        compose.mainClock.advanceTimeBy(300)
        assertNames()
        compose.mainClock.advanceTimeBy(1500)
        assertNames()
        compose.runOnIdle { assertEquals(2, clicks) }
    }

    @Test
    fun bookInfoReadingActionHasANameInBothEnginesAndStaysHiddenWhileLocked() {
        val engine = mutableStateOf("material")
        val state = mutableStateOf(BookInfoUiState())
        var reads = 0
        show(engine = { engine.value }) {
            BookInfoScreen(
                state = state.value, groups = persistentListOf(), onBack = {},
                onIntent = { if (it == BookInfoIntent.ReadClick) reads++ },
            )
        }
        val reading = RuntimeEnvironment.getApplication().getString(R.string.reading)
        for (value in listOf("material", "miuix")) {
            compose.runOnIdle { engine.value = value }
            compose.onNodeWithContentDescription(reading).assertIsDisplayed().assertHasClickAction().performClick()
        }
        compose.runOnIdle {
            assertEquals(2, reads)
            state.value = state.value.copy(privateLocked = true)
        }
        compose.onNodeWithContentDescription(reading).assertDoesNotExist()
    }

    @Test
    fun menuDismissAreaIsNamedAndInvokesTheDismissCallback() {
        val visible = mutableStateOf(true)
        var dismisses = 0
        show {
            ReaderMenuDismissLayer(visible = visible.value, onDismiss = {
                dismisses++
                visible.value = false
            })
        }
        compose.onNodeWithContentDescription("Close reading menu").assertHasClickAction().performClick()
        compose.onNodeWithContentDescription("Close reading menu").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, dismisses) }
    }

    @Test
    fun expandedFloatingCapsuleNamesTheBookAndKeepsAllPlaybackActions() {
        val context = RuntimeEnvironment.getApplication()
        stopKoin()
        startKoin {
            modules(module {
                single { CoverAlbumUseCase(CoverAlbumRepository(context, SettingsRepository())) }
            })
        }
        val bookName = mutableStateOf<String?>("Book")
        var opens = 0
        var pauses = 0
        var stops = 0
        try {
            show {
                ReadAloudCapsule(
                    bookName = bookName.value, author = null, coverPath = null, sourceOrigin = null,
                    isPaused = true, offsetXDp = 0f, offsetYDp = 0f, progress = 0.5f,
                    autoCollapse = false, onPositionChanged = { _, _ -> },
                    onTogglePause = { pauses++ }, onStop = { stops++ }, onOpenPlayer = { opens++ },
                    onWindowPositionChanged = { _, _, _ -> },
                )
            }
            compose.onNodeWithContentDescription("Open player for Book")
                .assertIsDisplayed().assertHasClickAction().performClick()
            compose.onNodeWithContentDescription(context.getString(R.string.resume_read_aloud)).performClick()
            compose.onNodeWithContentDescription(context.getString(R.string.stop_read_aloud)).performClick()
            compose.runOnIdle {
                assertEquals(1, opens)
                assertEquals(1, pauses)
                assertEquals(1, stops)
                bookName.value = "Next book"
            }
            compose.onNodeWithContentDescription("Open player for Next book").assertHasClickAction()
            compose.onNodeWithContentDescription("Open player for Book").assertDoesNotExist()
            compose.runOnIdle { bookName.value = null }
            compose.onNodeWithContentDescription("Open player").assertHasClickAction()
        } finally {
            stopKoin()
        }
    }

    @Test
    fun reorderReportsCommittedPositionAndRemovesUnavailableMovesAtTheEdges() {
        val index = mutableStateOf(0)
        show {
            Box(Modifier.size(48.dp).testTag("item").reorderAccessibility(
                index = index.value, itemCount = 3, description = "Book",
            ) { from, to ->
                assertEquals(index.value, from)
                index.value = to
            })
        }
        fun position(expected: String, actionCount: Int) {
            val config = compose.onNodeWithTag("item").fetchSemanticsNode().config
            assertEquals(expected, config[SemanticsProperties.StateDescription])
            assertEquals(actionCount, config[SemanticsActions.CustomActions].size)
        }
        fun move(label: Int) {
            val name = RuntimeEnvironment.getApplication().getString(label)
            val action = compose.onNodeWithTag("item").fetchSemanticsNode()
                .config[SemanticsActions.CustomActions].single { it.label == name }
            compose.runOnIdle { assertTrue(action.action()) }
        }
        position("Position 1 of 3", 1)
        move(R.string.a11y_move_down)
        position("Position 2 of 3", 2)
        move(R.string.a11y_move_down)
        position("Position 3 of 3", 1)
        move(R.string.a11y_move_up)
        position("Position 2 of 3", 2)
    }

    private fun show(engine: () -> String = { "material" }, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                AppTheme(AppUiConfiguration(), applyBackground = false) {
                    CompositionLocalProvider(LocalLegadoThemeColors provides
                        LocalLegadoThemeColors.current.copy(composeEngine = engine())) {
                        content()
                    }
                }
            }
        }
    }
}
