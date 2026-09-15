@file:OptIn(ExperimentalTestApi::class)

package io.legado.app.host.desktop

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.entryProvider
import io.legado.app.domain.rules.DictRule
import io.legado.app.domain.rules.DictRuleRepository
import io.legado.app.feature.dict.rule.DictRuleViewModel
import io.legado.app.host.desktop.nav.DesktopNavHost
import io.legado.app.host.desktop.nav.DesktopRoute
import io.legado.app.host.desktop.nav.desktopEntryViewModel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.isActive
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

/**
 * M1-4b 的导航验证：**Nav3 的 runtime 部分在 desktop 上真的能驱动界面切换**。
 *
 * M1-4 只渲染了单个 Screen（没有返回栈），这个测试补上导航这一环，并同时钉死两件事：
 *
 * 1. **两个目的地之间的切换是真的**：初始在 host 入口页 → 进词典规则页（同时断言 Room 数据
 *    到了界面）→ 返回后入口页回来、词典页文本消失。断言用 testTag 判断「当前在哪一屏」，
 *    不依赖 locale 文案（CI 机器语言环境不确定）。
 * 2. **宿主自己实现的 entry 级 ViewModel 作用域是真的**：`lifecycle-viewmodel-navigation3`
 *    只有 Android 变体，desktop 侧由 [DesktopNavHost] 用 KMP 的 `ViewModelStore` 自己维护；
 *    第二个用例断言 entry 离栈后 `viewModelScope` 真的被取消（不是「看起来清理了」）。
 *
 * 为什么不用 `NavDisplay`：见 `nav/DesktopNavHost.kt` 的 KDoc——`navigation3-ui` 在 desktop
 * 上只有 jvmStubs，**不存在 `NavDisplay` composable 本体**，连编译都过不去。
 */
class DesktopNavigationHostTest {

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun navigatesBetweenHomeAndDictRules() = runComposeUiTest {
        val koinApp = startKoin { modules(desktopHostModule(databasePath())) }
        try {
            koinApp.koin.get<DictRuleRepository>().insert(
                DictRule(
                    name = SEED_RULE_NAME,
                    urlRule = "https://example.com/dict?q=\${key}",
                    showRule = "$.data"
                )
            )

            val backStack = NavBackStack<DesktopRoute>(DesktopRoute.Home)
            setContent { DesktopApp(koin = koinApp.koin, backStack = backStack) }

            // 起始：host 入口页。
            onNodeWithTag(DESKTOP_HOME_TAG).assertIsDisplayed()

            // 进入词典规则页：走真实按钮点击，而不是直接改返回栈——
            // 这样连「entry 里的 onOpenDictRules 回调接到了宿主返回栈」一起验掉。
            onNodeWithTag(DESKTOP_OPEN_DICT_TAG).performClick()

            // 数据要从 Room 流到界面：这里有真实 IO，必须轮询等待。
            waitUntil(timeoutMillis = 10_000) {
                isTextPresent(SEED_RULE_NAME)
            }

            onNodeWithText(SEED_RULE_NAME).assertIsDisplayed()
            // 栈顶换人：入口页应当已经不在组合里。
            onNodeWithTag(DESKTOP_HOME_TAG).assertDoesNotExist()

            // 返回：栈顶回到入口页，词典页的文本消失。
            backStack.removeLastOrNull()
            waitForIdle()
            onNodeWithTag(DESKTOP_HOME_TAG).assertIsDisplayed()
            onNodeWithText(SEED_RULE_NAME).assertDoesNotExist()
        } finally {
            koinApp.close()
        }
    }

    /**
     * entry 级 ViewModel 作用域：进入 entry 时 VM 活着，离栈后被 `ViewModelStore.clear()`
     * 释放（`viewModelScope` 取消）。
     *
     * 这里自建一个两屏的 entryProvider（而不是用 `desktopEntryProvider`），是为了把被测 VM 的
     * 实例抓在测试手里——本用例的被测对象是宿主语义，不是词典页的 UI。
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun clearsEntryViewModelWhenEntryLeavesBackStack() = runComposeUiTest {
        val koinApp = startKoin { modules(desktopHostModule(databasePath())) }
        try {
            val backStack = NavBackStack<DesktopRoute>(DesktopRoute.Home)
            var enteredViewModel: DictRuleViewModel? = null

            setContent {
                DesktopTheme {
                    DesktopNavHost(
                        backStack = backStack,
                        entryProvider = entryProvider {
                            entry<DesktopRoute.Home> {
                                Text("home-entry", Modifier.testTag(HOME_ENTRY_TAG))
                            }
                            entry<DesktopRoute.DictRules> {
                                val viewModel = desktopEntryViewModel {
                                    koinApp.koin.get<DictRuleViewModel>()
                                }
                                enteredViewModel = viewModel
                                Text("dict-entry", Modifier.testTag(DICT_ENTRY_TAG))
                            }
                        }
                    )
                }
            }

            onNodeWithTag(HOME_ENTRY_TAG).assertIsDisplayed()

            backStack.add(DesktopRoute.DictRules)
            waitForIdle()

            val viewModel = requireNotNull(enteredViewModel) { "entry 内容没有被执行" }
            assertTrue(viewModel.viewModelScope.isActive, "entry 在栈上时 VM 应该是活的")

            backStack.removeLastOrNull()
            // 清理发生在 snapshotFlow 的 collector 里，需要给它跑的机会（不是同步完成）。
            waitUntil(timeoutMillis = 5_000) { !viewModel.viewModelScope.isActive }
            assertFalse(viewModel.viewModelScope.isActive, "entry 离栈后 viewModelScope 应被取消")

            onNodeWithTag(HOME_ENTRY_TAG).assertIsDisplayed()
            onNodeWithTag(DICT_ENTRY_TAG).assertDoesNotExist()
        } finally {
            koinApp.close()
        }
    }

    /**
     * `waitUntil` 的条件只回答「文本到了没有」，不塞断言进去。
     *
     * 这里问的是界面语义树而不是 VM 的 `uiState`：VM 实例由宿主内部创建，测试拿不到；
     * 而「界面上真出现了这一行」本来就是这个用例要证明的事。
     */
    private fun ComposeUiTest.isTextPresent(text: String): Boolean =
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun databasePath(): String =
        System.getProperty("java.io.tmpdir") + "nav-host-${System.nanoTime()}.db"

    private companion object {
        const val SEED_RULE_NAME = "词典规则-导航探针"
        const val HOME_ENTRY_TAG = "home-entry"
        const val DICT_ENTRY_TAG = "dict-entry"
    }
}
