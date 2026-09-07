package io.legado.app.domain.gateway

import io.legado.app.data.entities.AiPromptPreset

interface AiPromptPresetGateway {
    suspend fun getEnabledByTaskType(taskType: String): List<AiPromptPreset>
    suspend fun countByTaskType(taskType: String): Int
    suspend fun savePreset(preset: AiPromptPreset)
    suspend fun savePresets(presets: List<AiPromptPreset>)
    suspend fun deletePreset(id: String)
}
