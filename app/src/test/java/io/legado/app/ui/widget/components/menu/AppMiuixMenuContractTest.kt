package io.legado.app.ui.widget.components.menu

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
 * `AppActionMenu` 的 Miuix 原生分支契约：稳定 ID 回调、逐项禁用、一层子菜单、
 * trigger 纯展示（唯一可点节点）、`checked` / `selected` 的降级映射。
 *
 * 不套 `AppScaffold` 时 `LocalHasMiuixPopupHost` 为 false，走 `Window*` 系列；
 * 套上 `AppScaffold` 后走 `Overlay*` 系列。默认 `useHyperOs4Components = false`，
 * 不会进入依赖 AGSL shader 的玻璃变形分支（该路径只能真机验证）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w398dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class AppMiuixMenuContractTest {
    @get:Rule val compose = createComposeRule()

    @Composable
    private fun Menu(actions: ImmutableList<AppAction>, onAction: (String) -> Unit) {
        AppActionMenu(
            groups = persistentListOf(AppActionGroup(id = "group", actions = actions)),
            onAction = onAction,
        ) {
            AppIcon(
                imageVector = AppIcons.MoreVert,
                contentDescription = "More",
                modifier = this.modifier,
            )
        }
    }

    @Composable
    private fun Host(actions: ImmutableList<AppAction>, onAction: (String) -> Unit, withHost: Boolean) {
        AppTheme(AppUiConfiguration(appShell = AppShellSettings(composeEngine = "miuix"))) {
            if (withHost) {
                AppScaffold(topBar = {}) { Menu(actions, onAction) }
            } else {
                Menu(actions, onAction)
            }
        }
    }

    private fun show(
        actions: ImmutableList<AppAction>,
        withHost: Boolean = false,
        onAction: (String) -> Unit = {},
    ) {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent { Host(actions, onAction, withHost) }
    }

    private fun openMenu() {
        compose.onNode(hasClickAction(), useUnmergedTree = true).performClick()
        compose.waitForIdle()
    }

    @Test
    fun windowTriggerExposesSingleClickableNode() {
        show(persistentListOf(AppAction("copy", "Copy")))
        compose.onNodeWithContentDescription("More", useUnmergedTree = true).assertExists()
        compose.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun windowSelectingItemCommitsStableId() {
        var selected: String? = null
        show(persistentListOf(AppAction("copy", "Copy"), AppAction("paste", "Paste"))) {
            selected = it
        }
        openMenu()
        compose.onNodeWithText("Paste", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("paste", selected)
    }

    @Test
    fun overlaySelectingItemCommitsStableId() {
        var selected: String? = null
        show(
            persistentListOf(AppAction("copy", "Copy"), AppAction("paste", "Paste")),
            withHost = true,
        ) { selected = it }
        openMenu()
        compose.onNodeWithText("Paste", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("paste", selected)
    }

    @Test
    fun disabledItemIsNotEnabledAndDoesNotCommit() {
        var selected: String? = null
        show(persistentListOf(
            AppAction("copy", "Copy"),
            AppAction("paste", "Paste", enabled = false),
        )) { selected = it }
        openMenu()
        compose.onNodeWithText("Paste").assertIsNotEnabled()
        assertNull(selected)
    }

    @Test
    fun checkedAndSelectedBothMapToSelectedSemantics() {
        show(persistentListOf(
            AppAction("alpha", "Alpha", checked = true),
            AppAction("beta", "Beta", selected = true),
        ))
        openMenu()
        compose.onNodeWithText("Alpha").assertIsSelected()
        compose.onNodeWithText("Beta").assertIsSelected()
    }

    @Test
    fun cascadingChildCommitsChildId() {
        var selected: String? = null
        show(persistentListOf(
            AppAction(
                id = "share",
                label = "Share",
                children = persistentListOf(
                    AppAction("share_link", "Copy link"),
                    AppAction("share_file", "Send file"),
                ),
            ),
        )) { selected = it }
        openMenu()
        compose.onNodeWithText("Share", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        // 级联 popup 在展开过渡里会同时保留主/子面板的条目副本，取首个可见节点。
        compose.onAllNodesWithText("Copy link", useUnmergedTree = true)[0].performClick()
        compose.waitForIdle()
        assertEquals("share_link", selected)
    }
}
