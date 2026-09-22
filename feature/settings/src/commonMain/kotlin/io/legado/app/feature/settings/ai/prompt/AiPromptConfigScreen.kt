package io.legado.app.feature.settings.ai.prompt

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.ai_prompt_config
import io.legado.app.feature.settings.res.ai_prompt_edit_title
import io.legado.app.feature.settings.res.ai_prompt_reset_single
import io.legado.app.feature.settings.res.ai_prompt_restore_all
import io.legado.app.feature.settings.res.ai_prompt_restore_all_confirm
import io.legado.app.feature.settings.res.ai_prompt_restore_all_desc
import io.legado.app.feature.settings.res.ai_prompt_setting
import io.legado.app.feature.settings.res.cancel
import io.legado.app.feature.settings.res.confirm
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.stringResource

// M5-4c：从 `:app` 的 `io.legado.app.ui.config.ai.prompt.AiPromptConfigScreen` 迁来。
//
// 三类差异：
//   1. `R.string.*` → `Res.string.*`。
//   2. ⚠️ **条目文案的取法变了**：迁移前是 `stringResource(item.nameResId)`（状态里存着
//      Android 资源 id），现在状态存 [AiPromptTask] 枚举 ⇒ 这里用
//      `item.task.displayName()` / `item.task.description()` 查表。
//      这是本片最实质的改动——资源 id 不再出现在 UI 状态里。
//   3. `AiPromptConfigRouteScreen` 不搬（只做 `koinViewModel()`）；提示文案仍由**本 Screen**
//      自己收 Effect 显示（Snackbar），与 `ai/summary` 同形。
//      ⚠️ 保存成功那条**不在这里**——它是 Toast，由 VM 注入的 `Toaster` 直接发
//      （见 `AiPromptConfigViewModel` 的注释：Snackbar 与 Toast 的差异要保住）。

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiPromptConfigScreen(
    state: AiPromptConfigUiState,
    effects: Flow<AiPromptConfigEffect>,
    onIntent: (AiPromptConfigIntent) -> Unit,
    onBackClick: () -> Unit
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        effects.collectLatest { effect ->
            when (effect) {
                is AiPromptConfigEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(effect.message.localizedText())
                }
                is AiPromptConfigEffect.ShowRawMessage -> {
                    snackbarHostState.showSnackbar(effect.text)
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
                title = stringResource(Res.string.ai_prompt_config),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                }
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
                    state.items.forEach { item ->
                        ClickableSettingItem(
                            title = item.task.displayName(),
                            description = item.task.description(),
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        onIntent(AiPromptConfigIntent.ResetPrompt(item.taskType))
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Restore,
                                        contentDescription = stringResource(
                                            Res.string.ai_prompt_reset_single
                                        )
                                    )
                                }
                            },
                            onClick = {
                                onIntent(
                                    AiPromptConfigIntent.OpenPromptDialog(
                                        item.taskType,
                                        item.currentPrompt
                                    )
                                )
                            }
                        )
                    }
                }
            }
            item {
                SplicedColumnGroup {
                    ClickableSettingItem(
                        title = stringResource(Res.string.ai_prompt_restore_all),
                        description = stringResource(Res.string.ai_prompt_restore_all_desc),
                        onClick = { onIntent(AiPromptConfigIntent.OpenRestoreAllDialog) }
                    )
                }
            }
        }
    }

    when (val dialog = state.activeDialog) {
        is AiPromptConfigDialog.EditPrompt -> {
            AppAlertDialog(
                data = dialog,
                onDismissRequest = { onIntent(AiPromptConfigIntent.CloseDialog) },
                title = stringResource(Res.string.ai_prompt_edit_title),
                confirmText = stringResource(Res.string.confirm),
                onConfirm = {
                    onIntent(AiPromptConfigIntent.SavePrompt(dialog.taskType, dialog.currentPrompt))
                },
                dismissText = stringResource(Res.string.cancel),
                onDismiss = { onIntent(AiPromptConfigIntent.CloseDialog) },
                content = {
                    AppTextField(
                        value = dialog.currentPrompt,
                        onValueChange = { onIntent(AiPromptConfigIntent.UpdateDialogPrompt(it)) },
                        singleLine = false,
                        maxLines = 15,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 400.dp)
                            .imePadding()
                    )
                }
            )
        }

        is AiPromptConfigDialog.RestoreAllConfirm -> {
            AppAlertDialog(
                data = dialog,
                onDismissRequest = { onIntent(AiPromptConfigIntent.CloseDialog) },
                title = stringResource(Res.string.ai_prompt_restore_all),
                confirmText = stringResource(Res.string.confirm),
                onConfirm = { onIntent(AiPromptConfigIntent.RestoreAllDefaults) },
                dismissText = stringResource(Res.string.cancel),
                onDismiss = { onIntent(AiPromptConfigIntent.CloseDialog) },
                text = stringResource(Res.string.ai_prompt_restore_all_confirm)
            )
        }

        null -> {}
    }
}
