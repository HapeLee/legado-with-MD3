package io.legado.app.ui.config.labConfig

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.settings.LabSettings

@Stable
data class LabConfigUiState(
    val settings: LabSettings = LabSettings(),
)

sealed interface LabConfigIntent {
    data class SetEnabled(val value: Boolean) : LabConfigIntent
    data class SetEInkDisplay(val value: Boolean) : LabConfigIntent
}

sealed interface LabConfigEffect
