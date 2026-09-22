package io.legado.app.feature.settings.ai.summary

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.action_save
import io.legado.app.feature.settings.res.ai_chapter_summary_config
import io.legado.app.feature.settings.res.ai_current_value
import io.legado.app.feature.settings.res.ai_custom_prompt
import io.legado.app.feature.settings.res.ai_custom_prompt_desc
import io.legado.app.feature.settings.res.ai_max_output_tokens
import io.legado.app.feature.settings.res.ai_not_set
import io.legado.app.feature.settings.res.ai_param_setting
import io.legado.app.feature.settings.res.ai_prompt_setting
import io.legado.app.feature.settings.res.ai_prompt_template
import io.legado.app.feature.settings.res.ai_reset_prompt
import io.legado.app.feature.settings.res.ai_temperature
import io.legado.app.feature.settings.res.cancel
import io.legado.app.feature.settings.res.confirm
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppFloatingActionButton
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.InputSettingItem
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.stringResource

// M5-4b：从 `:app` 的 `io.legado.app.ui.config.ai.summary.AiSummaryConfigScreen` 迁来。
//
// 三类差异：
//   1. `R.string.*` → `Res.string.*`（17 条 ×4 语言；其中 10 条在 `:app` 的 zh-rHK/TW 里
//      本来就没有，靠 fallback 到默认语言——与迁移前一致，不是本片漏搬）。
//   2. `AiSummaryConfigRouteScreen` 不搬（只做 `koinViewModel()`），宿主那边与 translation
//      同形：只剩「取 VM、收 state」。
//   3. ⚠️ **提示文案的取法变了**：这个 Screen 自己收集 Effect 并用 **Snackbar** 显示，
//      迁移前 VM 已经把文案取好（成品 `String`）。现在 VM 发的是枚举 ⇒ 这里要
//      `effect.message.localizedText()`（suspend，在 `LaunchedEffect` 里调用，天然满足）。
//      运行期文本（`ShowRawMessage`）直接显示。
//
// 本页用到的 `InputSettingItem` 由 M5-4a-pre 上提到 designsystem。

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSummaryConfigScreen(
    state: AiSummaryConfigUiState,
    effects: Flow<AiSummaryConfigEffect>,
    onIntent: (AiSummaryConfigIntent) -> Unit,
    onBackClick: () -> Unit
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        effects.collectLatest { effect ->
            when (effect) {
                is AiSummaryConfigEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(effect.message.localizedText())
                }
                is AiSummaryConfigEffect.ShowRawMessage -> {
                    snackbarHostState.showSnackbar(effect.text)
                }
                is AiSummaryConfigEffect.NavigateBack -> {
                    onBackClick()
                }
            }
        }
    }

    AppScaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(Res.string.ai_chapter_summary_config),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                }
            )
        },
        floatingActionButton = {
            AppFloatingActionButton(
                onClick = { onIntent(AiSummaryConfigIntent.Save) },
                icon = Icons.Default.Save,
                tooltipText = stringResource(Res.string.action_save)
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp
            )
        ) {
            item {
                SplicedColumnGroup(title = stringResource(Res.string.ai_prompt_setting)) {
                    ClickableSettingItem(
                        title = stringResource(Res.string.ai_custom_prompt),
                        description = stringResource(Res.string.ai_custom_prompt_desc),
                        onClick = {
                            onIntent(AiSummaryConfigIntent.OpenPromptDialog(state.promptTemplate))
                        }
                    )
                    ClickableSettingItem(
                        title = stringResource(Res.string.ai_reset_prompt),
                        description = stringResource(Res.string.ai_prompt_template),
                        onClick = {
                            onIntent(AiSummaryConfigIntent.ResetPrompt)
                        }
                    )
                }
            }
            item {
                SplicedColumnGroup(title = stringResource(Res.string.ai_param_setting)) {
                    SliderSettingItem(
                        title = stringResource(Res.string.ai_temperature),
                        value = state.temperature,
                        defaultValue = TranslationConstants.DEFAULT_TEMPERATURE,
                        valueRange = TranslationConstants.MIN_TEMPERATURE..TranslationConstants.MAX_TEMPERATURE,
                        steps = 19,
                        description = state.temperature.toString(),
                        decimal = true,
                        onValueChange = { onIntent(AiSummaryConfigIntent.UpdateTemperature(it)) }
                    )
                    InputSettingItem(
                        title = stringResource(Res.string.ai_max_output_tokens),
                        value = if (state.maxOutputTokens > 0) state.maxOutputTokens.toString() else "",
                        defaultValue = "0",
                        description = if (state.maxOutputTokens > 0) {
                            stringResource(
                                Res.string.ai_current_value,
                                formatTokenLimit(state.maxOutputTokens)
                            )
                        } else {
                            stringResource(Res.string.ai_not_set)
                        },
                        onConfirm = { input ->
                            val tokens = input.trim().toIntOrNull() ?: 0
                            onIntent(AiSummaryConfigIntent.UpdateMaxOutputTokens(tokens))
                        }
                    )
                }
            }
        }
    }

    val activeDialog = state.activeDialog
    val editPromptDialog = activeDialog as? AiSummaryConfigDialog.EditPrompt
    AppAlertDialog(
        show = editPromptDialog != null,
        onDismissRequest = { onIntent(AiSummaryConfigIntent.CloseDialog) },
        title = stringResource(Res.string.ai_custom_prompt),
        confirmText = stringResource(Res.string.confirm),
        onConfirm = {
            if (editPromptDialog != null) {
                onIntent(AiSummaryConfigIntent.UpdatePrompt(editPromptDialog.currentPrompt))
            }
        },
        dismissText = stringResource(Res.string.cancel),
        onDismiss = { onIntent(AiSummaryConfigIntent.CloseDialog) },
        content = {
            if (editPromptDialog != null) {
                AppTextField(
                    value = editPromptDialog.currentPrompt,
                    onValueChange = { onIntent(AiSummaryConfigIntent.UpdateDialogPrompt(it)) },
                    singleLine = false,
                    maxLines = 10,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 320.dp)
                        .imePadding()
                )
            }
        }
    )
}

private fun formatTokenLimit(value: Int): String {
    return when {
        value <= 0 -> "0"
        value >= 1_000_000 && value % 1_000_000 == 0 -> "${value / 1_000_000}M"
        value >= 1_000 && value % 1_000 == 0 -> "${value / 1_000}K"
        else -> value.toString()
    }
}
