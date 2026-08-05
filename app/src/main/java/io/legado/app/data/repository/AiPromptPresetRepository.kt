package io.legado.app.data.repository

import io.legado.app.data.dao.AiPromptPresetDao
import io.legado.app.data.entities.AiPromptPreset
import io.legado.app.domain.gateway.AiPromptPresetGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiPromptPresetRepository(
    private val dao: AiPromptPresetDao,
) : AiPromptPresetGateway {

    override fun getEnabledByTaskType(taskType: String): List<AiPromptPreset> {
        return dao.getEnabledByTaskType(taskType)
    }

    override fun countByTaskTypeSync(taskType: String): Int {
        return dao.countByTaskTypeSync(taskType)
    }

    override fun savePresetsSync(presets: List<AiPromptPreset>) {
        dao.upsertAllSync(presets)
    }

    override fun deletePresetSync(id: String) {
        dao.deleteSync(id)
    }

    override suspend fun countByTaskType(taskType: String): Int = withContext(Dispatchers.IO) {
        dao.countByTaskType(taskType)
    }

    override suspend fun savePreset(preset: AiPromptPreset) = withContext(Dispatchers.IO) {
        dao.upsert(preset)
    }

    override suspend fun savePresets(presets: List<AiPromptPreset>) = withContext(Dispatchers.IO) {
        dao.upsertAll(presets)
    }

    override suspend fun deletePreset(id: String) = withContext(Dispatchers.IO) {
        dao.delete(id)
    }
}
