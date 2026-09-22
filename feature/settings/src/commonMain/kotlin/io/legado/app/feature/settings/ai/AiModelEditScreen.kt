package io.legado.app.feature.settings.ai

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
import androidx.compose.ui.unit.dp
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.ai_context_window
import io.legado.app.feature.settings.res.ai_current_value
import io.legado.app.feature.settings.res.ai_max_output_tokens
import io.legado.app.feature.settings.res.ai_model_config
import io.legado.app.feature.settings.res.ai_model_edit
import io.legado.app.feature.settings.res.ai_model_id
import io.legado.app.feature.settings.res.ai_model_name
import io.legado.app.feature.settings.res.ai_no_provider_configured
import io.legado.app.feature.settings.res.ai_not_set
import io.legado.app.feature.settings.res.ai_provider
import io.legado.app.feature.settings.res.ai_reasoning_level_high
import io.legado.app.feature.settings.res.ai_reasoning_level_low
import io.legado.app.feature.settings.res.ai_reasoning_level_max
import io.legado.app.feature.settings.res.ai_reasoning_level_medium
import io.legado.app.feature.settings.res.ai_reasoning_level_xhigh
import io.legado.app.feature.settings.res.ai_save_default_model
import io.legado.app.feature.settings.res.ai_temperature
import io.legado.app.feature.settings.res.ai_thinking_strength
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
import org.jetbrains.compose.resources.stringResource

// M5-5b：从 `:app` 的 `io.legado.app.ui.config.ai.AiModelEditScreen` 迁来。
//
// 差异两类：`R.string.*` → `Res.string.*`（14 条），`AiModelEditRouteScreen` 不搬
// （它只做 `koinViewModel(parameters = { parametersOf(providerId, modelProfileId) })`，
// 宿主那侧照原样接）。
//
// ⚠️ `formatTokenLimit` 原来在这个文件里是 `internal`，而**同包的 `AiProviderEditScreen`
// 也在用它** ⇒ 本片在 `:app` 留了一份**临时副本** `ai/TokenLimitFormat.kt`，
// 等 `AiProviderEdit` 迁走后删除（否则 `:app` 编译不过）。

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
            0 to stringResource(Res.string.ai_not_set),
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
        currentLabel = stringResource(Res.string.ai_current_value, formatTokenLimit(state.contextWindow))
    )
    val maxOutputTokenOptions = buildLimitOptions(
        baseOptions = listOf(
            0 to stringResource(Res.string.ai_not_set),
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
        currentLabel = stringResource(Res.string.ai_current_value, formatTokenLimit(state.maxOutputTokens))
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
                title = stringResource(Res.string.ai_model_edit),
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
                tooltipText = stringResource(Res.string.ai_save_default_model)
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
                SplicedColumnGroup(title = stringResource(Res.string.ai_model_config)) {
                    if (state.providers.isEmpty()) {
                        ClickableSettingItem(
                            title = stringResource(Res.string.ai_provider),
                            description = stringResource(Res.string.ai_no_provider_configured),
                            onClick = {}
                        )
                    } else {
                        DropdownListSettingItem(
                            title = stringResource(Res.string.ai_provider),
                            selectedValue = state.providerId.orEmpty(),
                            displayEntries = state.providers.map {
                                "${it.name} / ${it.protocol}"
                            }.toTypedArray(),
                            entryValues = state.providers.map { it.id }.toTypedArray(),
                            onValueChange = { onIntent(AiModelEditIntent.SelectProvider(it)) }
                        )
                    }
                    InputSettingItem(
                        title = stringResource(Res.string.ai_model_name),
                        value = state.modelName,
                        onConfirm = { onIntent(AiModelEditIntent.UpdateModelName(it)) }
                    )
                    InputSettingItem(
                        title = stringResource(Res.string.ai_model_id),
                        value = state.modelId,
                        onConfirm = { onIntent(AiModelEditIntent.UpdateModelId(it)) }
                    )
                    DropdownListSettingItem(
                        title = stringResource(Res.string.ai_context_window),
                        selectedValue = state.contextWindow.toString(),
                        displayEntries = contextWindowOptions.displayEntries,
                        entryValues = contextWindowOptions.entryValues,
                        onValueChange = { onIntent(AiModelEditIntent.UpdateContextWindow(it.toIntOrNull() ?: 0)) }
                    )
                    DropdownListSettingItem(
                        title = stringResource(Res.string.ai_max_output_tokens),
                        selectedValue = state.maxOutputTokens.toString(),
                        displayEntries = maxOutputTokenOptions.displayEntries,
                        entryValues = maxOutputTokenOptions.entryValues,
                        onValueChange = { onIntent(AiModelEditIntent.UpdateMaxOutputTokens(it.toIntOrNull() ?: 0)) }
                    )
                    DropdownListSettingItem(
                        title = stringResource(Res.string.ai_thinking_strength),
                        selectedValue = state.reasoningLevel.effort,
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
                            onIntent(AiModelEditIntent.UpdateReasoningLevel(AiReasoningLevel.fromEffort(it)))
                        }
                    )
                    SliderSettingItem(
                        title = stringResource(Res.string.ai_temperature),
                        value = state.temperature,
                        defaultValue = TranslationConstants.DEFAULT_TEMPERATURE,
                        valueRange = TranslationConstants.MIN_TEMPERATURE..TranslationConstants.MAX_TEMPERATURE,
                        steps = 19,
                        description = state.temperature.toString(),
                        decimal = true,
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
