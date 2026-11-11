package io.legado.app.ui.widget.components.menu

import android.app.Application
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.ui.theme.AppTheme
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
 * `AppAnchoredPanel` 的契约基线：trigger 纯展示（主入口唯一可点节点）、
 * 面板开合所有权（`onExpandedChange` / `dismiss()`）、Mochi 的 `Box(clickable)` + 锚点弹层，
 * 以及 `TopBarOverflowMenu` 收编使用的 [AppAnchoredPanel] 兼容路径。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w398dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class AppAnchoredPanelContractTest {
    @get:Rule val compose = createComposeRule()

    @Composable
    private fun Panel(engine: String, externalTrigger: Boolean, expanded: MutableState<Boolean>) {
        AppTheme(AppUiConfiguration(appShell = AppShellSettings(composeEngine = engine))) {
            AppAnchoredPanel(
                expanded = expanded.value,
                onExpandedChange = { expanded.value = it },
                externalTrigger = externalTrigger,
                trigger = {
                    if (externalTrigger) {
                        // 兼容路径模拟 TopBarOverflowMenu：调用方自己渲染可点按钮。
                        Button(onClick = { open() }, modifier = this.modifier) { Text("More") }
                    } else {
                        Text("More", modifier = this.modifier)
                    }
                },
            ) { dismiss ->
                Button(onClick = { dismiss() }) { Text("Panel action") }
            }
        }
    }

    private fun show(engine: String, externalTrigger: Boolean = false): MutableState<Boolean> {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        val expanded = mutableStateOf(false)
        compose.setContent { Panel(engine, externalTrigger, expanded) }
        return expanded
    }

    private fun clickOnlyAction() {
        compose.onNode(hasClickAction(), useUnmergedTree = true).performClick()
        compose.waitForIdle()
    }

    @Test
    fun materialTriggerExposesSingleClickableNode() {
        show("material")
        compose.onNodeWithText("More", useUnmergedTree = true).assertExists()
        compose.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun materialOpensPanelAndDismissClosesIt() {
        val expanded = show("material")
        clickOnlyAction()
        assertTrue(expanded.value)
        compose.onNodeWithText("Panel action", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Panel action", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertFalse(expanded.value)
    }

    @Test
    fun mochiTriggerOpensPanel() {
        val expanded = show("mochi")
        clickOnlyAction()
        assertTrue(expanded.value)
        compose.onNodeWithText("Panel action", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun miuixOpensPanel() {
        val expanded = show("miuix")
        clickOnlyAction()
        assertTrue(expanded.value)
        compose.onNodeWithText("Panel action", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun externalTriggerButtonOpensPanelAndStaysSingleClickableNode() {
        val expanded = show("material", externalTrigger = true)
        compose.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
        clickOnlyAction()
        assertTrue(expanded.value)
        compose.onNodeWithText("Panel action", useUnmergedTree = true).assertIsDisplayed()
    }
}
