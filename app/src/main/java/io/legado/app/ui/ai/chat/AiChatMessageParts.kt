package io.legado.app.ui.ai.chat

import io.legado.app.domain.model.AiMessagePart

/**
 * A single step inside a thinking block — either reasoning or a tool call.
 */
sealed interface AiThinkingStep {
    data class ReasoningStep(val text: String) : AiThinkingStep
    data class ToolStep(val tool: AiMessagePart.Tool) : AiThinkingStep
}

/**
 * A renderable block of message content.
 * ThinkingBlock groups consecutive reasoning + tool parts.
 * ContentBlock wraps a single non-thinking part (Text, BookResult, etc.).
 */
sealed interface AiMessagePartBlock {
    data class ThinkingBlock(
        val steps: List<AiThinkingStep>,
        val durationSeconds: Int = 0
    ) : AiMessagePartBlock
    data class ContentBlock(val part: AiMessagePart, val index: Int) : AiMessagePartBlock
}

/**
 * Groups consecutive [AiMessagePart.Reasoning] and [AiMessagePart.Tool] into
 * a single [AiMessagePartBlock.ThinkingBlock], preserving render order.
 */
fun List<AiMessagePart>.groupMessageParts(thinkingDurationSeconds: Int = 0): List<AiMessagePartBlock> {
    val result = mutableListOf<AiMessagePartBlock>()
    var currentThinkingSteps = mutableListOf<AiThinkingStep>()

    fun flushThinkingSteps() {
        if (currentThinkingSteps.isNotEmpty()) {
            result.add(AiMessagePartBlock.ThinkingBlock(currentThinkingSteps.toList(), thinkingDurationSeconds))
            currentThinkingSteps = mutableListOf()
        }
    }

    forEachIndexed { index, part ->
        when (part) {
            is AiMessagePart.Reasoning -> {
                currentThinkingSteps.add(AiThinkingStep.ReasoningStep(part.text))
            }
            is AiMessagePart.Tool -> {
                currentThinkingSteps.add(AiThinkingStep.ToolStep(part))
            }
            else -> {
                flushThinkingSteps()
                result.add(AiMessagePartBlock.ContentBlock(part, index))
            }
        }
    }
    flushThinkingSteps()
    return result
}
