package io.legado.app.ui.book.read

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.series.MediumPlainButton
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.image.cover.BookCoverImage
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.isActive

@Composable
fun ReadAloudCapsule(
    book: Book?,
    isPaused: Boolean,
    offsetXDp: Float,
    offsetYDp: Float,
    progress: Float,
    onPositionChanged: (xDp: Float, yDp: Float) -> Unit,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
) {
    val density = LocalDensity.current
    val currentOnPositionChanged by rememberUpdatedState(onPositionChanged)

    val coverRotation = remember { Animatable(0f) }
    LaunchedEffect(isPaused) {
        while (!isPaused && isActive) {
            coverRotation.animateTo(
                targetValue = coverRotation.value + 360f,
                animationSpec = tween(durationMillis = 12_000, easing = LinearEasing),
            )
        }
    }

    var offsetX by remember {
        mutableFloatStateOf(with(density) { Dp(offsetXDp).toPx() })
    }
    var offsetY by remember {
        mutableFloatStateOf(with(density) { Dp(offsetYDp).toPx() })
    }
    LaunchedEffect(offsetXDp, offsetYDp, density) {
        offsetX = with(density) { Dp(offsetXDp).toPx() }
        offsetY = with(density) { Dp(offsetYDp).toPx() }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 88.dp)
                .offset { IntOffset(offsetX.toInt(), offsetY.toInt()) }
                .pointerInput(density) {
                    detectDragGestures(
                        onDragEnd = {
                            currentOnPositionChanged(
                                offsetX / density.density,
                                offsetY / density.density,
                            )
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                },
            shape = RoundedCornerShape(28.dp),
            color = LegadoTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier.padding(all = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BookCoverImage(
                    name = book?.name,
                    author = book?.author,
                    path = book?.getDisplayCover(),
                    sourceOrigin = book?.origin,
                    modifier = Modifier.size(40.dp).graphicsLayer {
                        rotationZ = coverRotation.value
                    }.clip(CircleShape),
                )
                MediumTonalButton(
                    onClick = onTogglePause,
                    modifier = Modifier.size(40.dp),
                    icon = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = stringResource(
                        if (isPaused) R.string.resume_read_aloud else R.string.pause_read_aloud
                    ),
                )
                Box(
                    modifier = Modifier
                        .size(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.fillMaxSize(),
                        progress = { progress.coerceIn(0f, 1f) },
                        strokeWidth = 2.dp,
                    )
                    MediumPlainButton(
                        onClick = onStop,
                        modifier = Modifier.size(40.dp),
                        icon = Icons.Default.Close,
                        contentDescription = stringResource(R.string.stop_read_aloud),
                        tint = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
