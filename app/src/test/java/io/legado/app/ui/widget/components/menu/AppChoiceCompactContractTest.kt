package io.legado.app.ui.widget.components.menu

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
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
 * `AppChoiceDropdown(compact = true)` 的契约基线：紧凑图标按钮形态下 trigger 仍是
 * 唯一可点节点、稳定 ID 回调、Miuix 的 `DropdownItem.selected` 选中态，以及
 * `compact` 不改变既有 Material 分支行为；带文字/卡片的自定义 trigger 也不会被
 * 库渲染的图标按钮替代。`CharsetConfigSheet` 是本形态的首个生产调用方。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w398dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class AppChoiceCompactContractTest {
    @get:Rule val compose = createComposeRule()

    @Composable
    private fun Menu(onSelect: (String) -> Unit) {
        AppChoiceDropdown(
            options = persistentListOf(
                AppChoiceOption("utf8", "UTF-8"),
                AppChoiceOption("gbk", "GBK"),
                AppChoiceOption("big5", "BIG5"),
            ),
            selectedId = "utf8",
            onSelect = onSelect,
            title = "Charset",
            compact = true,
        ) {
            AppIcon(
                imageVector = AppIcons.MoreVert,
                contentDescription = "Charset",
                modifier = this.modifier,
            )
        }
    }

    @Composable
    private fun Host(engine: String, withHost: Boolean, onSelect: (String) -> Unit) {
        AppTheme(AppUiConfiguration(appShell = AppShellSettings(composeEngine = engine))) {
            if (withHost) {
                AppScaffold(topBar = {}) { Menu(onSelect) }
            } else {
                Menu(onSelect)
            }
        }
    }

    private fun show(
        engine: String,
        withHost: Boolean = false,
        onSelect: (String) -> Unit = {},
    ) {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent { Host(engine, withHost, onSelect) }
    }

    private fun openMenu() {
        compose.onNode(hasClickAction(), useUnmergedTree = true).performClick()
        compose.waitForIdle()
    }

    /** 复刻 `SourceEditOptionCard` / `GlassCard` 这类带文字的纯展示 trigger。 */
    @Composable
    private fun CardMenu(onSelect: (String) -> Unit) {
        AppChoiceDropdown(
            options = persistentListOf(
                AppChoiceOption("0", "小说"),
                AppChoiceOption("1", "漫画"),
            ),
            selectedId = "0",
            onSelect = onSelect,
            title = "书源类型",
            compact = true,
        ) {
            Text(text = "类型：小说", modifier = this.modifier)
        }
    }

    private fun showCard(engine: String, onSelect: (String) -> Unit = {}) {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent {
            AppTheme(AppUiConfiguration(appShell = AppShellSettings(composeEngine = engine))) {
                CardMenu(onSelect)
            }
        }
    }

    /** `CloudTtsScreen` 定下的形态：Tonal 按钮只展示，不带自己的点击节点。 */
    @Composable
    private fun TonalDisplayMenu(onSelect: (String) -> Unit) {
        AppChoiceDropdown(
            options = persistentListOf(
                AppChoiceOption("neural", "neural"),
                AppChoiceOption("standard", "standard"),
            ),
            selectedId = "neural",
            onSelect = onSelect,
            title = "模型：neural",
            modifier = Modifier.fillMaxWidth(),
            compact = true,
        ) {
            MediumTonalButton(
                onClick = null,
                text = "模型：neural",
                modifier = this.modifier.fillMaxWidth(),
            )
        }
    }

    @Test
    fun tonalDisplayTriggerKeepsSingleClickableNodeAndCommitsId() {
        var selected: String? = null
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent {
            AppTheme(AppUiConfiguration(appShell = AppShellSettings(composeEngine = "material"))) {
                TonalDisplayMenu { selected = it }
            }
        }
        compose.onAllNodesWithText("模型：neural", useUnmergedTree = true).onFirst().assertExists()
        compose.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
        openMenu()
        compose.onNodeWithText("standard", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("standard", selected)
    }

    /**
     * 受控弹层模式：不渲染 trigger 包装，弹层锚在调用方给的容器上
     * （设置行里就是右侧取值区，这里用等价的 48dp `Box` 模拟）。
     */
    @Composable
    private fun EmbeddedMenu(expanded: Boolean, onSelect: (String) -> Unit) {
        Box(Modifier.size(48.dp)) {
            AppChoiceDropdown(
                options = persistentListOf(
                    AppChoiceOption("all", "全部"),
                    AppChoiceOption("g1", "分组一"),
                ),
                selectedId = "all",
                onSelect = onSelect,
                title = "分组筛选",
                modifier = Modifier.matchParentSize(),
                embeddedExpanded = expanded,
                onDismissRequest = {},
            )
        }
    }

    /**
     * 受控模式要按真实时序走一遍：设置行初始是关闭的，点击行才把 `expanded` 置 true。
     * 首次组合就 `show = true` 时 Miuix 的 popup 还没量到锚点，弹层不会出现。
     */
    @Test
    fun embeddedModeRendersPopupAndCommitsIdWithoutTrigger() {
        var selected: String? = null
        var expanded by mutableStateOf(false)
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent {
            AppTheme(AppUiConfiguration(appShell = AppShellSettings(composeEngine = "material"))) {
                EmbeddedMenu(expanded) { selected = it }
            }
        }
        compose.runOnIdle { expanded = true }
        compose.waitForIdle()
        // 受控模式没有任何 trigger 内容，因此不存在「入口按钮」这一层
        compose.onAllNodesWithText("分组筛选", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithText("分组一", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("g1", selected)
    }

    @Test
    fun embeddedModeRendersPopupOnMiuix() {
        var selected: String? = null
        var expanded by mutableStateOf(false)
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent {
            AppTheme(AppUiConfiguration(appShell = AppShellSettings(composeEngine = "miuix"))) {
                EmbeddedMenu(expanded) { selected = it }
            }
        }
        compose.runOnIdle { expanded = true }
        compose.waitForIdle()
        compose.onNodeWithText("分组一", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("g1", selected)
    }

    /** Mochi 分支在受控模式下没有 trigger 可上报位置，锚点矩形取自调用方容器。 */
    @Test
    fun embeddedModeRendersPopupOnMochi() {
        var selected: String? = null
        var expanded by mutableStateOf(false)
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent {
            AppTheme(AppUiConfiguration(appShell = AppShellSettings(composeEngine = "mochi"))) {
                EmbeddedMenu(expanded) { selected = it }
            }
        }
        compose.runOnIdle { expanded = true }
        compose.waitForIdle()
        compose.onNodeWithText("分组一", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("g1", selected)
    }

    @Test
    fun materialCompactKeepsCustomCardTriggerAndCommitsId() {
        var selected: String? = null
        showCard("material") { selected = it }
        compose.onNodeWithText("类型：小说", useUnmergedTree = true).assertExists()
        compose.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
        openMenu()
        compose.onNodeWithText("漫画", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("1", selected)
    }

    @Test
    fun miuixWindowCompactKeepsCustomCardTriggerAndCommitsId() {
        var selected: String? = null
        showCard("miuix") { selected = it }
        compose.onNodeWithText("类型：小说", useUnmergedTree = true).assertExists()
        compose.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
        openMenu()
        compose.onNodeWithText("漫画", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("1", selected)
    }

    @Test
    fun materialCompactTriggerExposesSingleClickableNode() {
        show("material")
        compose.onNodeWithContentDescription("Charset", useUnmergedTree = true).assertExists()
        compose.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun materialCompactSelectingOptionCommitsStableId() {
        var selected: String? = null
        show("material") { selected = it }
        openMenu()
        compose.onNodeWithText("GBK", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("gbk", selected)
    }

    @Test
    fun miuixWindowCompactSelectingOptionCommitsStableId() {
        var selected: String? = null
        show("miuix") { selected = it }
        compose.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
        openMenu()
        compose.onNodeWithText("GBK", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("gbk", selected)
    }

    @Test
    fun miuixOverlayCompactMarksSelectedOptionAndCommitsId() {
        var selected: String? = null
        show("miuix", withHost = true) { selected = it }
        openMenu()
        compose.onNodeWithText("UTF-8").assertIsSelected()
        compose.onNodeWithText("BIG5", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("big5", selected)
    }
}
