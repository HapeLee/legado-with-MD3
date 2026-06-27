package io.legado.app.ui.ai.chat

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.AiMessagePart
import io.legado.app.domain.model.AiReasoningLevel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class AiChatUiState(
    val conversations: ImmutableList<AiChatConversationUi> = persistentListOf(),
    val messages: ImmutableList<AiChatMessageUi> = persistentListOf(),
    val currentConversationId: String? = null,
    val reasoningLevel: AiReasoningLevel = AiReasoningLevel.AUTO,
    val isSending: Boolean = false,
    val streamingMessage: AiChatMessageUi? = null,
    val pendingToolConfirmation: AiToolConfirmationUi? = null
)

@Stable
data class AiChatConversationUi(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val isSelected: Boolean,
    val providerName: String = "",
    val modelName: String = ""
)

@Stable
data class AiChatMessageUi(
    val id: String,
    val role: String,
    val parts: ImmutableList<AiMessagePart> = persistentListOf(),
    val content: String,
    val reasoning: String?,
    val toolTrace: String?,
    val createdAt: Long,
    val thinkingDuration: Int = 0,
    val bookResults: ImmutableList<AiChatBookResultUi> = persistentListOf(),
    val branchIndex: Int = 0,
    val totalBranches: Int = 1,
    val parentMessageId: String? = null
)

@Stable
data class AiChatBookResultUi(
    val bookUrl: String,
    val name: String,
    val author: String,
    val origin: String?,
    val coverPath: String?,
    val latestChapterTitle: String?,
    val currentChapterTitle: String?,
    val intro: String?
)

@Stable
data class AiToolConfirmationUi(
    val title: String,
    val description: String
)

sealed interface AiChatIntent {
    data object NewConversation : AiChatIntent
    data class SelectConversation(val id: String) : AiChatIntent
    data class SendMessage(val content: String) : AiChatIntent
    data object StopGenerating : AiChatIntent
    data object ConfirmPendingTool : AiChatIntent
    data object RejectPendingTool : AiChatIntent
    data class UpdateReasoningLevel(val level: AiReasoningLevel) : AiChatIntent
    data class RegenerateMessage(val messageId: String) : AiChatIntent
    data class SwitchBranch(val messageId: String) : AiChatIntent
    data class DeleteConversation(val id: String) : AiChatIntent
    data class RenameConversation(val id: String, val title: String) : AiChatIntent
}

sealed interface AiChatEffect {
    data class ShowMessage(val message: String) : AiChatEffect
}
