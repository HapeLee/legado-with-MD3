package io.legado.app.ui.config.ai.summary

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.domain.model.TranslationConstants
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
import org.koin.androidx.compose.koinViewModel

@Composable
fun AiSummaryConfigRouteScreen(
    onBackClick: () -> Unit,
    viewModel: AiSummaryConfigViewModel = koinViewModel()
) {
    AiSummaryConfigScreen(
        state = viewModel.uiState.collectAsStateWithLifecycle().value,
        effects = viewModel.effects,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick
    )
}

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
                    snackbarHostState.showSnackbar(effect.message)
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
                title = stringResource(R.string.ai_chapter_summary_config),
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
                tooltipText = stringResource(R.string.action_save)
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
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_custom_prompt),
                        description = stringResource(R.string.ai_custom_prompt_desc),
                        onClick = {
                            onIntent(AiSummaryConfigIntent.OpenPromptDialog(state.promptTemplate))
                        }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_reset_prompt),
                        description = stringResource(R.string.ai_prompt_template),
                        onClick = {
                            onIntent(AiSummaryConfigIntent.ResetPrompt)
                        }
                    )
                }
            }
            item {
                SplicedColumnGroup(title = stringResource(R.string.ai_param_setting)) {
                    SliderSettingItem(
                        title = stringResource(R.string.ai_temperature),
                        value = state.temperature,
                        defaultValue = TranslationConstants.DEFAULT_TEMPERATURE,
                        valueRange = TranslationConstants.MIN_TEMPERATURE..TranslationConstants.MAX_TEMPERATURE,
                        steps = 19,
                        description = state.temperature.toString(),
                        onValueChange = { onIntent(AiSummaryConfigIntent.UpdateTemperature(it)) }
                    )
                    InputSettingItem(
                        title = stringResource(R.string.ai_max_output_tokens),
                        value = if (state.maxOutputTokens > 0) state.maxOutputTokens.toString() else "",
                        defaultValue = "0",
                        description = if (state.maxOutputTokens > 0) stringResource(R.string.ai_current_value, formatTokenLimit(state.maxOutputTokens)) else stringResource(R.string.ai_not_set),
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
        title = stringResource(R.string.ai_custom_prompt),
        confirmText = stringResource(R.string.confirm),
        onConfirm = {
            if (editPromptDialog != null) {
                onIntent(AiSummaryConfigIntent.UpdatePrompt(editPromptDialog.currentPrompt))
            }
        },
        dismissText = stringResource(R.string.cancel),
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
