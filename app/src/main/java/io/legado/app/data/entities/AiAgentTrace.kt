package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "ai_agent_traces",
    indices = [
        Index(value = ["jobId", "round", "createdAt"]),
        Index(value = ["sessionId", "createdAt"]),
        Index(value = ["eventType", "createdAt"])
    ]
)
data class AiAgentTrace(
    @PrimaryKey
    val traceId: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "")
    val sessionId: String = "",
    @ColumnInfo(defaultValue = "")
    val jobId: String = "",
    @ColumnInfo(defaultValue = "0")
    val round: Int = 0,
    @ColumnInfo(defaultValue = "")
    val eventType: String = EVENT_STATUS,
    @ColumnInfo(defaultValue = "")
    val payloadJson: String = "",
    @ColumnInfo(defaultValue = "")
    val usageJson: String = "",
    @ColumnInfo(defaultValue = "1")
    val success: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val EVENT_STATUS = "status"
        const val EVENT_MODEL_REQUEST = "model_request"
        const val EVENT_MODEL_RESPONSE = "model_response"
        const val EVENT_TOOL_CALL = "tool_call"
        const val EVENT_TOOL_RESULT = "tool_result"
        const val EVENT_PLAN_CREATED = "plan_created"
        const val EVENT_VALIDATION = "validation"
        const val EVENT_MEMORY_RETRIEVED = "memory_retrieved"
        const val EVENT_WORLD_BOOK_RETRIEVED = "world_book_retrieved"
        const val EVENT_ERROR = "error"
    }
}
