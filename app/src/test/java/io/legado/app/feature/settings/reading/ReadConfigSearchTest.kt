package io.legado.app.feature.settings.reading

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import io.legado.app.R
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.ui.config.readConfig.ReadConfigIntent
import io.legado.app.ui.config.readConfig.ReadConfigScreen
import io.legado.app.ui.config.readConfig.ReadConfigUiState
import io.legado.app.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

@Suppress("DEPRECATION") // StateRestorationTester uses the JUnit4 ComposeContentTestRule.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ReadConfigSearchTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `reading controls retain their intents and conditional brightness row`() {
        val app = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        val state = mutableStateOf(ReadConfigUiState())
        var lastIntent: ReadConfigIntent? = null
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                AppTheme(AppUiConfiguration()) {
                    ReadConfigScreen(state.value, { lastIntent = it }, {})
                }
            }
        }
        fun row(res: Int): SemanticsNodeInteraction {
            val text = app.getString(res)
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
            return compose.onNodeWithText(text)
        }
        row(R.string.volume_key_page).performClick()
        assertEquals(ReadConfigIntent.VolumeKeyPageChanged(false), lastIntent)
        row(R.string.custom_page_key).performClick()
        assertEquals(ReadConfigIntent.OpenPageKeys, lastIntent)
        row(R.string.show_brightness_view).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.brightness_bar_position)).assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(showBrightnessView = "2") }
        row(R.string.brightness_bar_position).assertIsDisplayed()
        row(R.string.eye_protection).performClick()
        assertEquals(ReadConfigIntent.OpenEyeProtection, lastIntent)
    }

    private fun exerciseTargets(engine: String) {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val target = mutableStateOf(ReadSetting.VolumeKeys.id)
        var lastIntent: ReadConfigIntent? = null
        val restore = StateRestorationTester(compose)
        restore.setContent {
            CompositionLocalProvider(LocalInspectionMode provides false) {
                AppTheme(AppUiConfiguration(appShell = AppShellSettings(composeEngine = engine))) {
                    ReadConfigScreen(ReadConfigUiState(showBrightnessView = "2"), { lastIntent = it }, {}, target.value)
                }
            }
        }
        compose.onNodeWithTag("setting:${ReadSetting.VolumeKeys.id}").assertIsDisplayed()
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.volume_key_page))
            .performClick()
        assertEquals(ReadConfigIntent.VolumeKeyPageChanged(false), lastIntent)
        compose.runOnIdle { target.value = ReadSetting.MenuIcons.id }
        compose.onNodeWithTag("setting:${ReadSetting.MenuIcons.id}").assertIsDisplayed()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("setting:${ReadSetting.MenuIcons.id}").assertIsDisplayed()
        compose.runOnIdle { target.value = ReadSetting.Orientation.id }
        compose.onNodeWithTag("setting:${ReadSetting.Orientation.id}").assertIsDisplayed()
    }

    @Test fun `material targets locate across groups and survive recreation`() = exerciseTargets("material")
    @Test fun `miuix targets keep controls actionable and survive recreation`() = exerciseTargets("miuix")
    @Test @Config(qualifiers = "w1000dp-h700dp")
    fun `expanded reading page locates lower and upper targets`() = exerciseTargets("material")

    @Test fun `hidden brightness position guides to mode without changing settings`() {
        val app = RuntimeEnvironment.getApplication().also { it.injectAsAppCtx() }
        var lastIntent: ReadConfigIntent? = null
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                AppTheme(AppUiConfiguration()) {
                    ReadConfigScreen(ReadConfigUiState(), { lastIntent = it }, {}, ReadSetting.BrightnessPosition.id)
                }
            }
        }
        compose.onNodeWithTag("setting:${ReadSetting.BrightnessMode.id}").assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.settings_search_brightness_guidance,
            app.getString(R.string.brightness_bar_vertical))).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.brightness_bar_position)).assertDoesNotExist()
        assertNull(lastIntent)
    }

    @Test fun `every catalog entry has a real reading control and unique identity`() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                AppTheme(AppUiConfiguration()) {
                    ReadConfigScreen(ReadConfigUiState(showBrightnessView = "2"), {}, {})
                }
            }
        }
        assertEquals(ReadSetting.entries.size, ReadSetting.entries.map { it.id }.toSet().size)
        for (setting in ReadSetting.entries) {
            val tag = "setting:${setting.id}"
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).assertIsDisplayed()
            assertEquals(setting, ReadSetting.fromId(setting.id))
        }
        assertNull(ReadSetting.fromId("unknown"))
        assertEquals(ReadSetting.BrightnessMode, ReadSetting.BrightnessPosition.navigationTarget("0"))
        assertEquals(ReadSetting.BrightnessPosition, ReadSetting.BrightnessPosition.navigationTarget("2"))
        assertTrue(ReadSetting.entries.all { it.id.startsWith("reading.") })
    }
}
