package io.legado.app.feature.settings.lab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.gateway.LabSettingsGateway
import io.legado.app.feature.reader.core.pageestimate.LocalPageEstimateMetrics
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// M5-2a：从 `:app` 的 `io.legado.app.ui.config.labConfig` 迁来（**只改包名**）。
//
// 迁移前它的两个依赖已经是共享层的：`LabSettingsGateway`（`:core:data` 的端口）与
// `LocalPageEstimateMetrics`（`:feature:reader:core` 的 object）。因此这里**没有**
// 任何平台改写 —— 这是本页与 about 最大的差别（后者要抽三个契约）。
//
// `LocalPageEstimateMetrics` 保留**直接引用**而不是抽端口：它已经在共享层，且语义是
// 「进程内的诊断计数器」，抽端口只会多一层无实现的间接（AGENTS.md：只在跨平台实现
// 真需要分化时才抽契约）。
class LabConfigViewModel(
    private val settingsGateway: LabSettingsGateway,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        LabConfigUiState(
            settings = settingsGateway.currentSettings,
            pageEstimateDiagnosticCount = LocalPageEstimateMetrics.size(),
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<LabConfigEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            settingsGateway.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    fun onIntent(intent: LabConfigIntent) {
        // 导出诊断不改设置、且要立即产出 Effect（不等协程调度），故先于 update 分支返回。
        if (intent is LabConfigIntent.ExportPageEstimateDiagnostics) {
            _effects.tryEmit(
                LabConfigEffect.SharePageEstimateDiagnostics(LocalPageEstimateMetrics.export())
            )
            return
        }
        viewModelScope.launch {
            settingsGateway.update { settings ->
                when (intent) {
                    is LabConfigIntent.SetEnabled -> settings.copy(enabled = intent.value)
                    is LabConfigIntent.SetEInkDisplay -> settings.copy(eInkDisplay = intent.value)
                    LabConfigIntent.ExportPageEstimateDiagnostics -> settings
                }
            }
        }
    }
}
