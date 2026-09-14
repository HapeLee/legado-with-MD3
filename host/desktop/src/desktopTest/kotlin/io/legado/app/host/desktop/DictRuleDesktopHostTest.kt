package io.legado.app.host.desktop

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.legado.app.core.platform.ImportJsonEditor
import io.legado.app.data.entities.DictRule
import io.legado.app.data.repository.DictRuleRepository
import io.legado.app.feature.dict.rule.DictRuleScreen
import io.legado.app.feature.dict.rule.DictRuleViewModel
import kotlin.test.AfterTest
import kotlin.test.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

/**
 * M1-4 的主路径验证：**同一 Feature 在 desktop 上真的跑起来**。
 *
 * 在此之前，四个 Feature 转 CMP 的 desktop 证据全部止于 `compileKotlinDesktop`——
 * 能编译证明不了能组装、能渲染、能从数据库读到数据。这个测试把三件事一起验掉：
 *
 * 1. **Koin graph**：`desktopHostModule` 能在一个非 Android target 上完整解析出
 *    `DictRuleViewModel` 的全部四个依赖（少注册任何一个都会在这里炸，而不是等到运行时）；
 * 2. **一条数据路径**：Room(desktop, BundledSQLiteDriver) → `DictRuleRepository.flowAll()`
 *    → VM 的 `uiState` → 界面上一行真实文本；
 * 3. **CMP resources**：界面上的标题来自 `feature:dict` 的 `composeResources`
 *    （`Res.string.dict_rule`），不是 Android `R.string`。资源在 desktop 上读不到的话，
 *    标题那一行就是空的，断言直接失败。
 *
 * 断言用规则名（[SEED_RULE_NAME]）而不是「列表非空」：后者在渲染失败但没崩时也会通过，
 * 前者要求数据真的从数据库流到了具体一行。
 */
class DictRuleDesktopHostTest {

    @AfterTest
    fun tearDown() {
        // Koin 是全局单例，不 close 会污染同 JVM 内的其他用例。
        stopKoin()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun rendersRulesFromDatabaseOnDesktop() = runComposeUiTest {
        val koinApp = startKoin {
            modules(desktopHostModule(databasePath()))
        }
        try {
            val repository = koinApp.koin.get<DictRuleRepository>()
            val viewModel = koinApp.koin.get<DictRuleViewModel>()
            // M2-1：编辑页的字段拆解能力改为显式传入（原先 designsystem 自己读全局 Provider）。
            val importJsonEditor = koinApp.koin.get<ImportJsonEditor>()

            repository.insert(
                DictRule(
                    name = SEED_RULE_NAME,
                    urlRule = "https://example.com/dict?q=\${key}",
                    showRule = "$.data"
                )
            )

            setContent {
                DesktopTheme {
                    val state by viewModel.uiState.collectAsState()
                    DictRuleScreen(
                        state = state,
                        importState = viewModel.importState.collectAsState().value,
                        events = viewModel.events,
                        effects = viewModel.effects,
                        onIntent = viewModel::onIntent,
                        onPasteRule = viewModel::pasteRule,
                        onPickImportSource = {},
                        onPickExportTarget = {},
                        importJsonEditor = importJsonEditor,
                        onBackClick = {}
                    )
                }
            }

            // Room 的查询跑在真实 IO 线程上，数据到达需要真实时间；这里轮询等待而不是
            // 只 advance 测试时钟——那只会推进协程调度，不会驱动数据库回调。
            waitUntil(timeoutMillis = 10_000) {
                stateHasRule(viewModel)
            }

            // 数据路径的终点：界面上真的出现了这条规则的文本。
            onNodeWithText(SEED_RULE_NAME).assertIsDisplayed()
        } finally {
            koinApp.close()
        }
    }

    /**
     * VM 是否已经收到数据库里的那条规则。
     *
     * 单独抽出来是因为 `waitUntil` 的条件在每帧都会跑，写内联容易把「读状态」和
     * 「断言」混在一起；这里只回答「到了没有」，断言留给下面的 `onNodeWithText`。
     */
    private fun stateHasRule(viewModel: DictRuleViewModel): Boolean =
        viewModel.uiState.value.items.any { it.id == SEED_RULE_NAME }

    private fun databasePath(): String =
        System.getProperty("java.io.tmpdir") + "dict-host-${System.nanoTime()}.db"

    private companion object {
        const val SEED_RULE_NAME = "词典规则-桌面端探针"
    }
}
