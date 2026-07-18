package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.LabSettings
import kotlinx.coroutines.flow.Flow

interface LabSettingsGateway {
    val settings: Flow<LabSettings>
    suspend fun update(update: LabSettingsUpdate)
}

sealed interface LabSettingsUpdate {
    data class Enabled(val value: Boolean) : LabSettingsUpdate
    data class EInkDisplay(val value: Boolean) : LabSettingsUpdate
    data class EyeProtection(val value: Boolean) : LabSettingsUpdate
}
