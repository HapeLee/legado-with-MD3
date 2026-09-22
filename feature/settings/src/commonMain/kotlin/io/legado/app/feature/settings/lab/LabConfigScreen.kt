package io.legado.app.feature.settings.lab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.lab_diagnostics
import io.legado.app.feature.settings.res.lab_display
import io.legado.app.feature.settings.res.lab_eink_display_hint
import io.legado.app.feature.settings.res.lab_eink_display_summary
import io.legado.app.feature.settings.res.lab_eink_display_title
import io.legado.app.feature.settings.res.lab_enabled_summary
import io.legado.app.feature.settings.res.lab_enabled_title
import io.legado.app.feature.settings.res.lab_page_estimate_diagnostics_count
import io.legado.app.feature.settings.res.lab_page_estimate_diagnostics_summary
import io.legado.app.feature.settings.res.lab_page_estimate_diagnostics_title
import io.legado.app.feature.settings.res.lab_setting
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import org.jetbrains.compose.resources.stringResource

// M5-2a：从 `:app` 的 `io.legado.app.ui.config.labConfig.LabConfigScreen` 迁来。
//
// 与迁移前的差异只有三类，都是机械的：
//   1. **`R.string.*` → `Res.string.*`**（CMP 多平台资源；每个 key 单独 import，
//      `stringResource` 换 `org.jetbrains.compose.resources.stringResource`）。
//      文案值由 `verify-compose-resources.py` 与 `:app` 侧逐字比对钉住。
//   2. **`LabConfigRouteScreen` 不搬**：它做两件事——`koinViewModel()` 与把
//      `ACTION_SEND` 分享解释成 `Intent`——都是宿主职责（`android.content.Intent`
//      不能进 `commonMain`）。按 about 的前例留在 `:app` 的 `MainNavGraph` entry。
//      因此本文件只导出纯 `LabConfigScreen(state, onIntent, onBackClick)`。
//   3. **`HintText` 收 `String` 而不是 `@StringRes Int`**：`Res.string.*` 是
//      `StringResource`，不是 Android 资源 id；调用点用 `stringResource(...)` 求值。
//      组件本体因此变成纯 CMP（不依赖任何资源表）。

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabConfigScreen(
    state: LabConfigUiState,
    onIntent: (LabConfigIntent) -> Unit,
    onBackClick: () -> Unit,
) {
    val settings = state.settings
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(Res.string.lab_setting),
                scrollBehavior = scrollBehavior,
                navigationIcon = { TopBarNavigationButton(onClick = onBackClick) },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp,
            ),
        ) {
            item {
                SplicedColumnGroup {
                    SwitchSettingItem(
                        title = stringResource(Res.string.lab_enabled_title),
                        description = stringResource(Res.string.lab_enabled_summary),
                        checked = settings.enabled,
                        onCheckedChange = { onIntent(LabConfigIntent.SetEnabled(it)) },
                    )
                }
                AnimatedVisibility(visible = settings.enabled) {
                    SplicedColumnGroup(title = stringResource(Res.string.lab_display)) {
                        SwitchSettingItem(
                            title = stringResource(Res.string.lab_eink_display_title),
                            description = stringResource(Res.string.lab_eink_display_summary),
                            checked = settings.eInkDisplay,
                            onCheckedChange = { onIntent(LabConfigIntent.SetEInkDisplay(it)) },
                        )
                        if (settings.eInkDisplay) {
                            HintText(stringResource(Res.string.lab_eink_display_hint))
                        }
                    }
                }
                SplicedColumnGroup(title = stringResource(Res.string.lab_diagnostics)) {
                    ClickableSettingItem(
                        title = stringResource(Res.string.lab_page_estimate_diagnostics_title),
                        description = stringResource(
                            Res.string.lab_page_estimate_diagnostics_summary
                        ),
                        option = stringResource(
                            Res.string.lab_page_estimate_diagnostics_count,
                            state.pageEstimateDiagnosticCount,
                        ),
                        onClick = {
                            onIntent(LabConfigIntent.ExportPageEstimateDiagnostics)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = LegadoTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
