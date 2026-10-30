package io.legado.app.feature.manga.translation

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import io.legado.app.R
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.manga.*
import io.legado.app.ui.theme.AppTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
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
class MangaTranslationSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `inherited book opens global settings and only reveals custom controls when disabled`() {
        val app = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val state = mutableStateOf(MangaTranslationUiState(open = true, loaded = true, useGlobalSettings = true))
        var opened = false
        compose.setContent { CompositionLocalProvider(LocalInspectionMode provides true) { AppTheme(AppUiConfiguration()) {
            MangaTranslationSheet(state.value, onOpenGlobalSettings = { opened = true }) { intent ->
                if (intent is MangaTranslationIntent.SetUseGlobalSettings) state.value = state.value.copy(useGlobalSettings = intent.enabled)
            }
        } } }
        compose.onNodeWithText(app.getString(R.string.manga_translation_source)).assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.manga_translation_global_settings)).performScrollTo().performClick()
        assertTrue(opened)
        compose.onNodeWithText(app.getString(R.string.manga_translation_use_global)).performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.manga_translation_source)).performScrollTo().assertIsDisplayed()
    }

    @Test fun `glossary list supports adding editing deleting and saving entries`() {
        val app = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val draft = mutableStateOf("")
        var saved = ""
        compose.setContent { CompositionLocalProvider(LocalInspectionMode provides true) { AppTheme(AppUiConfiguration()) {
            MangaGlossarySheet(true, draft.value, true, {}, { draft.value = it }, { saved = draft.value })
        } } }
        compose.onNodeWithText(app.getString(R.string.manga_glossary_add)).performClick()
        compose.onNodeWithText(app.getString(R.string.manga_glossary_source)).performTextInput("Alice")
        compose.onNodeWithText(app.getString(R.string.manga_glossary_target)).performTextInput("Alias")
        compose.onNodeWithText(app.getString(R.string.manga_glossary_apply)).performClick()
        compose.onNodeWithText("Alice").assertExists().performClick()
        compose.onNodeWithText(app.getString(R.string.manga_glossary_target)).performTextReplacement("Updated")
        compose.onNodeWithText(app.getString(R.string.manga_glossary_apply)).performClick()
        compose.onNodeWithText(app.getString(R.string.manga_translation_save_glossary)).performClick()
        assertTrue(saved == "Alice=Updated")
        compose.onNodeWithText("Alice").performClick()
        compose.onNodeWithText(app.getString(R.string.delete)).performClick()
        assertTrue(draft.value.isEmpty())
    }

    @Test fun `automatic switches stay available while busy and change independent scopes`() {
        val app = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val state = mutableStateOf(MangaTranslationUiState(open = true, loaded = true, busy = true))
        val intents = mutableListOf<MangaTranslationIntent>()
        compose.setContent { CompositionLocalProvider(LocalInspectionMode provides true) { AppTheme(AppUiConfiguration()) {
            MangaTranslationSheet(state.value) { intent ->
                intents += intent
                when (intent) {
                    is MangaTranslationIntent.ChangeSettings -> state.value = state.value.copy(settings = intent.transform(state.value.settings))
                    is MangaTranslationIntent.ChangeGlobalSettings -> state.value = state.value.copy(globalSettings = intent.transform(state.value.globalSettings))
                    else -> Unit
                }
            }
        } } }
        val master = compose.onNodeWithText(app.getString(R.string.manga_translation_automatic_global))
        val book = compose.onNodeWithText(app.getString(R.string.manga_translation_automatic_book))
        master.assertDoesNotExist()
        book.performScrollTo().assertIsEnabled().performClick()
        assertTrue(state.value.settings.automatic)
        assertTrue(!state.value.globalSettings.automatic)
        assertTrue(intents.none { it is MangaTranslationIntent.ChangeGlobalSettings })
        compose.onNodeWithText("自动翻译当前页").assertDoesNotExist()
        compose.onNodeWithText("显示译图").assertDoesNotExist()
    }

    private fun settingsPreview() {
        val app = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        compose.setContent { CompositionLocalProvider(LocalInspectionMode provides true) { AppTheme(AppUiConfiguration()) {
            MangaTranslationSheet(MangaTranslationUiState(open = true, loaded = true, chapterPages = 8)) {}
        } } }
        compose.onNodeWithText(app.getString(R.string.manga_translation_source)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.manga_translation_target)).performScrollTo().assertIsDisplayed()
    }
    @Test fun `compact settings preview keeps language dropdowns visible`() = settingsPreview()
    @Test @Config(qualifiers = "w1000dp-h700dp")
    fun `expanded settings preview keeps language dropdowns visible`() = settingsPreview()

    private fun exercise() {
        val app = RuntimeEnvironment.getApplication()
        app.injectAsAppCtx()
        val state = mutableStateOf(MangaTranslationUiState(open = true, loaded = true,
            currentPageAvailable = true, currentPageKey = "p", chapterPages = 8,
            images = persistentMapOf("p" to MangaTranslatedImageUi("/saved.png", "saved", true)),
            ai = MangaTranslationAiProgress("request", "Model", reasoning = "Provider thought fixture", running = false,
                reasoningParameters = "{\"thinking\":{\"type\":\"disabled\"}}", reasoningDisabledRequested = true),
            models = persistentListOf(MangaTranslationModelUi("vision", "Vision model", true))))
        val intents = mutableListOf<MangaTranslationIntent>()
        compose.setContent { CompositionLocalProvider(LocalInspectionMode provides true) { AppTheme(AppUiConfiguration()) {
            MangaTranslationSheet(state.value) { intent ->
                intents += intent
                if (intent is MangaTranslationIntent.ChangeSettings) state.value = state.value.copy(settings = intent.transform(state.value.settings))
            }
        } } }
        compose.onNodeWithText(app.getString(R.string.manga_translation_title)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.manga_translation_reasoning_parameters, "{\"thinking\":{\"type\":\"disabled\"}}"))
            .assertDoesNotExist()
        compose.onNodeWithContentDescription(app.getString(R.string.feature_manga_translation_diagnostics)).performClick()
        compose.onNodeWithText(app.getString(R.string.manga_translation_reasoning_parameters, "{\"thinking\":{\"type\":\"disabled\"}}"))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.manga_translation_reasoning_disable_ignored)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.manga_translation_reasoning_not_declared)).assertDoesNotExist()
        compose.onNodeWithText("Provider thought fixture").assertDoesNotExist()
        compose.onNodeWithText("思考").performScrollTo().performClick()
        compose.onNodeWithText("Provider thought fixture").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.back)).performClick()
        compose.onNodeWithText(app.getString(R.string.manga_translation_current)).assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.manga_translation_retry_current)).assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.manga_translation_recompose)).assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.manga_translation_recompose_chapter)).performScrollTo().performClick()
        assertTrue(intents.any { it is MangaTranslationIntent.Recompose })
        compose.onNodeWithText(app.getString(R.string.manga_translation_composition)).performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.manga_translation_ai_page)).performClick()
        compose.onNodeWithContentDescription(app.getString(R.string.ai_thinking_mode)).assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.manga_translation_image_model)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.manga_translation_chapter, 8)).performScrollTo().performClick()
        assertTrue(intents.any { it is MangaTranslationIntent.Translate })
    }

    private fun languageAndCleanupControls() {
        val app = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val state = mutableStateOf(MangaTranslationUiState(open = true, loaded = true))
        val intents = mutableListOf<MangaTranslationIntent>()
        compose.setContent { CompositionLocalProvider(LocalInspectionMode provides true) { AppTheme(AppUiConfiguration()) {
            MangaTranslationSheet(state.value) { intent ->
                intents += intent
                if (intent is MangaTranslationIntent.ChangeSettings) state.value = state.value.copy(settings = intent.transform(state.value.settings))
            }
        } } }
        compose.onNodeWithText(app.getString(R.string.manga_translation_source)).performScrollTo().performClick()
        compose.onNodeWithText(java.util.Locale.forLanguageTag("ko").getDisplayName(java.util.Locale.getDefault())).performClick()
        assertTrue(state.value.settings.sourceLanguage == "ko")
        compose.onNodeWithText(app.getString(R.string.manga_translation_target)).performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.manga_translation_language_traditional)).performClick()
        assertTrue(state.value.settings.targetLanguage == "zh-TW")
        compose.onNodeWithText(app.getString(R.string.manga_translation_glossary_hint)).assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.manga_translation_glossary)).performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.manga_translation_save_glossary)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.manga_translation_clear)).performScrollTo().performClick()
        assertTrue(MangaTranslationIntent.Clear !in intents)
        compose.onNodeWithText(app.getString(R.string.manga_translation_clear_confirm)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.delete)).performClick()
        assertTrue(MangaTranslationIntent.Clear in intents)
    }
    @Test fun `compact languages use dropdowns and cleanup requires confirmation`() = languageAndCleanupControls()
    @Test @Config(qualifiers = "w1000dp-h700dp")
    fun `expanded languages use dropdowns and cleanup requires confirmation`() = languageAndCleanupControls()

    private fun ocrControls() {
        val app = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val state = mutableStateOf(MangaTranslationUiState(open = true, loaded = true,
            settings = MangaTranslationSettings(locator = MangaLocator.LOCAL, localOcrEngine = MangaLocalOcrEngine.PADDLE_V5),
            ocrModels = persistentMapOf(MangaLocalOcrEngine.PADDLE_V5 to MangaOcrModelState(MangaOcrModelPhase.MISSING, totalBytes = 21_509_645))))
        val intents = mutableListOf<MangaTranslationIntent>()
        compose.setContent { CompositionLocalProvider(LocalInspectionMode provides true) { AppTheme(AppUiConfiguration()) {
            MangaTranslationSheet(state.value) { intent ->
                intents += intent
                if (intent is MangaTranslationIntent.ChangeSettings) state.value = state.value.copy(settings = intent.transform(state.value.settings))
            }
        } } }
        compose.onNodeWithText(app.getString(R.string.manga_ocr_download)).performScrollTo().performClick()
        assertTrue(intents.contains(MangaTranslationIntent.DownloadOcr(MangaLocalOcrEngine.PADDLE_V5)))
        state.value = state.value.copy(ocrModels = persistentMapOf(MangaLocalOcrEngine.PADDLE_V5 to MangaOcrModelState(MangaOcrModelPhase.DOWNLOADING, 1024, 21_509_645)), ocrOperation = MangaLocalOcrEngine.PADDLE_V5)
        // Switching engines must not hide the running download's cancellation control.
        state.value = state.value.copy(settings = state.value.settings.copy(localOcrEngine = MangaLocalOcrEngine.ML_KIT))
        compose.onNodeWithText(app.getString(R.string.manga_ocr_downloading)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.cancel)).performScrollTo().performClick()
        assertTrue(intents.contains(MangaTranslationIntent.CancelOcrDownload(MangaLocalOcrEngine.PADDLE_V5)))
        state.value = state.value.copy(settings = state.value.settings.copy(localOcrEngine = MangaLocalOcrEngine.PADDLE_V5),
            ocrModels = persistentMapOf(MangaLocalOcrEngine.PADDLE_V5 to MangaOcrModelState(MangaOcrModelPhase.READY, 21_509_645, 21_509_645)), ocrOperation = null)
        compose.onNodeWithText(app.getString(R.string.manga_ocr_delete)).performScrollTo().performClick()
        assertTrue(intents.contains(MangaTranslationIntent.DeleteOcr(MangaLocalOcrEngine.PADDLE_V5)))
        state.value = state.value.copy(ocrModels = (state.value.ocrModels + (MangaLocalOcrEngine.PADDLE_V6_SMALL to
            MangaOcrModelState(MangaOcrModelPhase.MISSING, totalBytes = 31_190_469))).toImmutableMap())
        compose.onNodeWithText(app.getString(R.string.manga_ocr_local_engine)).performScrollTo().performClick()
        compose.onNodeWithText("PP-OCRv6 Small").performClick()
        compose.onNodeWithText(app.getString(R.string.manga_ocr_v6_small_hint)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.manga_ocr_download)).performScrollTo().performClick()
        assertTrue(intents.contains(MangaTranslationIntent.DownloadOcr(MangaLocalOcrEngine.PADDLE_V6_SMALL)))
        assertTrue(state.value.settings.localOcrEngine == MangaLocalOcrEngine.PADDLE_V6_SMALL)
        assertTrue(state.value.ocrModels.getValue(MangaLocalOcrEngine.PADDLE_V5).phase == MangaOcrModelPhase.READY)
    }

    @Test fun `compact OCR controls support download cancel and deletion`() = ocrControls()
    @Test @Config(qualifiers = "w1000dp-h700dp")
    fun `expanded OCR controls support download cancel and deletion`() = ocrControls()

    @Test fun `compact sheet scrolls to image editing and dispatches translate`() = exercise()

    @Test
    @Config(qualifiers = "w1000dp-h700dp")
    fun `expanded sheet keeps controls reachable`() = exercise()

    private fun reasoningControl() {
        val app = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val state = mutableStateOf(MangaTranslationUiState(open = true, loaded = true, useGlobalSettings = true))
        compose.setContent { CompositionLocalProvider(LocalInspectionMode provides true) { AppTheme(AppUiConfiguration()) {
            MangaTranslationSheet(state.value) { intent ->
                if (intent is MangaTranslationIntent.ChangeSettings) state.value = state.value.copy(settings = intent.transform(state.value.settings))
            }
        } } }
        compose.onNodeWithContentDescription(app.getString(R.string.ai_thinking_mode)).performClick()
        compose.onNodeWithText(app.getString(R.string.ai_thinking_off)).assertIsDisplayed()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).performSemanticsAction(SemanticsActions.SetProgress) {
            it(AiReasoningLevel.LOW.ordinal.toFloat())
        }
        compose.onNodeWithText(app.getString(R.string.ai_reasoning_level_low)).assertIsDisplayed()
        compose.runOnIdle { assertTrue(state.value.settings.reasoningLevel == AiReasoningLevel.LOW.storageValue) }
    }

    @Test fun `compact manga sheet uses the shared reasoning strength selector`() = reasoningControl()

    @Test @Config(qualifiers = "w1000dp-h700dp")
    fun `expanded manga sheet uses the shared reasoning strength selector`() = reasoningControl()

    private fun cloudControls() {
        val app = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val state = mutableStateOf(MangaTranslationUiState(open = true, loaded = true,
            settings = MangaTranslationSettings(locator = MangaLocator.CLOUD, cloudOcrProvider = MangaCloudOcrProvider.PADDLE),
            cloudProvider = MangaCloudOcrProvider.PADDLE))
        val intents = mutableListOf<MangaTranslationIntent>()
        compose.setContent { CompositionLocalProvider(LocalInspectionMode provides true) { AppTheme(AppUiConfiguration()) {
            MangaTranslationSheet(state.value) { intent ->
                intents += intent
                if (intent is MangaTranslationIntent.EditCloudOcr) state.value = state.value.copy(cloudDraft = intent.transform(state.value.cloudDraft))
            }
        } } }
        compose.onNodeWithText(app.getString(R.string.manga_translation_connection)).performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.manga_cloud_ocr_token)).performScrollTo().performTextInput("fake-token")
        compose.onNodeWithText(app.getString(R.string.manga_cloud_ocr_save)).performScrollTo().performClick()
        assertTrue(intents.contains(MangaTranslationIntent.SaveCloudOcr))
        assertTrue(state.value.cloudDraft.apiKey == "fake-token")
        compose.onNodeWithText(app.getString(R.string.manga_cloud_ocr_provider)).performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.manga_cloud_ocr_baidu)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.manga_cloud_ocr_tencent)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.manga_cloud_ocr_server)).assertIsDisplayed()
    }

    @Test fun `compact cloud OCR settings expose independent providers and save credentials`() = cloudControls()
    @Test @Config(qualifiers = "w1000dp-h700dp")
    fun `expanded cloud OCR settings expose independent providers and save credentials`() = cloudControls()

    private fun ocrAdvancedControls() {
        val app = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val state = mutableStateOf(MangaTranslationUiState(open = true, loaded = true,
            settings = MangaTranslationSettings(locator = MangaLocator.LOCAL, localOcrEngine = MangaLocalOcrEngine.PADDLE_V6_SMALL)))
        compose.setContent { CompositionLocalProvider(LocalInspectionMode provides true) { AppTheme(AppUiConfiguration()) {
            MangaTranslationSheet(state.value) { intent ->
                if (intent is MangaTranslationIntent.ChangeSettings) state.value = state.value.copy(settings = intent.transform(state.value.settings))
            }
        } } }
        compose.onNodeWithText(app.getString(R.string.manga_ocr_detection_profile)).performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.manga_ocr_profile_accurate)).performClick()
        assertTrue(state.value.settings.ocrOptions.detectionProfile == MangaOcrDetectionProfile.ACCURATE)
        compose.onNodeWithText(app.getString(R.string.manga_ocr_advanced)).performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.manga_ocr_custom_detection)).performScrollTo().performClick()
        assertTrue(state.value.settings.ocrOptions.boxScoreThreshold == .45f)
        compose.onNodeWithText(app.getString(R.string.manga_ocr_probability_threshold), useUnmergedTree = true).performScrollTo().performClick()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(37f) }
        compose.onNodeWithText(app.getString(R.string.manga_ocr_probability_threshold), useUnmergedTree = true).performScrollTo().performClick()
        org.junit.Assert.assertEquals("Probability must commit the displayed slider value", .37f, state.value.settings.ocrOptions.probabilityThreshold!!, .00001f)
        compose.onNodeWithText(app.getString(R.string.manga_ocr_upscale)).performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.manga_ocr_otsu)).performScrollTo().performClick()
        assertTrue(state.value.settings.ocrOptions.upscale2x && state.value.settings.ocrOptions.binarize)
        compose.onNodeWithText(app.getString(R.string.manga_ocr_reset_options)).performScrollTo().performClick()
        assertTrue(state.value.settings.ocrOptions == MangaOcrOptions())
    }

    @Test fun `compact OCR advanced settings edit real thresholds and reset options`() = ocrAdvancedControls()
    @Test @Config(qualifiers = "w1000dp-h700dp")
    fun `expanded OCR advanced settings edit real thresholds and reset options`() = ocrAdvancedControls()

    private fun aiGeometryControls() {
        val app = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val state = mutableStateOf(MangaTranslationUiState(open = true, loaded = true,
            settings = MangaTranslationSettings(), currentPageKey = "p", diagnosticPath = "/fixture.png",
            images = persistentMapOf("p" to MangaTranslatedImageUi("/saved.png", "revision", true))))
        compose.setContent { CompositionLocalProvider(LocalInspectionMode provides true) { AppTheme(AppUiConfiguration()) {
            MangaTranslationSheet(state.value) { intent ->
                if (intent is MangaTranslationIntent.ChangeSettings) state.value = state.value.copy(settings = intent.transform(state.value.settings))
            }
        } } }
        assertTrue(state.value.currentPageCanRecompose)
        compose.onNodeWithText(app.getString(R.string.manga_translation_advanced)).performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.manga_ai_geometry_diagnostics)).performScrollTo().performClick()
        assertTrue(state.value.settings.geometryDiagnostics)
        compose.onNodeWithText(app.getString(R.string.manga_ai_geometry_preview)).performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.manga_ai_geometry_legend)).performScrollTo().assertIsDisplayed()
    }
    @Test fun `compact AI detail and diagnostic controls preserve published page`() = aiGeometryControls()
    @Test @Config(qualifiers = "w1000dp-h700dp")
    fun `expanded AI detail and diagnostic controls preserve published page`() = aiGeometryControls()

    private fun aiTypesettingControls() {
        val app = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val state = mutableStateOf(MangaTranslationUiState(open = true, loaded = true,
            currentPageKey = "p", images = persistentMapOf("p" to MangaTranslatedImageUi("/saved.png", "saved", true))))
        compose.setContent { CompositionLocalProvider(LocalInspectionMode provides true) { AppTheme(AppUiConfiguration()) {
            MangaTranslationSheet(state.value) { intent ->
                if (intent is MangaTranslationIntent.ChangeSettings) state.value = state.value.copy(settings = intent.transform(state.value.settings))
            }
        } } }
        for (title in listOf("AI 识别精细度", "AI recognition detail", "每批精读图片数",
            "Images per refinement request", "AI 识字与排版", "AI recognition and typesetting"))
            compose.onNodeWithText(title).assertDoesNotExist()
        assertTrue(state.value.settings.usesAiTypesetting && state.value.currentPageCanRecompose)
        compose.onNodeWithText(app.getString(R.string.manga_ai_layout_hint)).assertDoesNotExist()

    }
    @Test fun `compact AI typesetting mode hides crop refinement and keeps the published image`() = aiTypesettingControls()
    @Test @Config(qualifiers = "w1000dp-h700dp")
    fun `expanded AI typesetting mode hides crop refinement and keeps the published image`() = aiTypesettingControls()

    private fun inpaintControls() {
        val app = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val state = mutableStateOf(MangaTranslationUiState(open = true, loaded = true,
            settings = MangaTranslationSettings(inpaintEngine = MangaInpaintEngine.MI_GAN),
            inpaintModels = persistentMapOf(MangaInpaintEngine.MI_GAN to MangaInpaintModelState(MangaInpaintModelPhase.MISSING, totalBytes = 28_079_181)),
            currentPageKey = "p", images = persistentMapOf("p" to MangaTranslatedImageUi("/saved.png", "saved", true))))
        val intents = mutableListOf<MangaTranslationIntent>()
        compose.setContent { CompositionLocalProvider(LocalInspectionMode provides true) { AppTheme(AppUiConfiguration()) {
            MangaTranslationSheet(state.value) { intent ->
                intents += intent
                if (intent is MangaTranslationIntent.ChangeSettings) state.value = state.value.copy(settings = intent.transform(state.value.settings))
            }
        } } }
        compose.onNodeWithText(app.getString(R.string.manga_inpaint_engine)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.manga_inpaint_download)).performScrollTo().performClick()
        assertTrue(intents.contains(MangaTranslationIntent.DownloadInpaint(MangaInpaintEngine.MI_GAN)))
        state.value = state.value.copy(inpaintModels = persistentMapOf(MangaInpaintEngine.MI_GAN to MangaInpaintModelState(MangaInpaintModelPhase.DOWNLOADING, 100, 1000)),
            inpaintOperation = MangaInpaintEngine.MI_GAN, settings = state.value.settings.copy(inpaintEngine = MangaInpaintEngine.LOCAL))
        compose.onNodeWithText(app.getString(R.string.manga_inpaint_downloading)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.cancel)).performScrollTo().performClick()
        assertTrue(intents.contains(MangaTranslationIntent.CancelInpaintDownload(MangaInpaintEngine.MI_GAN)))
        state.value = state.value.copy(inpaintOperation = null)
        compose.onNodeWithText(app.getString(R.string.manga_inpaint_engine)).performScrollTo().performClick()
        compose.onNodeWithText("LaMa").performClick()
        assertTrue(state.value.settings.inpaintEngine == MangaInpaintEngine.LAMA && state.value.currentPageCanRecompose)
        compose.onNodeWithText(app.getString(R.string.manga_inpaint_lama_hint)).performScrollTo().assertIsDisplayed()
    }
    @Test fun `compact inpainting selection and downloads retain the published page`() = inpaintControls()
    @Test @Config(qualifiers = "w1000dp-h700dp")
    fun `expanded inpainting selection and downloads retain the published page`() = inpaintControls()

}
