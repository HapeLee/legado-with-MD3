package io.legado.app.ui.config.ai

import androidx.compose.foundation.layout.fillMaxSize
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
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.InputSettingItem
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AiModelEditRouteScreen(
    providerId: String?,
    modelProfileId: String?,
    onBackClick: () -> Unit,
    viewModel: AiModelEditViewModel = koinViewModel(
        key = "${providerId.orEmpty()}_${modelProfileId.orEmpty()}",
        parameters = { parametersOf(providerId, modelProfileId) }
    )
) {
    AiModelEditScreen(
        state = viewModel.uiState.collectAsStateWithLifecycle().value,
        effects = viewModel.effects,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiModelEditScreen(
    state: AiModelEditUiState,
    effects: Flow<AiModelEditEffect>,
    onIntent: (AiModelEditIntent) -> Unit,
    onBackClick: () -> Unit
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val contextWindowOptions = buildLimitOptions(
        baseOptions = listOf(
            0 to stringResource(R.string.ai_not_set),
            8_000 to "8K",
            16_000 to "16K",
            32_000 to "32K",
            64_000 to "64K",
            128_000 to "128K",
            200_000 to "200K",
            256_000 to "256K",
            512_000 to "512K",
            1_000_000 to "1M",
            2_000_000 to "2M"
        ),
        currentValue = state.contextWindow,
        currentLabel = stringResource(R.string.ai_current_value, formatTokenLimit(state.contextWindow))
    )
    val maxOutputTokenOptions = buildLimitOptions(
        baseOptions = listOf(
            0 to stringResource(R.string.ai_not_set),
            1_000 to "1K",
            2_000 to "2K",
            4_000 to "4K",
            8_000 to "8K",
            16_000 to "16K",
            32_000 to "32K",
            64_000 to "64K",
            128_000 to "128K"
        ),
        currentValue = state.maxOutputTokens,
        currentLabel = stringResource(R.string.ai_current_value, formatTokenLimit(state.maxOutputTokens))
    )

    LaunchedEffect(Unit) {
        effects.collectLatest { effect ->
            when (effect) {
                is AiModelEditEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                AiModelEditEffect.NavigateBack -> onBackClick()
            }
        }
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.ai_model_edit),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                }
            )
        },
        floatingActionButton = {
            AppFloatingActionButton(
                onClick = { onIntent(AiModelEditIntent.Save) },
                icon = Icons.Default.Save,
                tooltipText = stringResource(R.string.ai_save_default_model)
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
                SplicedColumnGroup(title = stringResource(R.string.ai_model_config)) {
                    if (state.providers.isEmpty()) {
                        ClickableSettingItem(
                            title = stringResource(R.string.ai_provider),
                            description = stringResource(R.string.ai_no_provider_configured),
                            onClick = {}
                        )
                    } else {
                        DropdownListSettingItem(
                            title = stringResource(R.string.ai_provider),
                            selectedValue = state.providerId.orEmpty(),
                            displayEntries = state.providers.map {
                                "${it.name} / ${it.protocol}"
                            }.toTypedArray(),
                            entryValues = state.providers.map { it.id }.toTypedArray(),
                            onValueChange = { onIntent(AiModelEditIntent.SelectProvider(it)) }
                        )
                    }
                    InputSettingItem(
                        title = stringResource(R.string.ai_model_name),
                        value = state.modelName,
                        onConfirm = { onIntent(AiModelEditIntent.UpdateModelName(it)) }
                    )
                    InputSettingItem(
                        title = stringResource(R.string.ai_model_id),
                        value = state.modelId,
                        onConfirm = { onIntent(AiModelEditIntent.UpdateModelId(it)) }
                    )
                    DropdownListSettingItem(
                        title = stringResource(R.string.ai_context_window),
                        selectedValue = state.contextWindow.toString(),
                        displayEntries = contextWindowOptions.displayEntries,
                        entryValues = contextWindowOptions.entryValues,
                        onValueChange = { onIntent(AiModelEditIntent.UpdateContextWindow(it.toIntOrNull() ?: 0)) }
                    )
                    DropdownListSettingItem(
                        title = stringResource(R.string.ai_max_output_tokens),
                        selectedValue = state.maxOutputTokens.toString(),
                        displayEntries = maxOutputTokenOptions.displayEntries,
                        entryValues = maxOutputTokenOptions.entryValues,
                        onValueChange = { onIntent(AiModelEditIntent.UpdateMaxOutputTokens(it.toIntOrNull() ?: 0)) }
                    )
                    SliderSettingItem(
                        title = stringResource(R.string.ai_temperature),
                        value = state.temperature,
                        defaultValue = TranslationConstants.DEFAULT_TEMPERATURE,
                        valueRange = TranslationConstants.MIN_TEMPERATURE..TranslationConstants.MAX_TEMPERATURE,
                        steps = 19,
                        description = state.temperature.toString(),
                        onValueChange = { onIntent(AiModelEditIntent.UpdateTemperature(it)) }
                    )
                }
            }

        }
    }
}

private data class TokenLimitOptions(
    val displayEntries: Array<String>,
    val entryValues: Array<String>
)

private fun buildLimitOptions(
    baseOptions: List<Pair<Int, String>>,
    currentValue: Int,
    currentLabel: String
): TokenLimitOptions {
    val options = if (currentValue > 0 && baseOptions.none { it.first == currentValue }) {
        baseOptions + (currentValue to currentLabel)
    } else {
        baseOptions
    }
    return TokenLimitOptions(
        displayEntries = options.map { it.second }.toTypedArray(),
        entryValues = options.map { it.first.toString() }.toTypedArray()
    )
}

internal fun formatTokenLimit(value: Int): String {
    return when {
        value <= 0 -> "0"
        value >= 1_000_000 && value % 1_000_000 == 0 -> "${value / 1_000_000}M"
        value >= 1_000 && value % 1_000 == 0 -> "${value / 1_000}K"
        else -> value.toString()
    }
}
