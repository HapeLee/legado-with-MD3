package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "ai_memory_fragments",
    indices = [
        Index(value = ["scope", "updatedAt"]),
        Index(value = ["bookKey", "chapterIndex"]),
        Index(value = ["sessionId", "updatedAt"]),
        Index(value = ["contentHash"], unique = true)
    ]
)
data class AiMemoryFragment(
    @PrimaryKey
    val fragmentId: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "")
    val scope: String = AiMemoryItem.SCOPE_GLOBAL,
    @ColumnInfo(defaultValue = "")
    val bookKey: String = "",
    @ColumnInfo(defaultValue = "")
    val sessionId: String = "",
    @ColumnInfo(defaultValue = "")
    val sourceType: String = SOURCE_CHAT,
    @ColumnInfo(defaultValue = "")
    val title: String = "",
    @ColumnInfo(defaultValue = "")
    val content: String = "",
    @ColumnInfo(defaultValue = "-1")
    val chapterIndex: Int = -1,
    @ColumnInfo(defaultValue = "")
    val chapterTitle: String = "",
    @ColumnInfo(defaultValue = "-1")
    val paragraphStart: Int = -1,
    @ColumnInfo(defaultValue = "-1")
    val paragraphEnd: Int = -1,
    @ColumnInfo(defaultValue = "")
    val contentHash: String = "",
    @ColumnInfo(defaultValue = "50")
    val importance: Int = 50,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val lastUsedAt: Long = 0L
) {
    companion object {
        const val SOURCE_CHAT = "chat"
        const val SOURCE_READ_AI = "read_ai"
        const val SOURCE_CHAPTER_SUMMARY = "chapter_summary"
        const val SOURCE_CHARACTER = "character"
        const val SOURCE_RELATION = "relation"
    }
}
