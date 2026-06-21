package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_agent_sessions",
    indices = [
        Index(value = ["scope", "updatedAt"]),
        Index(value = ["status", "updatedAt"])
    ]
)
data class AiAgentSession(
    @PrimaryKey
    val sessionId: String = "",
    @ColumnInfo(defaultValue = "")
    val scope: String = SCOPE_CHAT,
    @ColumnInfo(defaultValue = "")
    val status: String = STATUS_IDLE,
    @ColumnInfo(defaultValue = "")
    val currentGoal: String = "",
    @ColumnInfo(defaultValue = "")
    val currentTask: String = "",
    @ColumnInfo(defaultValue = "")
    val currentStep: String = "",
    @ColumnInfo(defaultValue = "")
    val contextJson: String = "",
    @ColumnInfo(defaultValue = "")
    val pendingConfirmationsJson: String = "",
    @ColumnInfo(defaultValue = "")
    val retryStateJson: String = "",
    @ColumnInfo(defaultValue = "")
    val lastError: String = "",
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val SCOPE_CHAT = "chat"
        const val SCOPE_READ_AI = "read_ai"
        const val SCOPE_ROLEPLAY = "roleplay"
        const val SCOPE_READ_ALOUD_ROLE = "read_aloud_role"
        const val SCOPE_READ_ALOUD_AUDIO = "read_aloud_audio"

        const val STATUS_IDLE = "idle"
        const val STATUS_RUNNING = "running"
        const val STATUS_WAITING_RESUME = "waiting_resume"
        const val STATUS_WAITING_USER = "waiting_user"
        const val STATUS_DONE = "done"
        const val STATUS_FAILED = "failed"
        const val STATUS_CANCELLED = "cancelled"
        const val STATUS_INTERRUPTED = "interrupted"
    }
}
