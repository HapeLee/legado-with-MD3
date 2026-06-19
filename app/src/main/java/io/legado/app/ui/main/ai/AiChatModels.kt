package io.legado.app.ui.main.ai

import androidx.annotation.Keep
import java.util.UUID

@Keep
data class AiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val content: String,
    val pending: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val kind: Kind? = Kind.TEXT,
    val statusName: String? = null,
    val statusStage: String? = null,
    val statusSuccess: Boolean = true,
    val statusLabel: String? = null,
    val statusDetail: String? = null,
    val statusKey: String? = null,
    val collapsed: Boolean = false,
    val updatedAt: Long = createdAt
) {
    @Keep
    enum class Role {
        USER,
        ASSISTANT
    }

    @Keep
    enum class Kind {
        TEXT,
        STATUS,
        THINKING,
        TOOL
    }
}

@Keep
data class AiChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<AiChatMessage> = emptyList(),
    val contextSummary: AiContextSummary? = null
)

@Keep
class AiChatException(
    override val message: String,
    val debugLog: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)
