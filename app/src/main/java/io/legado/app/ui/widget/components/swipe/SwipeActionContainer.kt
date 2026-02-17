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
    content: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance ->
            totalDistance * 0.6f
        }
    )
    val isThresholdReached =
        dismissState.progress > 0.5f && dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd

    LaunchedEffect(dismissState.targetValue) {
        if (dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd) {
            if (startAction?.hapticFeedback == true) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    LaunchedEffect(dismissState.settledValue) {
        if (dismissState.settledValue == SwipeToDismissBoxValue.StartToEnd) {
            startAction?.onSwipe?.invoke()
            dismissState.reset()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = startAction != null,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val progress = dismissState.progress

            if (direction == SwipeToDismissBoxValue.StartToEnd) {
                val backgroundColor by animateColorAsState(
                    targetValue = if (isThresholdReached) startAction!!.background
                    else MaterialTheme.colorScheme.surfaceVariant,
                    label = "bgColor"
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    val iconScale by animateFloatAsState(
                        targetValue = if (isThresholdReached) 1.3f else progress.coerceIn(0.5f, 1f),
                        label = "iconScale"
                    )

                    Icon(
                        imageVector = startAction!!.icon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            },
                        tint = if (isThresholdReached)
                            contentColorFor(startAction.background)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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