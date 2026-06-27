package io.legado.app.domain.gateway

import io.legado.app.data.entities.AiChatConversation
import io.legado.app.data.entities.AiChatMessage
import io.legado.app.domain.model.AiMessagePart
import kotlinx.coroutines.flow.Flow

interface AiChatGateway {
    fun observeConversations(): Flow<List<AiChatConversation>>
    fun observeMessages(conversationId: String): Flow<List<AiChatMessage>>
    fun observeSelectedMessages(conversationId: String): Flow<List<AiChatMessage>>
    suspend fun getConversation(id: String): AiChatConversation?
    suspend fun createConversation(title: String = "New Chat"): AiChatConversation
    suspend fun saveMessage(
        conversationId: String,
        role: String,
        parts: List<AiMessagePart>,
        parentMessageId: String? = null,
        thinkingDuration: Int = 0
    ): AiChatMessage
    suspend fun saveRegeneratedMessage(
        conversationId: String,
        role: String,
        parts: List<AiMessagePart>,
        parentMessageId: String,
        thinkingDuration: Int = 0
    ): AiChatMessage
    suspend fun selectBranch(messageId: String)
    suspend fun getBranches(parentMessageId: String): List<AiChatMessage>
    suspend fun getBranchCounts(conversationId: String): Map<String, Int>
    suspend fun updateConversationTitle(conversationId: String, title: String)
    suspend fun updateReasoningLevel(conversationId: String, reasoningLevel: String)
    suspend fun deleteConversation(conversationId: String)
}
