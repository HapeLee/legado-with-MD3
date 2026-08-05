package io.legado.app.ui.book.read

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.image.cover.BookCoverImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.basic.VerticalDivider
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun ReadAloudCapsule(
    book: Book?,
    isPaused: Boolean,
    offsetXDp: Float,
    offsetYDp: Float,
    progress: Float,
    autoCollapse: Boolean,
    onPositionChanged: (xDp: Float, yDp: Float) -> Unit,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
    onOpenPlayer: () -> Unit,
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

    var collapsed by remember { mutableStateOf(false) }
    var touchTimestamp by remember { mutableFloatStateOf(0f) }

    // Auto-collapse timer
    LaunchedEffect(autoCollapse, collapsed) {
        if (autoCollapse && !collapsed) {
            delay(3000.milliseconds)
            collapsed = true
        }
    }

    // Reset collapse on any touch
    fun onTouched() {
        touchTimestamp = System.currentTimeMillis().toFloat()
        if (collapsed) {
            collapsed = false
        }
    }

    val capsuleHeight by animateDpAsState(
        targetValue = if (collapsed) 16.dp else 48.dp,
        animationSpec = tween(durationMillis = 250),
        label = "capsuleHeight",
    )

    val cornerRadius by animateDpAsState(
        targetValue = if (collapsed) 8.dp else 28.dp,
        animationSpec = tween(durationMillis = 250),
        label = "cornerRadius",
    )

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
                        onTouched()
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTouched() },
                    )
                },
            shape = RoundedCornerShape(cornerRadius),
            color = LegadoTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            AnimatedContent(
                targetState = collapsed,
                transitionSpec = {
                    fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.9f) togetherWith
                            fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.9f)
                },
                label = "capsuleContent",
            ) { isCollapsed ->
                if (isCollapsed) {
                    CollapsedCapsuleContent(
                        isPaused = isPaused,
                        onTogglePause = onTogglePause,
                        onExpand = { collapsed = false },
                    )
                } else {
                    ExpandedCapsuleContent(
                        book = book,
                        isPaused = isPaused,
                        progress = progress,
                        coverRotation = coverRotation.value,
                        onTogglePause = onTogglePause,
                        onStop = onStop,
                        onOpenPlayer = onOpenPlayer,
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsedCapsuleContent(
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onExpand: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onTogglePause)
                .padding(all = 8.dp),
        ) {
            Icon(
                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = stringResource(
                    if (isPaused) R.string.resume_read_aloud else R.string.pause_read_aloud
                ),
                modifier = Modifier
                    .size(16.dp),
                tint = LegadoTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(
                    if (isPaused) R.string.resume_read_aloud else R.string.pause_read_aloud
                ),
                style = LegadoTheme.typography.labelSmall,
                color = LegadoTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 2.dp),
            )
        }

        VerticalDivider(
            modifier = Modifier
                .padding(start = 4.dp)
                .height(10.dp)
                .width(1.dp),
            color = LegadoTheme.colorScheme.outlineVariant,
        )

        Icon(
            imageVector = Icons.Default.ExpandLess,
            contentDescription = stringResource(R.string.expand),
            modifier = Modifier
                .clickable(onClick = onExpand)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .size(16.dp),
            tint = LegadoTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ExpandedCapsuleContent(
    book: Book?,
    isPaused: Boolean,
    progress: Float,
    coverRotation: Float,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
    onOpenPlayer: () -> Unit,
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
            modifier = Modifier
                .size(40.dp)
                .graphicsLayer { rotationZ = coverRotation }
                .clip(CircleShape)
                .clickable(onClick = onOpenPlayer),
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(LegadoTheme.colorScheme.secondaryContainer)
                .clickable(onClick = onTogglePause),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = stringResource(
                    if (isPaused) R.string.resume_read_aloud else R.string.pause_read_aloud
                ),
                modifier = Modifier.size(24.dp),
                tint = LegadoTheme.colorScheme.onSecondaryContainer,
            )
        }
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
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.stop_read_aloud),
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onStop),
                tint = LegadoTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
