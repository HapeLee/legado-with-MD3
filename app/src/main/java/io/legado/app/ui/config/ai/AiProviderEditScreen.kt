package io.legado.app.ui.config.ai

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.domain.model.AiProtocol
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
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AiProviderEditRouteScreen(
    providerId: String?,
    onBackClick: () -> Unit,
    viewModel: AiProviderEditViewModel = koinViewModel(
        key = providerId.orEmpty(),
        parameters = { parametersOf(providerId) }
    )
) {
    AiProviderEditScreen(
        state = viewModel.uiState.collectAsStateWithLifecycle().value,
        effects = viewModel.effects,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick
    )
}

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
    val providerPresetEntries = arrayOf(stringResource(R.string.ai_custom_provider)) +
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
                title = stringResource(R.string.ai_provider_edit),
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
                tooltipText = stringResource(R.string.ai_save_provider)
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
                SplicedColumnGroup(title = stringResource(R.string.ai_provider)) {
                    InputSettingItem(
                        title = stringResource(R.string.ai_provider_name),
                        value = state.providerName,
                        onConfirm = { onIntent(AiProviderEditIntent.UpdateProviderName(it)) }
                    )
                    DropdownListSettingItem(
                        title = stringResource(R.string.ai_protocol),
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
                        title = stringResource(R.string.ai_provider_preset),
                        selectedValue = state.selectedProviderPresetId,
                        displayEntries = providerPresetEntries,
                        entryValues = providerPresetValues,
                        onValueChange = { onIntent(AiProviderEditIntent.ApplyProviderPreset(it)) }
                    )
                    InputSettingItem(
                        title = stringResource(R.string.ai_base_url),
                        value = state.baseUrl,
                        onConfirm = { onIntent(AiProviderEditIntent.UpdateBaseUrl(it)) }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_api_key),
                        description = stringResource(R.string.ai_api_key_summary),
                        onClick = {
                            apiKeyDraft = state.apiKey
                            showApiKeyDialog = true
                        }
                    )
                    ClickableSettingItem(
                        title = if (state.isTesting) "${stringResource(R.string.ai_test_connection)}..." else stringResource(R.string.ai_test_connection),
                        onClick = {
                            if (!state.isTesting && !state.isSaving && !state.isFetchingModels) {
                                onIntent(AiProviderEditIntent.TestConnection)
                            }
                        }
                    )
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(R.string.ai_provider_models)) {
                    state.providerModels.forEach { model ->
                        ClickableSettingItem(
                            title = model.modelName,
                            description = model.modelId,
                            option = formatFetchedLimit(model.contextWindow, model.maxOutputTokens),
                            onClick = { onIntent(AiProviderEditIntent.EditModel(model.modelProfileId)) }
                        )
                    }
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_add_model_manually),
                        onClick = { onIntent(AiProviderEditIntent.AddModel) }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_fetch_and_save_models),
                        description = stringResource(R.string.ai_fetch_models),
                        onClick = { onIntent(AiProviderEditIntent.SyncModels) }
                    )
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(R.string.ai_advanced)) {
                    InputSettingItem(
                        title = stringResource(R.string.ai_models_url),
                        value = state.modelsUrl,
                        description = stringResource(R.string.ai_models_url_summary),
                        onConfirm = { onIntent(AiProviderEditIntent.UpdateModelsUrl(it)) }
                    )
                }
            }

            if (state.providerId != null) {
                item {
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_delete_provider),
                        onClick = { showDeleteProviderDialog = true }
                    )
                }
            }
        }
    }

    AppAlertDialog(
        show = showApiKeyDialog,
        onDismissRequest = { showApiKeyDialog = false },
        title = stringResource(R.string.ai_api_key),
        content = {
            Column {
                AppTextField(
                    value = apiKeyDraft,
                    onValueChange = { apiKeyDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = LegadoTheme.colorScheme.surface,
                    label = stringResource(R.string.ai_api_key),
                    visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        val image = if (apiKeyVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        val description = if (apiKeyVisible) {
                            stringResource(R.string.hide_password)
                        } else {
                            stringResource(R.string.show_password)
                        }
                        IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                            Icon(imageVector = image, contentDescription = description)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        },
        confirmText = stringResource(R.string.ok),
        onConfirm = {
            onIntent(AiProviderEditIntent.UpdateApiKey(apiKeyDraft))
            showApiKeyDialog = false
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { showApiKeyDialog = false }
    )

    AppAlertDialog(
        data = state.editingModel,
        onDismissRequest = { onIntent(AiProviderEditIntent.DismissModelEditor) },
        title = stringResource(R.string.ai_model_edit),
        content = { model ->
            Column {
                AppTextField(
                    value = model.modelName,
                    onValueChange = { onIntent(AiProviderEditIntent.UpdateEditingModelName(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = LegadoTheme.colorScheme.surface,
                    label = stringResource(R.string.ai_model_name)
                )
                Spacer(modifier = Modifier.height(8.dp))
                AppTextField(
                    value = model.modelId,
                    onValueChange = { onIntent(AiProviderEditIntent.UpdateEditingModelId(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = LegadoTheme.colorScheme.surface,
                    label = stringResource(R.string.ai_model_id)
                )
                Spacer(modifier = Modifier.height(8.dp))
                AppTextField(
                    value = model.contextWindow,
                    onValueChange = { onIntent(AiProviderEditIntent.UpdateEditingContextWindow(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = LegadoTheme.colorScheme.surface,
                    label = stringResource(R.string.ai_context_window),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(8.dp))
                AppTextField(
                    value = model.maxOutputTokens,
                    onValueChange = { onIntent(AiProviderEditIntent.UpdateEditingMaxOutputTokens(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = LegadoTheme.colorScheme.surface,
                    label = stringResource(R.string.ai_max_output_tokens),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(8.dp))
                AppTextField(
                    value = model.temperature,
                    onValueChange = { onIntent(AiProviderEditIntent.UpdateEditingTemperature(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = LegadoTheme.colorScheme.surface,
                    label = stringResource(R.string.ai_temperature),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                if (model.modelProfileId != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    ClickableSettingItem(
                        title = stringResource(R.string.ai_delete_model),
                        onClick = {
                            showDeleteModelDialog = model.modelProfileId
                        }
                    )
                }
            }
        },
        confirmText = stringResource(R.string.ai_save_model),
        onConfirm = { onIntent(AiProviderEditIntent.SaveEditingModel) },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(AiProviderEditIntent.DismissModelEditor) }
    )

    AppAlertDialog(
        show = showDeleteProviderDialog,
        onDismissRequest = { showDeleteProviderDialog = false },
        title = stringResource(R.string.ai_delete_provider),
        text = stringResource(R.string.ai_delete_provider_confirm),
        confirmText = stringResource(R.string.delete),
        onConfirm = {
            onIntent(AiProviderEditIntent.DeleteProvider)
            showDeleteProviderDialog = false
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { showDeleteProviderDialog = false }
    )

    AppAlertDialog(
        show = showDeleteModelDialog != null,
        onDismissRequest = { showDeleteModelDialog = null },
        title = stringResource(R.string.ai_delete_model),
        text = stringResource(R.string.ai_delete_model_confirm),
        confirmText = stringResource(R.string.delete),
        onConfirm = {
            showDeleteModelDialog?.let { onIntent(AiProviderEditIntent.DeleteModel(it)) }
            showDeleteModelDialog = null
        },
        dismissText = stringResource(R.string.cancel),
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
