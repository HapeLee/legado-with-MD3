package io.legado.app.ui.widget.components.swipe

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeActionContainer(
    modifier: Modifier = Modifier,
    startAction: SwipeAction? = null,
    endAction: SwipeAction? = null,
    content: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance ->
            totalDistance * 0.6f
        }
    )

    LaunchedEffect(dismissState.targetValue) {
        if (dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd) {
            if (startAction?.hapticFeedback == true) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    LaunchedEffect(dismissState.settledValue) {
        when (dismissState.settledValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                startAction?.onSwipe?.invoke()
                dismissState.reset()
            }

            SwipeToDismissBoxValue.EndToStart -> {
                endAction?.onSwipe?.invoke()
                dismissState.reset()
            }

            else -> {}
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = startAction != null,
        enableDismissFromEndToStart = endAction != null,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val progress = dismissState.progress

            when (direction) {

                SwipeToDismissBoxValue.StartToEnd -> {
                    startAction?.let { action ->
                        SwipeBackground(
                            action = action,
                            progress = progress,
                            alignStart = true
                        )
                    }
                }

                SwipeToDismissBoxValue.EndToStart -> {
                    endAction?.let { action ->
                        SwipeBackground(
                            action = action,
                            progress = progress,
                            alignStart = false
                        )
                    }
                }

                else -> {}
            }
        },
        content = {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
                shape = RectangleShape
            ) {
                content()
            }
        }
    )
}

@Composable
private fun SwipeBackground(
    action: SwipeAction,
    progress: Float,
    alignStart: Boolean
) {
    val isThresholdReached = progress > 0.6f

    val backgroundColor by animateColorAsState(
        targetValue = if (isThresholdReached)
            action.background
        else
            MaterialTheme.colorScheme.surfaceVariant,
        label = "bgColor"
    )

    val iconScale by animateFloatAsState(
        targetValue = if (isThresholdReached) 1.3f
        else progress.coerceIn(0.5f, 1f),
        label = "iconScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(horizontal = 24.dp),
        contentAlignment = if (alignStart)
            Alignment.CenterStart
        else
            Alignment.CenterEnd
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
            tint = if (isThresholdReached)
                contentColorFor(action.background)
            else
                MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}