package io.legado.app.feature.settings.translation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.gateway.TranslationSettingsGateway
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// M5-2d：从 `:app` 的 `io.legado.app.ui.config.translation` 迁来（**只改包名**，
// 逻辑逐字一致）。
//
// 与 labConfig 的 VM 同形：唯一依赖是 `:core:data` 的 `TranslationSettingsGateway`，
// 因此**零新增平台契约**（这也是审计把它评为 A 级「零 app 私有依赖」的原因）。
// 差异有两点：
//   - 这里**没有** `LocalPageEstimateMetrics` 那样的共享层直连；
//   - 三个 Intent 全部落到 `settingsGateway.update{}`，不像 labConfig 有一条
//     「导出 ⇒ 发 Effect 并提前返回」的分支。
class TranslationConfigViewModel(
    private val settingsGateway: TranslationSettingsGateway,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        TranslationConfigUiState(settings = settingsGateway.currentSettings)
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<TranslationConfigEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsGateway.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    fun onIntent(intent: TranslationConfigIntent) {
        viewModelScope.launch {
            settingsGateway.update { settings ->
                when (intent) {
                    is TranslationConfigIntent.SetProvider ->
                        settings.copy(provider = intent.value)
                    is TranslationConfigIntent.SetTargetLanguage ->
                        settings.copy(targetLanguage = intent.value)
                    is TranslationConfigIntent.SetMaxCharsPerChunk ->
                        settings.copy(maxCharsPerChunk = intent.value)
                }
            }
        }
    }
}
