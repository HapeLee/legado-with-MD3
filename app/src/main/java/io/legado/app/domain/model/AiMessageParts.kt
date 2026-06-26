package io.legado.app.domain.model

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Keep
@Serializable
sealed interface AiMessagePart {

    @Keep
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : AiMessagePart

    @Keep
    @Serializable
    @SerialName("reasoning")
    data class Reasoning(val text: String) : AiMessagePart

    /**
     * Unified tool part — represents the full lifecycle of a tool call.
     * When the model requests a tool, [input] is filled and [output] is empty.
     * After execution, [output] is filled and [approvalState] reflects the outcome.
     */
    @Keep
    @Serializable
    @SerialName("tool")
    data class Tool(
        val toolCallId: String,
        val toolName: String,
        val input: String,
        val output: String = "",
        val approvalState: AiToolApprovalState = AiToolApprovalState.AUTO,
        val rawType: String = "tool_call",
        val metadata: String? = null
    ) : AiMessagePart

    @Keep
    @Serializable
    @SerialName("book_result")
    data class BookResult(
        val bookUrl: String,
        val name: String,
        val author: String,
        val origin: String? = null,
        val coverPath: String? = null,
        val latestChapterTitle: String? = null,
        val currentChapterTitle: String? = null,
        val intro: String? = null
    ) : AiMessagePart

    // ---- Legacy parts (deprecated, kept for backward-compatible deserialization) ----

    @Deprecated("Use Tool instead — tool call and result are now a single part")
    @Keep
    @Serializable
    @SerialName("tool_call")
    data class ToolCall(
        val id: String,
        val name: String,
        val arguments: String,
        val rawType: String = "tool_call",
        val approvalState: AiToolApprovalState = AiToolApprovalState.AUTO
    ) : AiMessagePart

    @Deprecated("Use Tool instead — tool call and result are now a single part")
    @Keep
    @Serializable
    @SerialName("tool_result")
    data class ToolResult(
        val callId: String,
        val name: String,
        val content: String
    ) : AiMessagePart
}

@Keep
@Serializable
enum class AiToolApprovalState {
    @SerialName("auto")
    AUTO,

    @SerialName("pending")
    PENDING,

    @SerialName("approved")
    APPROVED,

    @SerialName("denied")
    DENIED,

    @SerialName("answered")
    ANSWERED;

    fun canResumeExecution(): Boolean =
        this == APPROVED || this == DENIED || this == ANSWERED
}

object AiMessagePartJson {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        classDiscriminator = "type"
    }

    fun encode(parts: List<AiMessagePart>): String {
        return json.encodeToString(parts)
    }

    fun decode(partsJson: String): List<AiMessagePart> {
        if (partsJson.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<AiMessagePart>>(partsJson) }
            .getOrElse { emptyList() }
            .map { it.migrateLegacy() }
    }

    /**
     * Migrate legacy ToolCall/ToolResult pairs into unified Tool parts.
     * Handles old messages stored before the unified Tool part was introduced.
     */
    private fun AiMessagePart.migrateLegacy(): AiMessagePart {
        return when (this) {
            is AiMessagePart.ToolCall -> AiMessagePart.Tool(
                toolCallId = id,
                toolName = name,
                input = arguments,
                rawType = rawType,
                approvalState = approvalState
            )
            is AiMessagePart.ToolResult -> AiMessagePart.Tool(
                toolCallId = callId,
                toolName = name,
                input = "",
                output = content,
                approvalState = AiToolApprovalState.AUTO
            )
            else -> this
        }
    }
}

// ---- Extension functions ----

fun List<AiMessagePart>.textContent(): String {
    return filterIsInstance<AiMessagePart.Text>()
        .joinToString("\n\n") { it.text }
        .trim()
}

fun List<AiMessagePart>.reasoningContent(): String? {
    return filterIsInstance<AiMessagePart.Reasoning>()
        .joinToString("\n\n") { it.text }
        .trim()
        .takeIf { it.isNotBlank() }
}

/** Returns all unified Tool parts (includes migrated legacy parts). */
fun List<AiMessagePart>.toolParts(): List<AiMessagePart.Tool> {
    return filterIsInstance<AiMessagePart.Tool>()
}

/** Returns tool calls that are pending approval (output is empty, state is PENDING). */
fun List<AiMessagePart>.pendingToolCalls(): List<AiMessagePart.Tool> {
    return toolParts().filter {
        it.output.isBlank() && it.approvalState == AiToolApprovalState.PENDING
    }
}

/** Returns tool calls that need execution (output is empty, approval allows execution). */
fun List<AiMessagePart>.executableToolCalls(): List<AiMessagePart.Tool> {
    return toolParts().filter {
        it.output.isBlank() && it.approvalState.canResumeExecution()
    }
}
