package io.legado.app.ui.config.ai

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.legado.app.R
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.ui.theme.AppTheme
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
class AiImageProviderScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun exercise() {
        val app = RuntimeEnvironment.getApplication(); app.injectAsAppCtx()
        val intents = mutableListOf<AiProviderEditIntent>()
        compose.setContent { CompositionLocalProvider(LocalInspectionMode provides true) { AppTheme(AppUiConfiguration()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                AiProviderModelFields(AiProviderModelEditorUi(modelName = "Image", modelId = "gemini-nano-banana-2.1", vision = true, imageGeneration = true, imageEditing = true),
                    { intents += it }, {})
            }
        } } }
        compose.onNodeWithText(app.getString(R.string.ai_capability_image_editing)).performScrollTo().performClick()
        assertTrue(intents.any { it == AiProviderEditIntent.UpdateEditingImageEditing(false) })
        compose.onNodeWithText(app.getString(R.string.ai_context_window)).assertDoesNotExist()
    }
    @Test fun `compact image model fields expose separate output capabilities`() = exercise()
    @Test @Config(qualifiers = "w1000dp-h700dp")
    fun `expanded image model fields keep output controls reachable`() = exercise()
}
