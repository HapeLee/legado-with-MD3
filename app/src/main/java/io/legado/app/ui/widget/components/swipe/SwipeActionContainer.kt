package io.legado.app.ui.widget.components.swipe

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import kotlin.math.absoluteValue

/** 滑动露出底块上下内缩量，让操作提示呈现为圆角矩形而不是整行色块。 */
private val SwipeRevealVerticalInset = 6.dp

/** 滑动露出底块在屏幕边缘一侧的内缩量，让圆角矩形与屏幕边缘之间保留边距。 */
private val SwipeRevealEdgeInset = 16.dp

/** 滑动露出底块的圆角半径。 */
private val SwipeRevealCornerRadius = 16.dp

/** 滑动操作图标尺寸。 */
private val SwipeActionIconSize = 24.dp

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
        modifier = modifier.then(
            swipeActionsSemantics(startAction, endAction)
        ),
        enableDismissFromStartToEnd = startAction != null,
        enableDismissFromEndToStart = endAction != null,
        backgroundContent = {
            // offset 在拖动过程中每帧变化，用派生状态收敛方向，避免拖动时逐帧重组背景层
            val direction by remember(dismissState) {
                derivedStateOf { dismissState.dismissDirection }
            }

            when (direction) {

                SwipeToDismissBoxValue.StartToEnd -> {
                    startAction?.let { action ->
                        SwipeBackground(
                            dismissState = dismissState,
                            action = action,
                            alignStart = true
                        )
                    }
                }

                SwipeToDismissBoxValue.EndToStart -> {
                    endAction?.let { action ->
                        SwipeBackground(
                            dismissState = dismissState,
                            action = action,
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
                color = Color.Transparent,
                shape = RectangleShape
            ) {
                content()
            }
        }
    )
}

private fun swipeActionsSemantics(
    startAction: SwipeAction?,
    endAction: SwipeAction?
): Modifier {
    val accessibilityActions = buildList {
        startAction?.contentDescription?.let { label ->
            add(
                CustomAccessibilityAction(label = label) {
                    startAction.onSwipe()
                    true
                }
            )
        }
        endAction?.contentDescription?.let { label ->
            add(
                CustomAccessibilityAction(label = label) {
                    endAction.onSwipe()
                    true
                }
            )
        }
    }
    if (accessibilityActions.isEmpty()) return Modifier
    return Modifier.semantics {
        customActions = accessibilityActions
    }
}

@Composable
private fun SwipeBackground(
    action: SwipeAction,
    dismissState: SwipeToDismissBoxState,
    alignStart: Boolean
) {
    val thresholdDirection = if (alignStart) {
        SwipeToDismissBoxValue.StartToEnd
    } else {
        SwipeToDismissBoxValue.EndToStart
    }
    // 只在越过阈值时切换，避免拖动过程中逐帧重组
    val isThresholdReached by remember(dismissState) {
        derivedStateOf { dismissState.targetValue == thresholdDirection }
    }

    val backgroundColor = animateColorAsState(
        targetValue = if (isThresholdReached) {
            action.background
        } else {
            LegadoTheme.colorScheme.surfaceVariant
        },
        label = "bgColor"
    )
    val iconScale = animateFloatAsState(
        targetValue = if (isThresholdReached) 1.2f else 1f,
        label = "iconScale"
    )
    val iconTint = if (isThresholdReached) {
        contentColorFor(action.background)
    } else {
        LegadoTheme.colorScheme.onSurfaceVariant
    }
    val iconPainter = rememberVectorPainter(action.icon)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // offset 在首次布局完成后才可读取，因此只能放到绘制阶段；
                // 本组件仅在方向不为 Settled 时才会组合，此时 offset 必然已初始化。
                val revealed = dismissState.requireOffset().absoluteValue
                    .coerceIn(0f, size.width)
                if (revealed <= 0f) return@drawBehind

                val verticalInset = SwipeRevealVerticalInset.toPx()
                val edgeInset = SwipeRevealEdgeInset.toPx()
                val revealedHeight = (size.height - verticalInset * 2f).coerceAtLeast(0f)
                // 外边缘与屏幕保持边距，内边缘仍与内容滑出的位置对齐
                val revealedLeft = if (alignStart) edgeInset else size.width - revealed
                val revealedWidth = (revealed - edgeInset).coerceAtLeast(0f)
                if (revealedWidth <= 0f) return@drawBehind

                // 随滑动距离增长的圆角底块
                drawRoundRect(
                    color = backgroundColor.value,
                    topLeft = Offset(revealedLeft, verticalInset),
                    size = Size(revealedWidth, revealedHeight),
                    cornerRadius = CornerRadius(SwipeRevealCornerRadius.toPx())
                )

                val iconSize = SwipeActionIconSize.toPx() * iconScale.value
                // 底块被拉开到能容纳图标之后，图标才居中出现并渐显
                val iconAlpha = ((revealedWidth - iconSize) / iconSize).coerceIn(0f, 1f)
                if (iconAlpha <= 0f) return@drawBehind

                val iconLeft = revealedLeft + revealedWidth / 2f - iconSize / 2f
                val iconTop = size.height / 2f - iconSize / 2f
                translate(left = iconLeft, top = iconTop) {
                    with(iconPainter) {
                        draw(
                            size = Size(iconSize, iconSize),
                            alpha = iconAlpha,
                            colorFilter = ColorFilter.tint(iconTint)
                        )
                    }
                }
            }
    )
}
