package io.legado.app.feature.settings.manga

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import io.legado.app.R
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.ui.config.ConfigNavScreen
import io.legado.app.ui.theme.AppTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import splitties.init.injectAsAppCtx

@Suppress("DEPRECATION") // The JUnit4 state-restoration adapter requires its ComposeContentTestRule.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class MangaConfigScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun row(res: Int): SemanticsNodeInteraction {
        val label = RuntimeEnvironment.getApplication().getString(res)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label))
        return compose.onNodeWithText(label)
    }

    private fun exercise() {
        val app = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val state = mutableStateOf(MangaConfigUiState(translationLoaded = true))
        var openedAi = false
        var openedCache = false
        val restoration = StateRestorationTester(compose)
        restoration.setContent { CompositionLocalProvider(LocalInspectionMode provides true) { AppTheme(AppUiConfiguration()) {
            MangaConfigScreen(state.value, { intent ->
                when (intent) {
                    is MangaConfigIntent.ChangeSettings -> state.value = state.value.copy(settings = intent.transform(state.value.settings))
                    is MangaConfigIntent.SetAutomaticTranslation -> state.value = state.value.copy(automaticTranslation = intent.enabled)
                    else -> Unit
                }
            }, {}, { openedAi = true }, { openedCache = true })
        } } }
        row(R.string.show_manga_ui).assertIsDisplayed()
        row(R.string.manga_config_default_mode).performClick()
        compose.onNodeWithText(app.getString(R.string.manga_reader_right_to_left)).performClick()
        assertEquals(2, state.value.settings.scrollMode)
        row(R.string.manga_reader_double_page).performClick()
        compose.onNodeWithText(app.getString(R.string.manga_reader_mode_always)).performClick()
        row(R.string.manga_reader_double_page_cover_single).assertIsDisplayed()
        row(R.string.manga_reader_pinch_zoom).performClick()
        assertFalse(state.value.settings.disableMangaScale)
        row(R.string.manga_reader_grayscale).performClick()
        assertTrue(state.value.settings.enableGray)
        row(R.string.manga_reader_eink).performClick()
        assertTrue(state.value.settings.enableEInk)
        assertFalse(state.value.settings.enableGray)
        row(R.string.manga_reader_eink_threshold).assertIsDisplayed()
        row(R.string.download_cache_config).performClick()
        assertTrue(openedCache)
        row(R.string.manga_translation_automatic_global).performClick()
        assertTrue(state.value.automaticTranslation)
        compose.onNodeWithText(app.getString(R.string.manga_translation_global_settings)).assertDoesNotExist()
        row(R.string.ai_config).performClick()
        assertTrue(openedAi)
        compose.onNodeWithText(app.getString(R.string.manga_translation_automatic_book)).assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        row(R.string.manga_translation_automatic_global).assertIsOn()
    }

    @Test fun `compact global settings expose reader controls and the shared translation master`() = exercise()
    @Test @Config(qualifiers = "w1000dp-h700dp")
    fun `expanded global settings remain scrollable and survive recreation`() = exercise()
    @Test @Config(qualifiers = "w800dp-h400dp-land")
    fun `landscape global settings keep lower actions reachable`() = exercise()

    @Test fun `settings hub opens manga next to reading and back returns to the hub`() {
        val app = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        compose.setContent { CompositionLocalProvider(LocalInspectionMode provides true) { AppTheme(AppUiConfiguration()) {
            var manga by rememberSaveable { mutableStateOf(false) }
            if (manga) MangaConfigScreen(MangaConfigUiState(), {}, { manga = false }, {}, {})
            else ConfigNavScreen(onBackClick = {}, onNavigateToOther = {}, onNavigateToRead = {},
                onNavigateToManga = { manga = true }, onNavigateToReadAloud = {}, onNavigateToCover = {},
                onNavigateToTheme = {}, onNavigateToBackup = {}, onNavigateToAi = {}, onNavigateToDownloadCache = {},
                onNavigateToTranslation = {}, onNavigateToLab = {}, onNavigateToPrivate = {})
        } } }
        row(R.string.read_config).assertIsDisplayed()
        row(R.string.manga_config).performClick()
        row(R.string.show_manga_ui).assertIsDisplayed()
        compose.onNodeWithContentDescription(app.getString(R.string.back)).performClick()
        row(R.string.read_config).assertIsDisplayed()
    }

    @Test fun `reader shortcut waits for inline options then focuses the translation group`() {
        val app = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val ready = mutableStateOf(false)
        compose.setContent { CompositionLocalProvider(LocalInspectionMode provides true) { AppTheme(AppUiConfiguration()) {
            MangaConfigScreen(MangaConfigUiState(translationLoaded = ready.value), {}, {}, {}, {},
                startAtTranslation = true, translationReady = ready.value)
        } } }
        compose.runOnIdle { ready.value = true }
        compose.waitForIdle()
        compose.onNodeWithText(app.getString(R.string.manga_translation_automatic_global)).assertIsDisplayed()
    }
}
