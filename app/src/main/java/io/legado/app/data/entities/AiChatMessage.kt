package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_chat_messages",
    indices = [Index(value = ["conversationId", "createdAt"])]
)
data class AiChatMessage(
    @PrimaryKey
    val id: String,
    val conversationId: String,
    val role: String,
    val partsJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val branchIndex: Int = 0,
    val isSelected: Boolean = true,
    val parentMessageId: String? = null,
    @ColumnInfo(defaultValue = "0")
    val thinkingDuration: Int = 0
)
