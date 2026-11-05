package io.legado.app.ui.book.manga

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import io.legado.app.R
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.feature.manga.translation.MangaTranslationStatus
import io.legado.app.feature.manga.translation.MangaTranslationUiState
import io.legado.app.ui.theme.AppTheme
import kotlinx.collections.immutable.persistentListOf
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
@Config(application = Application::class, sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class MangaTranslationFooterTest {
    @get:Rule val compose = createComposeRule()

    private fun exercise() {
        val app = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val translation = mutableStateOf(MangaTranslationUiState(busy = true, total = 8))
        val reader = mutableStateOf(MangaReaderUiState(
            pages = persistentListOf(MangaReaderItemUi.Page(
                "page", "image", "book", 0, 1, 0, 57, "", "",
            )),
            settings = MangaReaderSettings(
                footerAlignment = 2, hideChapterName = true, hidePageNumberLabel = true,
                hideChapter = true, hideProgress = true,
            ),
        ))
        var opened = 0
        compose.setContent {
            AppTheme(AppUiConfiguration()) {
                Box(Modifier.fillMaxSize()) {
                    MangaFooter(reader.value) {
                        MangaTranslationStatus(translation.value, { opened++ }, modifier = Modifier.widthIn(min = 160.dp), horizontalAlignment =
                            if (reader.value.settings.hideFooter) Alignment.Start else when (reader.value.settings.footerAlignment) {
                                1 -> Alignment.CenterHorizontally
                                2 -> Alignment.End
                                else -> Alignment.Start
                            })
                    }
                }
            }
        }
        val initial = app.getString(R.string.feature_manga_translation_page_progress, 0, 8)
        compose.onNodeWithText(initial).assertIsDisplayed().performClick()
        assertEquals(1, opened)
        val statusBounds = compose.onNodeWithText(initial, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val pageBounds = compose.onNodeWithText("/").getUnclippedBoundsInRoot()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        assertTrue(statusBounds.bottom <= pageBounds.top)
        assertTrue(statusBounds.left > (root.right - root.left) / 2)
        assertTrue(root.bottom - pageBounds.bottom < (root.bottom - root.top) / 4)
        for (alignment in 0..2) {
            compose.runOnIdle {
                reader.value = reader.value.copy(settings = reader.value.settings.copy(footerAlignment = alignment))
            }
            val bounds = compose.onNodeWithText(initial, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val footer = compose.onNodeWithText("/").getUnclippedBoundsInRoot()
            assertTrue(bounds.bottom <= footer.top)
            when (alignment) {
                0 -> assertEquals((root.left + 16.dp).value, bounds.left.value, 1f)
                1 -> assertEquals((root.left + root.right).value / 2, (bounds.left + bounds.right).value / 2, 1f)
                2 -> assertEquals((root.right - 16.dp).value, bounds.right.value, 1f)
            }
        }
        compose.runOnIdle { translation.value = translation.value.copy(done = 3) }
        compose.onNodeWithText(initial).assertDoesNotExist()
        val progress = app.getString(R.string.feature_manga_translation_page_progress, 3, 8)
        compose.onNodeWithText(progress).assertIsDisplayed()
        // Hiding the reading footer must not hide an active translation or its error entry.
        compose.runOnIdle {
            reader.value = reader.value.copy(settings = reader.value.settings.copy(hideFooter = true))
            translation.value = translation.value.copy(busy = false, error = "failed")
        }
        compose.onNodeWithText("/").assertDoesNotExist()
        val failed = app.getString(R.string.feature_manga_translation_footer_failed) + " · " + progress
        compose.onNodeWithText(failed).assertIsDisplayed().performClick()
        assertEquals(2, opened)
        val hiddenFooterBounds = compose.onNodeWithText(failed, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals((root.left + 16.dp).value, hiddenFooterBounds.left.value, 1f)
        assertTrue(root.bottom - hiddenFooterBounds.bottom < (root.bottom - root.top) / 4)
        compose.runOnIdle { translation.value = translation.value.copy(error = null) }
        compose.onNodeWithText(failed).assertDoesNotExist()
    }

    @Test fun `compact footer counts pages and keeps progress above reading footer`() = exercise()

    @Test @Config(qualifiers = "w1000dp-h700dp")
    fun `expanded footer counts pages and keeps progress above reading footer`() = exercise()
}
