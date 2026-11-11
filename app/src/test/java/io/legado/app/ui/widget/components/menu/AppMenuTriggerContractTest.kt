package io.legado.app.ui.widget.components.menu

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.widget.components.settingItem.SettingItem
import kotlinx.collections.immutable.persistentListOf
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

/**
 * 三入口的 trigger 契约基线：trigger 必须是纯展示内容，
 * 由主题分支包装出唯一的可点节点，否则 Mochi 库的 Press 手势会与内容自带的
 * `combinedClickable` 相争，TalkBack 也会读到两个可点区域。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w398dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class AppMenuTriggerContractTest {
    @get:Rule val compose = createComposeRule()

    @Composable
    private fun Choice(engine: String, onSelect: (String) -> Unit) {
        AppTheme(AppUiConfiguration(appShell = AppShellSettings(composeEngine = engine))) {
            AppChoiceDropdown(
                options = persistentListOf(
                    AppChoiceOption("one", "One"),
                    AppChoiceOption("two", "Two"),
                ),
                selectedId = "one",
                onSelect = onSelect,
                title = "Theme",
                commitAfterDismiss = true,
            ) {
                SettingItem(
                    title = "Theme",
                    option = "One",
                    modifier = this.modifier,
                    interactive = false,
                )
            }
        }
    }

    private fun showChoice(engine: String, onSelect: (String) -> Unit = {}) {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent { Choice(engine, onSelect) }
    }

    @Test
    fun mochiChoiceTriggerExposesSingleClickableNode() {
        showChoice("mochi")
        compose.onNodeWithText("One", useUnmergedTree = true).assertIsDisplayed()
        // 修复前：库的按钮语义 + SettingItem 的 combinedClickable = 2 个可点节点。
        compose.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun materialChoiceTriggerExposesSingleClickableNode() {
        showChoice("material")
        compose.onNodeWithText("One", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun mochiChoiceTapOpensNativeMenu() {
        showChoice("mochi")
        // 真实触摸（非语义点击）验证库的 Press 手势没被 trigger 内容吃掉。
        compose.onNode(hasClickAction(), useUnmergedTree = true).performTouchInput { click() }
        compose.waitForIdle()
        compose.onNodeWithText("Two", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun mochiChoiceSelectingAnOptionCommitsStableId() {
        var selected: String? = null
        showChoice("mochi") { selected = it }
        compose.onNode(hasClickAction(), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Two", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("two", selected)
    }

    @Test
    fun materialChoiceSelectingAnOptionCommitsStableId() {
        var selected: String? = null
        showChoice("material") { selected = it }
        compose.onNode(hasClickAction(), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Two", useUnmergedTree = true).performClick()
        assertEquals("two", selected)
    }
}
