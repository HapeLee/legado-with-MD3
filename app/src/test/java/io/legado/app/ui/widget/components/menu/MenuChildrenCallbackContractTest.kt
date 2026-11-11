package io.legado.app.ui.widget.components.menu

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
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
import java.io.File

/**
 * 子菜单（[AppAction.children]）回调契约：点叶子项必须回传**子项的稳定 ID**，
 * 不能回传父项 ID，也不能静默丢子项。
 *
 * 覆盖分工：
 * - Material（平铺）与 Miuix（Cascading）：`AppMenuDividerContractTest` 已有行为用例。
 * - Mochi：本文件用真实弹层点父项 → 子项，走 `onChildClick`，并顺带验证
 *   「选完由入口自己收起」（业务侧不再持 `expanded`）。
 * - 「选完由入口自己收起」：Mochi 子项用例顺带验证；另有 Material / Miuix 单层菜单
 *   的关闭所有权用例（业务侧不持 `expanded`，无需也不会漏关）。
 * - COUI：**只能做结构契约**。点击父项后 COUI 的二级弹层在本测试窗口里不出现
 *   （父项点击直接收起），无法用语义查询驱动；因此退化为源码断言
 *   `CouiMenuItem(value = child.id, children = ...)` + `onAction(item.value)`，
 *   确保子 ID 不被父项吞掉。COUI 二级菜单外观与触点**仍需真机复核**。
 *
 * 所有用例只跑 JVM / Robolectric：**不证明**真机二级菜单动画、触点位置与 TalkBack 顺序。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w398dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class MenuChildrenCallbackContractTest {
    @get:Rule val compose = createComposeRule()

    private val plainGroups = persistentListOf(
        AppActionGroup(
            id = "plain_group",
            actions = persistentListOf(
                AppAction("alpha", "Alpha"),
                AppAction("beta", "Beta"),
            ),
        ),
    )

    private val childGroups = persistentListOf(
        AppActionGroup(
            id = "share_group",
            actions = persistentListOf(
                AppAction(
                    id = "share",
                    label = "Share",
                    children = persistentListOf(
                        AppAction("share_link", "Copy link"),
                        AppAction("share_file", "Send file"),
                    ),
                ),
            ),
        ),
    )

    private fun showActionMenu(
        engine: String,
        groups: ImmutableList<AppActionGroup> = childGroups,
        onAction: (String) -> Unit,
    ) {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent {
            AppTheme(AppUiConfiguration(appShell = AppShellSettings(composeEngine = engine))) {
                AppActionMenu(groups = groups, onAction = onAction) {
                    AppIcon(
                        imageVector = AppIcons.MoreVert,
                        contentDescription = "More",
                        modifier = this.modifier,
                    )
                }
            }
        }
    }

    private fun openMenu(engine: String) {
        val trigger = compose.onNode(hasClickAction(), useUnmergedTree = true)
        if (engine == "mochi") trigger.performTouchInput { click() } else trigger.performClick()
        compose.waitForIdle()
    }

    @Test
    fun mochiChildClickCommitsChildIdAndClosesMenu() {
        var selected: String? = null
        showActionMenu("mochi") { selected = it }
        openMenu("mochi")
        compose.onNodeWithText("Share", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Copy link", useUnmergedTree = true)[0].performClick()
        compose.waitForIdle()
        assertEquals("子项应回传自己的稳定 ID", "share_link", selected)
        // 关闭所有权：入口自己收起，业务侧没有 expanded 可漏关。
        compose.onNodeWithText("Copy link", useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * 关闭所有权：选完由入口自己收起，业务侧不持 `expanded`。
     * Mochi 与 Miuix 的父项 / 子项用例已隐含验证，这里锁 Material 与 Miuix 的单层菜单。
     */
    private fun assertMenuClosesAfterSelection(engine: String) {
        var selected: String? = null
        showActionMenu(engine, plainGroups) { selected = it }
        openMenu(engine)
        compose.onNodeWithText("Beta", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("beta", selected)
        compose.onNodeWithText("Beta", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun materialMenuClosesItselfAfterSelection() = assertMenuClosesAfterSelection("material")

    @Test
    fun miuixMenuClosesItselfAfterSelection() = assertMenuClosesAfterSelection("miuix")

    @Test
    fun couiBranchKeepsChildIdsInsteadOfCollapsingIntoParent() {
        val source = sourceOf(APP_ACTION_MENU)
        val coui = source
            .substringAfter("private fun CouiActions(")
            .substringBefore("private fun MiuixActions(")
        assertTrue("COUI 分支未把 children 映射成子项", coui.contains("children = action.children.map"))
        assertTrue("COUI 子项未携带自己的稳定 ID", coui.contains("value = child.id"))
        assertTrue("COUI 未用菜单项 value 分派", coui.contains("onAction(item.value)"))
    }

    private fun sourceOf(path: String): String {
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            val candidate = File(directory, "app/src/main/java/$path")
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
        }
        error("从 ${File("").absolutePath} 向上找不到 $path")
    }

    private companion object {
        const val APP_ACTION_MENU = "io/legado/app/ui/widget/components/menu/AppActionMenu.kt"
    }
}
