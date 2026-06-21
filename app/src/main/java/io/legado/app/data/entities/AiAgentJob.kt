package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_agent_jobs",
    indices = [
        Index(value = ["sessionId", "createdAt"]),
        Index(value = ["status", "updatedAt"]),
        Index(value = ["type", "updatedAt"])
    ]
)
data class AiAgentJob(
    @PrimaryKey
    val jobId: String = "",
    @ColumnInfo(defaultValue = "")
    val sessionId: String = "",
    @ColumnInfo(defaultValue = "")
    val type: String = TYPE_CHAT,
    @ColumnInfo(defaultValue = "")
    val status: String = STATUS_PENDING,
    @ColumnInfo(defaultValue = "")
    val inputJson: String = "",
    @ColumnInfo(defaultValue = "")
    val checkpointJson: String = "",
    @ColumnInfo(defaultValue = "")
    val outputJson: String = "",
    @ColumnInfo(defaultValue = "")
    val error: String = "",
    @ColumnInfo(defaultValue = "0")
    val retryCount: Int = 0,
    @ColumnInfo(defaultValue = "2")
    val maxRetry: Int = 2,
    @ColumnInfo(defaultValue = "0")
    val nextRunAt: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val leaseUntil: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_CHAT = "chat"
        const val TYPE_READ_AI = "read_ai"
        const val TYPE_ROLE_ASSIGN = "role_assign"
        const val TYPE_AUDIO_ASSIGN = "audio_assign"

        const val STATUS_PENDING = "pending"
        const val STATUS_RUNNING = "running"
        const val STATUS_WAITING_RESUME = "waiting_resume"
        const val STATUS_DONE = "done"
        const val STATUS_FAILED = "failed"
        const val STATUS_CANCELLED = "cancelled"
        const val STATUS_INTERRUPTED = "interrupted"
    }
}
