package io.legado.app.host.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import io.legado.app.core.platform.ImportJsonEditor
import io.legado.app.core.platform.Toaster
import io.legado.app.feature.dict.rule.DictRuleScreen
import io.legado.app.feature.dict.rule.DictRuleViewModel
import io.legado.app.host.desktop.nav.DesktopNavHost
import io.legado.app.host.desktop.nav.DesktopRoute
import io.legado.app.host.desktop.nav.desktopEntryViewModel
import io.legado.app.host.desktop.res.Res
import io.legado.app.host.desktop.res.desktop_file_picker_unavailable
import io.legado.app.host.desktop.res.desktop_home_title
import io.legado.app.host.desktop.res.desktop_open_dict_rules
import org.jetbrains.compose.resources.stringResource
import org.koin.core.Koin
import org.koin.core.context.GlobalContext

/** 测试与宿主都用得上的 tag：断言「现在在哪一屏」时不依赖 locale 文案。 */
const val DESKTOP_HOME_TAG = "desktop-home"

/** 入口页「词典规则」按钮的 tag（同上，测试点它进下一屏时不受语言环境影响）。 */
const val DESKTOP_OPEN_DICT_TAG = "desktop-open-dict"

/**
 * desktop host 的根组合（M1-4b）。
 *
 * 职责与 Android 侧 `MainActivity` 的 Compose 根部一致，但**没有 NavDisplay**：
 * 导航渲染由 [DesktopNavHost] 自己完成（原因见那里的 KDoc——`navigation3-ui` 在桌面端只有
 * jvmStubs，不存在 `NavDisplay` 本体）。
 *
 * `backStack` 暴露成参数（而不是内部 `remember` 隐藏）是为了让测试能直接驱动返回栈，
 * 也让将来「从外部恢复返回栈」有一个明确的注入点。
 */
@Composable
fun DesktopApp(
    koin: Koin = GlobalContext.get(),
    backStack: NavBackStack<DesktopRoute> = remember { NavBackStack(DesktopRoute.Home) },
) {
    DesktopTheme {
        DesktopNavHost(
            backStack = backStack,
            entryProvider = desktopEntryProvider(koin = koin, backStack = backStack),
        )
    }
}

/**
 * desktop 的导航图：destination → entry。形态与 Android 的 `mainEntryProvider` 同构
 * （`entryProvider { entry<Route> { … } }`），差别只在 entry 内部取 VM 的方式
 * （[desktopEntryViewModel] 代替 `koinViewModel()`）。
 */
fun desktopEntryProvider(
    koin: Koin,
    backStack: NavBackStack<DesktopRoute>,
): (DesktopRoute) -> NavEntry<DesktopRoute> = entryProvider {
    entry<DesktopRoute.Home> {
        DesktopRuleHomeScreen(
            onOpenDictRules = { backStack.add(DesktopRoute.DictRules) },
        )
    }
    entry<DesktopRoute.DictRules> {
        DictRulesEntry(
            koin = koin,
            onBack = { backStack.removeLastOrNull() },
        )
    }
}

/**
 * host 的入口页：desktop 应用的第一个目的地。
 *
 * 它属于宿主而不是任何 Feature——Feature 不互相依赖，入口聚合是 host 的职责
 * （AGENTS.md：App host 聚合导航和 DI）。目前只有「词典规则」一个真实可进入的模块，
 * 其余模块尚未接入 desktop，故不列占位项。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DesktopRuleHomeScreen(onOpenDictRules: () -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag(DESKTOP_HOME_TAG),
        topBar = { TopAppBar(title = { Text(stringResource(Res.string.desktop_home_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.desktop_home_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Button(
                onClick = onOpenDictRules,
                modifier = Modifier.testTag(DESKTOP_OPEN_DICT_TAG),
            ) {
                Text(stringResource(Res.string.desktop_open_dict_rules))
            }
        }
    }
}

/**
 * 词典规则目的地：真实 Feature 屏 + 真实 Room 数据。
 *
 * 与 Android 的 `DictRuleRouteScreen` 一一对应，只有两处平台差异：
 * 1. VM 取自 entry 级作用域（[desktopEntryViewModel]），不是 `koinViewModel()`；
 * 2. 文件选择器在 desktop 不可用 ⇒ **显式**提示，而不是静默空实现（AGENTS.md 要求）。
 *    导入/导出要读写 URI，desktop 侧没有 SAF 对应物；`RuleTransferPlatform` 的 URL 分支
 *    同样显式抛 `UnsupportedOperationException`（见 `DesktopRuleTransferPlatform`）。
 */
@Composable
private fun DictRulesEntry(koin: Koin, onBack: () -> Unit) {
    val viewModel = desktopEntryViewModel { koin.get<DictRuleViewModel>() }
    val toaster = remember { koin.get<Toaster>() }
    // M2-1：导入对话框编辑页的能力，与 Android Route 一样由宿主装配（desktop 侧实现
    // 显式不支持，见 DesktopImportJsonEditor）。
    val importJsonEditor: ImportJsonEditor = remember { koin.get<ImportJsonEditor>() }
    val state by viewModel.uiState.collectAsState()
    val pickerUnavailable = stringResource(Res.string.desktop_file_picker_unavailable)

    DictRuleScreen(
        state = state,
        importState = viewModel.importState.collectAsState().value,
        importJsonEditor = importJsonEditor,
        events = viewModel.events,
        effects = viewModel.effects,
        onIntent = viewModel::onIntent,
        onPasteRule = viewModel::pasteRule,
        onPickImportSource = { toaster.toast(pickerUnavailable) },
        onPickExportTarget = { toaster.toast(pickerUnavailable) },
        onBackClick = onBack,
    )
}
