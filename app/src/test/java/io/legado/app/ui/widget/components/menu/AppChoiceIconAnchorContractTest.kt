package io.legado.app.ui.widget.components.menu

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.ui.theme.AppTheme
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
 * `AppChoiceDropdown(anchor = AppMenuAnchor.Icon)` 的契约基线（第十九轮）。
 *
 * 顶栏图标单选此前只能手画 `TopBarActionButton` + `RoundDropdownMenu`，本轮补上 Icon 锚点：
 * 由分支包装各自的原生图标按钮壳（Miuix 走库 `Icon` 弹层、Material 退 `IconButton`），
 * trigger 仍是纯展示内容，语义树上只有**一个**可点节点，稳定 ID 回调不变。
 *
 * COUI 的 `CouiTopAppBarButton` 需要 Android 14 原生工具栏与胶囊渲染器，Robolectric 下
 * 走降级分支，因此这里不覆盖；Mochi 的 `MochiBarIconButton` 也留给真机。
 * 本测试只证明结构、可点节点数量与 ID 回调，**不证明弹层定位与真机外观**。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w398dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class AppChoiceIconAnchorContractTest {
    @get:Rule val compose = createComposeRule()

    @Composable
    private fun IconMenu(onSelect: (String) -> Unit) {
        AppChoiceDropdown(
            options = persistentListOf(
                AppChoiceOption("all", "全部"),
                AppChoiceOption("g1", "分组一"),
            ),
            selectedId = "all",
            onSelect = onSelect,
            title = "分组筛选",
            anchor = AppMenuAnchor.Icon,
        ) {
            AppIcon(
                imageVector = AppIcons.Filter,
                contentDescription = "分组筛选",
            )
        }
    }

    private fun show(engine: String, onSelect: (String) -> Unit = {}) {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent {
            AppTheme(AppUiConfiguration(appShell = AppShellSettings(composeEngine = engine))) {
                IconMenu(onSelect)
            }
        }
    }

    @Test
    fun materialIconAnchorKeepsSingleClickableNode() {
        show("material")
        compose.onNodeWithContentDescription("分组筛选", useUnmergedTree = true).assertExists()
        compose.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun materialIconAnchorCommitsStableId() {
        var selected: String? = null
        show("material") { selected = it }
        compose.onNode(hasClickAction(), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("分组一", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("g1", selected)
    }

    @Test
    fun miuixWindowIconAnchorKeepsSingleClickableNode() {
        show("miuix")
        compose.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun miuixWindowIconAnchorCommitsStableId() {
        var selected: String? = null
        show("miuix") { selected = it }
        compose.onNode(hasClickAction(), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("分组一", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("g1", selected)
    }
}
