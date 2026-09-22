package io.legado.app.feature.settings.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.core.platform.JsonCodec
import io.legado.app.domain.ai.AiProfileGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiModelDraft
import io.legado.app.domain.model.AiReasoningLevel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// M5-5b：从 `:app` 的 `io.legado.app.ui.config.ai` 迁来。
//
// **唯一的非机械改动**：`GSON.fromJson(json, AiGenerationParams::class.java)` →
// `JsonCodec.fromJsonObject(json, AiGenerationParams::class)`（`:core:platform` 的
// `expect object`，是平台原语、无需注入）。这是 ai 域两处 `GSON` 之一。
//
// ⚠️ 顺带修掉一个空安全差异：`GSON.fromJson(...)` 在 Kotlin 里是**平台类型**，
// 返回 null 时 `runCatching{}.getOrDefault(...)` **不会**兜住（null 不是异常），
// 后面 `params.temperature` 会 NPE。`JsonCodec.fromJsonObject` 的返回类型是显式的
// `T?`，所以这里用 `?: AiGenerationParams()` 兜住 —— 行为等价于「正常 JSON」的情况，
// 且把原来的潜在 NPE 变成明确的回落值。
//
// 其余逻辑逐字保留，包括 4 条**硬编码英文**提示（迁移前就如此）。
class AiModelEditViewModel(
    private val initialProviderId: String?,
    private val initialModelProfileId: String?,
    private val aiProfileGateway: AiProfileGateway,
    private val aiTextGateway: AiTextGateway
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AiModelEditUiState(
            providerId = initialProviderId,
            modelProfileId = initialModelProfileId
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiModelEditEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                aiProfileGateway.observeProviders(),
                aiProfileGateway.observeModels()
            ) { providers, models ->
                providers to models
            }.collect { (providers, models) ->
                val providerOptions = providers.map {
                    AiModelProviderOptionUi(
                        id = it.id,
                        name = it.name,
                        protocol = it.protocol,
                        baseUrl = it.baseUrl
                    )
                }.toImmutableList()
                _uiState.update { current ->
                    val model = models.firstOrNull { it.id == initialModelProfileId }
                    val params = parseParams(model?.defaultParamsJson)
                    val selectedProviderId = current.providerId
                        ?: model?.providerId
                        ?: initialProviderId
                        ?: providers.firstOrNull()?.id
                    if (current.initialized) {
                        current.copy(providers = providerOptions)
                    } else {
                        current.copy(
                            providers = providerOptions,
                            providerId = selectedProviderId,
                            modelProfileId = model?.id ?: current.modelProfileId,
                            modelName = model?.displayName.orEmpty(),
                            modelId = model?.modelId.orEmpty(),
                            contextWindow = model?.contextWindow ?: 0,
                            maxOutputTokens = model?.maxOutputTokens ?: 0,
                            temperature = params.temperature ?: current.temperature,
                            reasoningLevel = params.reasoningLevel.toModelConfigLevel(),
                            initialized = true
                        )
                    }
                }
            }
        }
    }

    fun onIntent(intent: AiModelEditIntent) {
        when (intent) {
            is AiModelEditIntent.SelectProvider -> _uiState.update { it.copy(providerId = intent.providerId) }
            is AiModelEditIntent.UpdateModelName -> _uiState.update { it.copy(modelName = intent.value) }
            is AiModelEditIntent.UpdateModelId -> _uiState.update { it.copy(modelId = intent.value) }
            is AiModelEditIntent.UpdateContextWindow -> _uiState.update { it.copy(contextWindow = intent.value) }
            is AiModelEditIntent.UpdateMaxOutputTokens -> _uiState.update { it.copy(maxOutputTokens = intent.value) }
            is AiModelEditIntent.UpdateTemperature -> _uiState.update { it.copy(temperature = intent.value) }
            is AiModelEditIntent.UpdateReasoningLevel -> _uiState.update { it.copy(reasoningLevel = intent.value) }
            AiModelEditIntent.Save -> save(navigateBack = true)
            AiModelEditIntent.TestConnection -> testConnection()
        }
    }

    private fun save(navigateBack: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            runCatching {
                val model = aiProfileGateway.saveModel(_uiState.value.toDraft())
                aiProfileGateway.setDefaultModel(model.id)
                model
            }.onSuccess { model ->
                _uiState.update { it.copy(modelProfileId = model.id) }
                _effects.tryEmit(AiModelEditEffect.ShowMessage("Default AI model saved"))
                if (navigateBack) {
                    _effects.tryEmit(AiModelEditEffect.NavigateBack)
                }
            }.onFailure { error ->
                _effects.tryEmit(AiModelEditEffect.ShowMessage(error.message ?: "Failed to save AI model"))
            }
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    private fun testConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true) }
            runCatching {
                val model = aiProfileGateway.saveModel(_uiState.value.toDraft())
                val preset = aiProfileGateway.setDefaultModel(model.id)
                aiTextGateway.generate(
                    AiGenerateRequest(
                        model = preset.model,
                        messages = listOf(
                            AiMessage(AiMessageRole.SYSTEM, "Reply with OK only."),
                            AiMessage(AiMessageRole.USER, "Connection test")
                        ),
                        params = preset.params.copy(maxOutputTokens = 16)
                    )
                ).getOrThrow()
            }.onSuccess {
                _effects.tryEmit(AiModelEditEffect.ShowMessage("AI connection test succeeded"))
            }.onFailure { error ->
                _effects.tryEmit(AiModelEditEffect.ShowMessage(error.message ?: "AI connection test failed"))
            }
            _uiState.update { it.copy(isTesting = false) }
        }
    }

    private fun AiModelEditUiState.toDraft(): AiModelDraft {
        return AiModelDraft(
            modelProfileId = modelProfileId,
            providerId = providerId.orEmpty(),
            modelName = modelName.trim(),
            modelId = modelId.trim(),
            contextWindow = contextWindow,
            maxOutputTokens = maxOutputTokens,
            temperature = temperature,
            reasoningLevel = reasoningLevel
        )
    }

    private fun AiReasoningLevel.toModelConfigLevel(): AiReasoningLevel {
        return takeIf { it in AiReasoningLevel.modelConfigEntries } ?: AiReasoningLevel.MEDIUM
    }

    private fun parseParams(json: String?): AiGenerationParams {
        if (json.isNullOrBlank()) return AiGenerationParams()
        return runCatching {
            JsonCodec.fromJsonObject(json, AiGenerationParams::class)
        }.getOrNull() ?: AiGenerationParams()
    }
}
