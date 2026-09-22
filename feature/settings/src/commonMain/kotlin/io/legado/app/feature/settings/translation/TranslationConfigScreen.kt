package io.legado.app.feature.settings.translation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.ai_config
import io.legado.app.feature.settings.res.llm_max_chars_per_chunk
import io.legado.app.feature.settings.res.llm_provider
import io.legado.app.feature.settings.res.llm_target_language
import io.legado.app.feature.settings.res.translation_app_ai_provider
import io.legado.app.feature.settings.res.translation_app_ai_provider_summary
import io.legado.app.feature.settings.res.translation_config
import io.legado.app.feature.settings.res.translation_options
import io.legado.app.feature.settings.res.translation_provider
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import org.jetbrains.compose.resources.stringResource

// M5-2d：从 `:app` 的 `io.legado.app.ui.config.translation.TranslationConfigScreen` 迁来。
//
// 与迁移前的差异只有两类，都是机械的：
//   1. **`R.string.*` → `Res.string.*`**（CMP 多平台资源，9 条 ×4 语言的值逐字照搬）。
//   2. **`TranslationConfigRouteScreen` 不搬**：它只做 `koinViewModel()` 与转发
//      `onNavigateToAi` 回调。按 about / labConfig 的前例留在 `:app` 的 `MainNavGraph` entry。
//      ⚠️ 与 labConfig 不同：本页**没有**平台动作（没有 `Intent`），所以宿主那侧只剩下
//      「取 VM + 收 state」——Route 保留纯粹是为了 `koinViewModel()` 不进共享层。
//
// 本页能整页进共享层，靠的是 M5-2a-pre / M5-2b / M5-2c 三次资产上提：
// `ClickableSettingItem` / `DropdownListSettingItem` / `SliderSettingItem` 三个组件
// 原先都在 Android-only 的 `:core:ui`。

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationConfigScreen(
    state: TranslationConfigUiState,
    onIntent: (TranslationConfigIntent) -> Unit,
    onBackClick: () -> Unit,
    onNavigateToAi: () -> Unit,
) {
    val settings = state.settings
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(Res.string.translation_config),
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
                SplicedColumnGroup(title = stringResource(Res.string.translation_provider)) {
                    DropdownListSettingItem(
                        title = stringResource(Res.string.llm_provider),
                        selectedValue = settings.provider,
                        displayEntries = TranslationConstants.providerDisplayNames.toTypedArray(),
                        entryValues = TranslationConstants.providerValues.toTypedArray(),
                        onValueChange = { onIntent(TranslationConfigIntent.SetProvider(it)) },
                    )
                }
            }
            item {
                SplicedColumnGroup(title = stringResource(Res.string.translation_options)) {
                    DropdownListSettingItem(
                        title = stringResource(Res.string.llm_target_language),
                        selectedValue = settings.targetLanguage,
                        displayEntries = TranslationConstants.targetLanguages.map { it.second }.toTypedArray(),
                        entryValues = TranslationConstants.targetLanguages.map { it.first }.toTypedArray(),
                        onValueChange = { onIntent(TranslationConfigIntent.SetTargetLanguage(it)) },
                    )
                    SliderSettingItem(
                        title = stringResource(Res.string.llm_max_chars_per_chunk),
                        value = settings.maxCharsPerChunk.toFloat(),
                        defaultValue = 10000f,
                        valueRange = 1000f..10000f,
                        steps = 17,
                        onValueChange = {
                            onIntent(TranslationConfigIntent.SetMaxCharsPerChunk(it.toInt()))
                        },
                    )
                }
            }
            if (settings.provider == TranslationConstants.PROVIDER_APP_AI) {
                item {
                    SplicedColumnGroup(title = stringResource(Res.string.ai_config)) {
                        ClickableSettingItem(
                            title = stringResource(Res.string.translation_app_ai_provider),
                            description = stringResource(
                                Res.string.translation_app_ai_provider_summary
                            ),
                            onClick = onNavigateToAi,
                        )
                    }
                }
            }
        }
    }
}
