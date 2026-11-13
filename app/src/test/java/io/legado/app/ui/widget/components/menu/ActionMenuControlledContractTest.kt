package io.legado.app.ui.widget.components.menu

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.ui.theme.AppTheme
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
import java.io.File

/**
 * [AppActionMenu] 受控弹层模式契约（第二十三轮）。
 *
 * 与 [AppChoiceDropdown] 的 `embeddedExpanded` 同语义：调用方持有开合状态、自己渲染触发按钮，
 * 入口只把弹层挂在调用方给的容器上。行为用例覆盖 Material / Miuix / Mochi 三引擎；
 * COUI 在 Robolectric 下 `canUseCouiSettingMenu()` 为假、会退回 Material 分支，
 * 所以 COUI 只做源码结构契约（锚点必须挂容器），真机触点与材质仍需人工复核。
 *
 * 静态契约只保证四分支都不渲染 trigger、都读外部状态，不证明弹层在场上的像素位置。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w398dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ActionMenuControlledContractTest {
    @get:Rule val compose = createComposeRule()

    private val groups = persistentListOf(
        AppActionGroup(
            id = "plain_group",
            actions = persistentListOf(
                AppAction("alpha", "Alpha"),
                AppAction("beta", "Beta"),
            ),
        ),
    )

    private fun showControlledMenu(engine: String, onAction: (String) -> Unit) {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent {
            var expanded by remember { mutableStateOf(false) }
            AppTheme(AppUiConfiguration(appShell = AppShellSettings(composeEngine = engine))) {
                Box {
                    Button(onClick = { expanded = true }) { Text("Toggle") }
                    Text(if (expanded) STATE_OPEN else STATE_CLOSED)
                    AppActionMenu(
                        groups = groups,
                        onAction = onAction,
                        modifier = Modifier.matchParentSize(),
                        embeddedExpanded = expanded,
                        onDismissRequest = { expanded = false },
                        trigger = { Text(TRIGGER_TEXT) },
                    )
                }
            }
        }
    }

    /** 受控模式不渲染 trigger 槽、也不包可点节点：可点节点只有调用方那颗按钮。 */
    @Test
    fun controlledModeDoesNotRenderTriggerOrClickableWrapper() {
        showControlledMenu("material") {}
        compose.onNodeWithText(TRIGGER_TEXT, useUnmergedTree = true).assertDoesNotExist()
        compose.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
    }

    /** 调用方置 true 就能开弹层；选中后经 onDismissRequest 把状态收回去。 */
    private fun assertControlledSelectionCommitsAndDismisses(engine: String) {
        var selected: String? = null
        showControlledMenu(engine) { selected = it }
        compose.onNodeWithText("Toggle", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Beta", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("$engine 未回传稳定 ID", "beta", selected)
        compose.onNodeWithText(STATE_CLOSED, useUnmergedTree = true).assertExists()
    }

    @Test
    fun materialControlledSelectionCommitsAndDismisses() =
        assertControlledSelectionCommitsAndDismisses("material")

    @Test
    fun miuixControlledSelectionCommitsAndDismisses() =
        assertControlledSelectionCommitsAndDismisses("miuix")

    @Test
    fun mochiControlledSelectionCommitsAndDismisses() =
        assertControlledSelectionCommitsAndDismisses("mochi")

    /** 同语义的 [AppChoiceDropdown] 受控模式也必须能靠 onDismissRequest 收口。 */
    private fun assertControlledChoiceDismisses(engine: String) {
        var selected: String? = null
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent {
            var expanded by remember { mutableStateOf(true) }
            AppTheme(AppUiConfiguration(appShell = AppShellSettings(composeEngine = engine))) {
                Box {
                    AppChoiceDropdown(
                        options = persistentListOf(
                            AppChoiceOption("alpha", "Alpha"),
                            AppChoiceOption("beta", "Beta"),
                        ),
                        selectedId = "alpha",
                        onSelect = { selected = it },
                        title = "Choice",
                        modifier = Modifier.matchParentSize(),
                        embeddedExpanded = expanded,
                        onDismissRequest = { expanded = false },
                    )
                    Text(if (expanded) STATE_OPEN else STATE_CLOSED)
                }
            }
        }
        compose.onNodeWithText("Beta", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("$engine 未回传所选 ID", "beta", selected)
        compose.onNodeWithText(STATE_CLOSED, useUnmergedTree = true).assertExists()
    }

    @Test
    fun materialControlledChoiceDismisses() = assertControlledChoiceDismisses("material")

    @Test
    fun miuixControlledChoiceDismisses() = assertControlledChoiceDismisses("miuix")

    @Test
    fun mochiControlledChoiceDismisses() = assertControlledChoiceDismisses("mochi")

    /** 受控优先于 Icon 形态：Miuix 的库图标弹层自持 expanded，必须绕开。 */
    @Test
    fun miuixControlledModeRoutesToPopupBranch() {
        val source = sourceOf(APP_ACTION_MENU)
        assertTrue(
            "Miuix 未把受控模式排到 Icon 形态之前",
            source.contains("if (embeddedExpanded != null || anchor == AppMenuAnchor.Row)"),
        )
        val rowActions = source
            .substringAfter("private fun MiuixRowActions(")
            .substringBefore("private fun MiuixActions(")
        assertTrue(
            "MiuixRowActions 受控时仍渲染 trigger 节点",
            rowActions.contains("if (embeddedExpanded == null)"),
        )
        assertTrue(
            "MiuixRowActions 未把 show 交给外部状态",
            rowActions.contains("show = isOpen && enabled"),
        )
    }

    /** 四个分支都要读外部状态、跳过 trigger；Mochi 只上报容器矩形，COUI 不得挂触点锚点。 */
    @Test
    fun fourBranchesReadExternalStateAndSkipTrigger() {
        val source = sourceOf(APP_ACTION_MENU)
        listOf("MochiActions", "CouiActions", "MiuixRowActions", "LegacyActions").forEach { name ->
            val body = source.substringAfter("private fun $name(").substringBefore("\n/**")
            assertTrue("$name 未使用外部状态", body.contains("isOpen = embeddedExpanded ?: expanded"))
            assertTrue("$name 未跳过 trigger 节点", body.contains("if (embeddedExpanded"))
        }
        val mochi = source.substringAfter("private fun MochiActions(").substringBefore("\n/**")
        assertTrue(
            "Mochi 受控模式未从容器取锚点矩形",
            mochi.contains("modifier.then(") && mochi.contains("Modifier.onGloballyPositioned"),
        )
    }

    /**
     * 受控模式的两个 COUI 入口都不得在调用方容器上挂 `couiMenuAnchor`。
     *
     * 该修饰符内含 `pointerInput`，作为覆盖在触发按钮之上的兄弟节点会成为命中的唯一节点，
     * 按钮再也收不到点击，菜单永远打不开（Robolectric 下 COUI 退回 Material 分支，
     * 行为用例测不到这个洞，只能锁源码结构）。定位也不依赖它：无触点时 COUI 的 anchor
     * 不产生坐标，弹层按 Popup 的父容器矩形定位，见 [ControlledMenuAnchorContractTest]。
     */
    @Test
    fun controlledEntriesNeverPutTouchAnchorOnCallerContainer() {
        listOf(APP_ACTION_MENU, APP_CHOICE_DROPDOWN).forEach { path ->
            val source = sourceOf(path)
            assertFalse(
                "$path 把 couiMenuAnchor 挂到了受控容器上，触发按钮会被覆盖层挡住点击",
                source.contains("modifier.couiMenuAnchor(couiAnchor)"),
            )
            assertTrue(
                "$path 受控模式仍把 null 触点锚点传给 Menu",
                source.contains("anchor = if (embeddedExpanded == null) couiAnchor else null"),
            )
        }
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
        const val APP_CHOICE_DROPDOWN = "io/legado/app/ui/widget/components/menu/AppChoiceDropdown.kt"
        const val STATE_OPEN = "STATE_OPEN"
        const val STATE_CLOSED = "STATE_CLOSED"
        const val TRIGGER_TEXT = "ENTRY_TRIGGER_SHOULD_NOT_RENDER"
    }
}
