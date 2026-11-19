package io.legado.app.ui.main.bookshelf

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.PrivateAccessState
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.model.settings.ThemeSettings
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.theme.LocalBottomOverlayPadding
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
@Config(application = Application::class, sdk = [35], qualifiers = "w398dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class BookshelfSelectionBarTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun keepsTitleAlignedAndRoutesSelectionActionsFromTheFloatingBar() {
        val app = RuntimeEnvironment.getApplication()
        app.injectAsAppCtx()
        val shelf = mutableStateOf(BookshelfUiState(title = "Shelf title"))
        val intents = mutableListOf<BookshelfIntent>()
        val selectedUrls = persistentSetOf("https://example.test/book")
        val footerPadding = 92.dp

        compose.setContent {
            AppTheme(
                AppUiConfiguration(
                    appShell = AppShellSettings(composeEngine = "oxygen"),
                    theme = ThemeSettings(useFlexibleTopAppBar = false),
                ),
                applyBackground = false,
            ) {
                CompositionLocalProvider(LocalBottomOverlayPadding provides footerPadding) {
                    Box(Modifier.fillMaxSize().testTag("shelf-root")) {
                        BookshelfTopBar(
                            uiState = shelf.value,
                            scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior(),
                            isContentScrolled = false,
                            onSearchClick = {},
                            onSearchLongClick = null,
                            onSearchQueryChange = {},
                            onSearchSubmit = {},
                            onClearSearch = {},
                        )
                        BookshelfSelectionOverlay(
                            uiState = shelf.value,
                            onIntent = { intents.add(it) },
                            onExitEditMode = {
                                shelf.value = shelf.value.copy(isEditMode = false, selectedBookUrls = persistentSetOf())
                            },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }

        fun button(id: Int) = compose.onNode(hasClickAction() and hasContentDescription(app.getString(id)))
        fun menuItem(id: Int) = compose.onNodeWithText(app.getString(id))
        val title = compose.onNodeWithText("Shelf title")
        val root = compose.onNodeWithTag("shelf-root")
        val exit = button(R.string.cancel_select)
        val selectAll = button(R.string.select_all)
        val download = button(R.string.action_download)
        fun assertTitleInset() {
            assertEquals(20.dp, title.getUnclippedBoundsInRoot().left - root.getUnclippedBoundsInRoot().left)
        }

        assertTitleInset()
        selectAll.assertDoesNotExist()
        compose.runOnIdle { shelf.value = shelf.value.copy(isEditMode = true) }

        assertTitleInset()
        exit.assertIsDisplayed()
        assertTrue(exit.getUnclippedBoundsInRoot().bottom <= root.getUnclippedBoundsInRoot().bottom - footerPadding)
        download.assertDoesNotExist()
        selectAll.performClick()
        button(R.string.invert_selection).performClick()
        compose.runOnIdle {
            assertEquals(BookshelfIntent.SelectAllVisible, intents[intents.lastIndex - 1])
            assertEquals(BookshelfIntent.InvertVisibleSelection, intents.last())
            shelf.value = shelf.value.copy(
                selectedBookUrls = selectedUrls,
                privateAccess = PrivateAccessState(hasPassword = true),
            )
        }

        download.performClick()
        fun chooseOverflowAction(id: Int) {
            button(R.string.more_menu).performClick()
            menuItem(id).performClick()
        }
        chooseOverflowAction(R.string.move_to_group)
        chooseOverflowAction(R.string.private_mark_book)
        chooseOverflowAction(R.string.delete_selected)
        compose.runOnIdle {
            assertEquals(
                listOf(
                    BookshelfIntent.ShowOverlay(BookshelfOverlay.BatchDownloadConfirmDialog),
                    BookshelfIntent.ShowOverlay(BookshelfOverlay.GroupSelectSheet),
                    BookshelfIntent.SetBooksPrivate(selectedUrls, true),
                    BookshelfIntent.ShowOverlay(BookshelfOverlay.DeleteBooksConfirmDialog),
                ),
                intents.takeLast(4),
            )
        }

        exit.performClick()
        selectAll.assertDoesNotExist()
        assertTitleInset()
    }
}
