package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.Index

/**
 * Long-term memory entries for AI conversations.
 * Stored as key-value pairs scoped to a conversation (or global when conversationId is null).
 * Injected into the system prompt so the model can reference past context.
 */
@Entity(
    tableName = "ai_memory",
    primaryKeys = ["conversationId", "key"],
    indices = [Index(value = ["conversationId"])]
)
data class AiMemory(
    val conversationId: String,  // "" for global memories
    val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)
