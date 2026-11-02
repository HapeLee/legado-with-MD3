package io.legado.app.ui.book.source.manage

import android.app.Application
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.legado.app.R
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.help.config.AppConfigStore
import io.legado.app.ui.theme.AppTheme
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class BookSourceReorderScreenTest {
    @get:Rule val compose = createComposeRule()

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        AppConfigStore.init(context)
    }

    @Test
    fun `consecutive accessible moves use pending order until the repository catches up`() {
        val state = mutableStateOf(
            BookSourceUiState(items = listOf("A", "B", "C").map(::source).toImmutableList())
        )
        val commits = mutableListOf<BookSourceIntent.CommitSortOrder>()
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                AppTheme(AppUiConfiguration(), applyBackground = false) {
                    BookSourceScreen(
                        state = state.value,
                        onIntent = { if (it is BookSourceIntent.CommitSortOrder) commits += it },
                        snackbarHostState = remember { SnackbarHostState() },
                        onBackClick = {}, onAddSource = {}, onEditSource = {},
                        onLoginSource = {}, onSearchSource = { _, _ -> }, onDebugSource = {},
                    )
                }
            }
        }
        moveDown("A")
        moveDown("A")
        compose.runOnIdle {
            assertEquals(listOf("B", "A", "C"), commits.first().ids)
            assertEquals(listOf("B", "C", "A"), commits.last().ids)
            // Deliver only the latest repository state, as StateFlow may conflate intermediate writes.
            state.value = state.value.copy(items = commits.last().ids.map(::source).toImmutableList())
        }
        compose.waitForIdle()
        compose.runOnIdle {
            state.value = state.value.copy(items = listOf("A", "B", "C").map(::source).toImmutableList())
        }
        moveDown("A")
        compose.runOnIdle {
            assertEquals(listOf("B", "A", "C"), commits.last().ids)
        }
    }

    private fun moveDown(id: String) {
        val label = RuntimeEnvironment.getApplication().getString(R.string.a11y_move_down)
        val node = compose.onNode(
            hasAnyDescendant(hasText(id)) and SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)
        ).fetchSemanticsNode()
        compose.runOnIdle {
            assertTrue(node.config[SemanticsActions.CustomActions].single { it.label == label }.action())
        }
    }

    private fun source(id: String) = BookSourceItemUi(
        id = id, domain = "#", name = id, group = null, enabled = true,
        enabledExplore = false, hasLoginUrl = false, hasExploreUrl = false, customOrder = 0,
    )
}
