package io.legado.app.ui.ai.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.domain.model.AiMessagePart
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.text.MarkdownBlock
import kotlinx.coroutines.delay

/**
 * Unified thinking/tool card.
 *
 * - Reasoning can expand while streaming
 * - Tool calls can opt out and expand only after a user click
 * - Auto-collapses when streaming ends
 * - Header: left icon + title | right TextCard (time, reasoning only) + expand arrow
 */
@Composable
fun AiThinkingCard(
    steps: List<AiThinkingStep>,
    isStreaming: Boolean,
    durationSeconds: Int = 0,
    modifier: Modifier = Modifier,
    autoExpandWhileStreaming: Boolean = true,
) {
    val hasTools = steps.any { it is AiThinkingStep.ToolStep }
    val hasReasoning = steps.any { it is AiThinkingStep.ReasoningStep }
    val headerTitle = when {
        hasTools && hasReasoning -> "思考与工具"
        hasTools -> "工具调用"
        isStreaming -> "思考中"
        else -> "思考"
    }
    val headerIcon = if (hasTools) Icons.Default.Build else Icons.Default.AutoAwesome

    // Live timer while streaming (fallback before first content token arrives)
    var liveSeconds by remember { mutableStateOf(0) }
    LaunchedEffect(isStreaming, durationSeconds) {
        if (isStreaming && durationSeconds == 0) {
            liveSeconds = 0
            while (true) {
                delay(1000)
                liveSeconds++
            }
        }
    }
    val displaySeconds = if (durationSeconds > 0) durationSeconds else liveSeconds

    // Tool cards disable this so only an explicit user click can expand them.
    var expanded by remember {
        mutableStateOf(isStreaming && autoExpandWhileStreaming)
    }
    LaunchedEffect(isStreaming, autoExpandWhileStreaming) {
        if (autoExpandWhileStreaming) {
            expanded = isStreaming
        }
    }

    // Show time only for reasoning (not tool-only)
    val showTime = hasReasoning && displaySeconds > 0

    NormalCard(modifier = modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                imageVector = headerIcon,
                contentDescription = null,
                tint = LegadoTheme.colorScheme.onSurface,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            AppText(
                text = headerTitle,
                style = LegadoTheme.typography.labelSmallEmphasized,
                color = LegadoTheme.colorScheme.onSurface,
                maxLines = 1
            )

            Spacer(modifier = Modifier.weight(1f))

            if (showTime) {
                TextCard(text = "${displaySeconds}s")
                Spacer(modifier = Modifier.width(8.dp))
            }

            AppIcon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = LegadoTheme.colorScheme.onSurfaceVariant
            )
        }

        // Body
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                steps.forEach { step ->
                    when (step) {
                        is AiThinkingStep.ReasoningStep -> {
                            MarkdownBlock(
                                content = step.text,
                                style = LegadoTheme.typography.bodySmall.copy(
                                    color = LegadoTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                        is AiThinkingStep.ToolStep -> {
                            ToolStepContent(tool = step.tool)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolStepContent(tool: AiMessagePart.Tool) {
    val hasContent = tool.output.isNotBlank() || tool.input.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppText(
                text = tool.toolName,
                style = LegadoTheme.typography.labelSmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant
            )
            if (!hasContent) {
                Spacer(modifier = Modifier.width(6.dp))
                AppText(
                    text = "· 无输出",
                    style = LegadoTheme.typography.labelSmall,
                    color = LegadoTheme.colorScheme.outline
                )
            }
        }
        if (hasContent) {
            MarkdownBlock(
                content = buildToolContent(tool),
                style = LegadoTheme.typography.bodySmall.copy(
                    color = LegadoTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun buildToolContent(tool: AiMessagePart.Tool): String = buildString {
    if (tool.input.isNotBlank()) {
        append("**输入:**\n")
        append(tool.input)
        if (tool.output.isNotBlank()) append("\n\n")
    }
    if (tool.output.isNotBlank()) {
        append("**结果:**\n")
        append(tool.output)
    }
}
