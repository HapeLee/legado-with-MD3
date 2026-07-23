package io.legado.app.ui.config.labConfig

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.settings.LabSettings

@Stable
data class LabConfigUiState(
    val settings: LabSettings = LabSettings(),
    val pageEstimateDiagnosticCount: Int = 0,
)

sealed interface LabConfigIntent {
    data class SetEnabled(val value: Boolean) : LabConfigIntent
    data class SetEInkDisplay(val value: Boolean) : LabConfigIntent
    data object ExportPageEstimateDiagnostics : LabConfigIntent
}

sealed interface LabConfigEffect {
    data class SharePageEstimateDiagnostics(val text: String) : LabConfigEffect
}
