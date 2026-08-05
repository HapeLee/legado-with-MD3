package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.AiPromptPreset

@Dao
interface AiPromptPresetDao {

    @Query(
        """
        select * from ai_prompt_presets
        where taskType = :taskType and enabled = 1
        order by sortNumber, createdAt
        """
    )
    fun getEnabledByTaskType(taskType: String): List<AiPromptPreset>

    @Query("select count(*) from ai_prompt_presets where taskType = :taskType")
    suspend fun countByTaskType(taskType: String): Int

    @Query("select count(*) from ai_prompt_presets where taskType = :taskType")
    fun countByTaskTypeSync(taskType: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preset: AiPromptPreset)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(presets: List<AiPromptPreset>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAllSync(presets: List<AiPromptPreset>)

    @Query("delete from ai_prompt_presets where id = :id")
    suspend fun delete(id: String)

    @Query("delete from ai_prompt_presets where id = :id")
    fun deleteSync(id: String)
}
