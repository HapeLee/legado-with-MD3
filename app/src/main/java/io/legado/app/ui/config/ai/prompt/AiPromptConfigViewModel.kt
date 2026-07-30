package io.legado.app.ui.config.ai.prompt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.utils.toastOnUi
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import splitties.init.appCtx

class AiPromptConfigViewModel(
    private val aiProfileGateway: AiProfileGateway
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiPromptConfigUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiPromptConfigEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private data class TaskPromptMeta(
        val taskType: String,
        val nameResId: Int,
        val descResId: Int,
        val defaultPromptResId: Int
    )

    private val taskPromptMetas = listOf(
        TaskPromptMeta(
            taskType = AiTaskType.CHAT,
            nameResId = R.string.ai_prompt_task_chat,
            descResId = R.string.ai_prompt_task_chat_desc,
            defaultPromptResId = R.string.ai_prompt_default_chat
        ),
        TaskPromptMeta(
            taskType = AiTaskType.TRANSLATE_CHAPTER,
            nameResId = R.string.ai_prompt_task_translate,
            descResId = R.string.ai_prompt_task_translate_desc,
            defaultPromptResId = R.string.ai_prompt_default_translate
        ),
        TaskPromptMeta(
            taskType = AiTaskType.SUMMARIZE_CHAPTER,
            nameResId = R.string.ai_prompt_task_summary,
            descResId = R.string.ai_prompt_task_summary_desc,
            defaultPromptResId = R.string.ai_prompt_default_summary
        ),
        TaskPromptMeta(
            taskType = AiTaskType.CLEAN_SELECTION,
            nameResId = R.string.ai_prompt_task_clean,
            descResId = R.string.ai_prompt_task_clean_desc,
            defaultPromptResId = R.string.ai_prompt_default_clean
        ),
        TaskPromptMeta(
            taskType = AiTaskType.TEXT_FACTORY,
            nameResId = R.string.ai_prompt_task_text_factory,
            descResId = R.string.ai_prompt_task_text_factory_desc,
            defaultPromptResId = R.string.ai_prompt_default_text_factory
        ),
        TaskPromptMeta(
            taskType = AiTaskType.ANALYZE_SPEECH,
            nameResId = R.string.ai_prompt_task_analyze_speech,
            descResId = R.string.ai_prompt_task_analyze_speech_desc,
            defaultPromptResId = R.string.ai_prompt_default_analyze_speech
        ),
        TaskPromptMeta(
            taskType = AiTaskType.IDENTIFY_CHARACTERS,
            nameResId = R.string.ai_prompt_task_identify_characters,
            descResId = R.string.ai_prompt_task_identify_characters_desc,
            defaultPromptResId = R.string.ai_prompt_default_identify_characters
        )
    )

    private fun resolveDefaultPrompt(meta: TaskPromptMeta): String {
        return appCtx.getString(meta.defaultPromptResId)
    }

    init {
        viewModelScope.launch {
            val items = taskPromptMetas.map { meta ->
                val config =
                    runCatching { aiProfileGateway.getTaskPreset(meta.taskType) }.getOrNull()
                val defaultPrompt = resolveDefaultPrompt(meta)
                AiPromptTaskItem(
                    taskType = meta.taskType,
                    nameResId = meta.nameResId,
                    descResId = meta.descResId,
                    defaultPrompt = defaultPrompt,
                    currentPrompt = config?.promptTemplate ?: defaultPrompt
                )
            }
            _uiState.update {
                it.copy(loading = false, items = items.toImmutableList())
            }
        }
    }

    fun onIntent(intent: AiPromptConfigIntent) {
        when (intent) {
            is AiPromptConfigIntent.OpenPromptDialog -> {
                _uiState.update {
                    it.copy(
                        activeDialog = AiPromptConfigDialog.EditPrompt(
                            intent.taskType,
                            intent.currentPrompt
                        )
                    )
                }
            }

            is AiPromptConfigIntent.UpdateDialogPrompt -> {
                val dialog =
                    _uiState.value.activeDialog as? AiPromptConfigDialog.EditPrompt ?: return
                _uiState.update {
                    it.copy(activeDialog = dialog.copy(currentPrompt = intent.prompt))
                }
            }

            is AiPromptConfigIntent.CloseDialog -> {
                _uiState.update { it.copy(activeDialog = null) }
            }

            is AiPromptConfigIntent.SavePrompt -> savePrompt(intent.taskType, intent.prompt)
            is AiPromptConfigIntent.ResetPrompt -> resetPrompt(intent.taskType)
            is AiPromptConfigIntent.OpenRestoreAllDialog -> {
                _uiState.update { it.copy(activeDialog = AiPromptConfigDialog.RestoreAllConfirm) }
            }

            is AiPromptConfigIntent.RestoreAllDefaults -> restoreAllDefaults()
        }
    }

    private fun savePrompt(taskType: String, prompt: String) {
        viewModelScope.launch {
            runCatching {
                val existingConfig = aiProfileGateway.getTaskPreset(taskType)
                aiProfileGateway.saveTaskPreset(
                    taskType = taskType,
                    promptTemplate = prompt,
                    temperature = existingConfig?.params?.temperature
                        ?: TranslationConstants.DEFAULT_TEMPERATURE,
                    maxOutputTokens = existingConfig?.params?.maxOutputTokens ?: 0
                )
                _uiState.update { current ->
                    current.copy(
                        items = current.items.map {
                            if (it.taskType == taskType) it.copy(currentPrompt = prompt) else it
                        }.toImmutableList(),
                        activeDialog = null
                    )
                }
            }.onSuccess {
                appCtx.toastOnUi(R.string.ai_config_saved_success)
            }.onFailure { error ->
                _effects.tryEmit(
                    AiPromptConfigEffect.ShowMessage(
                        error.message ?: appCtx.getString(R.string.ai_config_save_failed)
                    )
                )
            }
        }
    }

    private fun resetPrompt(taskType: String) {
        val meta = taskPromptMetas.find { it.taskType == taskType } ?: return
        val defaultPrompt = resolveDefaultPrompt(meta)
        viewModelScope.launch {
            runCatching {
                val existingConfig = aiProfileGateway.getTaskPreset(taskType)
                aiProfileGateway.saveTaskPreset(
                    taskType = taskType,
                    promptTemplate = defaultPrompt,
                    temperature = existingConfig?.params?.temperature
                        ?: TranslationConstants.DEFAULT_TEMPERATURE,
                    maxOutputTokens = existingConfig?.params?.maxOutputTokens ?: 0
                )
                _uiState.update { current ->
                    current.copy(
                        items = current.items.map {
                            if (it.taskType == taskType) it.copy(currentPrompt = defaultPrompt) else it
                        }.toImmutableList()
                    )
                }
            }.onSuccess {
                _effects.tryEmit(AiPromptConfigEffect.ShowMessage(appCtx.getString(R.string.ai_prompt_reset_success)))
            }.onFailure { error ->
                _effects.tryEmit(
                    AiPromptConfigEffect.ShowMessage(
                        error.message ?: appCtx.getString(R.string.ai_config_save_failed)
                    )
                )
            }
        }
    }

    private fun restoreAllDefaults() {
        viewModelScope.launch {
            var allSuccess = true
            for (meta in taskPromptMetas) {
                runCatching {
                    val defaultPrompt = resolveDefaultPrompt(meta)
                    val existingConfig = aiProfileGateway.getTaskPreset(meta.taskType)
                    aiProfileGateway.saveTaskPreset(
                        taskType = meta.taskType,
                        promptTemplate = defaultPrompt,
                        temperature = existingConfig?.params?.temperature
                            ?: TranslationConstants.DEFAULT_TEMPERATURE,
                        maxOutputTokens = existingConfig?.params?.maxOutputTokens ?: 0
                    )
                }.onFailure {
                    allSuccess = false
                }
            }
            _uiState.update { current ->
                current.copy(
                    items = taskPromptMetas.map { meta ->
                        val defaultPrompt = resolveDefaultPrompt(meta)
                        AiPromptTaskItem(
                            taskType = meta.taskType,
                            nameResId = meta.nameResId,
                            descResId = meta.descResId,
                            defaultPrompt = defaultPrompt,
                            currentPrompt = defaultPrompt
                        )
                    }.toImmutableList(),
                    activeDialog = null
                )
            }
            if (allSuccess) {
                _effects.tryEmit(AiPromptConfigEffect.ShowMessage(appCtx.getString(R.string.ai_prompt_restored_all)))
            } else {
                _effects.tryEmit(AiPromptConfigEffect.ShowMessage(appCtx.getString(R.string.ai_config_save_failed)))
            }
        }
    }
}
