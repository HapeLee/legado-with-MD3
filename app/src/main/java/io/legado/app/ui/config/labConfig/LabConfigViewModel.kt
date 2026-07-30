package io.legado.app.ui.config.labConfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.domain.gateway.LabSettingsGateway
import io.legado.app.ui.book.read.pageestimate.LocalPageEstimateMetrics
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
