package io.legado.app.help.ai

import io.legado.app.help.config.AppConfig
import io.legado.app.ui.main.ai.AiChatMessage
import io.legado.app.ui.main.ai.AiContextSummary

object AiContextManager {
    private const val CHARS_PER_TOKEN = 3
    private const val RECENT_MESSAGE_COUNT = 10
    private const val COMPRESS_TRIGGER_PERCENT = 90
    private const val TARGET_PERCENT = 35
    private const val MAX_SUMMARY_CHARS = 32_000

    data class PreparedContext(
        val messages: List<AiChatMessage>,
        val summary: AiContextSummary?,
        val compressed: Boolean,
        val inputTokens: Int,
        val limitTokens: Int
    )

    fun prepare(
        messages: List<AiChatMessage>,
        previousSummary: AiContextSummary?,
        reserveTokens: Int = 0
    ): PreparedContext {
        val clean = messages.filterNot { it.pending }.filter { it.content.isNotBlank() }
        if (!AppConfig.aiContextCompressionEnabled) {
            val estimated = estimateMessagesTokens(clean)
            return PreparedContext(clean, null, false, estimated, AppConfig.aiContextWindowTokens)
        }
        val limit = AppConfig.aiContextWindowTokens
        val usableLimit = (limit - reserveTokens).coerceAtLeast(0)
        val summaryBudget = (usableLimit * TARGET_PERCENT / 100).coerceAtLeast(0)
        val summaryEndIndex = summaryEndIndex(clean, previousSummary)
        val activeSummary = previousSummary
            ?.takeIf { it.isValid && summaryEndIndex >= 0 }
            ?.let { fitSummary(it, summaryBudget, usableLimit) }
        val unsummarized = if (summaryEndIndex >= 0) clean.drop(summaryEndIndex + 1) else clean
        val estimated = estimateMessagesTokens(unsummarized) + estimateTokens(activeSummary?.summary.orEmpty())
        if (estimated < usableLimit * COMPRESS_TRIGGER_PERCENT / 100) {
            val fitted = fitMessages(unsummarized, usableLimit - estimateTokens(activeSummary?.summary.orEmpty()))
            val preparedTokens = estimateMessagesTokens(fitted) + estimateTokens(activeSummary?.summary.orEmpty())
            return PreparedContext(
                fitted,
                activeSummary?.withTokenStats(fitted, preparedTokens, limit),
                false,
                preparedTokens,
                limit
            )
        }
        val recent = unsummarized.takeLast(RECENT_MESSAGE_COUNT)
        val old = unsummarized.dropLast(recent.size)
        if (old.isEmpty()) {
            val fitted = fitMessages(recent, usableLimit - estimateTokens(activeSummary?.summary.orEmpty()))
            val preparedTokens = estimateMessagesTokens(fitted) + estimateTokens(activeSummary?.summary.orEmpty())
            return PreparedContext(
                fitted,
                activeSummary?.withTokenStats(fitted, preparedTokens, limit),
                false,
                preparedTokens,
                limit
            )
        }
        val targetSummaryChars = (summaryBudget * CHARS_PER_TOKEN)
            .coerceAtMost(MAX_SUMMARY_CHARS)
        val summaryText = buildSummary(activeSummary, old, targetSummaryChars)
        val lastSummarized = old.last()
        val summarizedCount = clean.indexOfLast { it.id == lastSummarized.id }
            .takeIf { it >= 0 }
            ?.plus(1)
            ?: clean.size
        val rawSummary = AiContextSummary(
            summary = summaryText,
            sourceMessageCount = summarizedCount,
            sourceChars = clean.sumOf { it.content.length },
            summaryChars = summaryText.length,
            lastMessageId = lastSummarized.id,
            lastMessageCreatedAt = lastSummarized.createdAt
        )
        val summary = fitSummary(rawSummary, summaryBudget, usableLimit)
        val fittedRecent = fitMessages(recent, usableLimit - estimateTokens(summary.summary))
        val preparedTokens = estimateMessagesTokens(fittedRecent) + estimateTokens(summary.summary)
        return PreparedContext(
            fittedRecent,
            summary.withTokenStats(fittedRecent, preparedTokens, limit),
            true,
            preparedTokens,
            limit
        )
    }

    fun estimateMessagesTokens(messages: List<AiChatMessage>): Int {
        return messages.sumOf { estimateTokens(it.content) + 8 }
    }

    fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        var ascii = 0
        var nonAscii = 0
        text.forEach { char -> if (char.code <= 0x7f) ascii++ else nonAscii++ }
        return (ascii / 4) + nonAscii + 1
    }

    private fun buildSummary(
        previousSummary: AiContextSummary?,
        oldMessages: List<AiChatMessage>,
        maxChars: Int
    ): String {
        if (maxChars <= 0) return ""
        val lines = mutableListOf<String>()
        previousSummary?.summary?.takeIf { it.isNotBlank() }?.let {
            lines += "Existing summary:"
            lines += it
        }
        lines += "Condensed conversation facts:"
        oldMessages.forEach { message ->
            val role = if (message.role == AiChatMessage.Role.USER) "User" else "Assistant"
            val content = message.content.replace(Regex("\\s+"), " ").trim().take(900)
            if (content.isNotBlank()) lines += "- $role: $content"
        }
        val joined = lines.joinToString("\n")
        return if (joined.length <= maxChars) joined else joined.takeLast(maxChars).trimStart()
    }

    private fun summaryEndIndex(messages: List<AiChatMessage>, summary: AiContextSummary?): Int {
        if (summary?.isValid != true) return -1
        summary.lastMessageId.takeIf { it.isNotBlank() }?.let { id ->
            messages.indexOfLast { it.id == id }.takeIf { it >= 0 }?.let { return it }
        }
        return (summary.sourceMessageCount - 1).coerceIn(-1, messages.lastIndex)
    }

    private fun fitMessages(messages: List<AiChatMessage>, tokenBudget: Int): List<AiChatMessage> {
        val budget = tokenBudget.coerceAtLeast(0)
        if (budget <= 8) return emptyList()
        val result = ArrayDeque<AiChatMessage>()
        var used = 0
        messages.asReversed().forEach { message ->
            val tokens = estimateTokens(message.content) + 8
            if (used + tokens <= budget) {
                result.addFirst(message)
                used += tokens
            } else if (result.isEmpty()) {
                val maxChars = ((budget - 8).coerceAtLeast(1) * CHARS_PER_TOKEN)
                result.addFirst(message.copy(content = message.content.takeLast(maxChars)))
                return result.toList()
            } else {
                return result.toList()
            }
        }
        return result.toList()
    }

    private fun fitSummary(summary: AiContextSummary, tokenBudget: Int, limitTokens: Int): AiContextSummary {
        val budget = tokenBudget.coerceAtLeast(0)
        if (budget <= 0 || summary.summary.isBlank()) {
            return summary.copy(summary = "", summaryChars = 0, summaryTokens = 0, limitTokens = limitTokens)
        }
        val fittedText = if (estimateTokens(summary.summary) <= budget) {
            summary.summary
        } else {
            summary.summary.takeLast((budget * CHARS_PER_TOKEN).coerceAtLeast(1)).trimStart()
        }
        return summary.copy(
            summary = fittedText,
            summaryChars = fittedText.length,
            summaryTokens = estimateTokens(fittedText),
            limitTokens = limitTokens
        )
    }

    private fun AiContextSummary.withTokenStats(
        recentMessages: List<AiChatMessage>,
        preparedTokens: Int,
        limitTokens: Int
    ): AiContextSummary {
        return copy(
            summaryTokens = estimateTokens(summary),
            recentTokens = estimateMessagesTokens(recentMessages),
            preparedTokens = preparedTokens,
            limitTokens = limitTokens
        )
    }
}
