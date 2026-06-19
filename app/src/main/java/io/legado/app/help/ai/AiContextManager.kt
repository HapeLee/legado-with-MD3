package io.legado.app.help.ai

object AiContextManager {

    fun summarize(
        messages: List<AiChatMessage>,
        existingSummary: AiContextSummary? = null,
        limitChars: Int = 2000
    ): AiContextSummary {
        val recent = messages.takeLast(20)
        val total = recent.fold(StringBuilder()) { acc, msg ->
            acc.append("${if (msg.role == AiChatMessage.Role.USER) "用户" else "助手"}: ${msg.content.take(500)}\n\n")
        }.toString()

        val summaryText = buildString {
            append("早期对话摘要（压缩内容：\n")
            append("总消息数: ${messages.size} 条，总字数: ${messages.sumOf { it.content.length }}\n")
            recent.firstOrNull()?.let {
                append("最早消息时间: ${it.createdAt}\n")
            }
        }

        return AiContextSummary(
            summary = summaryText.take(limitChars),
            sourceMessageCount = messages.size,
            sourceChars = messages.sumOf { it.content.length },
            summaryChars = summaryText.length,
            summaryTokens = AiChatService.estimateTokens(summaryText),
            recentTokens = recent.sumOf { AiChatService.estimateTokens(it.content) },
            lastMessageId = messages.lastOrNull()?.id.orEmpty(),
            lastMessageCreatedAt = messages.lastOrNull()?.createdAt ?: 0L,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun prepare(
        messages: List<AiChatMessage>,
        contextSummary: AiContextSummary?,
        reserveTokens: Int
    ): PreparedContext {
        val limit = 32_000
        val input = messages.sumOf { AiChatService.estimateTokens(it.content) }
        val total = input + reserveTokens
        val compressed = total > limit
        return PreparedContext(
            messages = if (compressed) messages.takeLast(4) else messages,
            summary = contextSummary,
            compressed = compressed,
            inputTokens = if (compressed) messages.takeLast(4).sumOf { AiChatService.estimateTokens(it.content) } else input,
            limitTokens = limit
        )
    }
}

data class PreparedContext(
    val messages: List<AiChatMessage>,
    val summary: AiContextSummary?,
    val compressed: Boolean,
    val inputTokens: Int,
    val limitTokens: Int
)
