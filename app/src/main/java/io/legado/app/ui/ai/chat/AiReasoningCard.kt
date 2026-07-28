package io.legado.app.ui.ai.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Three states for the reasoning card display.
 */
enum class ReasoningCardState(val expanded: Boolean) {
    /** Fully hidden — only the toggle header is visible. */
    Collapsed(false),
    /** Preview mode during streaming — limited height with fade gradient. */
    Preview(true),
    /** Fully expanded — all content visible. */
    Expanded(true),
}

@Stable
private class ReasoningState(
    val scrollState: ScrollState,
) {
    var expandState by mutableStateOf(ReasoningCardState.Collapsed)
    var elapsedSeconds by mutableStateOf(0f)
}

@Composable
private fun rememberReasoningState(
    text: String,
    isStreaming: Boolean,
    messageCreatedAt: Long,
): Pair<ReasoningState, Boolean> {
    val scrollState = rememberScrollState()
    val state = remember(messageCreatedAt) {
        ReasoningState(scrollState = scrollState)
    }

    // Auto-expand to Preview during streaming, auto-close after
    LaunchedEffect(text, isStreaming) {
        if (isStreaming) {
            if (!state.expandState.expanded) {
                state.expandState = ReasoningCardState.Preview
            }
            scrollState.animateScrollTo(scrollState.maxValue)
        } else {
            if (state.expandState.expanded) {
                state.expandState = ReasoningCardState.Collapsed
            }
        }
    }

    // Duration timer during streaming - update once per second
    LaunchedEffect(isStreaming) {
        if (isStreaming) {
            while (isActive) {
                state.elapsedSeconds = (System.currentTimeMillis() - messageCreatedAt) / 1000f
                delay(1000)
            }
        } else {
            state.elapsedSeconds = (System.currentTimeMillis() - messageCreatedAt) / 1000f
        }
    }

    return state to isStreaming
}

/**
 * A reasoning card with three display states:
 * - **Collapsed**: header row with "Thinking" label, click to expand
 * - **Preview** (streaming): max 100dp height with vertical fade gradient, auto-scrolls
 * - **Expanded**: full content with text selection
 *
 * Auto-enters Preview during streaming, auto-collapses when streaming ends.
 */
@Composable
fun ReasoningCard(
    text: String,
    isStreaming: Boolean,
    messageCreatedAt: Long,
    modifier: Modifier = Modifier,
) {
    if (text.isBlank()) return

    val (state, loading) = rememberReasoningState(text, isStreaming, messageCreatedAt)
    val fadeHeight = 64f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    state.expandState = when (state.expandState) {
                        ReasoningCardState.Collapsed -> ReasoningCardState.Expanded
                        ReasoningCardState.Preview -> ReasoningCardState.Expanded
                        ReasoningCardState.Expanded -> if (loading)
                            ReasoningCardState.Preview else ReasoningCardState.Collapsed
                    }
                }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = LegadoTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.width(6.dp))
            // Animated label: show duration during streaming, static text otherwise
            AnimatedContent(
                targetState = if (loading) {
                    stringResource(R.string.ai_thinking_seconds, state.elapsedSeconds)
                } else {
                    stringResource(R.string.ai_reasoning_done)
                },
                transitionSpec = {
                    (slideInVertically { it } + fadeIn()).togetherWith(
                        slideOutVertically { -it } + fadeOut()
                    )
                },
                label = "ReasoningLabel"
            ) { label ->
                AppText(
                    text = label,
                    style = LegadoTheme.typography.labelMedium,
                    color = LegadoTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (state.expandState.expanded)
                    Icons.Default.KeyboardArrowDown
                else
                    Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .then(
                        if (state.expandState.expanded)
                            Modifier  // pointing up when expanded
                        else Modifier
                    ),
                tint = LegadoTheme.colorScheme.outline
            )
        }

        // Content area
        if (state.expandState != ReasoningCardState.Collapsed) {
            val isPreview = state.expandState == ReasoningCardState.Preview
            val contentModifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isPreview) {
                        Modifier
                            .graphicsLayer { alpha = 0.99f }
                            .drawWithCache {
                                val brush = Brush.verticalGradient(
                                    startY = 0f,
                                    endY = size.height,
                                    colorStops = arrayOf(
                                        0.0f to Color.Transparent,
                                        (fadeHeight / size.height) to Color.Black,
                                        (1 - fadeHeight / size.height) to Color.Black,
                                        1.0f to Color.Transparent
                                    )
                                )
                                onDrawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = brush,
                                        size = Size(size.width, size.height),
                                        blendMode = BlendMode.DstIn,
                                    )
                                }
                            }
                            .heightIn(max = 100.dp)
                            .verticalScroll(state.scrollState)
                    } else Modifier
                )

            Column(modifier = contentModifier.padding(bottom = 6.dp)) {
                if (loading) {
                    AppText(
                        text = text,
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    SelectionContainer {
                        AppText(
                            text = text,
                            style = LegadoTheme.typography.bodySmall,
                            color = LegadoTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
