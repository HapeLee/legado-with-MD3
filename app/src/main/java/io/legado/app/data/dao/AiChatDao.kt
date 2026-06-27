package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.AiChatConversation
import io.legado.app.data.entities.AiChatMessage
import kotlinx.coroutines.flow.Flow

data class BranchCount(
    val parentMessageId: String,
    val cnt: Int
)

@Dao
interface AiChatDao {

    @Query("SELECT * FROM ai_chat_conversations ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<AiChatConversation>>

    @Query("SELECT * FROM ai_chat_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeMessages(conversationId: String): Flow<List<AiChatMessage>>

    /** Observe only the selected branch path for display */
    @Query("SELECT * FROM ai_chat_messages WHERE conversationId = :conversationId AND isSelected = 1 ORDER BY createdAt ASC")
    fun observeSelectedMessages(conversationId: String): Flow<List<AiChatMessage>>

    /** Count branches for a given parent message (for regenerate UI) */
    @Query("SELECT COUNT(*) FROM ai_chat_messages WHERE parentMessageId = :parentMessageId")
    suspend fun countBranches(parentMessageId: String): Int

    /** Get all branches for a given parent message */
    @Query("SELECT * FROM ai_chat_messages WHERE parentMessageId = :parentMessageId ORDER BY branchIndex ASC")
    suspend fun getBranches(parentMessageId: String): List<AiChatMessage>

    /** Get branch counts grouped by parentMessageId for a conversation */
    @Query("SELECT parentMessageId, COUNT(*) as cnt FROM ai_chat_messages WHERE conversationId = :conversationId AND parentMessageId IS NOT NULL GROUP BY parentMessageId")
    suspend fun getBranchCounts(conversationId: String): List<BranchCount>

    /** Mark all messages after a given point in the conversation as unselected */
    @Query("UPDATE ai_chat_messages SET isSelected = 0 WHERE conversationId = :conversationId AND createdAt > :afterTimestamp AND role = 'assistant'")
    suspend fun deselectAssistantAfter(conversationId: String, afterTimestamp: Long)

    /** Select a specific branch by id */
    @Query("UPDATE ai_chat_messages SET isSelected = 1 WHERE id = :messageId")
    suspend fun selectBranch(messageId: String)

    /** Deselect all messages for a conversation */
    @Query("UPDATE ai_chat_messages SET isSelected = 0 WHERE conversationId = :conversationId")
    suspend fun deselectAll(conversationId: String)

    @Query("SELECT * FROM ai_chat_conversations WHERE id = :id")
    suspend fun getConversation(id: String): AiChatConversation?

    @Query("SELECT * FROM ai_chat_messages WHERE id = :id")
    suspend fun getMessage(id: String): AiChatMessage?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: AiChatConversation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: AiChatMessage)

    @Query("UPDATE ai_chat_conversations SET title = :title, updatedAt = :updatedAt WHERE id = :conversationId")
    suspend fun updateConversationTitle(conversationId: String, title: String, updatedAt: Long)

    @Query(
        """
        UPDATE ai_chat_conversations
        SET reasoningLevel = :reasoningLevel, updatedAt = :updatedAt
        WHERE id = :conversationId
        """
    )
    suspend fun updateConversationReasoningLevel(conversationId: String, reasoningLevel: String, updatedAt: Long)

    @Query("UPDATE ai_chat_conversations SET updatedAt = :updatedAt WHERE id = :conversationId")
    suspend fun touchConversation(conversationId: String, updatedAt: Long)

    @Query("DELETE FROM ai_chat_conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    @Query("DELETE FROM ai_chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesByConversation(conversationId: String)
}
