package io.legado.app.ui.ai

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.theme.LocalLegadoThemeColors
import io.legado.app.ui.theme.MiuixThemeWrapper
import org.junit.Assert.assertEquals
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
class AiReasoningModeButtonTest {
    @get:Rule val compose = createComposeRule()

    private fun dragEveryLevel(engine: String, widths: List<Int> = listOf(390)) {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val level = mutableStateOf(AiReasoningLevel.AUTO)
        val controlWidth = mutableStateOf(widths.first())
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                Box(Modifier.width(controlWidth.value.dp)) {
                    AppTheme(AppUiConfiguration(appShell = AppShellSettings(composeEngine = engine))) {
                        val colors = LocalLegadoThemeColors.current.copy(composeEngine = engine)
                        // Inspection previews always choose Material; explicitly exercise Miuix.
                        CompositionLocalProvider(LocalLegadoThemeColors provides colors) {
                            if (engine == "miuix") MiuixThemeWrapper(colors, null, applyBackground = false) {
                                AiReasoningLevelControl(level.value) { level.value = it }
                            } else AiReasoningLevelControl(level.value) { level.value = it }
                        }
                    }
                }
            }
        }
        val slider = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)).assertIsDisplayed()
        // Pointer input passes through pixel-to-value conversion, unlike SetProgress.
        for (windowWidth in widths) {
            compose.runOnIdle { controlWidth.value = windowWidth }
            for (targetLevel in AiReasoningLevel.entries + AiReasoningLevel.entries.reversed()) {
                val startFraction = (level.value.ordinal.toFloat() / AiReasoningLevel.entries.lastIndex).coerceIn(0.05f, 0.95f)
                slider.performTouchInput {
                    val target = Offset(width * (targetLevel.ordinal.toFloat() / AiReasoningLevel.entries.lastIndex).coerceIn(0.05f, 0.95f), centerY)
                    if (targetLevel == level.value) click(target)
                    else swipe(Offset(width * startFraction, centerY), target, 400)
                }
                compose.runOnIdle { assertEquals("$engine width=$windowWidth drag to $targetLevel", targetLevel, level.value) }
            }
        }
    }

    @Test fun `Material dragging reaches all seven levels in both directions`() = dragEveryLevel("material")

    @Test @Config(qualifiers = "w1001dp-h700dp")
    fun `wide Material dragging reaches all seven levels`() = dragEveryLevel("material", (380..430).toList())

    @Test fun `Miuix dragging reaches all seven levels in both directions`() = dragEveryLevel("miuix")

    @Test @Config(qualifiers = "w1001dp-h700dp")
    fun `wide Miuix dragging reaches all seven levels`() = dragEveryLevel("miuix", (380..430).toList())
}
