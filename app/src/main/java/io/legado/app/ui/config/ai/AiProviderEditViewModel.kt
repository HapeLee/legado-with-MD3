package io.legado.app.ui.config.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiModelDraft
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiProviderDraft
import io.legado.app.domain.model.AiProviderPresets
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.utils.GSON
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import splitties.init.appCtx

class AiProviderEditViewModel(
    private val initialProviderId: String?,
    private val aiProfileGateway: AiProfileGateway,
    private val aiTextGateway: AiTextGateway
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AiProviderEditUiState(
            providerPresets = AiProviderPresets.items.map {
                AiProviderPresetUi(
                    id = it.id,
                    name = it.name,
                    protocol = it.protocol,
                    baseUrl = it.baseUrl,
                    modelsUrl = it.modelsUrl,
                    modelName = it.modelName,
                    modelId = it.modelId
                )
            }.toImmutableList(),
            providerId = initialProviderId
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiProviderEditEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                aiProfileGateway.observeProviders(),
                aiProfileGateway.observeModels()
            ) { providers, models ->
                providers to models
            }.collect { (providers, models) ->
                _uiState.update { current ->
                    val providerId = current.providerId ?: initialProviderId
                    val provider = providerId?.let { id -> providers.firstOrNull { it.id == id } }
                    val providerModels = models
                        .filter { it.providerId == providerId }
                        .map { model ->
                            val params = parseParams(model.defaultParamsJson)
                            AiProviderModelUi(
                                modelProfileId = model.id,
                                providerId = model.providerId,
                                modelName = model.displayName,
                                modelId = model.modelId,
                                contextWindow = model.contextWindow,
                                maxOutputTokens = model.maxOutputTokens,
                                temperature = params.temperature ?: TranslationConstants.DEFAULT_TEMPERATURE
                            )
                        }
                        .toImmutableList()
                    if (current.initialized) {
                        current.copy(providerModels = providerModels)
                    } else {
                        current.copy(
                            providerId = provider?.id ?: current.providerId,
                            providerName = provider?.name ?: current.providerName,
                            protocol = provider?.protocol ?: current.protocol,
                            baseUrl = provider?.baseUrl.orEmpty(),
                            modelsUrl = provider?.modelsUrl.orEmpty(),
                            apiKey = provider?.apiKey.orEmpty(),
                            providerModels = providerModels,
                            initialized = true
                        )
                    }
                }
            }
        }
    }

    fun onIntent(intent: AiProviderEditIntent) {
        when (intent) {
            is AiProviderEditIntent.ApplyProviderPreset -> applyProviderPreset(intent.id)
            is AiProviderEditIntent.UpdateProviderName -> _uiState.update { it.copy(providerName = intent.value) }
            is AiProviderEditIntent.UpdateProtocol -> _uiState.update {
                it.copy(protocol = intent.value, selectedProviderPresetId = "", fetchedModels = emptyList<AiFetchedModelUi>().toImmutableList())
            }
            is AiProviderEditIntent.UpdateBaseUrl -> _uiState.update { it.copy(baseUrl = intent.value, selectedProviderPresetId = "") }
            is AiProviderEditIntent.UpdateModelsUrl -> _uiState.update { it.copy(modelsUrl = intent.value, selectedProviderPresetId = "") }
            is AiProviderEditIntent.UpdateApiKey -> _uiState.update { it.copy(apiKey = intent.value) }
            AiProviderEditIntent.AddModel -> _uiState.update {
                it.copy(editingModel = AiProviderModelEditorUi(temperature = TranslationConstants.DEFAULT_TEMPERATURE.toString()))
            }
            is AiProviderEditIntent.EditModel -> editModel(intent.modelProfileId)
            AiProviderEditIntent.DismissModelEditor -> _uiState.update { it.copy(editingModel = null) }
            is AiProviderEditIntent.UpdateEditingModelName -> updateEditingModel { copy(modelName = intent.value) }
            is AiProviderEditIntent.UpdateEditingModelId -> updateEditingModel { copy(modelId = intent.value) }
            is AiProviderEditIntent.UpdateEditingContextWindow -> updateEditingModel { copy(contextWindow = intent.value) }
            is AiProviderEditIntent.UpdateEditingMaxOutputTokens -> updateEditingModel { copy(maxOutputTokens = intent.value) }
            is AiProviderEditIntent.UpdateEditingTemperature -> updateEditingModel { copy(temperature = intent.value) }
            AiProviderEditIntent.SaveEditingModel -> saveEditingModel()
            AiProviderEditIntent.TestConnection -> testConnection()
            AiProviderEditIntent.SaveProvider -> saveProvider()
            AiProviderEditIntent.SyncModels -> syncModels()
            AiProviderEditIntent.DeleteProvider -> deleteProvider()
            is AiProviderEditIntent.DeleteModel -> deleteModel(intent.modelProfileId)
        }
    }

    private fun applyProviderPreset(id: String) {
        if (id.isBlank()) {
            _uiState.update { it.copy(selectedProviderPresetId = "") }
            return
        }
        val preset = AiProviderPresets.items.firstOrNull { it.id == id } ?: return
        _uiState.update {
            it.copy(
                selectedProviderPresetId = preset.id,
                providerName = preset.name,
                protocol = preset.protocol,
                baseUrl = preset.baseUrl,
                modelsUrl = preset.modelsUrl,
                fetchedModels = emptyList<AiFetchedModelUi>().toImmutableList()
            )
        }
    }

    private fun editModel(modelProfileId: String) {
        val model = _uiState.value.providerModels.firstOrNull { it.modelProfileId == modelProfileId } ?: return
        _uiState.update {
            it.copy(
                editingModel = AiProviderModelEditorUi(
                    modelProfileId = model.modelProfileId,
                    modelName = model.modelName,
                    modelId = model.modelId,
                    contextWindow = model.contextWindow.takeIf { value -> value > 0 }?.toString().orEmpty(),
                    maxOutputTokens = model.maxOutputTokens.takeIf { value -> value > 0 }?.toString().orEmpty(),
                    temperature = model.temperature.toString()
                )
            )
        }
    }

    private fun updateEditingModel(update: AiProviderModelEditorUi.() -> AiProviderModelEditorUi) {
        _uiState.update {
            it.copy(editingModel = it.editingModel?.update())
        }
    }

    private fun saveEditingModel() {
        viewModelScope.launch {
            val editor = _uiState.value.editingModel ?: return@launch
            _uiState.update { it.copy(isSaving = true) }
            runCatching {
                val providerId = _uiState.value.providerId
                    ?: aiProfileGateway.saveProvider(_uiState.value.toDraft()).id
                aiProfileGateway.saveModel(
                    AiModelDraft(
                        modelProfileId = editor.modelProfileId,
                        providerId = providerId,
                        modelName = editor.modelName.trim(),
                        modelId = editor.modelId.trim(),
                        contextWindow = editor.contextWindow.toIntOrNull() ?: 0,
                        maxOutputTokens = editor.maxOutputTokens.toIntOrNull() ?: 0,
                        temperature = editor.temperature.toFloatOrNull()
                            ?: TranslationConstants.DEFAULT_TEMPERATURE
                    )
                )
            }.onSuccess { model ->
                _uiState.update { it.copy(editingModel = null, providerId = model.providerId) }
                _effects.tryEmit(AiProviderEditEffect.ShowMessage("AI model saved"))
            }.onFailure { error ->
                _effects.tryEmit(AiProviderEditEffect.ShowMessage(error.message ?: "Failed to save AI model"))
            }
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    private fun saveProvider() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            runCatching {
                aiProfileGateway.saveProvider(_uiState.value.toDraft())
            }.onSuccess { provider ->
                _uiState.update { it.copy(providerId = provider.id) }
                _effects.tryEmit(AiProviderEditEffect.ShowMessage("AI provider saved"))
                _effects.tryEmit(AiProviderEditEffect.NavigateBack)
            }.onFailure { error ->
                _effects.tryEmit(AiProviderEditEffect.ShowMessage(error.message ?: "Failed to save AI provider"))
            }
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    private fun testConnection() {
        if (_uiState.value.isTesting || _uiState.value.isSaving || _uiState.value.isFetchingModels) return
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true) }
            try {
                val models = aiTextGateway.fetchModels(_uiState.value.toProviderConfig()).getOrThrow()
                val count = models.size
                val message = if (count == 0) {
                    appCtx.getString(R.string.ai_test_success_no_models)
                } else {
                    appCtx.getString(R.string.ai_test_success_with_models, count)
                }
                _effects.tryEmit(AiProviderEditEffect.ShowMessage(message))
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                val errorMsg = error.message
                val failMsg = appCtx.getString(R.string.ai_test_failed)
                val message = if (errorMsg.isNullOrBlank()) failMsg else "$failMsg: $errorMsg"
                _effects.tryEmit(AiProviderEditEffect.ShowMessage(message))
            } finally {
                _uiState.update { it.copy(isTesting = false) }
            }
        }
    }

    private fun syncModels() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, isFetchingModels = true) }
            runCatching {
                aiProfileGateway.saveProvider(_uiState.value.toDraft())
            }.onSuccess { provider ->
                _uiState.update { it.copy(providerId = provider.id) }
                val state = _uiState.value
                runCatching {
                    aiTextGateway.fetchModels(state.toProviderConfig(provider.id)).getOrThrow()
                }.onSuccess { models ->
                    aiProfileGateway.importProviderModels(provider.id, models)
                    applyFetchedModels(provider.id, models)
                }.onFailure { error ->
                    _effects.tryEmit(
                        AiProviderEditEffect.ShowMessage(
                            error.message ?: "AI provider saved, but failed to fetch models"
                        )
                    )
                }
            }.onFailure { error ->
                _effects.tryEmit(AiProviderEditEffect.ShowMessage(error.message ?: "Failed to save AI provider"))
            }
            _uiState.update { it.copy(isSaving = false, isFetchingModels = false) }
        }
    }

    private fun deleteProvider() {
        val providerId = _uiState.value.providerId ?: return
        viewModelScope.launch {
            runCatching {
                aiProfileGateway.deleteProvider(providerId)
            }.onSuccess {
                _effects.tryEmit(AiProviderEditEffect.ShowMessage("AI provider deleted"))
                _effects.tryEmit(AiProviderEditEffect.NavigateBackAfterDelete)
            }.onFailure { error ->
                _effects.tryEmit(AiProviderEditEffect.ShowMessage(error.message ?: "Failed to delete AI provider"))
            }
        }
    }

    private fun deleteModel(modelProfileId: String) {
        viewModelScope.launch {
            runCatching {
                aiProfileGateway.deleteModel(modelProfileId)
            }.onSuccess {
                _uiState.update { it.copy(editingModel = null) }
                _effects.tryEmit(AiProviderEditEffect.ShowMessage("AI model deleted"))
            }.onFailure { error ->
                _effects.tryEmit(AiProviderEditEffect.ShowMessage(error.message ?: "Failed to delete AI model"))
            }
        }
    }

    private fun applyFetchedModels(providerId: String, models: List<AiAvailableModel>) {
        val options = models.map {
            AiFetchedModelUi(
                id = it.id,
                name = it.name,
                contextWindow = it.contextWindow,
                maxOutputTokens = it.maxOutputTokens
            )
        }.toImmutableList()
        _uiState.update {
            it.copy(
                providerId = providerId,
                fetchedModels = options
            )
        }
        val message = if (options.isEmpty()) {
            "No models found"
        } else {
            "Fetched and saved ${options.size} models"
        }
        _effects.tryEmit(AiProviderEditEffect.ShowMessage(message))
    }

    private fun AiProviderEditUiState.toDraft(): AiProviderDraft {
        return AiProviderDraft(
            providerId = providerId,
            providerName = providerName,
            protocol = protocol,
            baseUrl = baseUrl,
            modelsUrl = modelsUrl,
            apiKey = apiKey
        )
    }

    private fun AiProviderEditUiState.toProviderConfig(id: String = providerId ?: "test_connection_id"): AiProviderConfig {
        return AiProviderConfig(
            id = id,
            name = providerName,
            protocol = protocol,
            baseUrl = baseUrl,
            apiKey = apiKey,
            modelsUrl = modelsUrl.ifBlank { null }
        )
    }

    private fun parseParams(json: String?): AiGenerationParams {
        if (json.isNullOrBlank()) return AiGenerationParams()
        return runCatching {
            GSON.fromJson(json, AiGenerationParams::class.java)
        }.getOrDefault(AiGenerationParams())
    }
}
