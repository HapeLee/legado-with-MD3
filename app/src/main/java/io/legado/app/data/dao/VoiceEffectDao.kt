package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.legado.app.data.entities.VoiceEffectPreset

@Dao
interface VoiceEffectDao {

    @Query("SELECT * FROM voice_effect_presets ORDER BY `order`, name COLLATE NOCASE")
    suspend fun getAll(): List<VoiceEffectPreset>

    @Query("SELECT * FROM voice_effect_presets WHERE name = :name")
    suspend fun getOne(name: String): VoiceEffectPreset?

    @Query("SELECT COUNT(*) FROM voice_effect_presets")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(preset: VoiceEffectPreset): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preset: VoiceEffectPreset)

    @Update
    suspend fun update(preset: VoiceEffectPreset)

    @Query("DELETE FROM voice_effect_presets WHERE name = :name")
    suspend fun deleteByName(name: String)
}
