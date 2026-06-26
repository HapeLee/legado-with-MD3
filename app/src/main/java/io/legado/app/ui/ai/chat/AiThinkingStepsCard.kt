package io.legado.app.ui.ai.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.text.AppText

/**
 * A card displaying a group of thinking steps (reasoning + tool calls).
 *
 * When there are more than [collapsedVisibleCount] steps, only the last
 * few are shown with a "show more" toggle.
 */
@Composable
fun AiThinkingStepsCard(
    steps: List<AiThinkingStep>,
    isStreaming: Boolean,
    messageCreatedAt: Long,
    modifier: Modifier = Modifier,
    collapsedVisibleCount: Int = 2,
) {
    if (steps.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    val canCollapse = steps.size > collapsedVisibleCount
    val visibleSteps = if (expanded || !canCollapse) {
        steps
    } else {
        steps.takeLast(collapsedVisibleCount)
    }

    NormalCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .animateContentSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Collapse/expand toggle - no padding, ripple clipped by card
            if (canCollapse) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (expanded)
                            Icons.Default.KeyboardArrowDown
                        else
                            Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = LegadoTheme.colorScheme.primary,
                    )
                    AppText(
                        modifier = Modifier.padding(start = 8.dp),
                        text = if (expanded) {
                            stringResource(R.string.ai_thinking_collapse)
                        } else {
                            stringResource(R.string.ai_thinking_show_more, steps.size - collapsedVisibleCount)
                        },
                        style = LegadoTheme.typography.labelMedium,
                        color = LegadoTheme.colorScheme.primary,
                    )
                }
            }

            // Content
            Column {
                visibleSteps.forEachIndexed { index, step ->
                    ThinkingStepRow(
                        step = step,
                        isStreaming = isStreaming && index == visibleSteps.lastIndex,
                        messageCreatedAt = messageCreatedAt,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThinkingStepRow(
    step: AiThinkingStep,
    isStreaming: Boolean,
    messageCreatedAt: Long,
) {
    when (step) {
        is AiThinkingStep.ReasoningStep -> {
            ReasoningStepRow(
                text = step.text,
                isStreaming = isStreaming,
                messageCreatedAt = messageCreatedAt,
            )
        }
        is AiThinkingStep.ToolStep -> {
            ToolStepRow(tool = step.tool)
        }
    }
}

@Composable
private fun ReasoningStepRow(
    text: String,
    isStreaming: Boolean,
    messageCreatedAt: Long,
) {
    ReasoningCard(
        text = text,
        isStreaming = isStreaming,
        messageCreatedAt = messageCreatedAt,
    )
}

@Composable
private fun ToolStepRow(
    tool: AiMessagePart.Tool,
) {
    var expanded by remember { mutableStateOf(false) }
    val hasContent = tool.input.isNotBlank() || tool.output.isNotBlank()

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (hasContent) {
                        Modifier
                            .clickable { expanded = !expanded }
                    } else Modifier
                )
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(LegadoTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = LegadoTheme.colorScheme.primary,
                )
            }

            // Label
            AppText(
                text = tool.toolName,
                style = LegadoTheme.typography.labelMedium,
                color = LegadoTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Status
            val statusText = when {
                tool.output.isNotBlank() -> stringResource(R.string.ai_tool_done)
                tool.approvalState == io.legado.app.domain.model.AiToolApprovalState.PENDING ->
                    stringResource(R.string.ai_tool_pending)
                else -> stringResource(R.string.ai_tool_running)
            }
            AppText(
                text = statusText,
                style = LegadoTheme.typography.labelSmall,
                color = LegadoTheme.colorScheme.outline
            )

            if (hasContent) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = LegadoTheme.colorScheme.outline
                )
            }
        }

        // Expandable tool details
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, end = 4.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (tool.input.isNotBlank()) {
                    AppText(
                        text = stringResource(R.string.ai_tool_input),
                        style = LegadoTheme.typography.labelSmall,
                        color = LegadoTheme.colorScheme.outline
                    )
                    AppText(
                        text = tool.input,
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (tool.output.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    AppText(
                        text = stringResource(R.string.ai_tool_output),
                        style = LegadoTheme.typography.labelSmall,
                        color = LegadoTheme.colorScheme.outline
                    )
                    AppText(
                        text = tool.output,
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        maxLines = 12,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
