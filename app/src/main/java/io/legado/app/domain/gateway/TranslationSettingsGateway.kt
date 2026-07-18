package io.legado.app.domain.gateway

import io.legado.app.domain.model.settings.TranslationSettings
import kotlinx.coroutines.flow.Flow

interface TranslationSettingsGateway {
    val currentSettings: TranslationSettings
    val settings: Flow<TranslationSettings>
    suspend fun update(update: TranslationSettingsUpdate)
}

sealed interface TranslationSettingsUpdate {
    data class Provider(val value: String) : TranslationSettingsUpdate
    data class TargetLanguage(val value: String) : TranslationSettingsUpdate
    data class MaxCharsPerChunk(val value: Int) : TranslationSettingsUpdate
    data class ConcurrentChunks(val value: Int) : TranslationSettingsUpdate
    data class RetryCount(val value: Int) : TranslationSettingsUpdate
}
