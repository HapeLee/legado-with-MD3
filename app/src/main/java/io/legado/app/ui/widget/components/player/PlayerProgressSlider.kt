package io.legado.app.ui.widget.components.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme

/**
 * 播放器通用的进度条：拖动时放大轨道、松手提交进度。
 */
@Composable
fun PlayerProgressSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    val rangeLength = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1f)
    var isDragging by remember { mutableStateOf(false) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    var previewFraction by remember(valueRange) {
        mutableFloatStateOf(((value - valueRange.start) / rangeLength).coerceIn(0f, 1f))
    }
    LaunchedEffect(value, valueRange) {
        if (!isDragging) {
            previewFraction = ((value - valueRange.start) / rangeLength).coerceIn(0f, 1f)
        }
    }
    val trackScale by animateFloatAsState(
        targetValue = if (isDragging) 1.35f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "player_progress_track_scale",
    )
    val activeColor = LegadoTheme.colorScheme.onSurface
    val inactiveColor = LegadoTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(18.dp)
            .graphicsLayer { scaleY = trackScale }
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(valueRange, widthPx) {
                fun updatePreview(x: Float) {
                    previewFraction = (x / widthPx).coerceIn(0f, 1f)
                }
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        updatePreview(offset.x)
                    },
                    onDragEnd = {
                        isDragging = false
                        onValueChange(valueRange.start + previewFraction * rangeLength)
                    },
                    onDragCancel = { isDragging = false },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        updatePreview(change.position.x)
                    },
                )
            },
    ) {
        val centerY = size.height / 2f
        val progressX = size.width * previewFraction
        val trackHeight = 2.dp.toPx()
        val trackRadius = trackHeight / 2f
        val trackClip = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = centerY - trackRadius,
                    right = size.width,
                    bottom = centerY + trackRadius,
                    cornerRadius = CornerRadius(trackRadius),
                )
            )
        }
        val tickSpacing = 4.dp.toPx()
        val tickHeight = 2.dp.toPx()
        clipPath(trackClip) {
            var tickX = 0f
            while (tickX <= size.width) {
                drawLine(
                    color = inactiveColor,
                    start = Offset(tickX, centerY - tickHeight / 2f),
                    end = Offset(tickX, centerY + tickHeight / 2f),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                tickX += tickSpacing
            }
            if (progressX > 0f) {
                drawLine(
                    color = activeColor,
                    start = Offset(0f, centerY),
                    end = Offset(progressX, centerY),
                    strokeWidth = trackHeight,
                    cap = StrokeCap.Butt,
                )
            }
        }
    }
}
