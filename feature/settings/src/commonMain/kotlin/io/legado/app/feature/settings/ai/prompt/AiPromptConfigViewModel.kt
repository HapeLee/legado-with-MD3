package io.legado.app.feature.settings.ai.prompt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.core.platform.Toaster
import io.legado.app.domain.ai.AiProfileGateway
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.ai_config_saved_success
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

// M5-4c：从 `:app` 的 `io.legado.app.ui.config.ai.prompt` 迁来。
//
// 三处改动，都不是机械搬迁：
//
// 1. **去掉 `TaskPromptMeta`（`nameResId`/`descResId`/`defaultPromptResId` 三个 `Int`）**，
//    改用 [AiPromptTask] 枚举（`AiPromptTask.entries` 就是原来那张表的顺序）。
//    原因见 `AiPromptConfigContract.kt`：**Android 资源 id 不该出现在 UI 状态里**，
//    而 CMP 的资源也不是 `Int`。
//
// 2. **默认提示词改成 `suspend` 取**（[AiPromptTask.defaultPrompt]）。
//    迁移前是 `appCtx.getString(meta.defaultPromptResId)`；共享层没有 `Context`，
//    用 CMP 的 `getString`（commonMain 可用）。它**不是**平台依赖，所以本 VM 允许
//    直接取——与 `ai/summary` 不同：那边的文案只用于展示（可以发枚举让 UI 侧查表），
//    这边的默认提示词要**写回 gateway**，VM 必须拿到实际字符串。
//
// 3. **保存成功的提示改由 [Toaster] 直发**。迁移前是 `appCtx.toastOnUi(...)`（**Toast**），
//    而 `ShowMessage` 在 Screen 里是 **Snackbar**——为保住这个差异，这一条不走 Effect，
//    而是注入 `:core:platform` 既有的 `Toaster` 共享契约（`:app` 已绑定实现）。
class AiPromptConfigViewModel(
    private val aiProfileGateway: AiProfileGateway,
    private val toaster: Toaster,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiPromptConfigUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<AiPromptConfigEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            val items = AiPromptTask.entries.map { task ->
                val config =
                    runCatching { aiProfileGateway.getTaskPreset(task.taskType) }.getOrNull()
                val defaultPrompt = task.defaultPrompt()
                AiPromptTaskItem(
                    task = task,
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
                toaster.toast(getString(Res.string.ai_config_saved_success))
            }.onFailure { error ->
                emitFailure(error)
            }
        }
    }

    private fun resetPrompt(taskType: String) {
        val task = AiPromptTask.entries.find { it.taskType == taskType } ?: return
        viewModelScope.launch {
            val defaultPrompt = task.defaultPrompt()
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
                            if (it.taskType == taskType) it.copy(currentPrompt = defaultPrompt)
                            else it
                        }.toImmutableList()
                    )
                }
            }.onSuccess {
                _effects.tryEmit(
                    AiPromptConfigEffect.ShowMessage(AiPromptMessage.ResetSuccess)
                )
            }.onFailure { error ->
                emitFailure(error)
            }
        }
    }

    private fun restoreAllDefaults() {
        viewModelScope.launch {
            var allSuccess = true
            for (task in AiPromptTask.entries) {
                runCatching {
                    val defaultPrompt = task.defaultPrompt()
                    val existingConfig = aiProfileGateway.getTaskPreset(task.taskType)
                    aiProfileGateway.saveTaskPreset(
                        taskType = task.taskType,
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
                    items = AiPromptTask.entries.map { task ->
                        val defaultPrompt = task.defaultPrompt()
                        AiPromptTaskItem(
                            task = task,
                            defaultPrompt = defaultPrompt,
                            currentPrompt = defaultPrompt
                        )
                    }.toImmutableList(),
                    activeDialog = null
                )
            }
            _effects.tryEmit(
                AiPromptConfigEffect.ShowMessage(
                    if (allSuccess) AiPromptMessage.RestoredAll else AiPromptMessage.SaveFailed
                )
            )
        }
    }

    /**
     * `error.message ?: getString(ai_config_save_failed)` —— 「有异常文案就用它，没有才回落
     * 资源文案」。两个来源不同，所以 Effect 分成 `ShowRawMessage` 与 `ShowMessage` 两个。
     */
    private suspend fun emitFailure(error: Throwable) {
        _effects.tryEmit(
            error.message?.let { AiPromptConfigEffect.ShowRawMessage(it) }
                ?: AiPromptConfigEffect.ShowMessage(AiPromptMessage.SaveFailed)
        )
    }
}
