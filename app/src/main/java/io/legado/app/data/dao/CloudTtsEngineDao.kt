package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.CloudTtsEngineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CloudTtsEngineDao {
    @Query("select * from cloud_tts_engines order by enabled desc, name collate nocase")
    fun observeAll(): Flow<List<CloudTtsEngineEntity>>

    @Query("select * from cloud_tts_engines order by enabled desc, name collate nocase")
    suspend fun getAll(): List<CloudTtsEngineEntity>

    @Query("select * from cloud_tts_engines where id = :id limit 1")
    suspend fun get(id: String): CloudTtsEngineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(engine: CloudTtsEngineEntity)

    @Query(
        """
        update cloud_tts_engines
        set apiKey = :newApiKey, secretKey = :newSecretKey
        where id = :id and apiKey = :oldApiKey and secretKey = :oldSecretKey
        """
    )
    suspend fun updateCredentials(
        id: String,
        oldApiKey: String,
        oldSecretKey: String,
        newApiKey: String,
        newSecretKey: String,
    )

    @Delete
    suspend fun delete(engine: CloudTtsEngineEntity)
}
