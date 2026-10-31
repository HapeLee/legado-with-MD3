package io.legado.app.feature.manga.translation

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.legado.app.R
import io.legado.app.feature.settings.manga.MangaConfigScreen
import io.legado.app.feature.settings.manga.MangaConfigUiState
import io.legado.app.feature.settings.manga.MangaConfigIntent
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.model.settings.ThemeSettings
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

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class MangaTranslationGlobalSettingsContentTest {
    @get:Rule val compose = createComposeRule()
    private fun row(res: Int): SemanticsNodeInteraction {
        val text = RuntimeEnvironment.getApplication().getString(res)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
        return compose.onNodeWithText(text)
    }

    private fun exercise(theme: ThemeSettings = ThemeSettings()) {
        val app = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val state = mutableStateOf(MangaTranslationUiState(globalMode = true, loaded = true))
        var ai = false
        val config = mutableStateOf(MangaConfigUiState(translationLoaded = true))
        compose.setContent { CompositionLocalProvider(LocalInspectionMode provides true) { AppTheme(AppUiConfiguration(theme = theme)) {
            MangaConfigScreen(config.value, { intent ->
                if (intent is MangaConfigIntent.SetAutomaticTranslation)
                    config.value = config.value.copy(automaticTranslation = intent.enabled)
            }, {}, { ai = true }, {}, translationContent = {
                MangaTranslationGlobalSettingsContent(state.value) { intent ->
                    if (intent is MangaTranslationIntent.ChangeSettings)
                        state.value = state.value.copy(settings = intent.transform(state.value.settings))
                    else error("Unexpected global settings action: $intent")
                }
            }, startAtTranslation = true)
        } } }
        row(R.string.manga_translation_automatic_global).performClick()
        assertTrue(config.value.automaticTranslation)
        row(R.string.manga_translation_target).performClick()
        compose.onNodeWithText(app.getString(R.string.manga_translation_language_traditional)).performClick()
        assertEquals("zh-TW", state.value.settings.targetLanguage)
        row(R.string.manga_translation_font_scale).performClick()
        compose.onNodeWithText("75%").performClick()
        assertEquals(.75f, state.value.settings.fontScale)
        row(R.string.ai_thinking_mode).performClick()
        compose.onNodeWithText(app.getString(R.string.ai_reasoning_level_low)).performClick()
        assertEquals("low", state.value.settings.reasoningLevel)
        row(R.string.ai_config).performClick()
        assertTrue(ai)
        compose.onNodeWithText(app.getString(R.string.manga_translation_global_settings)).assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.manga_translation_clear)).assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.manga_translation_automatic_book)).assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.manga_translation_glossary)).assertDoesNotExist()
        row(R.string.manga_translation_model_relation).performClick()
        compose.onNodeWithText(app.getString(R.string.manga_translation_model_relation_roles)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.manga_translation_model_relation_merge)).assertIsDisplayed()
    }

    @Test fun `compact manga translation group edits language font and reasoning inline`() = exercise()
    @Test @Config(qualifiers = "w1000dp-h700dp")
    fun `expanded manga translation group uses full settings controls`() = exercise()
    @Test @Config(qualifiers = "w800dp-h400dp-land")
    fun `landscape manga settings reaches inline translation options`() = exercise()
    @Test fun `translation groups support dividers borders and configured card corners`() = exercise(ThemeSettings(
        enableItemDivider = true, overrideBaseCardBorder = true, baseCardBorderWidth = 2f,
        overrideBaseCardCornerRadius = true, baseCardCornerRadius = 24f))
}
