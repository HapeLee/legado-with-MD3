package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.AiMemory
import kotlinx.coroutines.flow.Flow

@Dao
interface AiMemoryDao {

    @Query("SELECT * FROM ai_memory WHERE conversationId = :conversationId ORDER BY updatedAt DESC")
    fun observeByConversation(conversationId: String): Flow<List<AiMemory>>

    @Query("SELECT * FROM ai_memory WHERE conversationId = '' ORDER BY updatedAt DESC")
    fun observeGlobal(): Flow<List<AiMemory>>

    @Query("SELECT * FROM ai_memory WHERE conversationId = :conversationId")
    suspend fun getByConversation(conversationId: String): List<AiMemory>

    @Query("SELECT * FROM ai_memory WHERE conversationId = ''")
    suspend fun getGlobal(): List<AiMemory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: AiMemory)

    @Query("DELETE FROM ai_memory WHERE conversationId = :conversationId AND `key` = :key")
    suspend fun delete(conversationId: String, key: String)

    @Query("DELETE FROM ai_memory WHERE conversationId = :conversationId")
    suspend fun deleteAllForConversation(conversationId: String)
}
