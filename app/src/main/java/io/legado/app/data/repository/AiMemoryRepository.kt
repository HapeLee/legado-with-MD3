package io.legado.app.data.repository

import io.legado.app.data.dao.AiMemoryDao
import io.legado.app.data.entities.AiMemory
import io.legado.app.domain.gateway.AiMemoryGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AiMemoryRepository(
    private val aiMemoryDao: AiMemoryDao
) : AiMemoryGateway {

    override fun observeByConversation(conversationId: String): Flow<List<AiMemory>> =
        aiMemoryDao.observeByConversation(conversationId)

    override fun observeGlobal(): Flow<List<AiMemory>> = aiMemoryDao.observeGlobal()

    override suspend fun getByConversation(conversationId: String): List<AiMemory> =
        withContext(Dispatchers.IO) { aiMemoryDao.getByConversation(conversationId) }

    override suspend fun getGlobal(): List<AiMemory> =
        withContext(Dispatchers.IO) { aiMemoryDao.getGlobal() }

    override suspend fun getForPrompt(conversationId: String): List<AiMemory> =
        withContext(Dispatchers.IO) {
            val global = aiMemoryDao.getGlobal()
            val scoped = if (conversationId.isNotBlank()) {
                aiMemoryDao.getByConversation(conversationId)
            } else {
                emptyList()
            }
            global + scoped
        }

    override suspend fun upsert(memory: AiMemory) = withContext(Dispatchers.IO) {
        aiMemoryDao.upsert(memory.copy(updatedAt = System.currentTimeMillis()))
    }

    override suspend fun delete(conversationId: String, key: String) = withContext(Dispatchers.IO) {
        aiMemoryDao.delete(conversationId, key)
    }

    override suspend fun deleteAllForConversation(conversationId: String) =
        withContext(Dispatchers.IO) {
            aiMemoryDao.deleteAllForConversation(conversationId)
        }
}
