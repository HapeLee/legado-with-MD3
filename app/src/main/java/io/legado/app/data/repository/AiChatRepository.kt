package io.legado.app.data.repository

import io.legado.app.data.dao.AiChatDao
import io.legado.app.data.entities.AiChatConversation
import io.legado.app.data.entities.AiChatMessage
import io.legado.app.domain.gateway.AiChatGateway
import io.legado.app.domain.model.AiMessagePart
import io.legado.app.domain.model.AiMessagePartJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class AiChatRepository(
    private val aiChatDao: AiChatDao
) : AiChatGateway {

    override fun observeConversations(): Flow<List<AiChatConversation>> = aiChatDao.observeConversations()

    override fun observeMessages(conversationId: String): Flow<List<AiChatMessage>> =
        aiChatDao.observeMessages(conversationId)

    override fun observeSelectedMessages(conversationId: String): Flow<List<AiChatMessage>> =
        aiChatDao.observeSelectedMessages(conversationId)

    override suspend fun getConversation(id: String): AiChatConversation? = withContext(Dispatchers.IO) {
        aiChatDao.getConversation(id)
    }

    override suspend fun createConversation(title: String): AiChatConversation = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        AiChatConversation(
            id = newId("chat"),
            title = title,
            createdAt = now,
            updatedAt = now
        ).also { aiChatDao.insertConversation(it) }
    }

    override suspend fun saveMessage(
        conversationId: String,
        role: String,
        parts: List<AiMessagePart>,
        parentMessageId: String?,
        thinkingDuration: Int
    ): AiChatMessage = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        AiChatMessage(
            id = newId("message"),
            conversationId = conversationId,
            role = role,
            partsJson = AiMessagePartJson.encode(parts),
            createdAt = now,
            branchIndex = 0,
            isSelected = true,
            parentMessageId = parentMessageId,
            thinkingDuration = thinkingDuration
        ).also {
            aiChatDao.insertMessage(it)
            aiChatDao.touchConversation(conversationId, now)
        }
    }

    override suspend fun saveRegeneratedMessage(
        conversationId: String,
        role: String,
        parts: List<AiMessagePart>,
        parentMessageId: String,
        thinkingDuration: Int
    ): AiChatMessage = withContext(Dispatchers.IO) {
        val branchCount = aiChatDao.countBranches(parentMessageId)
        val now = System.currentTimeMillis()
        // Deselect existing branches for this parent
        val siblings = aiChatDao.getBranches(parentMessageId)
        siblings.forEach { sibling ->
            aiChatDao.insertMessage(sibling.copy(isSelected = false))
        }
        AiChatMessage(
            id = newId("message"),
            conversationId = conversationId,
            role = role,
            partsJson = AiMessagePartJson.encode(parts),
            createdAt = now,
            branchIndex = branchCount,
            isSelected = true,
            parentMessageId = parentMessageId,
            thinkingDuration = thinkingDuration
        ).also {
            aiChatDao.insertMessage(it)
            aiChatDao.touchConversation(conversationId, now)
        }
    }

    override suspend fun selectBranch(messageId: String) = withContext(Dispatchers.IO) {
        val message = aiChatDao.getMessage(messageId) ?: return@withContext
        val parentId = message.parentMessageId ?: return@withContext
        // Deselect siblings
        val siblings = aiChatDao.getBranches(parentId)
        siblings.forEach { sibling ->
            aiChatDao.insertMessage(sibling.copy(isSelected = false))
        }
        // Select this branch
        aiChatDao.selectBranch(messageId)
    }

    override suspend fun getBranches(parentMessageId: String): List<AiChatMessage> =
        withContext(Dispatchers.IO) {
            aiChatDao.getBranches(parentMessageId)
        }

    override suspend fun getBranchCounts(conversationId: String): Map<String, Int> =
        withContext(Dispatchers.IO) {
            aiChatDao.getBranchCounts(conversationId).associate { it.parentMessageId to it.cnt }
        }

    override suspend fun updateConversationTitle(conversationId: String, title: String) = withContext(Dispatchers.IO) {
        aiChatDao.updateConversationTitle(conversationId, title, System.currentTimeMillis())
    }

    override suspend fun updateReasoningLevel(conversationId: String, reasoningLevel: String) =
        withContext(Dispatchers.IO) {
            aiChatDao.updateConversationReasoningLevel(
                conversationId = conversationId,
                reasoningLevel = reasoningLevel,
                updatedAt = System.currentTimeMillis()
            )
        }

    override suspend fun deleteConversation(conversationId: String) = withContext(Dispatchers.IO) {
        aiChatDao.deleteMessagesByConversation(conversationId)
        aiChatDao.deleteConversation(conversationId)
    }

    private fun newId(prefix: String): String = "${prefix}_${UUID.randomUUID().toString().replace("-", "")}"
}
