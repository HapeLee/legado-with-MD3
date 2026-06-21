package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "ai_memory_items",
    indices = [
        Index(value = ["scope", "updatedAt"]),
        Index(value = ["bookKey", "updatedAt"]),
        Index(value = ["sessionId", "updatedAt"]),
        Index(value = ["type", "updatedAt"]),
        Index(value = ["fingerprint"], unique = true)
    ]
)
data class AiMemoryItem(
    @PrimaryKey
    val memoryId: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "")
    val scope: String = SCOPE_GLOBAL,
    @ColumnInfo(defaultValue = "")
    val bookKey: String = "",
    @ColumnInfo(defaultValue = "")
    val sessionId: String = "",
    @ColumnInfo(defaultValue = "")
    val type: String = TYPE_NOTE,
    @ColumnInfo(defaultValue = "")
    val subject: String = "",
    @ColumnInfo(defaultValue = "")
    val predicate: String = "",
    @ColumnInfo(defaultValue = "")
    val objectValue: String = "",
    @ColumnInfo(defaultValue = "")
    val content: String = "",
    @ColumnInfo(defaultValue = "50")
    val confidence: Int = 50,
    @ColumnInfo(defaultValue = "50")
    val importance: Int = 50,
    @ColumnInfo(defaultValue = "")
    val sourceIds: String = "",
    @ColumnInfo(defaultValue = "-1")
    val sourceChapterIndex: Int = -1,
    @ColumnInfo(defaultValue = "")
    val fingerprint: String = "",
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val lastUsedAt: Long = 0L
) {
    companion object {
        const val SCOPE_GLOBAL = "global"
        const val SCOPE_BOOK = "book"
        const val SCOPE_SESSION = "session"
        const val SCOPE_CHARACTER = "character"
        const val SCOPE_ROLEPLAY = "roleplay"

        const val TYPE_NOTE = "note"
        const val TYPE_USER_PREFERENCE = "user_preference"
        const val TYPE_PLOT_FACT = "plot_fact"
        const val TYPE_CHARACTER_FACT = "character_fact"
        const val TYPE_RELATION_STATE = "relation_state"
        const val TYPE_WORLD_STATE = "world_state"
    }
}
