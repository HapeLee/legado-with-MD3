package io.legado.app.feature.settings.ai.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.ai.AiProfileGateway
import io.legado.app.domain.model.AiPromptTemplate
import io.legado.app.domain.model.AiTaskType
import io.legado.app.domain.model.TranslationConstants
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// M5-4b：从 `:app` 的 `io.legado.app.ui.config.ai.summary` 迁来。
//
// 这是本批第一个「**VM 自己带着平台依赖**」的页：迁移前它直接
// `appCtx.getString(R.string.x)` 拼提示文案（3 处）。共享层没有 `Context` ⇒ 按 M5-1c 的
// about 先例改成发**枚举**，由 Screen 侧查表（见 `AiSummaryMessages.kt`）。
//
// 三处文案的对应关系：
//   迁移前                                   迁移后
//   `getString(ai_prompt_reset_success)`   → ShowMessage(ResetPromptSuccess)
//   `getString(ai_config_saved_success)`   → ShowMessage(SaveSuccess)
//   `error.message ?: getString(ai_config_save_failed)`
//                                          → 有 message 时 ShowRawMessage(message)，
//                                            否则 ShowMessage(SaveFailed)
//
// ⚠️ 最后那条**不能**合成一个枚举参数：原语义是「有异常文案就用它，没有才回落资源文案」，
// 两个来源不同。合成会丢掉这个 fallback。
//
// 另外 `init` 里那句「加载章节摘要配置失败: …」迁移前就是**硬编码**（不是资源），
// 迁移后保持硬编码 —— 这是既有行为，不是本片引入的，也不在本次改动范围内。
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
                _effects.tryEmit(
                    AiSummaryConfigEffect.ShowRawMessage(
                        "加载章节摘要配置失败: ${error.localizedMessage ?: "使用默认参数"}"
                    )
                )
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
                _effects.tryEmit(
                    AiSummaryConfigEffect.ShowMessage(AiSummaryMessage.ResetPromptSuccess)
                )
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
                _effects.tryEmit(
                    AiSummaryConfigEffect.ShowMessage(AiSummaryMessage.SaveSuccess)
                )
                _effects.tryEmit(AiSummaryConfigEffect.NavigateBack)
            }.onFailure { error ->
                // 有异常文案就用它，没有才回落到资源里的「保存失败」
                // ——这两个来源不能合成枚举的一个参数，见本文件头部注释。
                _effects.tryEmit(
                    error.message?.let { AiSummaryConfigEffect.ShowRawMessage(it) }
                        ?: AiSummaryConfigEffect.ShowMessage(AiSummaryMessage.SaveFailed)
                )
            }
        }
    }
}
