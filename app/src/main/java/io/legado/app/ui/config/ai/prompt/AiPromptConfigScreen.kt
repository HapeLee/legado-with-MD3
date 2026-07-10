package io.legado.app.ui.config.ai.prompt

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
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
import org.koin.androidx.compose.koinViewModel

@Composable
fun AiPromptConfigRouteScreen(
    onBackClick: () -> Unit,
    viewModel: AiPromptConfigViewModel = koinViewModel()
) {
    AiPromptConfigScreen(
        state = viewModel.uiState.collectAsStateWithLifecycle().value,
        effects = viewModel.effects,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick
    )
}

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
                    snackbarHostState.showSnackbar(effect.message)
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
                title = stringResource(R.string.ai_prompt_config),
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
                SplicedColumnGroup(title = stringResource(R.string.ai_prompt_setting)) {
                    state.items.forEach { item ->
                        ClickableSettingItem(
                            title = stringResource(item.nameResId),
                            description = stringResource(item.descResId),
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        onIntent(AiPromptConfigIntent.ResetPrompt(item.taskType))
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Restore,
                                        contentDescription = stringResource(R.string.ai_prompt_reset_single)
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
                        title = stringResource(R.string.ai_prompt_restore_all),
                        description = stringResource(R.string.ai_prompt_restore_all_desc),
                        onClick = { onIntent(AiPromptConfigIntent.OpenRestoreAllDialog) }
                    )
                }
            }
        }
    }

    when (val dialog = state.activeDialog) {
        is AiPromptConfigDialog.EditPrompt -> {
            AppAlertDialog(
                show = true,
                onDismissRequest = { onIntent(AiPromptConfigIntent.CloseDialog) },
                title = stringResource(R.string.ai_prompt_edit_title),
                confirmText = stringResource(R.string.confirm),
                onConfirm = {
                    onIntent(AiPromptConfigIntent.SavePrompt(dialog.taskType, dialog.currentPrompt))
                },
                dismissText = stringResource(R.string.cancel),
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
                show = true,
                onDismissRequest = { onIntent(AiPromptConfigIntent.CloseDialog) },
                title = stringResource(R.string.ai_prompt_restore_all),
                confirmText = stringResource(R.string.confirm),
                onConfirm = { onIntent(AiPromptConfigIntent.RestoreAllDefaults) },
                dismissText = stringResource(R.string.cancel),
                onDismiss = { onIntent(AiPromptConfigIntent.CloseDialog) },
                text = stringResource(R.string.ai_prompt_restore_all_confirm)
            )
        }

        null -> {}
    }
}
