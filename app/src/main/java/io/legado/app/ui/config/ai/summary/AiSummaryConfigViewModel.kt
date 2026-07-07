package io.legado.app.ui.config.ai.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.model.AiPromptTemplate
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import splitties.init.appCtx

class AiSummaryConfigViewModel(
    private val aiProfileGateway: AiProfileGateway
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiSummaryConfigUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiSummaryConfigEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            runCatching {
                val config = aiProfileGateway.getTaskPreset(AiTaskType.SUMMARIZE_CHAPTER)
                _uiState.update { current ->
                    current.copy(
                        loading = false,
                        promptTemplate = config?.promptTemplate ?: AiPromptTemplate.DEFAULT_CHAPTER_SUMMARY,
                        temperature = config?.params?.temperature ?: TranslationConstants.DEFAULT_TEMPERATURE,
                        maxOutputTokens = config?.params?.maxOutputTokens ?: 0,
                        defaultPrompt = AiPromptTemplate.DEFAULT_CHAPTER_SUMMARY,
                        initialized = true
                    )
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        loading = false,
                        promptTemplate = AiPromptTemplate.DEFAULT_CHAPTER_SUMMARY,
                        temperature = TranslationConstants.DEFAULT_TEMPERATURE,
                        maxOutputTokens = 0,
                        defaultPrompt = AiPromptTemplate.DEFAULT_CHAPTER_SUMMARY,
                        initialized = true
                    )
                }
                appCtx.toastOnUi("加载章节摘要配置失败: ${error.localizedMessage ?: "使用默认参数"}")
            }
        }
    }

    fun onIntent(intent: AiSummaryConfigIntent) {
        when (intent) {
            is AiSummaryConfigIntent.UpdatePrompt -> {
                _uiState.update { it.copy(promptTemplate = intent.prompt, activeDialog = null) }
            }
            is AiSummaryConfigIntent.OpenPromptDialog -> {
                _uiState.update { it.copy(activeDialog = AiSummaryConfigDialog.EditPrompt(intent.currentPrompt)) }
            }
            is AiSummaryConfigIntent.UpdateDialogPrompt -> {
                val currentDialog = _uiState.value.activeDialog as? AiSummaryConfigDialog.EditPrompt
                if (currentDialog != null) {
                    _uiState.update { it.copy(activeDialog = currentDialog.copy(currentPrompt = intent.prompt)) }
                }
            }
            is AiSummaryConfigIntent.CloseDialog -> {
                _uiState.update { it.copy(activeDialog = null) }
            }
            is AiSummaryConfigIntent.UpdateTemperature -> {
                _uiState.update { it.copy(temperature = intent.temperature) }
            }
            is AiSummaryConfigIntent.UpdateMaxOutputTokens -> {
                _uiState.update { it.copy(maxOutputTokens = intent.tokens) }
            }
            is AiSummaryConfigIntent.ResetPrompt -> {
                _uiState.update { it.copy(promptTemplate = AiPromptTemplate.DEFAULT_CHAPTER_SUMMARY) }
                _effects.tryEmit(AiSummaryConfigEffect.ShowMessage(appCtx.getString(R.string.ai_prompt_reset_success)))
            }
            is AiSummaryConfigIntent.Save -> save()
        }
    }

    private fun save() {
        viewModelScope.launch {
            runCatching {
                val state = _uiState.value
                val savedConfig = aiProfileGateway.saveTaskPreset(
                    taskType = AiTaskType.SUMMARIZE_CHAPTER,
                    promptTemplate = state.promptTemplate,
                    temperature = state.temperature,
                    maxOutputTokens = state.maxOutputTokens
                )
                _uiState.update { current ->
                    current.copy(
                        promptTemplate = savedConfig.promptTemplate,
                        temperature = savedConfig.params.temperature ?: TranslationConstants.DEFAULT_TEMPERATURE,
                        maxOutputTokens = savedConfig.params.maxOutputTokens ?: 0
                    )
                }
            }.onSuccess {
                appCtx.toastOnUi(R.string.ai_config_saved_success)
                _effects.tryEmit(AiSummaryConfigEffect.NavigateBack)
            }.onFailure { error ->
                _effects.tryEmit(
                    AiSummaryConfigEffect.ShowMessage(
                        error.message ?: appCtx.getString(R.string.ai_config_save_failed)
                    )
                )
            }
        }
    }
}
