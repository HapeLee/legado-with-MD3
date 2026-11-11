package io.legado.app.ui.widget.components.topbar

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.widget.components.menu.AppAction
import io.legado.app.ui.widget.components.menu.AppActionGroup
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
 * 顶栏声明式溢出菜单的行为基线：`dropDownMenuContent` 换成 `menuGroups` 后，
 * 四套引擎都必须保留全部分组条目、分段线不吞项、稳定 ID 回调不变，
 * 且 MoreVert 按钮仍是语义树上唯一的可点节点。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w398dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class TopBarActionMenuContractTest {
    @get:Rule val compose = createComposeRule()

    private val groups = persistentListOf(
        AppActionGroup(
            id = "primary",
            actions = persistentListOf(
                AppAction("download", "Download"),
                AppAction("export", "Export"),
            ),
        ),
        AppActionGroup(
            id = "secondary",
            actions = persistentListOf(AppAction("log", "Log")),
        ),
    )

    private fun show(engine: String, onAction: (String) -> Unit) {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent {
            AppTheme(AppUiConfiguration(appShell = AppShellSettings(composeEngine = engine))) {
                TopBarActionMenu(groups = groups, onAction = onAction)
            }
        }
    }

    private fun assertContract(engine: String) {
        var selected: String? = null
        show(engine) { selected = it }
        compose.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
        val trigger = compose.onNode(hasClickAction(), useUnmergedTree = true)
        if (engine == "mochi") trigger.performTouchInput { click() } else trigger.performClick()
        compose.waitForIdle()
        listOf("Download", "Export", "Log").forEach { label ->
            compose.onNodeWithText(label, useUnmergedTree = true).assertExists()
        }
        compose.onNodeWithText("Log", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("log", selected)
    }

    @Test
    fun materialTopBarActionMenuKeepsAllItemsAndIds() = assertContract("material")

    @Test
    fun mochiTopBarActionMenuKeepsAllItemsAndIds() = assertContract("mochi")

    @Test
    fun miuixTopBarActionMenuKeepsAllItemsAndIds() = assertContract("miuix")

    @Test
    fun couiTopBarActionMenuKeepsAllItemsAndIds() = assertContract("coui")
}
