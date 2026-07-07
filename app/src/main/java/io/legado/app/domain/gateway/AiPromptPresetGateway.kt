package io.legado.app.domain.gateway

import io.legado.app.data.entities.AiPromptPreset

interface AiPromptPresetGateway {
    fun getEnabledByTaskType(taskType: String): List<AiPromptPreset>
    fun countByTaskTypeSync(taskType: String): Int
    fun savePresetsSync(presets: List<AiPromptPreset>)
    fun deletePresetSync(id: String)
    suspend fun countByTaskType(taskType: String): Int
    suspend fun savePreset(preset: AiPromptPreset)
    suspend fun savePresets(presets: List<AiPromptPreset>)
    suspend fun deletePreset(id: String)
}
