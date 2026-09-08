package io.legado.app.ui.widget.components.player

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** 播放器共用的播放/暂停键，包含图标与容器形变。 */
@Composable
fun AnimatedPlayPauseButton(
    isPlaying: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val transition = updateTransition(targetState = isPlaying, label = "PlayerPlayPause")
    val morphProgress by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 300, easing = FastOutSlowInEasing) },
        label = "PlayerPlayPauseMorph",
    ) { playing -> if (playing) 1f else 0f }
    val containerColor by transition.animateColor(
        transitionSpec = { tween(durationMillis = 300, easing = FastOutSlowInEasing) },
        label = "PlayerPlayPauseContainer",
    ) { playing ->
        if (playing) LegadoTheme.colorScheme.primaryContainer
        else LegadoTheme.colorScheme.secondaryContainer
    }
    val contentColor by transition.animateColor(
        transitionSpec = { tween(durationMillis = 300, easing = FastOutSlowInEasing) },
        label = "PlayerPlayPauseContent",
    ) { playing ->
        if (playing) LegadoTheme.colorScheme.onPrimaryContainer
        else LegadoTheme.colorScheme.onSecondaryContainer
    }
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(72.dp)
            .semantics { this.contentDescription = contentDescription }
            .clip(playerButtonContainerShape(morphProgress))
            .combinedClickable(
                interactionSource = interactionSource,
                role = Role.Button,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = min(size.width, size.height) / 2f
            drawPath(playerButtonContainerPath(radius, morphProgress), containerColor)
        }
        Canvas(modifier = Modifier.size(36.dp)) {
            rotate(degrees = 90f * morphProgress) {
                drawPath(playPauseIconPath(size.minDimension / 24f, morphProgress), contentColor)
            }
        }
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(56.dp),
                strokeWidth = 3.dp,
                color = contentColor,
            )
        }
    }
}

private fun playerButtonContainerPath(radius: Float, progress: Float): Path = Path().apply {
    val center = radius
    val baseRadius = radius * (1f - 0.055f * progress)
    val lobeAmplitude = radius * 0.055f * progress
    val rotation = 30f * progress / 180f * PI.toFloat()
    repeat(BUTTON_PATH_SAMPLES + 1) { index ->
        val sourceAngle =
            index.toFloat() / BUTTON_PATH_SAMPLES * 2f * PI.toFloat() - PI.toFloat() / 2f
        val angle = sourceAngle + rotation
        val animatedRadius = baseRadius + lobeAmplitude * cos(8f * sourceAngle)
        val x = center + animatedRadius * cos(angle)
        val y = center + animatedRadius * sin(angle)
        if (index == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun playerButtonContainerShape(progress: Float) = GenericShape { size, _ ->
    val radius = min(size.width, size.height) / 2f
    addPath(playerButtonContainerPath(radius, progress))
}

private fun playPauseIconPath(scale: Float, progress: Float): Path = Path().apply {
    for (subPath in PLAY_ICON_POINTS.indices) {
        val from = PLAY_ICON_POINTS[subPath]
        val to = PAUSE_ICON_POINTS[subPath]
        from.indices.forEach { index ->
            val point = from[index]
            val target = to[index]
            val x = (point.x + (target.x - point.x) * progress) * scale
            val y = (point.y + (target.y - point.y) * progress) * scale
            if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

private data class MorphPoint(val x: Float, val y: Float)

private const val BUTTON_PATH_SAMPLES = 128

private val PLAY_ICON_POINTS = arrayOf(
    arrayOf(
        MorphPoint(8f, 5f), MorphPoint(8f, 12f), MorphPoint(19f, 12f),
        MorphPoint(19f, 12f), MorphPoint(8f, 5f),
    ),
    arrayOf(
        MorphPoint(8f, 12f), MorphPoint(8f, 19f), MorphPoint(19f, 12f),
        MorphPoint(19f, 12f), MorphPoint(8f, 12f),
    ),
)

private val PAUSE_ICON_POINTS = arrayOf(
    arrayOf(
        MorphPoint(5f, 6f), MorphPoint(5f, 10f), MorphPoint(19f, 10f),
        MorphPoint(19f, 6f), MorphPoint(5f, 6f),
    ),
    arrayOf(
        MorphPoint(5f, 14f), MorphPoint(5f, 18f), MorphPoint(19f, 18f),
        MorphPoint(19f, 14f), MorphPoint(5f, 14f),
    ),
)
