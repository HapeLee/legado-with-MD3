package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_read_aloud_usage_records",
    indices = [
        Index(value = ["type", "createdAt"]),
        Index(value = ["bookUrl", "chapterIndex"]),
        Index(value = ["cacheKey"])
    ]
)
data class AiReadAloudUsageRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(defaultValue = "")
    val type: String = TYPE_ROLE,
    @ColumnInfo(defaultValue = "")
    val status: String = STATUS_SUCCESS,
    @ColumnInfo(defaultValue = "")
    val bookUrl: String = "",
    @ColumnInfo(defaultValue = "")
    val bookName: String = "",
    @ColumnInfo(defaultValue = "")
    val chapterTitle: String = "",
    @ColumnInfo(defaultValue = "0")
    val chapterIndex: Int = 0,
    @ColumnInfo(defaultValue = "")
    val cacheKey: String = "",
    @ColumnInfo(defaultValue = "")
    val batchName: String = "",
    @ColumnInfo(defaultValue = "")
    val providerName: String = "",
    @ColumnInfo(defaultValue = "")
    val modelId: String = "",
    @ColumnInfo(defaultValue = "0")
    val elapsedMillis: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val requestCount: Int = 1,
    @ColumnInfo(defaultValue = "0")
    val inputTokens: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val cachedInputTokens: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val outputTokens: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val totalTokens: Int = 0,
    @ColumnInfo(defaultValue = "")
    val summary: String = "",
    @ColumnInfo(defaultValue = "")
    val error: String = "",
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_ROLE = "role"
        const val TYPE_BGM = "bgm"
        const val TYPE_SFX = "sfx"
        const val TYPE_AUDIO = "audio"

        const val STATUS_SUCCESS = "success"
        const val STATUS_FAILED = "failed"
        const val STATUS_CACHE = "cache"
    }
}
