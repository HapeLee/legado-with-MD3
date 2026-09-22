package io.legado.app.feature.settings.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.ai_add_model_manually
import io.legado.app.feature.settings.res.ai_advanced
import io.legado.app.feature.settings.res.ai_api_key
import io.legado.app.feature.settings.res.ai_api_key_summary
import io.legado.app.feature.settings.res.ai_base_url
import io.legado.app.feature.settings.res.ai_context_window
import io.legado.app.feature.settings.res.ai_custom_provider
import io.legado.app.feature.settings.res.ai_delete_model
import io.legado.app.feature.settings.res.ai_delete_model_confirm
import io.legado.app.feature.settings.res.ai_delete_provider
import io.legado.app.feature.settings.res.ai_delete_provider_confirm
import io.legado.app.feature.settings.res.ai_fetch_and_save_models
import io.legado.app.feature.settings.res.ai_fetch_models
import io.legado.app.feature.settings.res.ai_max_output_tokens
import io.legado.app.feature.settings.res.ai_model_edit
import io.legado.app.feature.settings.res.ai_model_id
import io.legado.app.feature.settings.res.ai_model_name
import io.legado.app.feature.settings.res.ai_models_url
import io.legado.app.feature.settings.res.ai_models_url_summary
import io.legado.app.feature.settings.res.ai_protocol
import io.legado.app.feature.settings.res.ai_provider
import io.legado.app.feature.settings.res.ai_provider_edit
import io.legado.app.feature.settings.res.ai_provider_models
import io.legado.app.feature.settings.res.ai_provider_name
import io.legado.app.feature.settings.res.ai_provider_preset
import io.legado.app.feature.settings.res.ai_reasoning_level_high
import io.legado.app.feature.settings.res.ai_reasoning_level_low
import io.legado.app.feature.settings.res.ai_reasoning_level_max
import io.legado.app.feature.settings.res.ai_reasoning_level_medium
import io.legado.app.feature.settings.res.ai_reasoning_level_xhigh
import io.legado.app.feature.settings.res.ai_save_model
import io.legado.app.feature.settings.res.ai_save_provider
import io.legado.app.feature.settings.res.ai_temperature
import io.legado.app.feature.settings.res.ai_test_connection
import io.legado.app.feature.settings.res.ai_thinking_strength
import io.legado.app.feature.settings.res.cancel
import io.legado.app.feature.settings.res.delete
import io.legado.app.feature.settings.res.hide_password
import io.legado.app.feature.settings.res.ok
import io.legado.app.feature.settings.res.show_password
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppFloatingActionButton
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.InputSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.stringResource

// M5-5c：从 `:app` 的 `io.legado.app.ui.config.ai.AiProviderEditScreen` 迁来。
//
// 差异两类：`R.string.*` → `Res.string.*`（28 条），`AiProviderEditRouteScreen` 不搬
// （它只做 `koinViewModel(parameters = { parametersOf(providerId) })`，宿主那侧照原样接）。
//
// ⚠️ `formatFetchedLimit` 用的 `formatTokenLimit` 原本来自 M5-5b 留在 `:app` 的
// **临时副本** `ai/TokenLimitFormat.kt`。本片把最后一页也迁走后，那个副本连同它一起删除
// ——共享层里 `AiModelEditScreen.kt` 的 `internal fun formatTokenLimit` 与这里同包，直接可见。
//
// 本页是 ai 域里唯一**带多个对话框**的（API Key / 模型编辑 / 删除 provider / 删除 model），
// 但都是 Compose 状态 ⇒ 无平台依赖，全留在共享层。

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiProviderEditScreen(
    state: AiProviderEditUiState,
    effects: Flow<AiProviderEditEffect>,
    onIntent: (AiProviderEditIntent) -> Unit,
    onBackClick: () -> Unit
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val providerPresetEntries = arrayOf(stringResource(Res.string.ai_custom_provider)) +
            state.providerPresets
                .filter { it.protocol == state.protocol }
                .map { it.name }
                .toTypedArray()
    val providerPresetValues = arrayOf("") +
            state.providerPresets
                .filter { it.protocol == state.protocol }
                .map { it.id }
                .toTypedArray()
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var apiKeyDraft by remember { mutableStateOf("") }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var showDeleteProviderDialog by remember { mutableStateOf(false) }
    var showDeleteModelDialog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        effects.collectLatest { effect ->
            when (effect) {
                is AiProviderEditEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                AiProviderEditEffect.NavigateBack -> onBackClick()
                AiProviderEditEffect.NavigateBackAfterDelete -> onBackClick()
            }
        }
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(Res.string.ai_provider_edit),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                }
            )
        },
        floatingActionButton = {
            AppFloatingActionButton(
                onClick = { onIntent(AiProviderEditIntent.SaveProvider) },
                icon = Icons.Default.Save,
                tooltipText = stringResource(Res.string.ai_save_provider)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp
            )
        ) {
            item {
                SplicedColumnGroup(title = stringResource(Res.string.ai_provider)) {
                    InputSettingItem(
                        title = stringResource(Res.string.ai_provider_name),
                        value = state.providerName,
                        onConfirm = { onIntent(AiProviderEditIntent.UpdateProviderName(it)) }
                    )
                    DropdownListSettingItem(
                        title = stringResource(Res.string.ai_protocol),
                        selectedValue = state.protocol,
                        displayEntries = arrayOf("OpenAI Chat Completions", "OpenAI Responses", "Anthropic Messages"),
                        entryValues = arrayOf(
                            AiProtocol.OPENAI_CHAT_COMPLETIONS,
                            AiProtocol.OPENAI_RESPONSES,
                            AiProtocol.ANTHROPIC_MESSAGES
                        ),
                        onValueChange = { onIntent(AiProviderEditIntent.UpdateProtocol(it)) }
                    )
                    DropdownListSettingItem(
                        title = stringResource(Res.string.ai_provider_preset),
                        selectedValue = state.selectedProviderPresetId,
                        displayEntries = providerPresetEntries,
                        entryValues = providerPresetValues,
                        onValueChange = { onIntent(AiProviderEditIntent.ApplyProviderPreset(it)) }
                    )
                    InputSettingItem(
                        title = stringResource(Res.string.ai_base_url),
                        value = state.baseUrl,
                        onConfirm = { onIntent(AiProviderEditIntent.UpdateBaseUrl(it)) }
                    )
                    ClickableSettingItem(
                        title = stringResource(Res.string.ai_api_key),
                        description = stringResource(Res.string.ai_api_key_summary),
                        onClick = {
                            apiKeyDraft = state.apiKey
                            showApiKeyDialog = true
                        }
                    )
                    ClickableSettingItem(
                        title = if (state.isTesting) "${stringResource(Res.string.ai_test_connection)}..." else stringResource(Res.string.ai_test_connection),
                        onClick = {
                            if (!state.isTesting && !state.isSaving && !state.isFetchingModels) {
                                onIntent(AiProviderEditIntent.TestConnection)
                            }
                        }
                    )
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(Res.string.ai_provider_models)) {
                    state.providerModels.forEach { model ->
                        ClickableSettingItem(
                            title = model.modelName,
                            description = model.modelId,
                            option = formatFetchedLimit(model.contextWindow, model.maxOutputTokens),
                            onClick = { onIntent(AiProviderEditIntent.EditModel(model.modelProfileId)) }
                        )
                    }
                    ClickableSettingItem(
                        title = stringResource(Res.string.ai_add_model_manually),
                        onClick = { onIntent(AiProviderEditIntent.AddModel) }
                    )
                    ClickableSettingItem(
                        title = stringResource(Res.string.ai_fetch_and_save_models),
                        description = stringResource(Res.string.ai_fetch_models),
                        onClick = { onIntent(AiProviderEditIntent.SyncModels) }
                    )
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(Res.string.ai_advanced)) {
                    InputSettingItem(
                        title = stringResource(Res.string.ai_models_url),
                        value = state.modelsUrl,
                        description = stringResource(Res.string.ai_models_url_summary),
                        onConfirm = { onIntent(AiProviderEditIntent.UpdateModelsUrl(it)) }
                    )
                }
            }

            if (state.providerId != null) {
                item {
                    ClickableSettingItem(
                        title = stringResource(Res.string.ai_delete_provider),
                        onClick = { showDeleteProviderDialog = true }
                    )
                }
            }
        }
    }

    AppAlertDialog(
        show = showApiKeyDialog,
        onDismissRequest = { showApiKeyDialog = false },
        title = stringResource(Res.string.ai_api_key),
        content = {
            Column {
                AppTextField(
                    value = apiKeyDraft,
                    onValueChange = { apiKeyDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = LegadoTheme.colorScheme.surface,
                    label = stringResource(Res.string.ai_api_key),
                    visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        val image = if (apiKeyVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        val description = if (apiKeyVisible) {
                            stringResource(Res.string.hide_password)
                        } else {
                            stringResource(Res.string.show_password)
                        }
                        IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                            Icon(imageVector = image, contentDescription = description)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        },
        confirmText = stringResource(Res.string.ok),
        onConfirm = {
            onIntent(AiProviderEditIntent.UpdateApiKey(apiKeyDraft))
            showApiKeyDialog = false
        },
        dismissText = stringResource(Res.string.cancel),
        onDismiss = { showApiKeyDialog = false }
    )

    AppAlertDialog(
        data = state.editingModel,
        onDismissRequest = { onIntent(AiProviderEditIntent.DismissModelEditor) },
        title = stringResource(Res.string.ai_model_edit),
        content = { model ->
            Column {
                AppTextField(
                    value = model.modelName,
                    onValueChange = { onIntent(AiProviderEditIntent.UpdateEditingModelName(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = LegadoTheme.colorScheme.surface,
                    label = stringResource(Res.string.ai_model_name)
                )
                Spacer(modifier = Modifier.height(8.dp))
                AppTextField(
                    value = model.modelId,
                    onValueChange = { onIntent(AiProviderEditIntent.UpdateEditingModelId(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = LegadoTheme.colorScheme.surface,
                    label = stringResource(Res.string.ai_model_id)
                )
                Spacer(modifier = Modifier.height(8.dp))
                AppTextField(
                    value = model.contextWindow,
                    onValueChange = { onIntent(AiProviderEditIntent.UpdateEditingContextWindow(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = LegadoTheme.colorScheme.surface,
                    label = stringResource(Res.string.ai_context_window),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(8.dp))
                AppTextField(
                    value = model.maxOutputTokens,
                    onValueChange = { onIntent(AiProviderEditIntent.UpdateEditingMaxOutputTokens(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = LegadoTheme.colorScheme.surface,
                    label = stringResource(Res.string.ai_max_output_tokens),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(8.dp))
                AppTextField(
                    value = model.temperature,
                    onValueChange = { onIntent(AiProviderEditIntent.UpdateEditingTemperature(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = LegadoTheme.colorScheme.surface,
                    label = stringResource(Res.string.ai_temperature),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Spacer(modifier = Modifier.height(8.dp))
                DropdownListSettingItem(
                    title = stringResource(Res.string.ai_thinking_strength),
                    selectedValue = model.reasoningLevel.effort,
                    displayEntries = arrayOf(
                        stringResource(Res.string.ai_reasoning_level_low),
                        stringResource(Res.string.ai_reasoning_level_medium),
                        stringResource(Res.string.ai_reasoning_level_high),
                        stringResource(Res.string.ai_reasoning_level_xhigh),
                        stringResource(Res.string.ai_reasoning_level_max)
                    ),
                    entryValues = AiReasoningLevel.modelConfigEntries
                        .map { it.effort }
                        .toTypedArray(),
                    onValueChange = {
                        onIntent(AiProviderEditIntent.UpdateEditingReasoningLevel(AiReasoningLevel.fromEffort(it)))
                    }
                )
                if (model.modelProfileId != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    ClickableSettingItem(
                        title = stringResource(Res.string.ai_delete_model),
                        onClick = {
                            showDeleteModelDialog = model.modelProfileId
                        }
                    )
                }
            }
        },
        confirmText = stringResource(Res.string.ai_save_model),
        onConfirm = { onIntent(AiProviderEditIntent.SaveEditingModel) },
        dismissText = stringResource(Res.string.cancel),
        onDismiss = { onIntent(AiProviderEditIntent.DismissModelEditor) }
    )

    AppAlertDialog(
        show = showDeleteProviderDialog,
        onDismissRequest = { showDeleteProviderDialog = false },
        title = stringResource(Res.string.ai_delete_provider),
        text = stringResource(Res.string.ai_delete_provider_confirm),
        confirmText = stringResource(Res.string.delete),
        onConfirm = {
            onIntent(AiProviderEditIntent.DeleteProvider)
            showDeleteProviderDialog = false
        },
        dismissText = stringResource(Res.string.cancel),
        onDismiss = { showDeleteProviderDialog = false }
    )

    AppAlertDialog(
        show = showDeleteModelDialog != null,
        onDismissRequest = { showDeleteModelDialog = null },
        title = stringResource(Res.string.ai_delete_model),
        text = stringResource(Res.string.ai_delete_model_confirm),
        confirmText = stringResource(Res.string.delete),
        onConfirm = {
            showDeleteModelDialog?.let { onIntent(AiProviderEditIntent.DeleteModel(it)) }
            showDeleteModelDialog = null
        },
        dismissText = stringResource(Res.string.cancel),
        onDismiss = { showDeleteModelDialog = null }
    )
}

private fun formatFetchedLimit(contextWindow: Int, maxOutputTokens: Int): String? {
    return when {
        contextWindow > 0 && maxOutputTokens > 0 -> "${formatTokenLimit(contextWindow)} / ${formatTokenLimit(maxOutputTokens)}"
        contextWindow > 0 -> formatTokenLimit(contextWindow)
        maxOutputTokens > 0 -> formatTokenLimit(maxOutputTokens)
        else -> null
    }
}
