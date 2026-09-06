package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.legado.app.data.entities.AiModelProfile
import io.legado.app.data.entities.AiProviderProfile
import io.legado.app.data.entities.AiTaskPreset
import kotlinx.coroutines.flow.Flow

@Dao
interface AiProfileDao {

    @Query("select * from ai_provider_profiles order by createdAt")
    fun observeProviders(): Flow<List<AiProviderProfile>>

    @Query("select * from ai_model_profiles order by sortNumber, createdAt")
    fun observeModels(): Flow<List<AiModelProfile>>

    @Query("select * from ai_task_presets order by taskType, sortNumber, createdAt")
    fun observePresets(): Flow<List<AiTaskPreset>>

    @Query("select * from ai_provider_profiles where id = :id")
    suspend fun getProvider(id: String): AiProviderProfile?

    @Query("select * from ai_model_profiles where id = :id")
    suspend fun getModel(id: String): AiModelProfile?

    @Query("select * from ai_model_profiles where providerId = :providerId order by sortNumber, createdAt")
    suspend fun getModelsByProvider(providerId: String): List<AiModelProfile>

    @Query("select * from ai_task_presets where id = :id")
    suspend fun getPreset(id: String): AiTaskPreset?

    @Query("select * from ai_task_presets where taskType = :taskType and enabled = 1 order by isDefault desc, sortNumber, createdAt limit 1")
    suspend fun getDefaultPreset(taskType: String): AiTaskPreset?

    @Query("select count(*) from ai_provider_profiles")
    suspend fun countProviders(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProvider(provider: AiProviderProfile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: AiModelProfile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: AiTaskPreset)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateProvider(provider: AiProviderProfile)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateModel(model: AiModelProfile)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updatePreset(preset: AiTaskPreset)

    @Query("DELETE FROM ai_provider_profiles WHERE id = :providerId")
    suspend fun deleteProvider(providerId: String)

    @Query("DELETE FROM ai_model_profiles WHERE id = :modelId")
    suspend fun deleteModel(modelId: String)

    @Query("DELETE FROM ai_model_profiles WHERE providerId = :providerId")
    suspend fun deleteModelsByProvider(providerId: String)

    @Transaction
    suspend fun upsertProfile(
        provider: AiProviderProfile,
        model: AiModelProfile,
        preset: AiTaskPreset
    ) {
        insertProvider(provider)
        insertModel(model)
        insertPreset(preset)
    }
}
