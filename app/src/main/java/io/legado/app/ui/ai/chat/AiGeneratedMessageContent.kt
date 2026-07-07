package io.legado.app.ui.ai.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.AiMessagePart
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.text.MarkdownBlock

@Composable
fun AiGeneratedMessageContent(
    isUser: Boolean,
    isAssistant: Boolean,
    isStreaming: Boolean,
    message: AiChatMessageUi,
    modifier: Modifier = Modifier,
    assistantLabel: String = "",
    showHeader: Boolean = true,
    reasoningAutoExpandWhileStreaming: Boolean = true,
    onOpenBookInfo: (AiChatBookResultUi) -> Unit = {},
    onCopy: (() -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null,
    onSwitchBranch: ((direction: Int) -> Unit)? = null,
) {
    val groupedParts = remember(message.parts, message.thinkingDuration) {
        message.parts.groupMessageParts(message.thinkingDuration)
    }
    val reasoningSteps = groupedParts
        .filterIsInstance<AiMessagePartBlock.ThinkingBlock>()
        .flatMap { block -> block.steps.filterIsInstance<AiThinkingStep.ReasoningStep>() }
    val toolSteps = groupedParts
        .filterIsInstance<AiMessagePartBlock.ThinkingBlock>()
        .flatMap { block -> block.steps.filterIsInstance<AiThinkingStep.ToolStep>() }
    val contentBlocks = groupedParts.filterIsInstance<AiMessagePartBlock.ContentBlock>()

    Column(
        modifier = modifier.animateContentSize()
    ) {
        if (showHeader) {
            AppText(
                text = if (isUser) {
                    stringResource(R.string.ai_you)
                } else {
                    assistantLabel.ifBlank { stringResource(R.string.ai_assistant) }
                },
                style = LegadoTheme.typography.labelMedium,
                color = LegadoTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (reasoningSteps.isNotEmpty()) {
            AiThinkingCard(
                steps = reasoningSteps,
                isStreaming = isStreaming,
                durationSeconds = message.thinkingDuration,
                autoExpandWhileStreaming = reasoningAutoExpandWhileStreaming,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        if (toolSteps.isNotEmpty()) {
            AiThinkingCard(
                steps = toolSteps,
                isStreaming = isStreaming,
                durationSeconds = message.thinkingDuration,
                autoExpandWhileStreaming = false,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        contentBlocks.forEach { block ->
            when (val part = block.part) {
                is AiMessagePart.Text -> {
                    MessageTextContent(
                        text = part.text,
                        isUser = isUser,
                        isStreaming = isStreaming,
                    )
                }

                is AiMessagePart.BookResult -> {
                    BookResultsList(
                        books = listOf(
                            AiChatBookResultUi(
                                bookUrl = part.bookUrl,
                                name = part.name,
                                author = part.author,
                                origin = part.origin,
                                coverPath = part.coverPath,
                                latestChapterTitle = part.latestChapterTitle,
                                currentChapterTitle = part.currentChapterTitle,
                                intro = part.intro
                            )
                        ),
                        onOpenBookInfo = onOpenBookInfo
                    )
                }

                else -> Unit
            }
        }

        if (groupedParts.isEmpty()) {
            val displayReasoning = message.parts.filterIsInstance<AiMessagePart.Reasoning>()
                .joinToString("\n\n") { it.text }.trim()
                .takeIf { it.isNotBlank() } ?: message.reasoning
            if (!displayReasoning.isNullOrBlank()) {
                AiThinkingCard(
                    steps = listOf(AiThinkingStep.ReasoningStep(displayReasoning)),
                    isStreaming = isStreaming,
                    durationSeconds = message.thinkingDuration,
                    autoExpandWhileStreaming = reasoningAutoExpandWhileStreaming,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            val displayToolTrace = message.parts.filterIsInstance<AiMessagePart.Tool>()
                .joinToString("\n\n") { tool ->
                    buildString {
                        append("Tool: "); append(tool.toolName); append('\n')
                        append("ID: "); append(tool.toolCallId)
                        tool.input.takeIf { it.isNotBlank() }?.let { append('\n'); append(it) }
                        tool.output.takeIf { it.isNotBlank() }?.let {
                            append('\n'); append("Result: "); append(it)
                        }
                    }
                }.takeIf { it.isNotBlank() } ?: message.toolTrace
            if (!displayToolTrace.isNullOrBlank()) {
                TracePanel(
                    title = stringResource(R.string.ai_tool_trace),
                    content = displayToolTrace
                )
            }
            val displayContent = message.parts.filterIsInstance<AiMessagePart.Text>()
                .joinToString("\n\n") { it.text }.trim()
                .ifBlank { message.content }
            if (displayContent.isNotBlank()) {
                MessageTextContent(
                    text = displayContent,
                    isUser = isUser,
                    isStreaming = isStreaming,
                )
            } else if (isStreaming) {
                MessageTextContent(text = "", isUser = isUser, isStreaming = true)
            }
            if (message.bookResults.isNotEmpty()) {
                BookResultsList(books = message.bookResults, onOpenBookInfo = onOpenBookInfo)
            }
        }

        if (isAssistant && !isStreaming && (onCopy != null || onRegenerate != null)) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onSwitchBranch != null && message.totalBranches > 1) {
                    SmallPlainButton(
                        onClick = { onSwitchBranch(-1) },
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous branch",
                        modifier = Modifier.size(28.dp)
                    )
                    AppText(
                        text = "${message.branchIndex + 1}/${message.totalBranches}",
                        style = LegadoTheme.typography.labelSmall,
                        color = LegadoTheme.colorScheme.outline
                    )
                    SmallPlainButton(
                        onClick = { onSwitchBranch(1) },
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next branch",
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (onRegenerate != null) {
                    SmallPlainButton(
                        onClick = onRegenerate,
                        icon = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.ai_regenerate),
                        modifier = Modifier.size(32.dp)
                    )
                }
                if (onCopy != null) {
                    SmallPlainButton(
                        onClick = onCopy,
                        icon = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.copy_text),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageTextContent(
    text: String,
    isUser: Boolean,
    isStreaming: Boolean,
) {
    SelectionContainer {
        if (isUser) {
            AppText(
                text = text,
                style = LegadoTheme.typography.bodyLarge
            )
        } else if (text.isNotBlank()) {
            MarkdownBlock(
                content = text,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (isStreaming) {
            StreamingDots()
        }
    }
}

@Composable
private fun BookResultsList(
    books: List<AiChatBookResultUi>,
    onOpenBookInfo: (AiChatBookResultUi) -> Unit
) {
    if (books.isEmpty()) return
    Spacer(modifier = Modifier.height(10.dp))
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AppText(
            text = stringResource(R.string.ai_book_results),
            style = LegadoTheme.typography.labelMedium,
            color = LegadoTheme.colorScheme.outline
        )
        books.take(8).forEach { book ->
            ChatBookResultItem(
                book = book,
                onClick = { onOpenBookInfo(book) }
            )
        }
    }
}

@Composable
private fun ChatBookResultItem(
    book: AiChatBookResultUi,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(LegadoTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoilBookCover(
            name = book.name,
            author = book.author,
            path = book.coverPath,
            sourceOrigin = book.origin,
            modifier = Modifier
                .width(48.dp)
                .height(68.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = book.name.ifBlank { book.bookUrl },
                style = LegadoTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (book.author.isNotBlank()) {
                AppText(
                    text = book.author,
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val chapter = book.currentChapterTitle ?: book.latestChapterTitle
            if (!chapter.isNullOrBlank()) {
                AppText(
                    text = chapter,
                    style = LegadoTheme.typography.labelSmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!book.intro.isNullOrBlank()) {
                AppText(
                    text = book.intro,
                    style = LegadoTheme.typography.labelSmall,
                    color = LegadoTheme.colorScheme.outline,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StreamingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val dotCount by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dotCount"
    )
    val dots = ".".repeat(dotCount.toInt().coerceIn(1, 3))
    AppText(
        text = dots,
        color = LegadoTheme.colorScheme.outline,
        style = LegadoTheme.typography.bodyLarge
    )
}

@Composable
private fun TracePanel(
    title: String,
    content: String,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(
                text = title,
                style = LegadoTheme.typography.labelMedium,
                color = LegadoTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = LegadoTheme.colorScheme.outline
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 6.dp)
            ) {
                AppText(
                    text = content,
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
