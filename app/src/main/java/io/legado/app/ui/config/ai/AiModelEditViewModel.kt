package io.legado.app.ui.config.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiModelDraft
import io.legado.app.utils.GSON
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
            temperature = temperature
        )
    }

    private fun parseParams(json: String?): AiGenerationParams {
        if (json.isNullOrBlank()) return AiGenerationParams()
        return runCatching {
            GSON.fromJson(json, AiGenerationParams::class.java)
        }.getOrDefault(AiGenerationParams())
    }
}
