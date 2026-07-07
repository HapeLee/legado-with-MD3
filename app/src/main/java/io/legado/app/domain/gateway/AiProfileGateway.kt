package io.legado.app.domain.gateway

import io.legado.app.data.entities.AiModelProfile
import io.legado.app.data.entities.AiProviderProfile
import io.legado.app.data.entities.AiTaskPreset
import io.legado.app.domain.model.AiAvailableModel
import io.legado.app.domain.model.AiModelDraft
import io.legado.app.domain.model.AiProfileDraft
import io.legado.app.domain.model.AiProviderDraft
import io.legado.app.domain.model.AiTaskPresetConfig
import kotlinx.coroutines.flow.Flow

interface AiProfileGateway {
    fun observeProviders(): Flow<List<AiProviderProfile>>
    fun observeModels(): Flow<List<AiModelProfile>>
    fun observePresets(): Flow<List<AiTaskPreset>>
    suspend fun getProvider(id: String): AiProviderProfile?
    suspend fun getModel(id: String): AiModelProfile?
    suspend fun getTaskPreset(taskType: String): AiTaskPresetConfig?
    suspend fun getProviderApiKey(providerId: String): String
    suspend fun saveProvider(draft: AiProviderDraft): AiProviderProfile
    suspend fun saveModel(draft: AiModelDraft): AiModelProfile
    suspend fun importProviderModels(providerId: String, models: List<AiAvailableModel>): List<AiModelProfile>
    suspend fun setDefaultModel(modelProfileId: String): AiTaskPresetConfig
    suspend fun saveDefaultChatProfile(draft: AiProfileDraft): AiTaskPresetConfig
    suspend fun saveTaskPreset(
        taskType: String,
        promptTemplate: String,
        temperature: Float,
        maxOutputTokens: Int
    ): AiTaskPresetConfig
    suspend fun deleteProvider(providerId: String)
    suspend fun deleteModel(modelId: String)
}
