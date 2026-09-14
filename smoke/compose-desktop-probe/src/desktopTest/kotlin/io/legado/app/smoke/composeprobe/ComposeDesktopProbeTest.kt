package io.legado.app.smoke.composeprobe

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
// v2 而不是 v1：v1 的 `runComposeUiTest` 在 CMP 1.12.0 已废弃。v2 默认用
// `StandardTestDispatcher`，协程是排队执行而不是立即执行，更贴近生产行为——
// 对 M1-4 要验的「VM 状态流经 Flow 到界面」这条链路来说，v1 的立即执行会掩盖
// 「首帧还没收到数据」这类时序问题，所以从一开始就按 v2 写。
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

/**
 * M1-4 的工具链探针：证明 **commonMain 的 CMP 代码能在 desktop 上被真实渲染并断言**。
 *
 * 断言顺序即要证明的四件事，任何一件失败都说明 M1-4 的前提不成立：
 *   1. 初始文案可见 ⇒ 渲染链路（Skiko + 布局 + 语义树）通；
 *   2. 按钮可点击 ⇒ 输入事件链路通；
 *   3. 点击后文案变化 ⇒ 重组与状态链路通；
 *   4. `onNodeWithText(...).assertIsDisplayed()` 能查到 ⇒ 语义树可用于断言真实 Feature 界面。
 *
 * 这个测试只有 desktop 能跑（依赖 `compose.desktop.currentOs`），故放 `desktopTest`
 * 而不是 `commonTest`。
 */
class ComposeDesktopProbeTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun rendersAndReactsToClickOnDesktop() = runComposeUiTest {
        setContent {
            ProbeClickableText()
        }

        onNodeWithText(IDLE_TEXT).assertIsDisplayed()
        onNodeWithText("probe-button").performClick()
        onNodeWithText(CLICKED_TEXT).assertIsDisplayed()
    }
}
