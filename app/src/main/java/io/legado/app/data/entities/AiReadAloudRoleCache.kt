package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_read_aloud_role_caches",
    indices = [
        Index(value = ["bookUrl", "chapterIndex"]),
        Index(value = ["bookUrl", "contentHash"])
    ]
)
data class AiReadAloudRoleCache(
    @PrimaryKey
    val cacheKey: String = "",
    @ColumnInfo(defaultValue = "")
    val bookUrl: String = "",
    @ColumnInfo(defaultValue = "")
    val chapterKey: String = "",
    @ColumnInfo(defaultValue = "0")
    val chapterIndex: Int = 0,
    @ColumnInfo(defaultValue = "")
    val chapterTitle: String = "",
    @ColumnInfo(defaultValue = "")
    val contentHash: String = "",
    @ColumnInfo(defaultValue = "")
    val mode: String = "",
    @ColumnInfo(defaultValue = "0")
    val paragraphCount: Int = 0,
    @ColumnInfo(defaultValue = "success")
    val status: String = STATUS_SUCCESS,
    @ColumnInfo(defaultValue = "0")
    val retryCount: Int = 0,
    @ColumnInfo(defaultValue = "")
    val lastError: String = "",
    @ColumnInfo(defaultValue = "")
    val createdCharacterIdsJson: String = "",
    @ColumnInfo(defaultValue = "")
    val characterHash: String = "",
    @ColumnInfo(defaultValue = "")
    val voiceHash: String = "",
    @ColumnInfo(defaultValue = "")
    val segmentsJson: String = "",
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_RUNNING = "running"
        const val STATUS_SUCCESS = "success"
        const val STATUS_FALLBACK = "fallback"
        const val STATUS_FAILED = "failed"
    }
}
