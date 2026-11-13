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
import org.junit.Assert.assertFalse
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

    /**
     * 设置项下拉曾直接使用 M3 `DropdownMenu`，与其他菜单的 `RoundDropdownMenu` 分叉：
     * 圆角 4dp vs 12dp、容器色 surfaceContainer vs surfaceContainerLow、条目间距 0 vs 8dp，
     * 同一页会出现两种弹层。两个入口必须共用同一容器。
     */
    @Test
    fun choiceAndActionMenusShareTheSameContainer() {
        val choice = sourceOf(CHOICE)
        val actions = sourceOf(ACTIONS)
        assertFalse(
            "设置项下拉不应再直接使用 M3 DropdownMenu",
            choice.contains("import androidx.compose.material3.DropdownMenu"),
        )
        assertTrue("设置项下拉应复用统一菜单容器", choice.contains("RoundDropdownMenu("))
        assertTrue("命令菜单应复用同一容器", actions.contains("RoundDropdownMenu("))
    }

    private fun sourceOf(path: String): String {
        var directory = java.io.File("").absoluteFile
        while (directory != null) {
            val candidate = java.io.File(directory, "app/src/main/java/$path")
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
        }
        error("从 ${java.io.File("").absolutePath} 向上找不到 $path")
    }

    private companion object {
        const val CHOICE = "io/legado/app/ui/widget/components/menu/AppChoiceDropdown.kt"
        const val ACTIONS = "io/legado/app/ui/widget/components/menu/AppActionMenu.kt"
    }
}
