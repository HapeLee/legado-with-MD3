package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.TranslationSettings
import kotlinx.coroutines.flow.Flow

interface TranslationSettingsGateway {
    val currentSettings: TranslationSettings
    val settings: Flow<TranslationSettings>
    suspend fun update(transform: (TranslationSettings) -> TranslationSettings)
}
