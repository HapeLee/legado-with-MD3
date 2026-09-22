package io.legado.app.feature.settings.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.ai.AiProfileGateway
import io.legado.app.domain.model.AiTaskType
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// M5-5a：从 `:app` 的 `io.legado.app.ui.config.ai` 迁来（**只改包名**，逻辑逐字一致）。
//
// 它是本批**最干净**的一个 VM：唯一依赖是 `:domain:ai` 的 `AiProfileGateway`，
// 没有 `R` / `appCtx` / `GSON` / `toastOnUi` —— 因此可测性没有障碍（不像 ai/prompt
// 需要 M5-4e 那样注入字符串来源）。
//
// 两条提示文案（`"Default AI model saved"` / `"Failed to save default AI model"`）是
// **硬编码英文**，迁移前就如此 ⇒ 原样保留（见 `AiConfigContract.kt` 的注释）。
//
// 编排逻辑是本页真正值得测的部分：`combine(providers, models, presets)` 三个流，
// 再算「当前模型」= **默认的翻译预设所指的模型，没有则取第一个模型**，
// 并把模型按 provider 分组供下拉面板使用。
class AiConfigViewModel(
    private val aiProfileGateway: AiProfileGateway
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiConfigUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiConfigEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                aiProfileGateway.observeProviders(),
                aiProfileGateway.observeModels(),
                aiProfileGateway.observePresets()
            ) { providers, models, presets ->
                Triple(providers, models, presets)
            }.collect { (providers, models, presets) ->
                val providerMap = providers.associateBy { it.id }
                val defaultTranslatePreset = presets.firstOrNull {
                    it.taskType == AiTaskType.TRANSLATE_CHAPTER && it.isDefault
                }
                val currentModelProfileId = defaultTranslatePreset?.modelProfileId
                    ?: models.firstOrNull()?.id
                val modelItems = models.mapNotNull { model ->
                    val provider = providerMap[model.providerId] ?: return@mapNotNull null
                    AiModelListItemUi(
                        providerId = provider.id,
                        modelProfileId = model.id,
                        providerName = provider.name,
                        protocol = provider.protocol,
                        baseUrl = provider.baseUrl,
                        modelName = model.displayName,
                        modelId = model.modelId,
                        contextWindow = model.contextWindow,
                        maxOutputTokens = model.maxOutputTokens,
                        enabled = provider.enabled && model.enabled,
                        isCurrent = model.id == currentModelProfileId
                    )
                }
                val modelItemsByProvider = modelItems.groupBy { it.providerId }
                val providerItems = providers.map { provider ->
                    val providerModels = modelItemsByProvider[provider.id].orEmpty().toImmutableList()
                    AiProviderListItemUi(
                        providerId = provider.id,
                        providerName = provider.name,
                        protocol = provider.protocol,
                        baseUrl = provider.baseUrl,
                        modelCount = providerModels.size,
                        enabled = provider.enabled,
                        models = providerModels
                    )
                }.toImmutableList()
                val modelNameById = models.associate { it.id to it.displayName }
                val currentModelName = currentModelProfileId
                    ?.let { modelNameById[it] }
                    .orEmpty()

                _uiState.update {
                    it.copy(
                        providers = providerItems,
                        models = modelItems.toImmutableList(),
                        currentModelProfileId = currentModelProfileId,
                        currentModelName = currentModelName,
                        providerCount = providers.size,
                        modelCount = models.size,
                        presetCount = presets.size
                    )
                }
            }
        }
    }

    fun onIntent(intent: AiConfigIntent) {
        when (intent) {
            is AiConfigIntent.SetDefaultModel -> setDefaultModel(intent.modelProfileId)
        }
    }

    private fun setDefaultModel(modelProfileId: String) {
        viewModelScope.launch {
            runCatching {
                aiProfileGateway.setDefaultModel(modelProfileId)
            }.onSuccess {
                _effects.tryEmit(AiConfigEffect.ShowMessage("Default AI model saved"))
            }.onFailure { error ->
                _effects.tryEmit(AiConfigEffect.ShowMessage(error.message ?: "Failed to save default AI model"))
            }
        }
    }
}
