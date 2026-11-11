package io.legado.app.ui.widget.components.menu

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
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
 * `AppActionMenu(anchor = AppMenuAnchor.Row)` 的契约基线（第二十轮）。
 *
 * `Row` 锚点此前只有 Mochi 真正生效：COUI / Miuix / Material 都无条件渲染库或 M3 的图标按钮，
 * 列表项里那层壳不是 `SmallPlainButton`，迁过去就是外观变化。本轮补齐后，`Row` 在四分支都
 * 由 trigger 自己画壳、入口只包一个可点节点；Miuix 改走 `Window/OverlayDropdownPopup`，
 * 绕开 `Icon*DropdownMenu` 会把 content 包进库按钮的行为。
 *
 * COUI 的原生胶囊分支在 Robolectric 下走降级路径，Mochi 留给真机；
 * 本测试只证明结构、可点节点数量与 ID 回调，**不证明弹层定位与真机外观**。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w398dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class AppActionRowAnchorContractTest {
    @get:Rule val compose = createComposeRule()

    @Composable
    private fun RowActions(onAction: (String) -> Unit) {
        AppActionMenu(
            groups = persistentListOf(
                AppActionGroup(
                    id = "row_actions",
                    actions = persistentListOf(
                        AppAction(id = "rename", label = "重命名"),
                        AppAction(id = "delete", label = "删除"),
                    ),
                ),
            ),
            onAction = onAction,
            anchor = AppMenuAnchor.Row,
            title = "更多",
        ) {
            SmallPlainButton(
                onClick = null,
                icon = AppIcons.MoreVert,
                contentDescription = "更多",
            )
        }
    }

    private fun show(engine: String, onAction: (String) -> Unit = {}) {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent {
            AppTheme(AppUiConfiguration(appShell = AppShellSettings(composeEngine = engine))) {
                RowActions(onAction)
            }
        }
    }

    @Test
    fun materialRowAnchorKeepsSingleClickableNode() {
        show("material")
        compose.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithContentDescription("更多", useUnmergedTree = true).assertExists()
    }

    @Test
    fun materialRowAnchorDispatchesActionId() {
        var dispatched: String? = null
        show("material") { dispatched = it }
        compose.onNode(hasClickAction(), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("重命名", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("rename", dispatched)
    }

    @Test
    fun miuixRowAnchorKeepsSingleClickableNode() {
        show("miuix")
        compose.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun miuixRowAnchorDispatchesActionId() {
        var dispatched: String? = null
        show("miuix") { dispatched = it }
        compose.onNode(hasClickAction(), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("重命名", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("rename", dispatched)
    }
}
