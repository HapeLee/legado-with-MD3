package io.legado.app.domain.gateway

import io.legado.app.data.entities.AiMemory
import kotlinx.coroutines.flow.Flow

interface AiMemoryGateway {
    fun observeByConversation(conversationId: String): Flow<List<AiMemory>>
    fun observeGlobal(): Flow<List<AiMemory>>
    suspend fun getByConversation(conversationId: String): List<AiMemory>
    suspend fun getGlobal(): List<AiMemory>
    suspend fun getForPrompt(conversationId: String): List<AiMemory>
    suspend fun upsert(memory: AiMemory)
    suspend fun delete(conversationId: String, key: String)
    suspend fun deleteAllForConversation(conversationId: String)
}
